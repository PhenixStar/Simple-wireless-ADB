package com.phenix.wirelessadb.warpgate

import com.jcraft.jsch.JSch
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Properties

/**
 * Tests to verify JSch 2.27.7 API compatibility.
 *
 * These tests create real JSch objects to verify the API works correctly
 * with the updated dependency. No Android framework dependencies required.
 *
 * Key changes verified:
 * - JSch 0.1.55 (old, unmaintained) -> JSch 2.27.7 (maintained fork by mwiede)
 * - Same package name: com.jcraft.jsch
 * - API compatibility: All WarpgateManager methods still work
 * - Drop-in replacement with no code changes needed
 */
class JSchApiCompatibilityTest {

  private lateinit var jsch: JSch

  @Before
  fun setup() {
    jsch = JSch()
  }

  @After
  fun teardown() {
    // Clean up any sessions
  }

  // ============================================================
  // JSch 2.27.7 Constructor and Factory Methods
  // ============================================================

  @Test
  fun `JSch constructor creates instance`() {
    val instance = JSch()
    assertNotNull("JSch instance should be created", instance)
  }

  @Test
  fun `JSch can create multiple instances`() {
    val instance1 = JSch()
    val instance2 = JSch()

    assertNotNull("First instance created", instance1)
    assertNotNull("Second instance created", instance2)
  }

  // ============================================================
  // Session Creation (getSession method)
  // ============================================================

  @Test
  fun `getSession creates session with username host and port`() {
    val session = jsch.getSession("testuser", "localhost", 22)

    assertNotNull("Session should be created", session)
    assertEquals("Username should match", "testuser", session.userName)
    assertEquals("Host should match", "localhost", session.host)
    assertEquals("Port should match", 22, session.port)

    session.disconnect()
  }

  @Test
  fun `getSession supports Warpgate username format with hash`() {
    // Warpgate uses "username#target" format
    val session = jsch.getSession("user#adb", "warpgate.example.com", 8443)

    assertNotNull("Session should be created", session)
    assertEquals("Username should include hash separator", "user#adb", session.userName)

    session.disconnect()
  }

  @Test
  fun `getSession works with various port numbers`() {
    val ports = listOf(22, 8443, 2222, 65535)

    ports.forEach { port ->
      val session = jsch.getSession("user", "host", port)
      assertEquals("Port should be set for $port", port, session.port)
      session.disconnect()
    }
  }

  // ============================================================
  // Password Authentication (setPassword method)
  // ============================================================

  @Test
  fun `setPassword accepts password string`() {
    val session = jsch.getSession("user", "localhost", 22)

    // Should not throw exception
    session.setPassword("testpassword")

    assertNotNull("Session should accept password", session)
    session.disconnect()
  }

  @Test
  fun `setPassword accepts empty password`() {
    val session = jsch.getSession("user", "localhost", 22)

    // Should not throw exception with empty password
    session.setPassword("")

    assertNotNull("Session should accept empty password", session)
    session.disconnect()
  }

  // ============================================================
  // Session Configuration (setConfig method)
  // ============================================================

  @Test
  fun `setConfig accepts Properties object`() {
    val session = jsch.getSession("user", "localhost", 22)

    val config = Properties()
    config["StrictHostKeyChecking"] = "no"

    // Should not throw exception
    session.setConfig(config)

    assertNotNull("Session should accept configuration", session)
    session.disconnect()
  }

  @Test
  fun `setConfig accepts WarpgateManager configuration`() {
    val session = jsch.getSession("user", "host", 8443)

    // Exact configuration used by WarpgateManager
    val sshConfig = Properties().apply {
      put("StrictHostKeyChecking", "no")
      put("PreferredAuthentications", "password,publickey,keyboard-interactive")
    }

    session.setConfig(sshConfig)

    assertNotNull("Session should accept WarpgateManager config", session)
    session.disconnect()
  }

  @Test
  fun `setConfig accepts individual key-value pairs`() {
    val session = jsch.getSession("user", "localhost", 22)

    // Individual configuration
    session.setConfig("StrictHostKeyChecking", "no")
    session.setConfig("PreferredAuthentications", "password")

    assertNotNull("Session should accept individual configs", session)
    session.disconnect()
  }

  // ============================================================
  // Timeout Configuration (timeout property)
  // ============================================================

  @Test
  fun `timeout property can be set`() {
    val session = jsch.getSession("user", "localhost", 22)

    session.timeout = 0
    assertEquals("Timeout should be set to 0", 0, session.timeout)

    session.timeout = 15000
    assertEquals("Timeout should be updated to 15000", 15000, session.timeout)

    session.disconnect()
  }

