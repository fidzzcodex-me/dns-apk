package com.fidzz.dnsswitch

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fidzz.dnsswitch.data.DnsProvider
import com.fidzz.dnsswitch.data.DnsProviders
import com.fidzz.dnsswitch.ui.theme.DnsSwitchTheme
import com.fidzz.dnsswitch.ui.theme.Ok
import com.fidzz.dnsswitch.vpn.DnsVpnService
import com.fidzz.dnsswitch.vpn.VpnState
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var pendingProvider: DnsProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val connectedId by VpnState.connectedProviderId.collectAsState()
            val activeProvider = DnsProviders.byId(connectedId)
            val selectedProvider = remember { mutableStateOf(activeProvider) }

            LaunchedEffect(connectedId) {
                if (connectedId != null) {
                    selectedProvider.value = DnsProviders.byId(connectedId)
                }
            }

            val vpnLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == RESULT_OK) {
                    pendingProvider?.let { startVpn(it) }
                }
                pendingProvider = null
            }

            val notificationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            DnsSwitchTheme(accent = selectedProvider.value.accent) {
                DnsSwitchScreen(
                    selected = selectedProvider.value,
                    connectedId = connectedId,
                    onSelect = { provider ->
                        selectedProvider.value = provider
                    },
                    onToggleConnection = { provider ->
                        if (connectedId == provider.id) {
                            stopVpn()
                        } else {
                            requestVpnPermission(provider, vpnLauncher)
                        }
                    }
                )
            }
        }
    }

    private fun requestVpnPermission(
        provider: DnsProvider,
        launcher: androidx.activity.result.ActivityResultLauncher<Intent>
    ) {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            pendingProvider = provider
            launcher.launch(prepareIntent)
        } else {
            startVpn(provider)
        }
    }

    private fun startVpn(provider: DnsProvider) {
        val intent = Intent(this, DnsVpnService::class.java).apply {
            action = DnsVpnService.ACTION_CONNECT
            putExtra(DnsVpnService.EXTRA_PROVIDER_ID, provider.id)
            putExtra(DnsVpnService.EXTRA_PRIMARY_DNS, provider.primary)
            putExtra(DnsVpnService.EXTRA_SECONDARY_DNS, provider.secondary)
            putExtra(DnsVpnService.EXTRA_PROVIDER_NAME, provider.displayName)
        }
        startForegroundService(intent)
    }

    private fun stopVpn() {
        val intent = Intent(this, DnsVpnService::class.java).apply {
            action = DnsVpnService.ACTION_DISCONNECT
        }
        startService(intent)
    }
}

@Composable
fun DnsSwitchScreen(
    selected: DnsProvider,
    connectedId: String?,
    onSelect: (DnsProvider) -> Unit,
    onToggleConnection: (DnsProvider) -> Unit
) {
    val isConnected = connectedId == selected.id

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppHeader(isConnected = isConnected, providerName = selected.displayName)

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(DnsProviders.all) { provider ->
                    val index = DnsProviders.all.indexOf(provider)
                    StaggeredEntrance(index = index) {
                        DnsProviderCard(
                            provider = provider,
                            isSelected = selected.id == provider.id,
                            isConnected = connectedId == provider.id,
                            onClick = { onSelect(provider) }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            ConnectBar(
                provider = selected,
                isConnected = isConnected,
                onToggle = { onToggleConnection(selected) }
            )
        }
    }
}

@Composable
private fun StaggeredEntrance(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(450)) +
            slideInVertically(animationSpec = tween(450)) { it / 4 }
    ) {
        content()
    }
}

@Composable
private fun AppHeader(isConnected: Boolean, providerName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.RadioButtonChecked,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = "DNS Switch",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        StatusPill(isConnected = isConnected, providerName = providerName)
    }
}

@Composable
private fun StatusPill(isConnected: Boolean, providerName: String) {
    val bg = if (isConnected) Ok.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
    val dot = if (isConnected) Ok else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val label = if (isConnected) "Terhubung" else "Tidak aktif"

    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(dot, CircleShape)
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isConnected) Ok else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun DnsProviderCard(
    provider: DnsProvider,
    isSelected: Boolean,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) provider.accentSoft else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(provider.accent.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = provider.icon,
                    contentDescription = null,
                    tint = provider.accent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.size(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = provider.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    if (isConnected) {
                        Spacer(modifier = Modifier.size(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Ok, RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Aktif",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
                Text(
                    text = provider.tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = "${provider.primary} / ${provider.secondary}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }

            Icon(
                imageVector = if (isSelected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) provider.accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
            )
        }
    }
}

@Composable
private fun ConnectBar(
    provider: DnsProvider,
    isConnected: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Button(
                onClick = onToggle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.PowerSettingsNew,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = if (isConnected) "Putuskan dari ${provider.displayName}" else "Sambungkan ke ${provider.displayName}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
