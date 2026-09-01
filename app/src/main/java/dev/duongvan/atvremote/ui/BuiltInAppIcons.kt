package dev.duongvan.atvremote.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Built-in tvOS apps have no App Store listing, so no artwork can be looked up
 * for them. They get a fixed vector icon instead of a monogram.
 *
 * Matching is done on the bundle identifier first and on the display name as a
 * fallback, because the names come back localised from the device.
 */
data class BuiltInAppIcon(val icon: ImageVector, val background: Color)

private val AppStore = BuiltInAppIcon(Icons.Filled.Storefront, Color(0xFF0A84FF))
private val Arcade = BuiltInAppIcon(Icons.Filled.SportsEsports, Color(0xFF5E35B1))
private val Settings = BuiltInAppIcon(Icons.Filled.Settings, Color(0xFF5A5F6A))
private val Computers = BuiltInAppIcon(Icons.Filled.Computer, Color(0xFF3F72A6))
private val Music = BuiltInAppIcon(Icons.Filled.MusicNote, Color(0xFFE0284A))
private val TvApp = BuiltInAppIcon(Icons.Filled.Tv, Color(0xFF1C1C1E))
private val Search = BuiltInAppIcon(Icons.Filled.Search, Color(0xFF34495E))

private val byBundleId: Map<String, BuiltInAppIcon> = mapOf(
    "com.apple.TVAppStore" to AppStore,
    "com.apple.Arcade" to Arcade,
    "com.apple.TVArcade" to Arcade,
    "com.apple.TVSettings" to Settings,
    "com.apple.TVHomeSharing" to Computers,
    "com.apple.TVMusic" to Music,
    "com.apple.TVWatchList" to TvApp,
    "com.apple.TVSearch" to Search
)

private val byName: Map<String, BuiltInAppIcon> = mapOf(
    "app store" to AppStore,
    "appstore" to AppStore,
    "arcade" to Arcade,
    "cài đặt" to Settings,
    "settings" to Settings,
    "máy tính" to Computers,
    "computers" to Computers,
    "nhạc" to Music,
    "music" to Music,
    "tv" to TvApp,
    "tìm kiếm" to Search,
    "search" to Search
)

fun builtInAppIcon(bundleId: String, name: String): BuiltInAppIcon? =
    byBundleId[bundleId] ?: byName[name.trim().lowercase()]
