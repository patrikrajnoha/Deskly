package com.example.deskly

import org.json.JSONObject

object ShortcutProtocol {
    const val TYPE = "shortcut_action"

    enum class Category {
        SYSTEM,
        BROWSER
    }

    enum class Platform {
        WINDOWS,
        MACOS
    }

    enum class Browser {
        CHROME,
        FIREFOX,
        OPERA
    }

    data class ShortcutAction(
        val id: String,
        val label: String,
        val category: Category
    )

    data class Mapping(
        val keys: List<String> = emptyList(),
        val wheelSteps: Int = 0
    )

    val supportedActions = listOf(
        ShortcutAction("show_desktop", "Show Desktop", Category.SYSTEM),
        ShortcutAction("task_manager", "Task Manager", Category.SYSTEM),
        ShortcutAction("close_window", "Close Window", Category.SYSTEM),
        ShortcutAction("browser_back", "Back", Category.BROWSER),
        ShortcutAction("browser_forward", "Forward", Category.BROWSER),
        ShortcutAction("new_tab", "New Tab", Category.BROWSER),
        ShortcutAction("close_tab", "Close Tab", Category.BROWSER),
        ShortcutAction("next_tab", "Next Tab", Category.BROWSER),
        ShortcutAction("previous_tab", "Prev Tab", Category.BROWSER),
        ShortcutAction("refresh", "Refresh", Category.BROWSER),
        ShortcutAction("address_bar", "Address Bar", Category.BROWSER),
        ShortcutAction("fullscreen", "Fullscreen", Category.BROWSER),
        ShortcutAction("page_scroll_up", "Page Up", Category.BROWSER),
        ShortcutAction("page_scroll_down", "Page Down", Category.BROWSER),
        ShortcutAction("zoom_in", "Zoom In", Category.BROWSER),
        ShortcutAction("zoom_out", "Zoom Out", Category.BROWSER),
        ShortcutAction("zoom_reset", "Reset Zoom", Category.BROWSER)
    )

    fun normalizeAction(action: String): String =
        action.trim().lowercase().replace("-", "_").replace(" ", "_")

    fun actionLabel(action: String): String? =
        supportedActions.firstOrNull { it.id == normalizeAction(action) }?.label

    fun isSupported(action: String): Boolean =
        actionLabel(action) != null

    fun payload(action: String): JSONObject =
        JSONObject().put("action", normalizeAction(action))

    fun mappingFor(action: String, platform: Platform): Mapping? {
        return when (normalizeAction(action)) {
            "show_desktop" -> when (platform) {
                Platform.WINDOWS -> Mapping(keys = listOf("win", "d"))
                Platform.MACOS -> Mapping(keys = listOf("cmd", "f3"))
            }
            "task_manager" -> when (platform) {
                Platform.WINDOWS -> Mapping(keys = listOf("ctrl", "shift", "esc"))
                Platform.MACOS -> Mapping(keys = listOf("cmd", "alt", "esc"))
            }
            "close_window" -> when (platform) {
                Platform.WINDOWS -> Mapping(keys = listOf("alt", "f4"))
                Platform.MACOS -> Mapping(keys = listOf("cmd", "w"))
            }
            "browser_back" -> when (platform) {
                Platform.WINDOWS -> Mapping(keys = listOf("alt", "left"))
                Platform.MACOS -> Mapping(keys = listOf("cmd", "left_bracket"))
            }
            "browser_forward" -> when (platform) {
                Platform.WINDOWS -> Mapping(keys = listOf("alt", "right"))
                Platform.MACOS -> Mapping(keys = listOf("cmd", "right_bracket"))
            }
            "new_tab" -> Mapping(keys = listOf(primaryModifier(platform), "t"))
            "close_tab" -> Mapping(keys = listOf(primaryModifier(platform), "w"))
            "next_tab" -> when (platform) {
                Platform.WINDOWS -> Mapping(keys = listOf("ctrl", "tab"))
                Platform.MACOS -> Mapping(keys = listOf("ctrl", "tab"))
            }
            "previous_tab" -> when (platform) {
                Platform.WINDOWS -> Mapping(keys = listOf("ctrl", "shift", "tab"))
                Platform.MACOS -> Mapping(keys = listOf("ctrl", "shift", "tab"))
            }
            "refresh" -> Mapping(keys = listOf(primaryModifier(platform), "r"))
            "address_bar" -> Mapping(keys = listOf(primaryModifier(platform), "l"))
            "fullscreen" -> when (platform) {
                Platform.WINDOWS -> Mapping(keys = listOf("f11"))
                Platform.MACOS -> Mapping(keys = listOf("cmd", "ctrl", "f"))
            }
            "page_scroll_up" -> Mapping(keys = listOf("page_up"))
            "page_scroll_down" -> Mapping(keys = listOf("page_down"))
            "zoom_in" -> when (platform) {
                Platform.WINDOWS -> Mapping(wheelSteps = 1)
                Platform.MACOS -> Mapping(keys = listOf("cmd", "plus"))
            }
            "zoom_out" -> when (platform) {
                Platform.WINDOWS -> Mapping(wheelSteps = -1)
                Platform.MACOS -> Mapping(keys = listOf("cmd", "minus"))
            }
            "zoom_reset" -> Mapping(keys = listOf(primaryModifier(platform), "0"))
            else -> null
        }
    }

    fun mappingForBrowser(action: String, browser: Browser, platform: Platform = Platform.WINDOWS): Mapping? {
        return when (browser) {
            Browser.CHROME,
            Browser.FIREFOX,
            Browser.OPERA -> mappingFor(action, platform)
        }
    }

    fun isZoomAction(action: String): Boolean =
        normalizeAction(action) in setOf("zoom_in", "zoom_out", "zoom_reset")

    private fun primaryModifier(platform: Platform): String =
        when (platform) {
            Platform.WINDOWS -> "ctrl"
            Platform.MACOS -> "cmd"
        }
}
