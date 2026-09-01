package dev.duongvan.atvremote

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.duongvan.atvremote.data.AtvDeviceRecord
import dev.duongvan.atvremote.data.DeviceStore
import dev.duongvan.atvremote.net.AppEntry
import dev.duongvan.atvremote.net.AtvDevice
import dev.duongvan.atvremote.net.CompanionClient
import dev.duongvan.atvremote.net.Discovery
import dev.duongvan.atvremote.net.HidCommand
import dev.duongvan.atvremote.net.TouchPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data class NeedPin(val error: String? = null, val submitting: Boolean = false) : ConnectionState
    data object Connected : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

data class UiState(
    val devices: List<AtvDevice> = emptyList(),
    val scanning: Boolean = false,
    val selected: AtvDeviceRecord? = null,
    val connection: ConnectionState = ConnectionState.Disconnected,
    val apps: List<AppEntry> = emptyList(),
    val appsLoading: Boolean = false,
    val muted: Boolean = false,
    val message: String? = null
)

class RemoteViewModel(application: Application) : AndroidViewModel(application) {

    private val store = DeviceStore(application)
    private val discovery = Discovery(application)

    private val _state = MutableStateFlow(UiState(selected = store.lastDevice))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var client: CompanionClient? = null
    private var scanJob: Job? = null
    private var volumeBeforeMute: Double = 0.4

    private val controllerName: String =
        "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifEmpty { "Android Remote" }

    // ---------------------------------------------------------------- devices

    fun startScan() {
        scanJob?.cancel()
        _state.value = _state.value.copy(devices = emptyList(), scanning = true, message = null)
        scanJob = viewModelScope.launch {
            runCatching {
                discovery.devices().collect { device ->
                    val current = _state.value.devices
                    if (current.none { it.id == device.id }) {
                        _state.value = _state.value.copy(devices = current + device)
                    }
                }
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    scanning = false,
                    message = error.message ?: "không quét được thiết bị"
                )
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _state.value = _state.value.copy(scanning = false)
    }

    fun select(device: AtvDevice) = select(AtvDeviceRecord(device.name, device.host, device.port))

    fun select(record: AtvDeviceRecord) {
        stopScan()
        store.lastDevice = record
        _state.value = _state.value.copy(
            selected = record,
            connection = ConnectionState.Connecting,
            apps = emptyList(),
            message = null
        )
        connect(record)
    }

    fun back() {
        viewModelScope.launch { runCatching { client?.disconnect() } }
        client = null
        _state.value = _state.value.copy(
            selected = null,
            connection = ConnectionState.Disconnected,
            apps = emptyList()
        )
        startScan()
    }

    // ------------------------------------------------------------- connecting

    private fun connect(record: AtvDeviceRecord) {
        viewModelScope.launch {
            client?.close()
            val newClient = CompanionClient(record.host, record.port, controllerName)
            client = newClient

            val credentials = store.credentialsFor(record.id)
            if (credentials == null) {
                beginPairing(newClient)
                return@launch
            }
            runCatching { newClient.connect(credentials) }
                .onSuccess { onConnected() }
                .onFailure { error ->
                    // Stored credentials are no longer accepted: pair again.
                    store.forget(record.id)
                    newClient.close()
                    val retry = CompanionClient(record.host, record.port, controllerName)
                    client = retry
                    beginPairing(retry, error.message)
                }
        }
    }

    /** The app icon menu needs the list as soon as a session is live. */
    private fun onConnected() {
        _state.value = _state.value.copy(connection = ConnectionState.Connected)
        loadApps()
    }

    private suspend fun beginPairing(target: CompanionClient, previousError: String? = null) {
        runCatching { target.startPairing() }
            .onSuccess {
                _state.value = _state.value.copy(connection = ConnectionState.NeedPin(previousError))
            }
            .onFailure { error ->
                _state.value = _state.value.copy(
                    connection = ConnectionState.Failed(error.message ?: "không kết nối được")
                )
            }
    }

    fun submitPin(pin: String) {
        val record = _state.value.selected ?: return
        val target = client ?: return
        _state.value = _state.value.copy(connection = ConnectionState.NeedPin(submitting = true))
        viewModelScope.launch {
            runCatching { target.finishPairing(pin) }
                .onSuccess { credentials ->
                    store.saveCredentials(record.id, credentials)
                    val session = CompanionClient(record.host, record.port, controllerName)
                    client = session
                    runCatching { session.connect(credentials) }
                        .onSuccess { onConnected() }
                        .onFailure { error ->
                            _state.value = _state.value.copy(
                                connection = ConnectionState.Failed(
                                    error.message ?: "không mở được phiên điều khiển"
                                )
                            )
                        }
                }
                .onFailure { error ->
                    val fresh = CompanionClient(record.host, record.port, controllerName)
                    client = fresh
                    beginPairing(fresh, error.message ?: "ghép nối thất bại")
                }
        }
    }

    fun retry() {
        _state.value.selected?.let { select(it) }
    }

    // -------------------------------------------------------------- commands

    private fun execute(block: suspend CompanionClient.() -> Unit) {
        val target = client ?: return
        if (!target.isReady) return
        viewModelScope.launch {
            runCatching { target.block() }.onFailure { error ->
                _state.value = _state.value.copy(message = error.message)
            }
        }
    }

    fun press(button: HidCommand) = execute { pressButton(button) }

    /** Puts the Apple TV to sleep, which is what its power button does. */
    fun sleepDevice() = execute { pressButton(HidCommand.Sleep) }

    /** Press and hold home opens Control Center on tvOS. */
    fun holdHome() = execute { holdButton(HidCommand.Home, 1200) }

    /** A double press of home is what brings up the app switcher. */
    fun appSwitcher() = execute { doublePressButton(HidCommand.Home) }

    fun holdSelect() = execute { holdButton(HidCommand.Select, 1000) }

    fun touch(x: Int, y: Int, phase: TouchPhase) = execute { this.touch(x, y, phase) }

    fun volumeUp() = execute { pressButton(HidCommand.VolumeUp) }

    fun volumeDown() = execute { pressButton(HidCommand.VolumeDown) }

    fun toggleMute() {
        val target = client ?: return
        viewModelScope.launch {
            runCatching {
                if (_state.value.muted) {
                    target.setVolume(volumeBeforeMute)
                    _state.value = _state.value.copy(muted = false)
                } else {
                    val current = target.getVolume()
                    if (current != null && current > 0.0) volumeBeforeMute = current
                    target.setVolume(0.0)
                    _state.value = _state.value.copy(muted = true)
                }
            }.onFailure {
                _state.value = _state.value.copy(
                    message = "Thiết bị này không cho chỉnh âm lượng qua Companion, dùng phím vật lý thay thế"
                )
            }
        }
    }

    fun loadApps() {
        val target = client ?: return
        _state.value = _state.value.copy(appsLoading = true)
        viewModelScope.launch {
            runCatching { target.appList() }
                .onSuccess { apps ->
                    _state.value = _state.value.copy(apps = apps, appsLoading = false)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        appsLoading = false,
                        message = error.message ?: "không lấy được danh sách ứng dụng"
                    )
                }
        }
    }

    fun launchApp(bundleId: String) = execute { this.launchApp(bundleId) }

    fun sendText(text: String, clearPrevious: Boolean) =
        execute { inputText(text, clearPrevious) }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    override fun onCleared() {
        super.onCleared()
        client?.close()
        scanJob?.cancel()
    }
}
