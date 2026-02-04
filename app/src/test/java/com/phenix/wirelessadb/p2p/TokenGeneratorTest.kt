package com.phenix.wirelessadb.p2p

import org.junit.Assert.*
import org.junit.Test

class TokenGeneratorTest {

  private val testDeviceHash = "abc123def456"

  // ============================================================
  // generate() tests
  // ============================================================

  @Test
  fun `generate creates token with valid format`() {
    val token = TokenGenerator.generate(testDeviceHash)
    assertTrue("Token code should be valid format", TokenGenerator.isValidFormat(token.code))
  }

  @Test
  fun `generate creates token with correct device hash`() {
    val token = TokenGenerator.generate(testDeviceHash)
    assertEquals(testDeviceHash, token.deviceHash)
  }

  @Test
  fun `generate creates non-persistent token by default`() {
    val token = TokenGenerator.generate(testDeviceHash)
    assertFalse(token.isPersistent)
  }

  @Test
  fun `generate creates persistent token when requested`() {
    val token = TokenGenerator.generate(testDeviceHash, isPersistent = true)
    assertTrue(token.isPersistent)
  }

  @Test
  fun `generate creates unique session IDs`() {
    val token1 = TokenGenerator.generate(testDeviceHash)
    val token2 = TokenGenerator.generate(testDeviceHash)
    assertNotEquals(token1.sessionId, token2.sessionId)
  }

  @Test
  fun `generate creates unique token codes`() {
    val codes = (1..100).map { TokenGenerator.generate(testDeviceHash).code }.toSet()
    // High probability all 100 should be unique
    assertTrue("Generated codes should be unique", codes.size >= 95)
  }

  @Test
  fun `generate sets future expiration time`() {
    val before = System.currentTimeMillis()
    val token = TokenGenerator.generate(testDeviceHash)
    val after = System.currentTimeMillis()

    assertTrue("Expiration should be after creation", token.expiresAt > after)
    assertTrue("Expiration should be within expected range", token.expiresAt > before)
  }

  // ============================================================
  // isValidFormat() tests
  // ============================================================

  @Test
  fun `isValidFormat accepts valid uppercase token`() {
    assertTrue(TokenGenerator.isValidFormat("ABC-234-XYZ"))
  }

  @Test
  fun `isValidFormat accepts valid lowercase token`() {
    assertTrue(TokenGenerator.isValidFormat("abc-234-xyz"))
  }

  @Test
  fun `isValidFormat accepts mixed case token`() {
    assertTrue(TokenGenerator.isValidFormat("AbC-234-xYz"))
  }

  @Test
  fun `isValidFormat rejects token without dashes`() {
    assertFalse(TokenGenerator.isValidFormat("ABC234XYZ"))
  }

  @Test
  fun `isValidFormat rejects token with wrong segment length`() {
    assertFalse(TokenGenerator.isValidFormat("AB-234-XYZ"))
    assertFalse(TokenGenerator.isValidFormat("ABCD-234-XYZ"))
  }

  @Test
  fun `isValidFormat rejects empty string`() {
    assertFalse(TokenGenerator.isValidFormat(""))
  }

  @Test
  fun `isValidFormat rejects token with invalid characters`() {
    // 0, 1, O, I, L are excluded from charset
    assertFalse(TokenGenerator.isValidFormat("AB0-234-XYZ"))
    assertFalse(TokenGenerator.isValidFormat("AB1-234-XYZ"))
  }

  @Test
  fun `isValidFormat accepts all valid charset characters`() {
    // Valid: A-Z except O, I, L; 2-9
    assertTrue(TokenGenerator.isValidFormat("ABC-DEF-GHJ"))
    assertTrue(TokenGenerator.isValidFormat("KMN-PQR-STU"))
    assertTrue(TokenGenerator.isValidFormat("VWX-YZ2-345"))
    assertTrue(TokenGenerator.isValidFormat("678-9AB-CDE"))
  }

