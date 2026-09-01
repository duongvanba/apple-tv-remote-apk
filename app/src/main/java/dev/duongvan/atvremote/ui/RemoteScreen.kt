package dev.duongvan.atvremote.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.Image
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.duongvan.atvremote.ConnectionState
import dev.duongvan.atvremote.RemoteViewModel
import dev.duongvan.atvremote.UiState
import dev.duongvan.atvremote.net.AppEntry
import dev.duongvan.atvremote.net.ArtworkLoader
import dev.duongvan.atvremote.net.HidCommand
import dev.duongvan.atvremote.net.TouchPhase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(state: UiState, viewModel: RemoteViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showApps by remember { mutableStateOf(false) }
    var showKeyboard by remember { mutableStateOf(false) }
    var showPowerConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.selected?.name ?: "Apple TV") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.back() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Đổi thiết bị"
                        )
                    }
                },
                actions = {
                    if (state.connection is ConnectionState.Connected) {
                        IconButton(onClick = { showPowerConfirm = true }) {
                            Icon(
                                Icons.Filled.PowerSettingsNew,
                                contentDescription = "Tắt Apple TV"
                            )
                        }
                        IconButton(onClick = { showKeyboard = true }) {
                            Icon(
                                Icons.Filled.Keyboard,
                                contentDescription = "Nhập văn bản"
                            )
                        }
                        IconButton(
                            onClick = {
                                showApps = true
                                if (state.apps.isEmpty()) viewModel.loadApps()
                            }
                        ) {
                            Icon(Icons.Filled.Apps, contentDescription = "Danh sách ứng dụng")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val connection = state.connection) {
                is ConnectionState.Connecting -> CenteredStatus("Đang kết nối…", loading = true)
                is ConnectionState.Failed -> FailureView(connection.message) { viewModel.retry() }
                is ConnectionState.Disconnected -> CenteredStatus("Chưa kết nối", loading = false)
                is ConnectionState.NeedPin -> {
                    CenteredStatus("Nhập mã ghép nối hiển thị trên TV", loading = false)
                    PinDialog(
                        error = connection.error,
                        submitting = connection.submitting,
                        onSubmit = { viewModel.submitPin(it) },
                        onCancel = { viewModel.back() }
                    )
                }
                is ConnectionState.Connected -> ConnectedRemote(
                    state = state,
                    viewModel = viewModel
                )
            }
        }
    }

    if (showPowerConfirm) {
        AlertDialog(
            onDismissRequest = { showPowerConfirm = false },
            title = { Text("Tắt Apple TV?") },
            text = { Text("Apple TV sẽ chuyển sang chế độ ngủ. Bấm nút bất kỳ để bật lại.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.sleepDevice()
                        showPowerConfirm = false
                    }
                ) {
                    Text("Tắt")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPowerConfirm = false }) { Text("Huỷ") }
            }
        )
    }

    if (showKeyboard) {
        TextInputDialog(
            onDismiss = { showKeyboard = false },
            onSend = { text, clearPrevious -> viewModel.sendText(text, clearPrevious) }
        )
    }

    if (showApps) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showApps = false }, sheetState = sheetState) {
            AppSheet(
                apps = state.apps,
                loading = state.appsLoading,
                onLaunch = {
                    viewModel.launchApp(it)
                    showApps = false
                },
                onReload = { viewModel.loadApps() }
            )
        }
    }
}

@Composable
private fun ConnectedRemote(
    state: UiState,
    viewModel: RemoteViewModel
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TouchPad(
            onTouch = viewModel::touch,
            onTap = { viewModel.press(HidCommand.Select) },
            onLongPress = { viewModel.holdSelect() },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )

        Text(
            text = "Phím âm lượng vật lý điều khiển âm lượng Apple TV · giữ Home để mở Control Center",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        BottomControls(
            muted = state.muted,
            onBack = { viewModel.press(HidCommand.Menu) },
            onHome = { viewModel.press(HidCommand.Home) },
            onHomeLong = { viewModel.holdHome() },
            onRunningApps = { viewModel.appSwitcher() },
            onPlayPause = { viewModel.press(HidCommand.PlayPause) },
            onMute = { viewModel.toggleMute() }
        )
    }
}

// ------------------------------------------------------------- app icon menu

private val AppColors = listOf(
    Color(0xFF2E5AAC),
    Color(0xFF7A3E9D),
    Color(0xFF1F7A5A),
    Color(0xFFB5561B),
    Color(0xFF8E1F3D),
    Color(0xFF3E6B7A),
    Color(0xFF5B4AA8),
    Color(0xFF9A7B12)
)

@Composable
private fun AppSheet(
    apps: List<AppEntry>,
    loading: Boolean,
    onLaunch: (String) -> Unit,
    onReload: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(bottom = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Ứng dụng trên Apple TV",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onReload, enabled = !loading) { Text("Tải lại") }
        }

        when {
            apps.isEmpty() && loading ->
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CenteredStatus("Đang tải danh sách…", loading = true)
                }

            apps.isEmpty() ->
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CenteredStatus("Chưa lấy được danh sách ứng dụng", loading = false)
                }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                items(apps, key = { it.bundleId }) { app ->
                    AppIcon(app = app, onLaunch = onLaunch)
                }
            }
        }
    }
}

/** Companion only returns names, so app tiles use initials instead of artwork. */
private fun monogram(name: String): String {
    val words = name.trim().split(' ', '-', '_', '.').filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).replaceFirstChar { it.uppercaseChar() }
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

