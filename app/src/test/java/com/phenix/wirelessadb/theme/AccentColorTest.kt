package com.phenix.wirelessadb.theme

import org.junit.Assert.*
import org.junit.Test

class AccentColorTest {

  @Test
  fun `fromOrdinal returns BLUE for ordinal 0`() {
    assertEquals(AccentColor.BLUE, AccentColor.fromOrdinal(0))
  }

  @Test
  fun `fromOrdinal returns TEAL for ordinal 1`() {
    assertEquals(AccentColor.TEAL, AccentColor.fromOrdinal(1))
  }

  @Test
  fun `fromOrdinal returns PURPLE for ordinal 2`() {
    assertEquals(AccentColor.PURPLE, AccentColor.fromOrdinal(2))
  }

  @Test
  fun `fromOrdinal returns ORANGE for ordinal 3`() {
    assertEquals(AccentColor.ORANGE, AccentColor.fromOrdinal(3))
  }

  @Test
  fun `fromOrdinal returns PINK for ordinal 4`() {
    assertEquals(AccentColor.PINK, AccentColor.fromOrdinal(4))
  }

  @Test
  fun `fromOrdinal returns GREEN for ordinal 5`() {
    assertEquals(AccentColor.GREEN, AccentColor.fromOrdinal(5))
  }

  @Test
  fun `fromOrdinal returns DEFAULT for negative ordinal`() {
    assertEquals(AccentColor.DEFAULT, AccentColor.fromOrdinal(-1))
  }

  @Test
  fun `fromOrdinal returns DEFAULT for out of bounds ordinal`() {
    assertEquals(AccentColor.DEFAULT, AccentColor.fromOrdinal(6))
    assertEquals(AccentColor.DEFAULT, AccentColor.fromOrdinal(100))
  }

  @Test
  fun `DEFAULT is TEAL`() {
    assertEquals(AccentColor.TEAL, AccentColor.DEFAULT)
  }

  @Test
  fun `all entries have correct ordinals`() {
    assertEquals(0, AccentColor.BLUE.ordinal)
    assertEquals(1, AccentColor.TEAL.ordinal)
    assertEquals(2, AccentColor.PURPLE.ordinal)
    assertEquals(3, AccentColor.ORANGE.ordinal)
    assertEquals(4, AccentColor.PINK.ordinal)
    assertEquals(5, AccentColor.GREEN.ordinal)
  }

  @Test
  fun `entries contains all 6 colors`() {
    val entries = AccentColor.entries
    assertEquals(6, entries.size)
  }

  @Test
  fun `all colors have display names`() {
    AccentColor.entries.forEach { color ->
      assertNotNull(color.displayName)
      assertTrue(color.displayName.isNotEmpty())
    }
  }

  @Test
  fun `display names are correct`() {
    assertEquals("Blue", AccentColor.BLUE.displayName)
    assertEquals("Teal", AccentColor.TEAL.displayName)
    assertEquals("Purple", AccentColor.PURPLE.displayName)
    assertEquals("Orange", AccentColor.ORANGE.displayName)
    assertEquals("Pink", AccentColor.PINK.displayName)
    assertEquals("Green", AccentColor.GREEN.displayName)
  }

  @Test
  fun `all colors have valid resource IDs`() {
    AccentColor.entries.forEach { color ->
      // Resource IDs are non-zero
      assertTrue("${color.name} primary color should be non-zero", color.primaryColor != 0)
      assertTrue("${color.name} primary variant should be non-zero", color.primaryVariant != 0)
      assertTrue("${color.name} onPrimary should be non-zero", color.onPrimary != 0)
    }
  }

  @Test
  fun `fromOrdinal roundtrip preserves value`() {
    AccentColor.entries.forEach { color ->
      assertEquals(color, AccentColor.fromOrdinal(color.ordinal))
    }
  }
}