  // ============================================================
  // normalize() tests
  // ============================================================

  @Test
  fun `normalize uppercases lowercase input`() {
    val result = TokenGenerator.normalize("abc234xyz")
    assertTrue(result.all { it.isUpperCase() || it == '-' || it.isDigit() })
  }

  @Test
  fun `normalize adds dashes to 9-char input`() {
    val result = TokenGenerator.normalize("abc234xyz")
    assertEquals("ABC-234-XYZ", result)
  }

  @Test
  fun `normalize preserves already formatted input`() {
    val result = TokenGenerator.normalize("ABC-234-XYZ")
    assertEquals("ABC-234-XYZ", result)
  }

  @Test
  fun `normalize handles input with spaces`() {
    val result = TokenGenerator.normalize("abc 234 xyz")
    assertEquals("ABC-234-XYZ", result)
  }

  @Test
  fun `normalize returns uppercase for invalid length`() {
    val result = TokenGenerator.normalize("ab")
    assertEquals("AB", result)
  }

  // ============================================================
  // generatePersistent() tests
  // ============================================================

  @Test
  fun `generatePersistent creates persistent token`() {
    val token = TokenGenerator.generatePersistent(testDeviceHash)
    assertTrue(token.isPersistent)
  }

  @Test
  fun `generatePersistent has extended expiry`() {
    val token = TokenGenerator.generatePersistent(testDeviceHash)
    val regularToken = TokenGenerator.generate(testDeviceHash)

    assertTrue(
      "Persistent token should have longer expiry",
      token.expiresAt > regularToken.expiresAt
    )
  }

  // ============================================================
  // regenerate() tests
  // ============================================================

  @Test
  fun `regenerate creates new token code`() {
    val original = TokenGenerator.generate(testDeviceHash)
    val regenerated = TokenGenerator.regenerate(original)

    assertNotEquals(original.code, regenerated.code)
  }

  @Test
  fun `regenerate preserves device hash`() {
    val original = TokenGenerator.generate(testDeviceHash)
    val regenerated = TokenGenerator.regenerate(original)

    assertEquals(original.deviceHash, regenerated.deviceHash)
  }

  @Test
  fun `regenerate preserves persistence setting`() {
    val persistent = TokenGenerator.generate(testDeviceHash, isPersistent = true)
    val regenerated = TokenGenerator.regenerate(persistent)

    assertEquals(persistent.isPersistent, regenerated.isPersistent)
  }

  @Test
  fun `regenerate creates new session ID`() {
    val original = TokenGenerator.generate(testDeviceHash)
    val regenerated = TokenGenerator.regenerate(original)

    assertNotEquals(original.sessionId, regenerated.sessionId)
  }

  // ============================================================
  // Charset validation tests
  // ============================================================

  @Test
  fun `generated tokens never contain confusing characters`() {
    val confusingChars = listOf('O', '0', 'I', '1', 'L')

    repeat(100) {
      val token = TokenGenerator.generate(testDeviceHash)
      val codeWithoutDashes = token.code.replace("-", "")

      confusingChars.forEach { char ->
        assertFalse(
          "Token should not contain '$char'",
          codeWithoutDashes.contains(char)
        )
      }
    }
  }

  @Test
  fun `generated tokens only contain valid characters`() {
    val validChars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toSet()

    repeat(100) {
      val token = TokenGenerator.generate(testDeviceHash)
      val codeWithoutDashes = token.code.replace("-", "")

      codeWithoutDashes.forEach { char ->
        assertTrue(
          "Character '$char' should be in valid charset",
          validChars.contains(char)
        )
      }
    }
  }

  @Test
  fun `generated token code has correct structure`() {
    repeat(50) {
      val token = TokenGenerator.generate(testDeviceHash)
      val parts = token.code.split("-")

      assertEquals("Token should have 3 segments", 3, parts.size)
      parts.forEach { segment ->
        assertEquals("Each segment should have 3 characters", 3, segment.length)
      }
    }
  }
}
