package com.phenix.wirelessadb.theme

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import com.phenix.wirelessadb.PrefsManager
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ThemeManagerTest {

  private lateinit var mockContext: Context
  private lateinit var mockResources: Resources
  private lateinit var mockConfiguration: Configuration

  @Before
  fun setup() {
    mockContext = mockk(relaxed = true)
    mockResources = mockk(relaxed = true)
    mockConfiguration = Configuration()

    every { mockContext.resources } returns mockResources
    every { mockResources.configuration } returns mockConfiguration

    // Mock PrefsManager object
    mockkObject(PrefsManager)

    // Mock AppCompatDelegate static methods
    mockkStatic(AppCompatDelegate::class)
    every { AppCompatDelegate.setDefaultNightMode(any()) } just Runs
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  // ============================================================
  // applyThemeMode() tests
  // ============================================================

  @Test
  fun `applyThemeMode LIGHT sets MODE_NIGHT_NO`() {
    ThemeManager.applyThemeMode(ThemeMode.LIGHT)

    verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) }
  }

  @Test
  fun `applyThemeMode DARK sets MODE_NIGHT_YES`() {
    ThemeManager.applyThemeMode(ThemeMode.DARK)

    verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) }
  }

  @Test
  fun `applyThemeMode SYSTEM sets MODE_NIGHT_FOLLOW_SYSTEM`() {
    ThemeManager.applyThemeMode(ThemeMode.SYSTEM)

    verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
  }

  // ============================================================
  // applyTheme() tests
  // ============================================================

  @Test
  fun `applyTheme reads mode from PrefsManager and applies it`() {
    every { PrefsManager.getThemeMode(mockContext) } returns ThemeMode.DARK

    ThemeManager.applyTheme(mockContext)

    verify { PrefsManager.getThemeMode(mockContext) }
    verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) }
  }

  @Test
  fun `applyTheme with SYSTEM mode sets follow system`() {
    every { PrefsManager.getThemeMode(mockContext) } returns ThemeMode.SYSTEM

    ThemeManager.applyTheme(mockContext)

    verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
  }

  // ============================================================
  // setThemeMode() tests
  // ============================================================

  @Test
  fun `setThemeMode saves to PrefsManager and applies`() {
    every { PrefsManager.setThemeMode(mockContext, ThemeMode.DARK) } just Runs

    ThemeManager.setThemeMode(mockContext, ThemeMode.DARK)

    verify { PrefsManager.setThemeMode(mockContext, ThemeMode.DARK) }
    verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) }
  }

  @Test
  fun `setThemeMode LIGHT saves and applies`() {
    every { PrefsManager.setThemeMode(mockContext, ThemeMode.LIGHT) } just Runs

    ThemeManager.setThemeMode(mockContext, ThemeMode.LIGHT)

    verify { PrefsManager.setThemeMode(mockContext, ThemeMode.LIGHT) }
    verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) }
  }

  // ============================================================
  // getThemeMode() tests
  // ============================================================

  @Test
  fun `getThemeMode returns value from PrefsManager`() {
    every { PrefsManager.getThemeMode(mockContext) } returns ThemeMode.DARK

    val result = ThemeManager.getThemeMode(mockContext)

    assertEquals(ThemeMode.DARK, result)
    verify { PrefsManager.getThemeMode(mockContext) }
  }

  // ============================================================
  // getAccentColor() tests
  // ============================================================

  @Test
  fun `getAccentColor returns value from PrefsManager`() {
    every { PrefsManager.getAccentColor(mockContext) } returns AccentColor.ORANGE

    val result = ThemeManager.getAccentColor(mockContext)

    assertEquals(AccentColor.ORANGE, result)
  }

  // ============================================================
  // setAccentColor() tests
  // ============================================================

  @Test
  fun `setAccentColor saves to PrefsManager`() {
    every { PrefsManager.setAccentColor(mockContext, AccentColor.PURPLE) } just Runs

    ThemeManager.setAccentColor(mockContext, AccentColor.PURPLE)

    verify { PrefsManager.setAccentColor(mockContext, AccentColor.PURPLE) }
  }

  // ============================================================
  // isDarkMode() tests
  // ============================================================

  @Test
  fun `isDarkMode returns true when mode is DARK`() {
    every { PrefsManager.getThemeMode(mockContext) } returns ThemeMode.DARK

    assertTrue(ThemeManager.isDarkMode(mockContext))
  }

  @Test
  fun `isDarkMode returns false when mode is LIGHT`() {
    every { PrefsManager.getThemeMode(mockContext) } returns ThemeMode.LIGHT

    assertFalse(ThemeManager.isDarkMode(mockContext))
  }

  @Test
  fun `isDarkMode checks system setting when mode is SYSTEM and returns true for night`() {
    every { PrefsManager.getThemeMode(mockContext) } returns ThemeMode.SYSTEM
    mockConfiguration.uiMode = Configuration.UI_MODE_NIGHT_YES

    assertTrue(ThemeManager.isDarkMode(mockContext))
  }

  @Test
  fun `isDarkMode checks system setting when mode is SYSTEM and returns false for day`() {
    every { PrefsManager.getThemeMode(mockContext) } returns ThemeMode.SYSTEM
    mockConfiguration.uiMode = Configuration.UI_MODE_NIGHT_NO

    assertFalse(ThemeManager.isDarkMode(mockContext))
  }

  // ============================================================
  // toggleDarkMode() tests
  // ============================================================

  @Test
  fun `toggleDarkMode switches from dark to light`() {
    every { PrefsManager.getThemeMode(mockContext) } returns ThemeMode.DARK
    every { PrefsManager.setThemeMode(mockContext, ThemeMode.LIGHT) } just Runs

    ThemeManager.toggleDarkMode(mockContext)

    verify { PrefsManager.setThemeMode(mockContext, ThemeMode.LIGHT) }
    verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) }
  }

  @Test
  fun `toggleDarkMode switches from light to dark`() {
    every { PrefsManager.getThemeMode(mockContext) } returns ThemeMode.LIGHT
    every { PrefsManager.setThemeMode(mockContext, ThemeMode.DARK) } just Runs

    ThemeManager.toggleDarkMode(mockContext)

    verify { PrefsManager.setThemeMode(mockContext, ThemeMode.DARK) }
    verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) }
  }

  @Test
  fun `toggleDarkMode from SYSTEM night goes to LIGHT`() {
    every { PrefsManager.getThemeMode(mockContext) } returns ThemeMode.SYSTEM
    mockConfiguration.uiMode = Configuration.UI_MODE_NIGHT_YES
    every { PrefsManager.setThemeMode(mockContext, ThemeMode.LIGHT) } just Runs

    ThemeManager.toggleDarkMode(mockContext)

    verify { PrefsManager.setThemeMode(mockContext, ThemeMode.LIGHT) }
  }

  @Test
  fun `toggleDarkMode from SYSTEM day goes to DARK`() {
    every { PrefsManager.getThemeMode(mockContext) } returns ThemeMode.SYSTEM
    mockConfiguration.uiMode = Configuration.UI_MODE_NIGHT_NO
    every { PrefsManager.setThemeMode(mockContext, ThemeMode.DARK) } just Runs

    ThemeManager.toggleDarkMode(mockContext)

    verify { PrefsManager.setThemeMode(mockContext, ThemeMode.DARK) }
  }

  // ============================================================
  // cycleThemeMode() tests
  // ============================================================

  @Test
  fun `cycleThemeMode from SYSTEM goes to LIGHT`() {
    every { PrefsManager.getThemeMode(mockContext) } returns ThemeMode.SYSTEM
    every { PrefsManager.setThemeMode(mockContext, ThemeMode.LIGHT) } just Runs

    val result = ThemeManager.cycleThemeMode(mockContext)

    assertEquals(ThemeMode.LIGHT, result)
    verify { PrefsManager.setThemeMode(mockContext, ThemeMode.LIGHT) }
  }

  @Test
  fun `cycleThemeMode from LIGHT goes to DARK`() {
    every { PrefsManager.getThemeMode(mockContext) } returns ThemeMode.LIGHT
    every { PrefsManager.setThemeMode(mockContext, ThemeMode.DARK) } just Runs

    val result = ThemeManager.cycleThemeMode(mockContext)

    assertEquals(ThemeMode.DARK, result)
    verify { PrefsManager.setThemeMode(mockContext, ThemeMode.DARK) }
  }

  @Test
  fun `cycleThemeMode from DARK goes to SYSTEM`() {
    every { PrefsManager.getThemeMode(mockContext) } returns ThemeMode.DARK
    every { PrefsManager.setThemeMode(mockContext, ThemeMode.SYSTEM) } just Runs

    val result = ThemeManager.cycleThemeMode(mockContext)

    assertEquals(ThemeMode.SYSTEM, result)
    verify { PrefsManager.setThemeMode(mockContext, ThemeMode.SYSTEM) }
  }

  @Test
  fun `cycleThemeMode full cycle returns to original`() {
    // SYSTEM -> LIGHT -> DARK -> SYSTEM
    every { PrefsManager.setThemeMode(any(), any()) } just Runs

    every { PrefsManager.getThemeMode(mockContext) } returns ThemeMode.SYSTEM
    var result = ThemeManager.cycleThemeMode(mockContext)
    assertEquals(ThemeMode.LIGHT, result)

    every { PrefsManager.getThemeMode(mockContext) } returns ThemeMode.LIGHT
    result = ThemeManager.cycleThemeMode(mockContext)
    assertEquals(ThemeMode.DARK, result)

    every { PrefsManager.getThemeMode(mockContext) } returns ThemeMode.DARK
    result = ThemeManager.cycleThemeMode(mockContext)
    assertEquals(ThemeMode.SYSTEM, result)
  }
}
