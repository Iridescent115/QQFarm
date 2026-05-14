package com.example.qqfarm

import android.content.Context

enum class AutomationScript(
    val key: String,
    val label: String
) {
    STEAL_VEGETABLE("steal_vegetable", "自动偷菜"),
    ADD_FRIEND("add_friend", "自动加好友");

    companion object {
        fun fromKey(key: String?): AutomationScript {
            return entries.firstOrNull { it.key == key } ?: STEAL_VEGETABLE
        }
    }
}

object AutomationScriptConfig {
    private const val PREFS_NAME = "automation_script_config"
    private const val KEY_SELECTED_SCRIPT = "selected_script"

    fun loadSelected(context: Context): AutomationScript {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AutomationScript.fromKey(prefs.getString(KEY_SELECTED_SCRIPT, null))
    }

    fun saveSelected(context: Context, script: AutomationScript) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_SCRIPT, script.key)
            .apply()
    }
}