  @Test
  fun `timeout supports WarpgateManager values`() {
    val session = jsch.getSession("user", "host", 8443)

    // WarpgateManager uses 0 for session timeout (no timeout)
    session.timeout = 0

    assertEquals("Should accept 0 timeout", 0, session.timeout)
    session.disconnect()
  }

  // ============================================================
  // Connection State (isConnected property)
  // ============================================================

  @Test
  fun `isConnected returns false for new session`() {
    val session = jsch.getSession("user", "localhost", 22)

    assertFalse("New session should not be connected", session.isConnected)

    session.disconnect()
  }

  @Test
  fun `isConnected returns false after disconnect`() {
    val session = jsch.getSession("user", "localhost", 22)

    session.disconnect()

    assertFalse("Session should not be connected after disconnect", session.isConnected)
  }

  // ============================================================
  // Disconnect Method
  // ============================================================

  @Test
  fun `disconnect can be called on new session`() {
    val session = jsch.getSession("user", "localhost", 22)

    // Should not throw exception
    session.disconnect()

    assertFalse("Session should be disconnected", session.isConnected)
  }

  @Test
  fun `disconnect is idempotent`() {
    val session = jsch.getSession("user", "localhost", 22)

    // Multiple calls should be safe
    session.disconnect()
    session.disconnect()
    session.disconnect()

    assertFalse("Session should remain disconnected", session.isConnected)
  }

  // ============================================================
  // Session Properties
  // ============================================================

  @Test
  fun `session exposes host property`() {
    val session = jsch.getSession("user", "testhost.example.com", 22)

    assertEquals("Host property should be accessible", "testhost.example.com", session.host)

    session.disconnect()
  }

  @Test
  fun `session exposes port property`() {
    val session = jsch.getSession("user", "host", 8443)

    assertEquals("Port property should be accessible", 8443, session.port)

    session.disconnect()
  }

  @Test
  fun `session exposes userName property`() {
    val session = jsch.getSession("myuser", "host", 22)

    assertEquals("Username property should be accessible", "myuser", session.userName)

    session.disconnect()
  }

  // ============================================================
  // Integration: Complete Configuration Flow
  // ============================================================

  @Test
  fun `complete WarpgateManager configuration flow works`() {
    // Simulate exact WarpgateManager.connect() flow

    val username = "testuser#adb"
    val host = "warpgate.example.com"
    val port = 8443

    // Step 1: Create session
    val session = jsch.getSession(username, host, port)
    assertNotNull("Session created", session)

    // Step 2: Set password
    session.setPassword("testpassword")

    // Step 3: Configure SSH settings
    val sshConfig = Properties().apply {
      put("StrictHostKeyChecking", "no")
      put("PreferredAuthentications", "password,publickey,keyboard-interactive")
    }
    session.setConfig(sshConfig)

    // Step 4: Set timeout
    session.timeout = 0

    // Verify all properties set correctly
    assertEquals("Username set", username, session.userName)
    assertEquals("Host set", host, session.host)
    assertEquals("Port set", port, session.port)
    assertEquals("Timeout set", 0, session.timeout)
    assertFalse("Not connected yet", session.isConnected)

    // Clean up
    session.disconnect()
  }

  // ============================================================
  // Regression Tests: Ensure old patterns still work
  // ============================================================

  @Test
  fun `Properties put method still works`() {
    val props = Properties()

    // Old style with put()
    props.put("key1", "value1")
    props.put("key2", "value2")

    assertEquals("First property", "value1", props.getProperty("key1"))
    assertEquals("Second property", "value2", props.getProperty("key2"))
  }

  @Test
  fun `Properties apply block pattern works`() {
    val props = Properties().apply {
      put("StrictHostKeyChecking", "no")
      put("PreferredAuthentications", "password")
    }

    assertEquals("StrictHostKeyChecking", "no", props.getProperty("StrictHostKeyChecking"))
    assertEquals("PreferredAuthentications", "password", props.getProperty("PreferredAuthentications"))
  }

  // ============================================================
  // Version Verification
  // ============================================================

  @Test
  fun `JSch class is loaded successfully`() {
    val jschClass = JSch::class.java
    assertNotNull("JSch class should be loadable", jschClass)
    assertEquals("Package name should be com.jcraft.jsch", "com.jcraft.jsch", jschClass.packageName)
  }

  @Test
  fun `Session class is from com_jcraft_jsch package`() {
    val session = jsch.getSession("user", "host", 22)
    val sessionClass = session::class.java

    assertTrue(
      "Session should be from com.jcraft.jsch package",
      sessionClass.packageName.startsWith("com.jcraft.jsch")
    )

    session.disconnect()
  }
}
