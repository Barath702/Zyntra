package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.core.project.StudioViewModel
import com.example.core.project.TrackType
import com.example.feature.browser.SampleBrowserScreen
import com.example.feature.drums.DrumRackView
import com.example.feature.instrument.MelodicInstrumentView
import com.example.feature.library.SetLibraryScreen
import com.example.feature.pianoroll.PianoRollScreen
import com.example.feature.session.SessionViewScreen
import com.example.feature.settings.ExportAudioDialog
import com.example.feature.settings.MasterEffectsDialog
import com.example.feature.settings.SettingsScreen
import com.example.ui.theme.LocalStudioTokens
import com.example.ui.theme.NoteCraftTheme
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

sealed interface NoteCraftRoute {
    @Serializable data object Library : NoteCraftRoute
    @Serializable data object Session : NoteCraftRoute
    @Serializable data object Instrument : NoteCraftRoute
    @Serializable data object PianoRoll : NoteCraftRoute
    @Serializable data class SampleBrowser(val drumPadIndex: Int = -1) : NoteCraftRoute
    @Serializable data object Settings : NoteCraftRoute
}

class MainActivity : ComponentActivity() {
    private var incomingAudioUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingAudioUri = extractSharedAudioUri(intent)

        setContent {
            val vm: StudioViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()

            LaunchedEffect(incomingAudioUri) {
                incomingAudioUri?.let { uri ->
                    vm.importAudioSample(uri)
                    incomingAudioUri = null
                }
            }

            NoteCraftTheme(highContrast = settings.highContrastTheme) {
                NoteCraftApp(vm = vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractSharedAudioUri(intent)?.let { uri ->
            incomingAudioUri = uri
        }
    }

    @Suppress("DEPRECATION")
    private fun extractSharedAudioUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
    }
}

@Composable
fun NoteCraftApp(vm: StudioViewModel) {
    val tokens = LocalStudioTokens.current
    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentSet by vm.currentSet.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val samples by vm.samples.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val telemetry by vm.telemetry.collectAsStateWithLifecycle()
    val selectedTrackIdx by vm.selectedTrackIndex.collectAsStateWithLifecycle()
    val selectedSceneIdx by vm.selectedSceneIndex.collectAsStateWithLifecycle()
    val selectedPadIdx by vm.selectedPadIndex.collectAsStateWithLifecycle()
    val autosaveRecovery by vm.autosaveRecovery.collectAsStateWithLifecycle()
    val missingSamples by vm.missingSamples.collectAsStateWithLifecycle()
    val previewingSetId by vm.previewingSetId.collectAsStateWithLifecycle()
    val isResampling by vm.isResampling.collectAsStateWithLifecycle()
    val exportProgress by vm.exportProgress.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val canRedo by vm.canRedo.collectAsStateWithLifecycle()
    val noteRepeatRate by vm.noteRepeatRate.collectAsStateWithLifecycle()
    val automationRecording by vm.automationRecording.collectAsStateWithLifecycle()
    val statusMsg by vm.statusMessage.collectAsStateWithLifecycle()
    val capturedNoteCount by vm.capturedNoteCount.collectAsStateWithLifecycle()
    val lastExportResult by vm.lastExportResult.collectAsStateWithLifecycle()

    val isMicRecording by vm.micRecorder.isRecording.collectAsStateWithLifecycle()
    val micState by vm.micRecorder.state.collectAsStateWithLifecycle()
    val micError by vm.micRecorder.errorMessage.collectAsStateWithLifecycle()
    val micDurationMs by vm.micRecorder.durationMs.collectAsStateWithLifecycle()
    val micPeaks by vm.micRecorder.livePeaks.collectAsStateWithLifecycle()

    val midiDevices by vm.midiDevices.collectAsStateWithLifecycle()
    val connectedMidiIn by vm.connectedMidiInput.collectAsStateWithLifecycle()
    val connectedMidiOut by vm.connectedMidiOutput.collectAsStateWithLifecycle()
    val midiSupported by vm.midiSupported.collectAsStateWithLifecycle()

    var showExportDialog by remember { mutableStateOf(false) }
    var showMasterFxDialog by remember { mutableStateOf(false) }

    // Stop playback cleanly when backgrounding the app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                vm.audioController.stopPlayback()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(statusMsg) {
        if (statusMsg != null) {
            delay(2200)
            vm.clearStatusMessage()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bg),
        containerColor = tokens.bg,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = NoteCraftRoute.Library
            ) {
                composable<NoteCraftRoute.Library> {
                    LaunchedEffect(Unit) {
                        vm.setCaptureArmed(false)
                        vm.releaseAllPreviewNotes()
                    }
                    SetLibraryScreen(
                        projects = projects,
                        autosaveRecovery = autosaveRecovery,
                        previewingSetId = previewingSetId,
                        isPlaying = telemetry.isPlaying,
                        showOnboarding = !settings.onboardingCompleted,
                        onOpenSet = { id ->
                            vm.openSet(id) { navController.navigate(NoteCraftRoute.Session) { launchSingleTop = true } }
                        },
                        onCreateSet = { name, bpm ->
                            vm.createNewSet(name, bpm) { navController.navigate(NoteCraftRoute.Session) { launchSingleTop = true } }
                        },
                        onRenameSet = vm::renameSet,
                        onDuplicateSet = vm::duplicateSet,
                        onDeleteSet = vm::deleteSet,
                        onPreviewSet = vm::previewSet,
                        onImportSet = vm::importProjectFromUri,
                        onExportSet = vm::exportProjectToUri,
                        onRecoverAutosave = {
                            vm.recoverAutosave { navController.navigate(NoteCraftRoute.Session) { launchSingleTop = true } }
                        },
                        onDismissAutosave = vm::dismissAutosave,
                        onOpenSettings = { navController.navigate(NoteCraftRoute.Settings) { launchSingleTop = true } },
                        onDismissOnboarding = {
                            vm.updateSettings { it.copy(onboardingCompleted = true) }
                        }
                    )
                }

                composable<NoteCraftRoute.Session> {
                    LaunchedEffect(Unit) {
                        vm.setCaptureArmed(true)
                    }
                    DisposableEffect(Unit) {
                        onDispose { vm.releaseAllPreviewNotes() }
                    }
                    BackHandler {
                        vm.onExitProject()
                        navController.popBackStack()
                    }
                    SessionViewScreen(
                        set = currentSet,
                        telemetry = telemetry,
                        metronomeEnabled = settings.metronomeEnabled,
                        isResampling = isResampling,
                        canUndo = canUndo,
                        canRedo = canRedo,
                        missingSamples = missingSamples,
                        onBackToLibrary = {
                            vm.onExitProject()
                            navController.popBackStack()
                        },
                        onOpenTrackInstrument = { ti ->
                            vm.selectTrack(ti)
                            navController.navigate(NoteCraftRoute.Instrument) { launchSingleTop = true }
                        },
                        onOpenClipPianoRoll = { ti, si ->
                            vm.selectTrack(ti)
                            vm.selectScene(si)
                            navController.navigate(NoteCraftRoute.PianoRoll) { launchSingleTop = true }
                        },
                        onAddTrack = vm::addTrack,
                        onRenameTrack = vm::renameTrack,
                        onDuplicateTrack = vm::duplicateTrack,
                        onDeleteTrack = vm::removeTrack,
                        onChangeTrackColor = vm::changeTrackColor,
                        onMoveTrack = vm::moveTrack,
                        onCreateOrOpenClip = { ti, si ->
                            vm.createOrOpenClip(ti, si)
                            navController.navigate(NoteCraftRoute.Instrument) { launchSingleTop = true }
                        },
                        onLaunchClip = vm::launchClip,
                        onStopTrackClip = vm::stopTrackClip,
                        onDuplicateClip = vm::duplicateClip,
                        onRenameClip = vm::renameClip,
                        onChangeClipColor = vm::changeClipColor,
                        onDeleteClip = vm::deleteClip,
                        onLaunchScene = vm::launchScene,
                        onRenameScene = vm::renameScene,
                        onDuplicateScene = vm::duplicateScene,
                        onDeleteScene = vm::deleteScene,
                        onSwapScenes = vm::swapScenes,
                        onTogglePlay = vm::togglePlay,
                        onRestart = vm::restartTransport,
                        onBpmChange = vm::setBpm,
                        onSwingChange = vm::setSwing,
                        onToggleMetronome = vm::toggleMetronome,
                        onQuantizationChange = vm::setGlobalQuantization,
                        onKeyScaleChange = vm::setKeyAndScale,
                        onUndo = vm::undo,
                        onRedo = vm::redo,
                        onCapture = { vm.capturePerformance() },
                        onToggleResample = vm::toggleInternalResampling,
                        onSaveSet = vm::saveCurrentSet,
                        onOpenExport = { showExportDialog = true },
                        onOpenSampleBrowser = { navController.navigate(NoteCraftRoute.SampleBrowser(-1)) },
                        onOpenSettings = { navController.navigate(NoteCraftRoute.Settings) },
                        onOpenMasterFx = { showMasterFxDialog = true },
                        onTrackVolume = vm::setTrackVolume,
                        onTrackPan = vm::setTrackPan,
                        onToggleMute = vm::toggleTrackMute,
                        onToggleSolo = vm::toggleTrackSolo,
                        onMasterVolume = vm::setMasterVolume,
                        onDismissMissingSamples = vm::clearMissingSamples,
                        capturedNoteCount = capturedNoteCount
                    )
                }

                composable<NoteCraftRoute.Instrument> {
                    LaunchedEffect(Unit) {
                        vm.setCaptureArmed(true)
                    }
                    DisposableEffect(Unit) {
                        onDispose { vm.releaseAllPreviewNotes() }
                    }
                    val safeTrackIdx = if (selectedTrackIdx in currentSet.tracks.indices) selectedTrackIdx else 0
                    val track = currentSet.tracks.getOrNull(safeTrackIdx)
                    if (track == null) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    } else if (track.type == TrackType.DRUM_RACK) {
                        DrumRackView(
                            track = track,
                            trackIndex = safeTrackIdx,
                            sceneIndex = selectedSceneIdx,
                            selectedPadIndex = selectedPadIdx,
                            samples = samples,
                            telemetry = telemetry,
                            noteRepeatRate = noteRepeatRate,
                            isMicRecording = isMicRecording,
                            micPeaks = micPeaks,
                            onBack = { navController.popBackStack() },
                            onSelectPad = vm::selectPad,
                            onTriggerPad = { pad, vel, pitchOff ->
                                vm.audioController.triggerDrumPad(safeTrackIdx, pad, vel, pitchOff)
                            },
                            onReleasePad = { pad ->
                                vm.audioController.releaseDrumPad(safeTrackIdx, pad)
                            },
                            onPreviewPad = { pad, vel ->
                                vm.audioController.auditionDrumPad(safeTrackIdx, pad, vel)
                            },
                            onUpdatePad = { padIdx, fn -> vm.updateDrumPad(safeTrackIdx, padIdx, fn) },
                            onApplyKitPreset = { name, pads -> vm.applyDrumKitPreset(safeTrackIdx, name, pads) },
                            onAddKitEffect = { fxType -> vm.addTrackEffect(safeTrackIdx, fxType) },
                            onUpdateKitEffect = { slot, fx -> vm.updateTrackEffect(safeTrackIdx, slot, fx) },
                            onRemoveKitEffect = { slot -> vm.removeTrackEffect(safeTrackIdx, slot) },
                            onMoveKitEffect = { from, to -> vm.moveTrackEffect(safeTrackIdx, from, to) },
                            onNoteRepeatChange = { rate -> vm.setNoteRepeat(safeTrackIdx, rate) },
                            onOpenSampleBrowserForPad = { padIdx ->
                                navController.navigate(NoteCraftRoute.SampleBrowser(padIdx)) { launchSingleTop = true }
                            },
                            onStartMicRecord = vm::startMicRecording,
                            onStopMicRecord = { name, padIdx ->
                                vm.stopMicRecordingAndSave(name) { saved ->
                                    vm.assignSampleToDrumPad(safeTrackIdx, padIdx, saved)
                                }
                            },
                            onUpdateClip = { fn -> vm.updateClip(safeTrackIdx, selectedSceneIdx, true, fn) },
                            onOpenPianoRoll = { navController.navigate(NoteCraftRoute.PianoRoll) { launchSingleTop = true } },
                            onCapture = { vm.capturePerformance() },
                            onTogglePlay = vm::togglePlay,
                            onUndo = vm::undo,
                            timeSigNum = currentSet.timeSigNum,
                            micState = micState,
                            micDurationMs = micDurationMs,
                            micError = micError,
                            capturedNoteCount = capturedNoteCount,
                            captureMode = settings.captureMode,
                            onClearPadSample = { padIdx -> vm.clearDrumPadSample(safeTrackIdx, padIdx) },
                            onClearCapture = vm::clearCaptureBuffer,
                            onCaptureOverdub = { vm.capturePerformance(overdub = true) },
                            onAddPadEffect = { padIdx, fxType -> vm.addDrumPadEffect(safeTrackIdx, padIdx, fxType) },
                            onUpdatePadEffect = { padIdx, slot, fx -> vm.updateDrumPadEffect(safeTrackIdx, padIdx, slot, fx) },
                            onRemovePadEffect = { padIdx, slot -> vm.removeDrumPadEffect(safeTrackIdx, padIdx, slot) },
                            onMovePadEffect = { padIdx, from, to -> vm.moveDrumPadEffect(safeTrackIdx, padIdx, from, to) }
                        )
                    } else {
                        MelodicInstrumentView(
                            track = track,
                            trackIndex = safeTrackIdx,
                            sceneIndex = selectedSceneIdx,
                            rootNote = currentSet.rootNote,
                            scaleType = currentSet.scaleType,
                            padLayout = settings.melodicLayout,
                            showNoteLabels = settings.showPadNoteLabels,
                            samples = samples,
                            telemetry = telemetry,
                            automationRecording = automationRecording,
                            isMicRecording = isMicRecording,
                            micPeaks = micPeaks,
                            onBack = { navController.popBackStack() },
                            onNoteOn = { note, vel -> vm.audioController.noteOn(safeTrackIdx, note, vel) },
                            onNoteOff = { note -> vm.audioController.noteOff(safeTrackIdx, note) },
                            onUpdateSynth = { fn -> vm.updateSynthParams(safeTrackIdx, fn) },
                            onApplySynthPreset = { name, params -> vm.applySynthPreset(safeTrackIdx, name, params) },
                            onUpdateSampler = { fn -> vm.updateSamplerParams(safeTrackIdx, fn) },
                            onAddEffect = { fxType -> vm.addTrackEffect(safeTrackIdx, fxType) },
                            onUpdateEffect = { slot, fx -> vm.updateTrackEffect(safeTrackIdx, slot, fx) },
                            onRemoveEffect = { slot -> vm.removeTrackEffect(safeTrackIdx, slot) },
                            onMoveEffect = { from, to -> vm.moveTrackEffect(safeTrackIdx, from, to) },
                            onToggleAutomationRecord = vm::toggleAutomationRecording,
                            onChangeLayout = { layout -> vm.updateSettings { it.copy(melodicLayout = layout) } },
                            onOpenSampleBrowser = { navController.navigate(NoteCraftRoute.SampleBrowser(-1)) { launchSingleTop = true } },
                            onStartMicRecord = vm::startMicRecording,
                            onStopMicRecord = { name ->
                                vm.stopMicRecordingAndSave(name) { saved ->
                                    vm.assignSampleToMelodicSampler(safeTrackIdx, saved)
                                }
                            },
                            onOpenPianoRoll = { navController.navigate(NoteCraftRoute.PianoRoll) { launchSingleTop = true } },
                            onCapture = { vm.capturePerformance() },
                            onTogglePlay = vm::togglePlay,
                            onUndo = vm::undo,
                            timeSigNum = currentSet.timeSigNum,
                            noteRepeatRate = noteRepeatRate,
                            onNoteRepeatChange = { rate -> vm.setNoteRepeat(safeTrackIdx, rate) },
                            micState = micState,
                            micDurationMs = micDurationMs,
                            micError = micError,
                            capturedNoteCount = capturedNoteCount,
                            onClearSamplerSample = { vm.clearSamplerSample(safeTrackIdx) },
                            onAuditionSampler = {
                                vm.audioController.auditionNote(safeTrackIdx, track.samplerParams.rootNote, 0.9f)
                            },
                            onClearCapture = vm::clearCaptureBuffer,
                            onCaptureOverdub = { vm.capturePerformance(overdub = true) }
                        )
                    }
                }

