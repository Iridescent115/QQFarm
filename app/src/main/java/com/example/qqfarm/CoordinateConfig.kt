package com.example.qqfarm

import android.content.Context

data class ClickPoint(
    val key: String,
    val label: String,
    val defaultX: Float,
    val defaultY: Float
)

data class AutomationCoordinates(
    val openFriends: Pair<Float, Float>,
    val visitFirst: Pair<Float, Float>,
    val closeFriends: Pair<Float, Float>,
    val goHome: Pair<Float, Float>
)

object CoordinateConfig {
    const val PREFS_NAME = "coordinate_config"

    const val KEY_OPEN_FRIENDS = "open_friends"
    const val KEY_VISIT_FIRST = "visit_first"
    const val KEY_CLOSE_FRIENDS = "close_friends"
    const val KEY_GO_HOME = "go_home"

    val points = listOf(
        ClickPoint(KEY_OPEN_FRIENDS, "打开好友列表", 1098f, 2566f),
        ClickPoint(KEY_VISIT_FIRST, "拜访第一个好友", 1093f, 1177f),
        ClickPoint(KEY_CLOSE_FRIENDS, "关闭好友列表", 1169f, 651f),
        ClickPoint(KEY_GO_HOME, "回自己家", 1164f, 2127f)
    )

    fun loadPoint(context: Context, key: String): Pair<Float, Float> {
        val point = points.first { it.key == key }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val x = prefs.getFloat("${key}_x", point.defaultX)
        val y = prefs.getFloat("${key}_y", point.defaultY)
        return x to y
    }

    fun savePoint(context: Context, key: String, x: Float, y: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat("${key}_x", x)
            .putFloat("${key}_y", y)
            .apply()
    }

    fun loadAll(context: Context): AutomationCoordinates {
        return AutomationCoordinates(
            openFriends = loadPoint(context, KEY_OPEN_FRIENDS),
            visitFirst = loadPoint(context, KEY_VISIT_FIRST),
            closeFriends = loadPoint(context, KEY_CLOSE_FRIENDS),
            goHome = loadPoint(context, KEY_GO_HOME)
        )
    }
}
