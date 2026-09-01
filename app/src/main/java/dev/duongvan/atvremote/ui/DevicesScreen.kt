package dev.duongvan.atvremote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.duongvan.atvremote.RemoteViewModel
import dev.duongvan.atvremote.UiState
import dev.duongvan.atvremote.data.AtvDeviceRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(state: UiState, viewModel: RemoteViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chọn Apple TV") },
                actions = {
                    IconButton(onClick = { viewModel.startScan() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Quét lại")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.scanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (state.devices.isEmpty()) {
                    item { EmptyHint(scanning = state.scanning) }
                }
                items(state.devices, key = { it.id }) { device ->
                    DeviceCard(
                        title = device.name,
                        subtitle = listOfNotNull(device.model, "${device.host}:${device.port}")
                            .joinToString(" · "),
                        onClick = { viewModel.select(device) }
                    )
                }
                item {
                    ManualConnect { host, port ->
                        viewModel.select(AtvDeviceRecord(host, host, port))
                    }
                }
            }
            state.message?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(scanning: Boolean) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (scanning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(16.dp))
            }
            Text(
                if (scanning) {
                    "Đang tìm Apple TV trong mạng nội bộ. Điện thoại và Apple TV phải cùng Wi-Fi."
                } else {
                    "Chưa tìm thấy thiết bị nào. Bấm nút quét lại ở góc trên."
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun DeviceCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Tv, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ManualConnect(onConnect: (String, Int) -> Unit) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Nhập thủ công", style = MaterialTheme.typography.titleSmall)
            Text(
                "Dùng khi mDNS bị chặn. Cổng Companion thay đổi theo thiết bị, xem trong pyatv scan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Địa chỉ IP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit) },
                label = { Text("Cổng") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            FilledTonalButton(
                onClick = {
                    val parsedPort = port.toIntOrNull()
                    if (host.isNotBlank() && parsedPort != null) onConnect(host.trim(), parsedPort)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kết nối")
            }
        }
    }
}