@Composable
private fun AppIcon(app: AppEntry, onLaunch: (String) -> Unit) {
    val context = LocalContext.current
    val builtIn = remember(app.bundleId, app.name) { builtInAppIcon(app.bundleId, app.name) }

    // Built-in tvOS apps are not on the store, so don't even try to look them up.
    val artwork by produceState<ImageBitmap?>(initialValue = null, key1 = app.bundleId) {
        value = if (builtIn != null) null else ArtworkLoader.icon(context, app.bundleId)?.asImageBitmap()
    }
    val color = AppColors[
        (app.bundleId.hashCode().toLong().and(0xFFFFFFFFL) % AppColors.size).toInt()
    ]

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().clickable { onLaunch(app.bundleId) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    when {
                        artwork != null -> Color.Transparent
                        builtIn != null -> builtIn.background
                        else -> color
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            val image = artwork
            when {
                image != null -> Image(
                    bitmap = image,
                    contentDescription = app.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                builtIn != null -> Icon(
                    imageVector = builtIn.icon,
                    contentDescription = app.name,
                    tint = Color.White,
                    modifier = Modifier.fillMaxSize(0.55f)
                )

                else -> Text(
                    text = monogram(app.name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// ------------------------------------------------------------------ touchpad

@Composable
private fun TouchPad(
    onTouch: (Int, Int, TouchPhase) -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    var area by remember { mutableStateOf(IntSize.Zero) }

    fun toPad(value: Float, extent: Int): Int =
        if (extent == 0) 0 else ((value / extent) * 1000f).toInt().coerceIn(0, 1000)

    Box(
        modifier = modifier
            .onSizeChanged { area = it }
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(28.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                shape = RoundedCornerShape(28.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() })
            }
            .pointerInput(area) {
                var latest = Offset.Zero
                var lastSentAt = 0L
                detectDragGestures(
                    onDragStart = { offset ->
                        latest = offset
                        lastSentAt = System.currentTimeMillis()
                        onTouch(
                            toPad(offset.x, area.width),
                            toPad(offset.y, area.height),
                            TouchPhase.Press
                        )
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        latest = change.position
                        val now = System.currentTimeMillis()
                        if (now - lastSentAt >= 16) {
                            lastSentAt = now
                            onTouch(
                                toPad(latest.x, area.width),
                                toPad(latest.y, area.height),
                                TouchPhase.Hold
                            )
                        }
                    },
                    onDragEnd = {
                        onTouch(
                            toPad(latest.x, area.width),
                            toPad(latest.y, area.height),
                            TouchPhase.Release
                        )
                    },
                    onDragCancel = {
                        onTouch(
                            toPad(latest.x, area.width),
                            toPad(latest.y, area.height),
                            TouchPhase.Release
                        )
                    }
                )
            }
    ) {
        Icon(
            imageVector = Icons.Filled.TouchApp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
            modifier = Modifier.align(Alignment.Center).size(64.dp)
        )
    }
}

// -------------------------------------------------------------- bottom row

@Composable
private fun BottomControls(
    muted: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onHomeLong: () -> Unit,
    onRunningApps: () -> Unit,
    onPlayPause: () -> Unit,
    onMute: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ControlButton(label = "Quay lại", onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "Quay lại")
        }
        ControlButton(label = "Home", onClick = onHome, onLongClick = onHomeLong) {
            Icon(Icons.Filled.Home, contentDescription = "Home, giữ để mở Control Center")
        }
        ControlButton(label = "Đang chạy", onClick = onRunningApps) {
            Icon(Icons.Filled.Layers, contentDescription = "Ứng dụng đang chạy")
        }
        ControlButton(label = "Phát/Dừng", onClick = onPlayPause) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Tạm dừng hoặc tiếp tục")
        }
        ControlButton(label = if (muted) "Bật tiếng" else "Tắt tiếng", onClick = onMute) {
            Icon(
                if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Tắt tiếng"
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ControlButton(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    icon: @Composable () -> Unit
) {
    val view = LocalView.current

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .combinedClickable(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onClick()
                    },
                    onLongClick = onLongClick?.let { action ->
                        {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            action()
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                icon()
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ----------------------------------------------------------------- dialogs

@Composable
private fun TextInputDialog(onDismiss: () -> Unit, onSend: (String, Boolean) -> Unit) {
    var text by remember { mutableStateOf("") }
    var clearPrevious by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nhập văn bản lên Apple TV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Mở sẵn ô tìm kiếm hoặc ô nhập trên TV, rồi gõ nội dung ở đây.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Nội dung") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { clearPrevious = !clearPrevious }
                ) {
                    Checkbox(checked = clearPrevious, onCheckedChange = { clearPrevious = it })
                    Text("Xoá nội dung cũ trước khi gửi")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSend(text, clearPrevious)
                    onDismiss()
                },
                enabled = text.isNotEmpty() || clearPrevious
            ) {
                Text("Gửi")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}

@Composable
private fun PinDialog(
    error: String?,
    submitting: Boolean,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { },
        title = { Text("Ghép nối Apple TV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Nhập mã 4 chữ số đang hiện trên màn hình Apple TV.")
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(4) },
                    label = { Text("Mã ghép nối") },
                    singleLine = true,
                    enabled = !submitting,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                if (submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(pin) },
                enabled = pin.length == 4 && !submitting
            ) {
                Text("Ghép nối")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !submitting) { Text("Huỷ") }
        }
    )
}

@Composable
private fun CenteredStatus(message: String, loading: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (loading) CircularProgressIndicator()
        Text(message, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FailureView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onRetry) { Text("Thử lại") }
    }
}
