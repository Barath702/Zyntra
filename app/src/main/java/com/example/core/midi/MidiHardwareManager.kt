package com.example.core.midi

import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.core.audio.NativeAudioEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MidiDeviceSummary(
    val id: Int,
    val name: String,
    val manufacturer: String,
    val hasOutputFromDevice: Boolean, // sends MIDI into our app
    val hasInputToDevice: Boolean,    // receives MIDI from our app
    val isBluetooth: Boolean
)

class MidiHardwareManager(private val context: Context) {
    private val midiManager: MidiManager? =
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
        } else null

    private val handler = Handler(Looper.getMainLooper())

    private val _devices = MutableStateFlow<List<MidiDeviceSummary>>(emptyList())
    val devices: StateFlow<List<MidiDeviceSummary>> = _devices.asStateFlow()

    private val _connectedInputName = MutableStateFlow<String?>(null)
    val connectedInputName: StateFlow<String?> = _connectedInputName.asStateFlow()

    private val _connectedOutputName = MutableStateFlow<String?>(null)
    val connectedOutputName: StateFlow<String?> = _connectedOutputName.asStateFlow()

    private val _midiSupported = MutableStateFlow(midiManager != null)
    val midiSupported: StateFlow<Boolean> = _midiSupported.asStateFlow()

    private var callbackRegistered = false

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            refreshDevices()
            // Auto-connect first input-producing MIDI keyboard/controller if none connected
            if (_connectedInputName.value == null && device.outputPortCount > 0) {
                connectInputDevice(device.id)
            }
        }

        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            refreshDevices()
        }
    }

    fun start() {
        val mm = midiManager ?: return
        if (!callbackRegistered) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mm.registerDeviceCallback(
                    MidiManager.TRANSPORT_MIDI_BYTE_STREAM,
                    { runnable -> handler.post(runnable) },
                    deviceCallback
                )
            } else {
                @Suppress("DEPRECATION")
                mm.registerDeviceCallback(deviceCallback, handler)
            }
            callbackRegistered = true
        }
        refreshDevices()
    }

    fun stop() {
        val mm = midiManager ?: return
        if (callbackRegistered) {
            mm.unregisterDeviceCallback(deviceCallback)
            callbackRegistered = false
        }
    }

    @Suppress("DEPRECATION")
    fun refreshDevices() {
        val mm = midiManager ?: return
        val infos = mm.devices
        _devices.value = infos.map { info ->
            val props = info.properties
            val name = props.getString(MidiDeviceInfo.PROPERTY_NAME)
                ?: props.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
                ?: "MIDI Device ${info.id}"
            val mfg = props.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER) ?: "Generic"
            MidiDeviceSummary(
                id = info.id,
                name = name,
                manufacturer = mfg,
                hasOutputFromDevice = info.outputPortCount > 0,
                hasInputToDevice = info.inputPortCount > 0,
                isBluetooth = info.type == MidiDeviceInfo.TYPE_BLUETOOTH
            )
        }
    }

    @Suppress("DEPRECATION")
    fun connectInputDevice(deviceId: Int) {
        val mm = midiManager ?: return
        val info = mm.devices.firstOrNull { it.id == deviceId && it.outputPortCount > 0 } ?: return
        val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "MIDI Input"
        mm.openDevice(info, { device ->
            if (device != null && NativeAudioEngine.isNativeLoaded) {
                NativeAudioEngine.nativeAttachMidiInput(device, 0)
                _connectedInputName.value = name
            }
        }, handler)
    }

    fun disconnectInput() {
        if (NativeAudioEngine.isNativeLoaded) {
            NativeAudioEngine.nativeDetachMidiInput()
        }
        _connectedInputName.value = null
    }

    @Suppress("DEPRECATION")
    fun connectOutputDevice(deviceId: Int) {
        val mm = midiManager ?: return
        val info = mm.devices.firstOrNull { it.id == deviceId && it.inputPortCount > 0 } ?: return
        val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "MIDI Output"
        mm.openDevice(info, { device ->
            if (device != null && NativeAudioEngine.isNativeLoaded) {
                NativeAudioEngine.nativeAttachMidiOutput(device, 0)
                _connectedOutputName.value = name
            }
        }, handler)
    }

    fun disconnectOutput() {
        if (NativeAudioEngine.isNativeLoaded) {
            NativeAudioEngine.nativeDetachMidiOutput()
        }
        _connectedOutputName.value = null
    }

    fun setRouting(targetTrack: Int, channel: Int) {
        if (NativeAudioEngine.isNativeLoaded) {
            NativeAudioEngine.nativeSetMidiRouting(targetTrack, channel)
        }
    }
}
