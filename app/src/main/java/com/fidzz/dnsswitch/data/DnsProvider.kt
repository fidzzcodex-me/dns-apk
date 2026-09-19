package com.fidzz.dnsswitch.data

import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.vector.ImageVector

data class DnsProvider(
    val id: String,
    val displayName: String,
    val tagline: String,
    val primary: String,
    val secondary: String,
    val accent: Color,
    val accentSoft: Color,
    val icon: ImageVector
)

object DnsProviders {

    val all = listOf(
        DnsProvider(
            id = "cloudflare",
            displayName = "Cloudflare",
            tagline = "1.1.1.1 - cepat dan privat",
            primary = "1.1.1.1",
            secondary = "1.0.0.1",
            accent = Color(0xFFF6821F),
            accentSoft = Color(0xFFFFF1E3),
            icon = Icons.Filled.Speed
        ),
        DnsProvider(
            id = "google",
            displayName = "Google Public DNS",
            tagline = "8.8.8.8 - stabil di mana saja",
            primary = "8.8.8.8",
            secondary = "8.8.4.4",
            accent = Color(0xFF4285F4),
            accentSoft = Color(0xFFE8F0FE),
            icon = Icons.Filled.CloudQueue
        ),
        DnsProvider(
            id = "adguard",
            displayName = "AdGuard DNS",
            tagline = "Blokir iklan dan pelacak",
            primary = "94.140.14.14",
            secondary = "94.140.15.15",
            accent = Color(0xFF68BC71),
            accentSoft = Color(0xFFEAF7EC),
            icon = Icons.Filled.Shield
        ),
        DnsProvider(
            id = "quad9",
            displayName = "Quad9",
            tagline = "Fokus keamanan dan anti-malware",
            primary = "9.9.9.9",
            secondary = "149.112.112.112",
            accent = Color(0xFF7C4DFF),
            accentSoft = Color(0xFFF1ECFF),
            icon = Icons.Filled.Security
        ),
        DnsProvider(
            id = "opendns",
            displayName = "OpenDNS",
            tagline = "Filter konten oleh Cisco",
            primary = "208.67.222.222",
            secondary = "208.67.220.220",
            accent = Color(0xFF00A0DC),
            accentSoft = Color(0xFFE3F6FD),
            icon = Icons.Filled.Verified
        ),
        DnsProvider(
            id = "cleanbrowsing",
            displayName = "CleanBrowsing",
            tagline = "Ramah keluarga, filter dewasa",
            primary = "185.228.168.9",
            secondary = "185.228.169.9",
            accent = Color(0xFF356DFB),
            accentSoft = Color(0xFFEEF5FF),
            icon = Icons.Filled.Visibility
        )
    )

    fun byId(id: String?): DnsProvider = all.firstOrNull { it.id == id } ?: all.first()
}
