package com.phenix.wirelessadb.theme

import org.junit.Assert.*
import org.junit.Test

class ThemeModeTest {

  @Test
  fun `fromOrdinal returns SYSTEM for ordinal 0`() {
    assertEquals(ThemeMode.SYSTEM, ThemeMode.fromOrdinal(0))
  }

  @Test
  fun `fromOrdinal returns LIGHT for ordinal 1`() {
    assertEquals(ThemeMode.LIGHT, ThemeMode.fromOrdinal(1))
  }

  @Test
  fun `fromOrdinal returns DARK for ordinal 2`() {
    assertEquals(ThemeMode.DARK, ThemeMode.fromOrdinal(2))
  }

  @Test
  fun `fromOrdinal returns DEFAULT for negative ordinal`() {
    assertEquals(ThemeMode.DEFAULT, ThemeMode.fromOrdinal(-1))
  }

  @Test
  fun `fromOrdinal returns DEFAULT for out of bounds ordinal`() {
    assertEquals(ThemeMode.DEFAULT, ThemeMode.fromOrdinal(3))
    assertEquals(ThemeMode.DEFAULT, ThemeMode.fromOrdinal(100))
  }

  @Test
  fun `DEFAULT is SYSTEM`() {
    assertEquals(ThemeMode.SYSTEM, ThemeMode.DEFAULT)
  }

  @Test
  fun `all entries have correct ordinals`() {
    assertEquals(0, ThemeMode.SYSTEM.ordinal)
    assertEquals(1, ThemeMode.LIGHT.ordinal)
    assertEquals(2, ThemeMode.DARK.ordinal)
  }

  @Test
  fun `entries contains all theme modes`() {
    val entries = ThemeMode.entries
    assertEquals(3, entries.size)
    assertTrue(entries.contains(ThemeMode.SYSTEM))
    assertTrue(entries.contains(ThemeMode.LIGHT))
    assertTrue(entries.contains(ThemeMode.DARK))
  }

  @Test
  fun `fromOrdinal roundtrip preserves value`() {
    ThemeMode.entries.forEach { mode ->
      assertEquals(mode, ThemeMode.fromOrdinal(mode.ordinal))
    }
  }
}
