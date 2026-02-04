package com.phenix.wirelessadb.model

import org.junit.Assert.*
import org.junit.Test

class P2PTokenTest {

  private fun createToken(
    code: String = "ABC-123-XYZ",
    deviceHash: String = "testHash123",
    sessionId: String = "session-001",
    expiresAt: Long = System.currentTimeMillis() + 1000 * 60, // 1 minute from now
    isPersistent: Boolean = false,
    state: TokenState = TokenState.PENDING
  ): P2PToken {
    return P2PToken(
      code = code,
      deviceHash = deviceHash,
      sessionId = sessionId,
      expiresAt = expiresAt,
      isPersistent = isPersistent,
      state = state
    )
  }

  // ============================================================
  // masked() tests
  // ============================================================

  @Test
  fun `masked returns properly formatted masked code`() {
    val token = createToken(code = "ABC-123-XYZ")
    assertEquals("***-***-XYZ", token.masked())
  }

  @Test
  fun `masked handles different last segment`() {
    val token = createToken(code = "ABC-DEF-GHJ")
    assertEquals("***-***-GHJ", token.masked())
  }

  @Test
  fun `masked handles code without dashes gracefully`() {
    val token = createToken(code = "ABCDEFGHI")
    assertEquals("***-***-GHI", token.masked())
  }

  @Test
  fun `masked handles short code`() {
    val token = createToken(code = "AB")
    assertEquals("***-***-AB", token.masked())
  }

  // ============================================================
  // isExpired() tests
  // ============================================================

  @Test
  fun `isExpired returns false for future expiration`() {
    val token = createToken(expiresAt = System.currentTimeMillis() + 60000)
    assertFalse(token.isExpired())
  }

  @Test
  fun `isExpired returns true for past expiration`() {
    val token = createToken(expiresAt = System.currentTimeMillis() - 1000)
    assertTrue(token.isExpired())
  }

  @Test
  fun `isExpired returns true for exactly expired`() {
    val token = createToken(expiresAt = System.currentTimeMillis() - 1)
    assertTrue(token.isExpired())
  }

  // ============================================================
  // isValid() tests
  // ============================================================

  @Test
  fun `isValid returns true for non-expired pending token`() {
    val token = createToken(
      expiresAt = System.currentTimeMillis() + 60000,
      state = TokenState.PENDING
    )
    assertTrue(token.isValid())
  }

  @Test
  fun `isValid returns false for expired token`() {
    val token = createToken(
      expiresAt = System.currentTimeMillis() - 1000,
      state = TokenState.PENDING
    )
    assertFalse(token.isValid())
  }

  @Test
  fun `isValid returns false for revoked token`() {
    val token = createToken(
      expiresAt = System.currentTimeMillis() + 60000,
      state = TokenState.REVOKED
    )
    assertFalse(token.isValid())
  }

  @Test
  fun `isValid returns true for connected token`() {
    val token = createToken(
      expiresAt = System.currentTimeMillis() + 60000,
      state = TokenState.CONNECTED
    )
    assertTrue(token.isValid())
  }

  @Test
  fun `isValid returns true for connecting token`() {
    val token = createToken(
      expiresAt = System.currentTimeMillis() + 60000,
      state = TokenState.CONNECTING
    )
    assertTrue(token.isValid())
  }

  // ============================================================
  // getRemainingTime() tests
  // ============================================================

  @Test
  fun `getRemainingTime returns Expired for past expiration`() {
    val token = createToken(expiresAt = System.currentTimeMillis() - 1000)
    assertEquals("Expired", token.getRemainingTime())
  }

  @Test
  fun `getRemainingTime formats seconds only when under a minute`() {
    val token = createToken(expiresAt = System.currentTimeMillis() + 30000)
    val remaining = token.getRemainingTime()
    assertTrue("Should show seconds only", remaining.matches(Regex("\\d+s")))
  }

  @Test
  fun `getRemainingTime formats minutes and seconds`() {
    val token = createToken(expiresAt = System.currentTimeMillis() + 90000) // 1m 30s
    val remaining = token.getRemainingTime()
    assertTrue("Should show minutes and seconds", remaining.matches(Regex("\\d+m \\d+s")))
  }

  @Test
  fun `getRemainingTime handles exactly zero remaining`() {
    val token = createToken(expiresAt = System.currentTimeMillis())
    // May be "Expired" or "0s" depending on timing
    val result = token.getRemainingTime()
    assertTrue(result == "Expired" || result == "0s")
  }

  // ============================================================
  // getShareableString() tests
  // ============================================================

  @Test
  fun `getShareableString returns correct format`() {
    val token = createToken(code = "ABC-123-XYZ")
    assertEquals("p2p:ABC-123-XYZ", token.getShareableString())
  }

  @Test
  fun `getShareableString includes full code`() {
    val code = "DEF-456-GHI"
    val token = createToken(code = code)
    assertTrue(token.getShareableString().contains(code))
  }

  // ============================================================
  // Constant tests
  // ============================================================

  @Test
  fun `DEFAULT_EXPIRY_MS is 30 minutes`() {
    assertEquals(30 * 60 * 1000L, P2PToken.DEFAULT_EXPIRY_MS)
  }

  @Test
  fun `TRUSTED_EXPIRY_MS is 24 hours`() {
    assertEquals(24 * 60 * 60 * 1000L, P2PToken.TRUSTED_EXPIRY_MS)
  }

  @Test
  fun `TRUSTED_EXPIRY is longer than DEFAULT_EXPIRY`() {
    assertTrue(P2PToken.TRUSTED_EXPIRY_MS > P2PToken.DEFAULT_EXPIRY_MS)
  }

  @Test
  fun `DEFAULT_STUN_SERVER is set`() {
    assertNotNull(P2PToken.DEFAULT_STUN_SERVER)
    assertTrue(P2PToken.DEFAULT_STUN_SERVER.isNotEmpty())
  }

  @Test
  fun `FALLBACK_STUN_SERVER is set`() {
    assertNotNull(P2PToken.FALLBACK_STUN_SERVER)
    assertTrue(P2PToken.FALLBACK_STUN_SERVER.isNotEmpty())
  }

  // ============================================================
  // TokenState enum tests
  // ============================================================

  @Test
  fun `TokenState has all expected values`() {
    val states = TokenState.entries
    assertEquals(6, states.size)
    assertTrue(states.contains(TokenState.PENDING))
    assertTrue(states.contains(TokenState.CONNECTING))
    assertTrue(states.contains(TokenState.CONNECTED))
    assertTrue(states.contains(TokenState.REVOKED))
    assertTrue(states.contains(TokenState.EXPIRED))
    assertTrue(states.contains(TokenState.FAILED))
  }

  // ============================================================
  // Data class copy tests
  // ============================================================

  @Test
  fun `copy preserves all fields`() {
    val original = createToken()
    val copy = original.copy()
    assertEquals(original, copy)
  }

  @Test
  fun `copy allows modifying specific fields`() {
    val original = createToken(state = TokenState.PENDING)
    val connected = original.copy(state = TokenState.CONNECTED)

    assertEquals(TokenState.CONNECTED, connected.state)
    assertEquals(original.code, connected.code)
    assertEquals(original.deviceHash, connected.deviceHash)
  }
}
