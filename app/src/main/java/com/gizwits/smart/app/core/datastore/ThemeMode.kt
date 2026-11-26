package com.gizwits.smart.app.core.datastore

enum class ThemeMode(val preferenceValue: String, val label: String) {
    System("system", "跟随系统"),
    Light("light", "浅色"),
    Dark("dark", "深色")
}