                composable<NoteCraftRoute.PianoRoll> {
                    LaunchedEffect(Unit) {
                        vm.setCaptureArmed(false)
                        vm.releaseAllPreviewNotes()
                    }
                    DisposableEffect(Unit) {
                        onDispose { vm.releaseAllPreviewNotes() }
                    }
                    val safeTrackIdx = if (selectedTrackIdx in currentSet.tracks.indices) selectedTrackIdx else 0
                    val track = currentSet.tracks.getOrNull(safeTrackIdx)
                    if (track == null) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    } else {
                        PianoRollScreen(
                            track = track,
                            trackIndex = safeTrackIdx,
                            sceneIndex = selectedSceneIdx,
                            telemetry = telemetry,
                            canUndo = canUndo,
                            canRedo = canRedo,
                            onBack = { navController.popBackStack() },
                            onUpdateClip = { recordUndo, fn -> vm.updateClip(safeTrackIdx, selectedSceneIdx, recordUndo, fn) },
                            onAuditionNote = { note, vel ->
                                vm.audioController.auditionNote(safeTrackIdx, note, vel)
                            },
                            onAuditionNoteOff = { note ->
                                vm.audioController.auditionNoteOff(safeTrackIdx, note)
                            },
                            onAuditionDrumPad = { pad, vel ->
                                vm.audioController.auditionDrumPad(safeTrackIdx, pad, vel)
                            },
                            onReleaseAllAuditions = vm::releaseAllPreviewNotes,
                            onUndo = vm::undo,
                            onRedo = vm::redo,
                            onTogglePlay = { vm.togglePianoRollPlay(safeTrackIdx, selectedSceneIdx) },
                            timeSigNum = currentSet.timeSigNum,
                            rootNote = currentSet.rootNote,
                            scaleType = currentSet.scaleType,
                            onCheckpointUndo = vm::checkpointUndo
                        )
                    }
                }

                composable<NoteCraftRoute.SampleBrowser> { entry ->
                    val args = entry.toRoute<NoteCraftRoute.SampleBrowser>()
                    LaunchedEffect(Unit) {
                        vm.setCaptureArmed(false)
                        vm.releaseAllPreviewNotes()
                    }
                    DisposableEffect(Unit) {
                        onDispose { vm.releaseAllPreviewNotes() }
                    }
                    val safeTrackIdx = if (selectedTrackIdx in currentSet.tracks.indices) selectedTrackIdx else 0
                    SampleBrowserScreen(
                        samples = samples,
                        isMicRecording = isMicRecording,
                        micDurationMs = micDurationMs,
                        micPeaks = micPeaks,
                        onBack = { navController.popBackStack() },
                        onPreviewSample = vm::previewSample,
                        onAssignSample = { s ->
                            val track = currentSet.tracks.getOrNull(safeTrackIdx)
                            if (track?.type == TrackType.DRUM_RACK) {
                                val pad = args.drumPadIndex.takeIf { p -> p >= 0 } ?: selectedPadIdx
                                vm.assignSampleToDrumPad(safeTrackIdx, pad, s)
                            } else {
                                vm.assignSampleToMelodicSampler(safeTrackIdx, s)
                            }
                            navController.popBackStack()
                        },
                        onToggleFavorite = vm::toggleSampleFavorite,
                        onImportAudioUri = { uri -> vm.importAudioSample(uri) },
                        onStartMicRecord = vm::startMicRecording,
                        onStopMicRecord = { name -> vm.stopMicRecordingAndSave(name) },
                        micState = micState,
                        micError = micError
                    )
                }

                composable<NoteCraftRoute.Settings> {
                    LaunchedEffect(Unit) {
                        vm.setCaptureArmed(false)
                        vm.releaseAllPreviewNotes()
                    }
                    SettingsScreen(
                        settings = settings,
                        telemetry = telemetry,
                        midiDevices = midiDevices,
                        connectedMidiInput = connectedMidiIn,
                        connectedMidiOutput = connectedMidiOut,
                        midiSupported = midiSupported,
                        onBack = { navController.popBackStack() },
                        onUpdateSettings = vm::updateSettings,
                        onConnectMidiInput = vm::connectMidiInput,
                        onDisconnectMidiInput = vm::disconnectMidiInput,
                        onConnectMidiOutput = vm::connectMidiOutput,
                        onDisconnectMidiOutput = vm::disconnectMidiOutput
                    )
                }
            }

            // Sleek, non-blocking toast pill positioned cleanly above the 52dp bottom transport bar
            AnimatedVisibility(
                visible = statusMsg != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 62.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(tokens.surfaceElevated.copy(alpha = 0.94f))
                        .border(0.75.dp, tokens.border, RoundedCornerShape(16.dp))
                        .clickable { vm.clearStatusMessage() }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusMsg.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss notification",
                        tint = tokens.textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }

    if (showExportDialog) {
        val safeTrackIdx = if (selectedTrackIdx in currentSet.tracks.indices) selectedTrackIdx else 0
        ExportAudioDialog(
            set = currentSet,
            selectedSceneIndex = selectedSceneIdx,
            selectedTrackIndex = safeTrackIdx,
            exportProgress = exportProgress,
            lastExportResult = lastExportResult,
            onExportWavDownloads = vm::exportWavToDownloads,
            onExportWavFolder = vm::exportWavToFolderTree,
            onExportMidi = vm::exportMidiToUri,
            onCancelExport = { vm.audioController.cancelExport() },
            onDismiss = {
                vm.clearLastExportResult()
                showExportDialog = false
            }
        )
    }

    if (showMasterFxDialog) {
        MasterEffectsDialog(
            masterEffects = currentSet.masterEffects,
            onAddMasterEffect = vm::addMasterEffect,
            onUpdateMasterEffect = vm::updateMasterEffect,
            onRemoveMasterEffect = vm::removeMasterEffect,
            onMoveMasterEffect = vm::moveMasterEffect,
            onDismiss = { showMasterFxDialog = false }
        )
    }
}

