package com.example.core.project

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NoteCraftApplication
import com.example.core.audio.ExportedWavResult
import com.example.core.audio.TransportTelemetryState
import com.example.core.common.GlobalQuantization
import com.example.core.common.MelodicPadLayout
import com.example.core.common.NoteRepeatRate
import com.example.core.common.ScaleType
import com.example.core.common.SettingsState
import com.example.core.database.ProjectEntity
import com.example.core.database.SampleEntity
import com.example.core.midi.MidiDeviceSummary
import com.example.core.samples.MicRecorder
import com.example.core.samples.SamplePcmTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NoteCraftApplication
    private val projectRepo = app.projectRepository
    private val settingsRepo = app.settingsRepository
    val audioController = app.audioController
    private val midiManager = app.midiManager
    val micRecorder = MicRecorder()

    val projects: StateFlow<List<ProjectEntity>> = projectRepo.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val samples: StateFlow<List<SampleEntity>> = projectRepo.allSamples
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<SettingsState> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsState())

    val telemetry: StateFlow<TransportTelemetryState> = audioController.telemetry
    val isResampling: StateFlow<Boolean> = audioController.isResampling
    val exportProgress: StateFlow<Float?> = audioController.exportProgress
    val capturedNoteCount: StateFlow<Int> = audioController.capturedNoteCount

    val midiDevices: StateFlow<List<MidiDeviceSummary>> = midiManager.devices
    val connectedMidiInput: StateFlow<String?> = midiManager.connectedInputName
    val connectedMidiOutput: StateFlow<String?> = midiManager.connectedOutputName
    val midiSupported: StateFlow<Boolean> = midiManager.midiSupported

    private val _currentSet = MutableStateFlow(DemoProjectsFactory.createEmptySet("Untitled Set"))
    val currentSet: StateFlow<NoteSet> = _currentSet.asStateFlow()

    private val _selectedTrackIndex = MutableStateFlow(0)
    val selectedTrackIndex: StateFlow<Int> = _selectedTrackIndex.asStateFlow()

    private val _selectedSceneIndex = MutableStateFlow(0)
    val selectedSceneIndex: StateFlow<Int> = _selectedSceneIndex.asStateFlow()

    private val _selectedPadIndex = MutableStateFlow(0)
    val selectedPadIndex: StateFlow<Int> = _selectedPadIndex.asStateFlow()

    private val _autosaveRecovery = MutableStateFlow<NoteSet?>(null)
    val autosaveRecovery: StateFlow<NoteSet?> = _autosaveRecovery.asStateFlow()

    private val _missingSamples = MutableStateFlow<List<String>>(emptyList())
    val missingSamples: StateFlow<List<String>> = _missingSamples.asStateFlow()

    private val _previewingSetId = MutableStateFlow<String?>(null)
    val previewingSetId: StateFlow<String?> = _previewingSetId.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _noteRepeatRate = MutableStateFlow(NoteRepeatRate.OFF)
    val noteRepeatRate: StateFlow<NoteRepeatRate> = _noteRepeatRate.asStateFlow()

    private val _automationRecording = MutableStateFlow(false)
    val automationRecording: StateFlow<Boolean> = _automationRecording.asStateFlow()

    private val _lastExportResult = MutableStateFlow<ExportedWavResult?>(null)
    val lastExportResult: StateFlow<ExportedWavResult?> = _lastExportResult.asStateFlow()

    private val undoStack = ArrayDeque<NoteSet>()
    private val redoStack = ArrayDeque<NoteSet>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var autosaveJob: Job? = null

    init {
        viewModelScope.launch {
            val initialSettings = settingsRepo.settingsFlow.first()
            val loadedSamples = projectRepo.initializeIfNeeded()
            audioController.startEngine(initialSettings)
            audioController.loadSamplesIntoEngine(loadedSamples)
            midiManager.start()
            midiManager.setRouting(0, initialSettings.midiFilterChannel)

            _autosaveRecovery.value = projectRepo.checkAutosaveRecovery()

            val projs = projectRepo.allProjects.first()
            val firstSet = projs.firstOrNull()?.let { projectRepo.loadSet(it.id) }
                ?: DemoProjectsFactory.buildDemoSets().first()
            setCurrentSetInternal(firstSet, clearHistory = true)
            startAutosaveLoop()
        }
    }

    private fun startAutosaveLoop() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            while (isActive) {
                val intervalMs = (settings.value.autosaveIntervalSec.coerceIn(5, 120) * 1000L)
                delay(intervalMs)
                projectRepo.writeAutosave(_currentSet.value)
            }
        }
    }

    fun clearStatusMessage() { _statusMessage.value = null }
    fun clearMissingSamples() { _missingSamples.value = emptyList() }

    private fun setCurrentSetInternal(set: NoteSet, clearHistory: Boolean = false) {
        if (clearHistory) {
            undoStack.clear()
            redoStack.clear()
        }
        _currentSet.value = set
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        audioController.syncSet(set)
        viewModelScope.launch {
            _missingSamples.value = projectRepo.getMissingSamples(set)
        }
    }

    private fun mutateSet(recordUndo: Boolean = true, transform: (NoteSet) -> NoteSet) {
        val prev = _currentSet.value
        val next = transform(prev).copy(modifiedAt = System.currentTimeMillis())
        if (next == prev) return
        if (recordUndo) {
            undoStack.addLast(prev)
            if (undoStack.size > 40) undoStack.removeFirst()
            redoStack.clear()
        }
        _currentSet.value = next
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    fun checkpointUndo() {
        undoStack.addLast(_currentSet.value)
        if (undoStack.size > 40) undoStack.removeFirst()
        redoStack.clear()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_currentSet.value)
        _currentSet.value = prev
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        audioController.syncSet(prev)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_currentSet.value)
        _currentSet.value = next
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        audioController.syncSet(next)
    }

    fun saveCurrentSet() {
        viewModelScope.launch {
            val saved = projectRepo.saveSet(_currentSet.value)
            _currentSet.value = saved
            _statusMessage.value = "Saved \"${saved.name}\""
        }
    }

    fun onExitProject() {
        audioController.stopAndResetProjectTransport()
        val toSave = _currentSet.value
        viewModelScope.launch {
            val saved = projectRepo.saveSet(toSave)
            _currentSet.value = saved
        }
    }

    // ---------------- Set Library Actions ----------------
    fun createNewSet(name: String, bpm: Float, onOpened: () -> Unit = {}) {
        audioController.stopAndResetProjectTransport()
        viewModelScope.launch {
            val created = projectRepo.createNewSet(name, bpm)
            setCurrentSetInternal(created, clearHistory = true)
            _selectedTrackIndex.value = 0
            _selectedSceneIndex.value = 0
            onOpened()
        }
    }

    fun openSet(id: String, onOpened: () -> Unit = {}) {
        audioController.stopAndResetProjectTransport()
        viewModelScope.launch {
            val loaded = projectRepo.loadSet(id) ?: return@launch
            _previewingSetId.value = null
            setCurrentSetInternal(loaded, clearHistory = true)
            _selectedTrackIndex.value = 0
            _selectedSceneIndex.value = 0
            onOpened()
        }
    }

    fun previewSet(id: String) {
        viewModelScope.launch {
            if (_previewingSetId.value == id && telemetry.value.isPlaying) {
                audioController.stopPlayback()
                _previewingSetId.value = null
                return@launch
            }
            audioController.stopPlayback()
            val loaded = projectRepo.loadSet(id) ?: return@launch
            _previewingSetId.value = id
            setCurrentSetInternal(loaded, clearHistory = true)
            audioController.launchScene(0)
        }
    }

    fun renameSet(id: String, newName: String) {
        viewModelScope.launch {
            val updated = projectRepo.renameSet(id, newName)
            if (updated != null && _currentSet.value.id == id) {
                _currentSet.value = updated
            }
        }
    }

    fun duplicateSet(id: String) {
        viewModelScope.launch { projectRepo.duplicateSet(id) }
    }

    fun deleteSet(id: String) {
        viewModelScope.launch { projectRepo.deleteSet(id) }
    }

    fun recoverAutosave(onOpened: () -> Unit) {
        val rec = _autosaveRecovery.value ?: return
        audioController.stopAndResetProjectTransport()
        _autosaveRecovery.value = null
        setCurrentSetInternal(rec, clearHistory = true)
        saveCurrentSet()
        onOpened()
    }

    fun dismissAutosave() {
        _autosaveRecovery.value = null
        viewModelScope.launch { projectRepo.clearAutosave() }
    }

    fun exportProjectToUri(id: String, uri: Uri) {
        viewModelScope.launch {
            val target = if (id == _currentSet.value.id) _currentSet.value else projectRepo.loadSet(id) ?: return@launch
            val ok = projectRepo.exportProjectToUri(target, uri)
            _statusMessage.value = if (ok) "Exported project" else "Export failed"
        }
    }

    fun importProjectFromUri(uri: Uri) {
        viewModelScope.launch {
            val imported = projectRepo.importProjectFromUri(uri)
            _statusMessage.value = if (imported != null) "Imported \"${imported.name}\"" else "Invalid project file"
        }
    }

    // ---------------- Selection & Navigation ----------------
    fun selectTrack(index: Int) {
        val clamped = index.coerceIn(0, (_currentSet.value.tracks.size - 1).coerceAtLeast(0))
        _selectedTrackIndex.value = clamped
        midiManager.setRouting(clamped, settings.value.midiFilterChannel)
    }

    fun selectScene(index: Int) {
        _selectedSceneIndex.value = index.coerceIn(0, 7)
    }

    fun selectPad(pad: Int) {
        _selectedPadIndex.value = pad.coerceIn(0, 15)
    }

    // ---------------- Transport & Global ----------------
    fun togglePlay() {
        _previewingSetId.value = null
        audioController.togglePlay()
    }

    fun togglePianoRollPlay(trackIdx: Int, sceneIdx: Int) {
        _previewingSetId.value = null
        val tState = telemetry.value
        val activeForTrack = tState.activeClips.getOrElse(trackIdx) { -1 }
        if (!tState.isPlaying || activeForTrack != sceneIdx) {
            audioController.launchClip(trackIdx, sceneIdx, startTransportIfStopped = true)
        } else {
            audioController.togglePlay()
        }
    }

    fun restartTransport() = audioController.restart()

    fun setCaptureArmed(armed: Boolean) = audioController.setCaptureArmed(armed)

    fun releaseAllPreviewNotes() = audioController.releaseAllPreviewNotes()

    fun previewSample(sample: SampleEntity) {
        audioController.previewSampleSlot(sample.slotIndex, 0.85f)
    }

    fun setBpm(bpm: Float) {
        val clamped = bpm.coerceIn(40f, 240f)
        mutateSet(recordUndo = false) { it.copy(bpm = clamped) }
        audioController.setBpm(clamped)
    }

    fun setSwing(swing: Float) {
        val clamped = swing.coerceIn(0f, 1f)
        mutateSet(recordUndo = false) { it.copy(swing = clamped) }
        audioController.setSwing(clamped)
    }

    fun setGlobalQuantization(q: GlobalQuantization) {
        mutateSet { it.copy(globalQuantization = q) }
        audioController.syncSet(_currentSet.value)
    }

    fun setKeyAndScale(rootNote: Int, scale: ScaleType) {
        mutateSet { it.copy(rootNote = rootNote.mod(12), scaleType = scale) }
    }

    fun toggleMetronome() {
        val next = !settings.value.metronomeEnabled
        viewModelScope.launch { settingsRepo.update { it.copy(metronomeEnabled = next) } }
        audioController.setMetronome(next)
    }

    // ---------------- Track Management (up to 8) ----------------
    fun addTrack(type: TrackType) {
        val tracks = _currentSet.value.tracks
        if (tracks.size >= 8) {
            _statusMessage.value = "Max 8 tracks reached"
            return
        }
        val colorIdx = tracks.size % 8
        val newTrack = when (type) {
            TrackType.DRUM_RACK -> TrackModel(
                name = "Drums ${tracks.size + 1}",
                colorIndex = colorIdx,
                type = type,
                presetName = "Studio Kit",
                drumPads = DemoProjectsFactory.defaultDrumPads(tracks.size % 3)
            )
            TrackType.SUBTRACTIVE_SYNTH -> TrackModel(
                name = "Synth ${tracks.size + 1}",
                colorIndex = colorIdx,
                type = type,
                presetName = "Analog Poly"
            )
            TrackType.MELODIC_SAMPLER -> TrackModel(
                name = "Sampler ${tracks.size + 1}",
                colorIndex = colorIdx,
                type = type,
                presetName = "Electric Keys"
            )
        }
        mutateSet { it.copy(tracks = it.tracks + newTrack) }
        val newIdx = _currentSet.value.tracks.lastIndex
        _selectedTrackIndex.value = newIdx
        audioController.syncTrack(newIdx, newTrack)
    }

    fun duplicateTrack(index: Int) {
        val tracks = _currentSet.value.tracks
        if (index !in tracks.indices || tracks.size >= 8) return
        val copy = tracks[index].copy(
            id = UUID.randomUUID().toString(),
            name = "${tracks[index].name} Copy"
        )
        mutateSet { it.copy(tracks = it.tracks + copy) }
        audioController.syncSet(_currentSet.value)
    }

    fun removeTrack(index: Int) {
        if (_currentSet.value.tracks.size <= 1 || index !in _currentSet.value.tracks.indices) return
        mutateSet { set ->
            set.copy(tracks = set.tracks.filterIndexed { i, _ -> i != index })
        }
        _selectedTrackIndex.value = _selectedTrackIndex.value.coerceAtMost(_currentSet.value.tracks.lastIndex)
        audioController.syncSet(_currentSet.value)
    }

    fun renameTrack(index: Int, newName: String) {
        mutateSet { set ->
            set.copy(tracks = set.tracks.mapIndexed { i, t -> if (i == index) t.copy(name = newName.trim().ifEmpty { t.name }) else t })
        }
    }

    fun changeTrackColor(index: Int, colorIdx: Int) {
        mutateSet { set ->
            set.copy(tracks = set.tracks.mapIndexed { i, t -> if (i == index) t.copy(colorIndex = colorIdx) else t })
        }
    }

    fun moveTrack(from: Int, to: Int) {
        val list = _currentSet.value.tracks.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        val item = list.removeAt(from)
        list.add(to, item)
        mutateSet { it.copy(tracks = list) }
        _selectedTrackIndex.value = to
        audioController.syncSet(_currentSet.value)
    }

    // ---------------- Mixer ----------------
    fun setTrackVolume(trackIdx: Int, volume: Float) {
        val v = volume.coerceIn(0f, 1.25f)
        mutateSet(recordUndo = false) { set ->
            set.copy(tracks = set.tracks.mapIndexed { i, t -> if (i == trackIdx) t.copy(volume = v) else t })
        }
        val tr = _currentSet.value.tracks.getOrNull(trackIdx) ?: return
        audioController.setTrackMix(trackIdx, v, tr.pan, tr.muted, tr.soloed)
        recordAutomationIfArmed(trackIdx, 0, "Volume", (v / 1.2f).coerceIn(0f, 1f))
    }

    fun setTrackPan(trackIdx: Int, pan: Float) {
        val p = pan.coerceIn(-1f, 1f)
        mutateSet(recordUndo = false) { set ->
            set.copy(tracks = set.tracks.mapIndexed { i, t -> if (i == trackIdx) t.copy(pan = p) else t })
        }
        val tr = _currentSet.value.tracks.getOrNull(trackIdx) ?: return
        audioController.setTrackMix(trackIdx, tr.volume, p, tr.muted, tr.soloed)
        recordAutomationIfArmed(trackIdx, 1, "Pan", ((p + 1f) / 2f).coerceIn(0f, 1f))
    }

    fun toggleTrackMute(trackIdx: Int) {
        mutateSet { set ->
            set.copy(tracks = set.tracks.mapIndexed { i, t -> if (i == trackIdx) t.copy(muted = !t.muted) else t })
        }
        val tr = _currentSet.value.tracks.getOrNull(trackIdx) ?: return
        audioController.setTrackMix(trackIdx, tr.volume, tr.pan, tr.muted, tr.soloed)
    }

    fun toggleTrackSolo(trackIdx: Int) {
        mutateSet { set ->
            set.copy(tracks = set.tracks.mapIndexed { i, t -> if (i == trackIdx) t.copy(soloed = !t.soloed) else t })
        }
        val tr = _currentSet.value.tracks.getOrNull(trackIdx) ?: return
        audioController.setTrackMix(trackIdx, tr.volume, tr.pan, tr.muted, tr.soloed)
    }

    fun setMasterVolume(vol: Float) {
        val v = vol.coerceIn(0f, 1.25f)
        mutateSet(recordUndo = false) { it.copy(masterVolume = v) }
        audioController.setMasterVolume(v)
    }

    fun updateMasterEffect(slot: Int, fx: EffectModel) {
        mutateSet { set ->
            set.copy(masterEffects = EffectChainTransforms.updateEffectAt(set.masterEffects, slot, fx))
        }
        syncMasterEffectsToEngine()
    }

    fun addMasterEffect(type: EffectType) {
        if (type == EffectType.NONE) return
        val newFx = EffectModel(type = type, bypassed = false, params = type.defaultParams, presetName = "Default")
        mutateSet { set ->
            set.copy(masterEffects = EffectChainTransforms.addEffect(set.masterEffects, newFx))
        }
        syncMasterEffectsToEngine()
    }

    fun removeMasterEffect(slot: Int) {
        mutateSet { set ->
            set.copy(masterEffects = EffectChainTransforms.removeEffectAt(set.masterEffects, slot))
        }
        syncMasterEffectsToEngine()
    }

    fun moveMasterEffect(from: Int, to: Int) {
        mutateSet { set ->
            set.copy(masterEffects = EffectChainTransforms.moveEffect(set.masterEffects, from, to))
        }
        syncMasterEffectsToEngine()
    }

    private fun syncMasterEffectsToEngine() {
        val list = _currentSet.value.masterEffects
        for (slot in 0 until EffectChainTransforms.MAX_EFFECT_SLOTS) {
            audioController.sendEffect(
                scope = 2,
                trackOrPad = 0,
                slot = slot,
                fx = list.getOrNull(slot) ?: EffectModel(type = EffectType.NONE)
            )
        }
    }

    // ---------------- Clips & Scenes ----------------
    fun createOrOpenClip(trackIdx: Int, sceneIdx: Int) {
        val track = _currentSet.value.tracks.getOrNull(trackIdx) ?: return
        if (track.clips[sceneIdx] == null) {
            val newClip = ClipModel(
                name = "${track.name} ${sceneIdx + 1}",
                colorIndex = track.colorIndex,
                lengthBars = 1
            )
            mutateSet { set ->
                set.copy(tracks = set.tracks.mapIndexed { i, t ->
                    if (i == trackIdx) t.copy(clips = t.clips + (sceneIdx to newClip)) else t
                })
            }
            audioController.syncClip(trackIdx, sceneIdx, newClip, track.type == TrackType.DRUM_RACK)
        }
        _selectedTrackIndex.value = trackIdx
        _selectedSceneIndex.value = sceneIdx
    }

    fun launchClip(trackIdx: Int, sceneIdx: Int) {
        _selectedTrackIndex.value = trackIdx
        _selectedSceneIndex.value = sceneIdx
        audioController.launchClip(trackIdx, sceneIdx, true)
    }

    fun stopTrackClip(trackIdx: Int) {
        audioController.stopTrackClip(trackIdx)
    }

    fun duplicateClip(trackIdx: Int, sceneIdx: Int) {
        val track = _currentSet.value.tracks.getOrNull(trackIdx) ?: return
        val clip = track.clips[sceneIdx] ?: return
        val targetScene = (0 until 8).firstOrNull { track.clips[it] == null } ?: return
        val copy = clip.copy(id = UUID.randomUUID().toString(), name = "${clip.name} Copy")
        mutateSet { set ->
            set.copy(tracks = set.tracks.mapIndexed { i, t ->
                if (i == trackIdx) t.copy(clips = t.clips + (targetScene to copy)) else t
            })
        }
        audioController.syncClip(trackIdx, targetScene, copy, track.type == TrackType.DRUM_RACK)
    }

    fun renameClip(trackIdx: Int, sceneIdx: Int, name: String) {
        mutateSet { set ->
            set.copy(tracks = set.tracks.mapIndexed { i, t ->
                if (i == trackIdx) {
                    val c = t.clips[sceneIdx] ?: return@mapIndexed t
                    t.copy(clips = t.clips + (sceneIdx to c.copy(name = name.trim().ifEmpty { c.name })))
                } else t
            })
        }
    }

    fun changeClipColor(trackIdx: Int, sceneIdx: Int, colorIdx: Int) {
        mutateSet { set ->
            set.copy(tracks = set.tracks.mapIndexed { i, t ->
                if (i == trackIdx) {
                    val c = t.clips[sceneIdx] ?: return@mapIndexed t
                    t.copy(clips = t.clips + (sceneIdx to c.copy(colorIndex = colorIdx)))
                } else t
            })
        }
    }

    fun deleteClip(trackIdx: Int, sceneIdx: Int) {
        mutateSet { set ->
            set.copy(tracks = set.tracks.mapIndexed { i, t ->
                if (i == trackIdx) t.copy(clips = t.clips - sceneIdx) else t
            })
        }
        audioController.syncClip(trackIdx, sceneIdx, null, false)
    }

    fun launchScene(sceneIdx: Int) {
        _selectedSceneIndex.value = sceneIdx
        audioController.launchScene(sceneIdx)
    }

    fun renameScene(sceneIdx: Int, name: String) {
        mutateSet { set ->
            set.copy(scenes = set.scenes.mapIndexed { i, s ->
                if (i == sceneIdx) s.copy(name = name.trim().ifEmpty { s.name }) else s
            })
        }
    }

    fun duplicateScene(sceneIdx: Int) {
        val emptyScene = (0 until 8).firstOrNull { sIdx ->
            _currentSet.value.tracks.all { it.clips[sIdx] == null }
        } ?: return
        mutateSet { set ->
            val newTracks = set.tracks.map { tr ->
                val clip = tr.clips[sceneIdx]
                if (clip != null) {
                    tr.copy(clips = tr.clips + (emptyScene to clip.copy(id = UUID.randomUUID().toString())))
                } else tr
            }
            val newScenes = set.scenes.mapIndexed { i, s ->
                if (i == emptyScene) s.copy(name = "${set.scenes[sceneIdx].name} Copy") else s
            }
            set.copy(tracks = newTracks, scenes = newScenes)
        }
        audioController.syncSet(_currentSet.value)
    }

    fun deleteScene(sceneIdx: Int) {
        mutateSet { set ->
            val newTracks = set.tracks.map { it.copy(clips = it.clips - sceneIdx) }
            set.copy(tracks = newTracks)
        }
        audioController.syncSet(_currentSet.value)
    }

    fun swapScenes(a: Int, b: Int) {
        if (a !in 0..7 || b !in 0..7 || a == b) return
        mutateSet { set ->
            val newScenes = set.scenes.toMutableList()
            val tmpS = newScenes[a]
            newScenes[a] = newScenes[b]
            newScenes[b] = tmpS
            val newTracks = set.tracks.map { tr ->
                val map = tr.clips.toMutableMap()
                val ca = map[a]
                val cb = map[b]
                if (cb != null) map[a] = cb else map.remove(a)
                if (ca != null) map[b] = ca else map.remove(b)
                tr.copy(clips = map)
            }
            set.copy(scenes = newScenes, tracks = newTracks)
        }
        audioController.syncSet(_currentSet.value)
    }

    // ---------------- Instrument & Effects ----------------
    fun updateSynthParams(trackIdx: Int, transform: (SynthParamsModel) -> SynthParamsModel) {
        mutateSet(recordUndo = false) { set ->
            set.copy(tracks = set.tracks.mapIndexed { i, t ->
                if (i == trackIdx) t.copy(synthParams = transform(t.synthParams)) else t
            })
        }
        val tr = _currentSet.value.tracks.getOrNull(trackIdx) ?: return
        audioController.syncSynthParams(trackIdx, tr.synthParams)
        recordAutomationIfArmed(trackIdx, 2, "Cutoff", tr.synthParams.cutoff)
    }

    fun applySynthPreset(trackIdx: Int, presetName: String, params: SynthParamsModel) {
        mutateSet { set ->
            set.copy(tracks = set.tracks.mapIndexed { i, t ->
                if (i == trackIdx) t.copy(presetName = presetName, synthParams = params) else t
            })
        }
        audioController.syncSynthParams(trackIdx, params)
    }

    fun updateSamplerParams(trackIdx: Int, transform: (SamplerParamsModel) -> SamplerParamsModel) {
        mutateSet(recordUndo = false) { set ->
            set.copy(tracks = set.tracks.mapIndexed { i, t ->
                if (i == trackIdx) t.copy(samplerParams = transform(t.samplerParams)) else t
            })
        }
        val tr = _currentSet.value.tracks.getOrNull(trackIdx) ?: return
        audioController.syncSamplerParams(trackIdx, tr.samplerParams)
    }

    fun updateDrumPad(trackIdx: Int, padIdx: Int, transform: (DrumPadModel) -> DrumPadModel) {
        mutateSet(recordUndo = false) { set ->
            set.copy(tracks = set.tracks.mapIndexed { i, t ->
                if (i == trackIdx) {
                    val pads = t.drumPads.map { p -> if (p.index == padIdx) transform(p) else p }
                    t.copy(drumPads = pads)
                } else t
            })
        }
        val pad = _currentSet.value.tracks.getOrNull(trackIdx)?.drumPads?.firstOrNull { it.index == padIdx } ?: return
        audioController.syncDrumPad(trackIdx, pad)
    }

    fun applyDrumKitPreset(trackIdx: Int, presetName: String, pads: List<DrumPadModel>) {
        mutateSet { set ->
            set.copy(tracks = set.tracks.mapIndexed { i, t ->
                if (i == trackIdx) t.copy(presetName = presetName, drumPads = pads) else t
            })
        }
        val tr = _currentSet.value.tracks.getOrNull(trackIdx) ?: return
        tr.drumPads.forEach { audioController.syncDrumPad(trackIdx, it) }
    }

    fun addDrumPadEffect(trackIdx: Int, padIdx: Int, type: EffectType) {
        if (type == EffectType.NONE) return
        val newFx = EffectModel(type = type, bypassed = false, params = type.defaultParams, presetName = "Default")
        updateDrumPadChain(trackIdx, padIdx, recordUndo = true) { chain ->
            EffectChainTransforms.addEffect(chain, newFx)
        }
    }

    fun updateDrumPadEffect(trackIdx: Int, padIdx: Int, slot: Int, effect: EffectModel) {
        updateDrumPadChain(trackIdx, padIdx, recordUndo = false) { chain ->
            EffectChainTransforms.updateEffectAt(chain, slot, effect)
        }
    }

    fun removeDrumPadEffect(trackIdx: Int, padIdx: Int, slot: Int) {
        updateDrumPadChain(trackIdx, padIdx, recordUndo = true) { chain ->
            EffectChainTransforms.removeEffectAt(chain, slot)
        }
    }

    fun moveDrumPadEffect(trackIdx: Int, padIdx: Int, from: Int, to: Int) {
        updateDrumPadChain(trackIdx, padIdx, recordUndo = true) { chain ->
            EffectChainTransforms.moveEffect(chain, from, to)
        }
    }

    private fun updateDrumPadChain(
        trackIdx: Int,
        padIdx: Int,
        recordUndo: Boolean,
        transformChain: (List<EffectModel>) -> List<EffectModel>
    ) {
        mutateSet(recordUndo = recordUndo) { set ->
            set.copy(tracks = set.tracks.mapIndexed { i, t ->
                if (i == trackIdx) {
                    val pads = t.drumPads.map { p ->
                        if (p.index == padIdx) {
                            p.copy(
                                padEffect = EffectModel(type = EffectType.NONE),
                                padEffects = transformChain(p.effectivePadEffects())
                            )
                        } else p
                    }
                    t.copy(drumPads = pads)
                } else t
            })
        }
        val pad = _currentSet.value.tracks.getOrNull(trackIdx)?.drumPads?.firstOrNull { it.index == padIdx } ?: return
        audioController.syncDrumPad(trackIdx, pad)
    }

    fun updateTrackEffect(trackIdx: Int, slot: Int, effect: EffectModel) {
        mutateSet { set ->
            set.copy(tracks = set.tracks.mapIndexed { i, t ->
                if (i == trackIdx) {
                    t.copy(effects = EffectChainTransforms.updateEffectAt(t.effects, slot, effect))
                } else t
            })
        }
        syncTrackEffectsToEngine(trackIdx)
        if (slot in 0 until EffectChainTransforms.MAX_EFFECT_SLOTS) {
            recordAutomationIfArmed(trackIdx, 4 + slot, "${effect.type.displayName} Mix", effect.wetDry)
        }
    }

    fun addTrackEffect(trackIdx: Int, type: EffectType) {
        if (type == EffectType.NONE) return
        val newFx = EffectModel(type = type, bypassed = false, params = type.defaultParams, presetName = "Default")
        mutateSet { set ->
            set.copy(tracks = set.tracks.mapIndexed { i, t ->
                if (i == trackIdx) t.copy(effects = EffectChainTransforms.addEffect(t.effects, newFx)) else t
            })
        }
        syncTrackEffectsToEngine(trackIdx)
    }

    fun removeTrackEffect(trackIdx: Int, slot: Int) {
        mutateSet { set ->
            set.copy(tracks = set.tracks.mapIndexed { i, t ->
                if (i == trackIdx) t.copy(effects = EffectChainTransforms.removeEffectAt(t.effects, slot)) else t
            })
        }
        syncTrackEffectsToEngine(trackIdx)
    }

    fun moveTrackEffect(trackIdx: Int, from: Int, to: Int) {
        mutateSet { set ->
            set.copy(tracks = set.tracks.mapIndexed { i, t ->
                if (i == trackIdx) t.copy(effects = EffectChainTransforms.moveEffect(t.effects, from, to)) else t
            })
        }
        syncTrackEffectsToEngine(trackIdx)
    }

    fun swapTrackEffects(trackIdx: Int, slotA: Int = 0, slotB: Int = 1) {
        mutateSet { set ->
            set.copy(tracks = set.tracks.mapIndexed { i, t ->
                if (i == trackIdx) t.copy(effects = EffectChainTransforms.swapEffects(t.effects, slotA, slotB)) else t
            })
        }
        syncTrackEffectsToEngine(trackIdx)
    }

    private fun syncTrackEffectsToEngine(trackIdx: Int) {
        val tr = _currentSet.value.tracks.getOrNull(trackIdx) ?: return
        for (slot in 0 until EffectChainTransforms.MAX_EFFECT_SLOTS) {
            audioController.sendEffect(
                scope = 0,
                trackOrPad = trackIdx,
                slot = slot,
                fx = tr.effects.getOrNull(slot) ?: EffectModel(type = EffectType.NONE)
            )
        }
    }

    fun setNoteRepeat(trackIdx: Int, rate: NoteRepeatRate) {
        _noteRepeatRate.value = rate
        audioController.setNoteRepeat(trackIdx, rate != NoteRepeatRate.OFF, rate.beats)
    }

    // ---------------- Clip MIDI & Automation Editing ----------------
    fun updateClip(trackIdx: Int, sceneIdx: Int, recordUndo: Boolean = true, transform: (ClipModel) -> ClipModel) {
        val track = _currentSet.value.tracks.getOrNull(trackIdx) ?: return
        val existing = track.clips[sceneIdx] ?: ClipModel(name = "${track.name} ${sceneIdx + 1}", colorIndex = track.colorIndex)
        val updated = transform(existing)
        mutateSet(recordUndo = recordUndo) { set ->
            set.copy(tracks = set.tracks.mapIndexed { i, t ->
                if (i == trackIdx) t.copy(clips = t.clips + (sceneIdx to updated)) else t
            })
        }
        audioController.syncClip(trackIdx, sceneIdx, updated, track.type == TrackType.DRUM_RACK)
    }

    fun toggleAutomationRecording() {
        _automationRecording.value = !_automationRecording.value
    }

    private fun recordAutomationIfArmed(trackIdx: Int, paramId: Int, label: String, normalizedValue: Float) {
        if (!_automationRecording.value || !telemetry.value.isPlaying) return
        val sceneIdx = telemetry.value.activeClips.getOrElse(trackIdx) { -1 }.let { if (it >= 0) it else _selectedSceneIndex.value }
        val clip = _currentSet.value.tracks.getOrNull(trackIdx)?.clips?.get(sceneIdx) ?: return
        val tsNum = _currentSet.value.timeSigNum.coerceIn(1, 12)
        val clipLenBeats = clip.totalBeats(tsNum).coerceAtLeast(1f)
        val beatPos = (telemetry.value.trackClipBeats.getOrElse(trackIdx) { telemetry.value.transportBeat } % clipLenBeats).toFloat()
        updateClip(trackIdx, sceneIdx, recordUndo = false) { c ->
            val lanes = c.automationLanes.toMutableList()
            val idx = lanes.indexOfFirst { it.targetParamId == paramId }
            val newPt = AutomationPointModel(beatPos, normalizedValue)
            if (idx >= 0) {
                val pts = (lanes[idx].points + newPt).sortedBy { it.beat }.takeLast(64)
                lanes[idx] = lanes[idx].copy(points = pts)
            } else {
                lanes.add(AutomationLaneModel(paramId, label, listOf(newPt)))
            }
            c.copy(automationLanes = lanes)
        }
    }

    fun clearCaptureBuffer() {
        audioController.clearCaptureBuffer()
        _statusMessage.value = "Capture buffer cleared"
    }

    fun capturePerformance(fixedBars: Int? = null, overdub: Boolean = false) {
        val trackIdx = _selectedTrackIndex.value
        val sceneIdx = _selectedSceneIndex.value
        val track = _currentSet.value.tracks.getOrNull(trackIdx) ?: return
        val isDrum = track.type == TrackType.DRUM_RACK
        val tsNum = _currentSet.value.timeSigNum.coerceIn(1, 12)
        val curSettings = settings.value
        val existing = track.clips[sceneIdx]

        val finalClip = if (overdub && existing != null) {
            audioController.overdubRecentPerformanceIntoClip(
                existing = existing,
                trackIndex = trackIdx,
                quantizeGridBeats = 0.0,
                quantizeStrength = 0f,
                timeSigNum = tsNum,
                isDrumTrack = isDrum
            )
        } else {
            val captured = audioController.captureRecentPerformanceToClip(
                trackIndex = trackIdx,
                quantizeGridBeats = 0.0,
                quantizeStrength = 0f,
                fixedBars = fixedBars ?: if (curSettings.captureMode == com.example.core.common.CaptureMode.FIXED_LENGTH) curSettings.fixedCaptureBars else null,
                captureMode = curSettings.captureMode,
                timeSigNum = tsNum,
                isDrumTrack = isDrum
            )
            if (captured != null) {
                if (existing != null) {
                    val targetBars = maxOf(existing.lengthBars, captured.lengthBars).coerceIn(1, 16)
                    val merged = (existing.notes + captured.notes)
                        .sortedWith(compareBy({ it.startBeat }, { it.note }))
                    existing.copy(
                        lengthBars = targetBars,
                        loopEndBeats = (targetBars * tsNum).toFloat(),
                        notes = merged
                    )
                } else {
                    captured.copy(name = "${track.name} Cap", colorIndex = track.colorIndex)
                }
            } else null
        }

        if (finalClip == null) {
            _statusMessage.value = "Play notes first to Capture"
            return
        }

        mutateSet { set ->
            set.copy(tracks = set.tracks.mapIndexed { i, t ->
                if (i == trackIdx) t.copy(clips = t.clips + (sceneIdx to finalClip)) else t
            })
        }
        audioController.syncClip(trackIdx, sceneIdx, finalClip, isDrum, tsNum)
        audioController.launchClip(trackIdx, sceneIdx, true)
        _statusMessage.value = if (overdub && existing != null) {
            "Overdubbed · ${finalClip.notes.size} notes"
        } else {
            "Captured ${finalClip.notes.size} notes · ${finalClip.lengthBars}B"
        }
    }

    // ---------------- Samples & Recording ----------------
    fun assignSampleToDrumPad(trackIdx: Int, padIdx: Int, sample: SampleEntity) {
        updateDrumPad(trackIdx, padIdx) { pad ->
            pad.copy(sampleId = sample.id, sampleSlot = sample.slotIndex, name = sample.name.take(12), startNorm = 0f, endNorm = 1f)
        }
        viewModelScope.launch { projectRepo.touchSample(sample.id) }
    }

    fun clearDrumPadSample(trackIdx: Int, padIdx: Int) {
        updateDrumPad(trackIdx, padIdx) { pad ->
            pad.copy(sampleId = "", sampleSlot = -1, startNorm = 0f, endNorm = 1f)
        }
        _statusMessage.value = "Cleared pad sample"
    }

    fun assignSampleToMelodicSampler(trackIdx: Int, sample: SampleEntity) {
        updateSamplerParams(trackIdx) { sp ->
            sp.copy(sampleId = sample.id, sampleName = sample.name, sampleSlot = sample.slotIndex, startNorm = 0f, endNorm = 1f)
        }
        viewModelScope.launch { projectRepo.touchSample(sample.id) }
    }

    fun clearSamplerSample(trackIdx: Int) {
        updateSamplerParams(trackIdx) { sp ->
            sp.copy(sampleId = "", sampleName = "Empty", sampleSlot = -1, startNorm = 0f, endNorm = 1f)
        }
        _statusMessage.value = "Cleared sampler slot"
    }

    fun importAudioSample(uri: Uri, onLoaded: (SampleEntity) -> Unit = {}) {
        viewModelScope.launch {
            val res = projectRepo.importAudioSample(uri)
            if (res != null) {
                audioController.loadSingleSample(res.first, res.second)
                _statusMessage.value = "Imported \"${res.first.name}\""
                onLoaded(res.first)
            } else {
                _statusMessage.value = "Could not decode audio file"
            }
        }
    }

    fun startMicRecording() {
        micRecorder.clearError()
        micRecorder.start(settings.value.sampleRate)
        val err = micRecorder.errorMessage.value
        if (!err.isNullOrBlank()) {
            _statusMessage.value = err
        }
    }

    fun stopMicRecordingAndSave(name: String, onSaved: (SampleEntity) -> Unit = {}) {
        val decoded = micRecorder.stopAndGetPcm(settings.value.sampleRate)
        if (decoded == null) {
            _statusMessage.value = micRecorder.errorMessage.value ?: "Recording too short"
            return
        }
        viewModelScope.launch {
            val (entity, pcm) = projectRepo.saveDecodedSample(name, decoded, "Recorded", "Mic Recordings")
            audioController.loadSingleSample(entity, pcm)
            micRecorder.markSaved()
            _statusMessage.value = "Recorded \"${entity.name}\""
            onSaved(entity)
        }
    }

    fun toggleInternalResampling() {
        if (isResampling.value) {
            val decoded = audioController.stopResampling() ?: return
            viewModelScope.launch {
                val (entity, pcm) = projectRepo.saveDecodedSample(
                    "Resample ${(System.currentTimeMillis() / 1000) % 1000}",
                    decoded,
                    "Resampled",
                    "Internal Resamples"
                )
                audioController.loadSingleSample(entity, pcm)
                _statusMessage.value = "Saved resample \"${entity.name}\""
            }
        } else {
            audioController.startResampling()
            _statusMessage.value = "Resampling master output..."
        }
    }

    fun toggleSampleFavorite(sample: SampleEntity) {
        viewModelScope.launch { projectRepo.toggleSampleFavorite(sample.id, !sample.isFavorite) }
    }

    // ---------------- Export & Settings ----------------
    fun clearLastExportResult() {
        _lastExportResult.value = null
    }

    fun exportWavToDownloads(
        bitDepth: Int,
        sampleRate: Int,
        scopeMode: Int,
        sceneIndex: Int,
        trackIndex: Int,
        bars: Int
    ) {
        viewModelScope.launch {
            val beats = (bars * _currentSet.value.timeSigNum).toDouble().coerceAtLeast(4.0)
            val res = audioController.exportWavToDownloads(
                projectName = _currentSet.value.name,
                bitDepth = bitDepth,
                sampleRate = sampleRate,
                scopeMode = scopeMode,
                sceneIndex = sceneIndex,
                trackIndex = trackIndex,
                totalBeats = beats
            )
            if (res != null) {
                _lastExportResult.value = res
                _statusMessage.value = "Saved ${res.displayName}"
            } else {
                _statusMessage.value = "WAV export cancelled or failed"
            }
        }
    }

    fun exportWavToFolderTree(
        treeUri: Uri,
        bitDepth: Int,
        sampleRate: Int,
        scopeMode: Int,
        sceneIndex: Int,
        trackIndex: Int,
        bars: Int
    ) {
        viewModelScope.launch {
            val beats = (bars * _currentSet.value.timeSigNum).toDouble().coerceAtLeast(4.0)
            val res = audioController.exportWavToFolderTree(
                treeUri = treeUri,
                projectName = _currentSet.value.name,
                bitDepth = bitDepth,
                sampleRate = sampleRate,
                scopeMode = scopeMode,
                sceneIndex = sceneIndex,
                trackIndex = trackIndex,
                totalBeats = beats
            )
            if (res != null) {
                _lastExportResult.value = res
                _statusMessage.value = "Saved ${res.displayName}"
            } else {
                _statusMessage.value = "WAV export cancelled or failed"
            }
        }
    }

    fun exportWav(
        uri: Uri,
        bitDepth: Int,
        sampleRate: Int,
        scopeMode: Int,
        sceneIndex: Int,
        trackIndex: Int,
        bars: Int
    ) {
        viewModelScope.launch {
            val beats = (bars * _currentSet.value.timeSigNum).toDouble().coerceAtLeast(4.0)
            val ok = audioController.exportWavToUri(uri, bitDepth, sampleRate, scopeMode, sceneIndex, trackIndex, beats)
            _statusMessage.value = if (ok) "WAV export complete" else "WAV export cancelled or failed"
        }
    }

    fun exportMidiToUri(uri: Uri, sceneIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = StudioProjectTransforms.encodeStandardMidiFile(_currentSet.value, sceneIndex)
            val ok = runCatching {
                app.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                true
            }.getOrDefault(false)
            _statusMessage.value = if (ok) "MIDI exported" else "MIDI export failed"
        }
    }

    fun updateSettings(transform: (SettingsState) -> SettingsState) {
        viewModelScope.launch {
            val prev = settings.value
            settingsRepo.update(transform)
            val next = transform(prev)
            audioController.applySettings(next)
            if (prev.sampleRate != next.sampleRate || prev.bufferFrames != next.bufferFrames) {
                audioController.startEngine(next)
                audioController.syncSet(_currentSet.value)
            }
            if (prev.midiFilterChannel != next.midiFilterChannel) {
                midiManager.setRouting(_selectedTrackIndex.value, next.midiFilterChannel)
            }
        }
    }

    fun connectMidiInput(id: Int) = midiManager.connectInputDevice(id)
    fun disconnectMidiInput() = midiManager.disconnectInput()
    fun connectMidiOutput(id: Int) = midiManager.connectOutputDevice(id)
    fun disconnectMidiOutput() = midiManager.disconnectOutput()

    override fun onCleared() {
        super.onCleared()
        midiManager.stop()
        audioController.stopEngine()
    }
}
