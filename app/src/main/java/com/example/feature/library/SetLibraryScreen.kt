package com.example.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.database.ProjectEntity
import com.example.core.design.GenerativeSetArtwork
import com.example.core.project.NoteSet
import com.example.ui.theme.LocalStudioTokens
import com.example.ui.theme.TrackPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SetLibraryScreen(
    projects: List<ProjectEntity>,
    autosaveRecovery: NoteSet?,
    previewingSetId: String?,
    isPlaying: Boolean,
    showOnboarding: Boolean,
    onOpenSet: (String) -> Unit,
    onCreateSet: (String, Float) -> Unit,
    onRenameSet: (String, String) -> Unit,
    onDuplicateSet: (String) -> Unit,
    onDeleteSet: (String) -> Unit,
    onPreviewSet: (String) -> Unit,
    onImportSet: (android.net.Uri) -> Unit,
    onExportSet: (String, android.net.Uri) -> Unit,
    onRecoverAutosave: () -> Unit,
    onDismissAutosave: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalStudioTokens.current
    var showNewDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ProjectEntity?>(null) }
    var exportTargetId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val filteredProjects = remember(projects, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) projects
        else projects.filter {
            it.name.contains(q, ignoreCase = true) || it.keyLabel.contains(q, ignoreCase = true)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImportSet)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val id = exportTargetId
        if (uri != null && id != null) onExportSet(id, uri)
        exportTargetId = null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Minimal Studio Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(tokens.surface)
                    .border(0.5.dp, tokens.border)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ZYNTRA",
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.textPrimary
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.size(48.dp).testTag("import_set_button")
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = "Import Set", tint = tokens.textSecondary)
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(48.dp).testTag("library_settings_button")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = tokens.textSecondary)
                }
                IconButton(
                    onClick = { showNewDialog = true },
                    modifier = Modifier.size(48.dp).testTag("new_set_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Set", tint = TrackPalette[1])
                }
            }

            if (autosaveRecovery != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TrackPalette[3].copy(alpha = 0.14f))
                        .border(0.5.dp, TrackPalette[3])
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Autosaved session available",
                            style = MaterialTheme.typography.titleMedium,
                            color = TrackPalette[3]
                        )
                        Text(
                            text = autosaveRecovery.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textSecondary
                        )
                    }
                    TextButton(onClick = onDismissAutosave) { Text("Discard", color = tokens.textSecondary) }
                    Button(
                        onClick = onRecoverAutosave,
                        colors = ButtonDefaults.buttonColors(containerColor = TrackPalette[3], contentColor = Color.Black)
                    ) { Text("Recover") }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                if (projects.size > 2) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search sets or keys...", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = tokens.textMuted) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search", tint = tokens.textMuted)
                                }
                            }
                        } else null,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("library_search_input")
                    )
                }

                if (filteredProjects.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (projects.isEmpty()) "No sets yet" else "No sets match \"$searchQuery\"",
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.textSecondary
                            )
                            if (projects.isEmpty()) {
                                Button(onClick = { showNewDialog = true }) { Text("Create First Set") }
                            } else {
                                OutlinedButton(onClick = { searchQuery = "" }) { Text("Clear Filter") }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredProjects, key = { it.id }) { proj ->
                            SetProjectRow(
                                project = proj,
                                isPreviewing = (previewingSetId == proj.id && isPlaying),
                                onOpen = { onOpenSet(proj.id) },
                                onPreview = { onPreviewSet(proj.id) },
                                onRename = { renameTarget = proj },
                                onDuplicate = { onDuplicateSet(proj.id) },
                                onExport = {
                                    exportTargetId = proj.id
                                    exportLauncher.launch("${proj.name.replace(" ", "_")}.notecraft.json")
                                },
                                onDelete = { onDeleteSet(proj.id) }
                            )
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }

        if (showOnboarding) {
            OnboardingOverlay(onDismiss = onDismissOnboarding)
        }
    }

    if (showNewDialog) {
        NewSetDialog(
            onDismiss = { showNewDialog = false },
            onCreate = { name, bpm ->
                showNewDialog = false
                onCreateSet(name, bpm)
            }
        )
    }

    renameTarget?.let { target ->
        RenameDialog(
            title = "Rename Set",
            initialName = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                renameTarget = null
                onRenameSet(target.id, newName)
            }
        )
    }
}

@Composable
private fun SetProjectRow(
    project: ProjectEntity,
    isPreviewing: Boolean,
    onOpen: () -> Unit,
    onPreview: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val tokens = LocalStudioTokens.current
    var menuExpanded by remember { mutableStateOf(false) }
    val dateStr = remember(project.modifiedAt) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(project.modifiedAt))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(tokens.surface)
            .border(0.5.dp, if (isPreviewing) TrackPalette[1] else tokens.border, RoundedCornerShape(4.dp))
            .clickable(onClick = onOpen)
            .padding(8.dp)
            .testTag("set_item_${project.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GenerativeSetArtwork(
            seed = project.artworkSeed,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (project.isDemo) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "DEMO",
                        style = MaterialTheme.typography.labelSmall,
                        color = TrackPalette[1],
                        modifier = Modifier
                            .background(TrackPalette[1].copy(alpha = 0.14f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${project.bpm.roundToInt()} BPM · ${project.keyLabel} · ${project.trackCount} tracks · $dateStr",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onPreview,
            modifier = Modifier.size(48.dp).testTag("preview_set_${project.id}")
        ) {
            Icon(
                imageVector = if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isPreviewing) "Stop preview" else "Play preview",
                tint = if (isPreviewing) TrackPalette[1] else tokens.textSecondary
            )
        }
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "Set options", tint = tokens.textSecondary)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(text = { Text("Open") }, onClick = { menuExpanded = false; onOpen() })
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menuExpanded = false; onRename() })
                DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menuExpanded = false; onDuplicate() })
                DropdownMenuItem(text = { Text("Export Project") }, onClick = { menuExpanded = false; onExport() })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menuExpanded = false; onDelete() })
            }
        }
    }
}

@Composable
private fun NewSetDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Float) -> Unit
) {
    var name by remember { mutableStateOf("New Studio Set") }
    var bpm by remember { mutableFloatStateOf(120f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Set") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Set Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_set_name_input")
                )
                Spacer(Modifier.height(12.dp))
                Text("Tempo: ${bpm.roundToInt()} BPM", style = MaterialTheme.typography.labelMedium)
                Slider(value = bpm, onValueChange = { bpm = it }, valueRange = 60f..180f)
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, bpm.roundToInt().toFloat()) },
                modifier = Modifier.testTag("confirm_new_set_button")
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun RenameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { Button(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun OnboardingOverlay(onDismiss: () -> Unit) {
    val tokens = LocalStudioTokens.current
    var step by remember { mutableStateOf(0) }
    val slides = listOf(
        "Begin with a Beat" to "Tap into the 16-pad Drum Rack, record with Note Repeat, or sequence grooves across 8 scenes.",
        "Play & Capture Instantly" to "Perform on velocity-sensitive melodic pads in any scale. Hit Capture to turn free playing into a synced loop.",
        "Mix, Shape & Export" to "Sculpt sound with the native C++ synth, sampler, 12 real-time DSP effects, and offline 24-bit WAV export."
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.86f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.surface, RoundedCornerShape(6.dp))
                .border(1.dp, tokens.border, RoundedCornerShape(6.dp))
                .padding(20.dp)
        ) {
            Text(
                text = "ZYNTRA STUDIO · ${step + 1}/3",
                style = MaterialTheme.typography.labelMedium,
                color = TrackPalette[1]
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = slides[step].first,
                style = MaterialTheme.typography.displaySmall,
                color = tokens.textPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = slides[step].second,
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.textSecondary
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("skip_onboarding_button")
                ) { Text("Skip") }
                Button(
                    onClick = {
                        if (step < slides.lastIndex) step++ else onDismiss()
                    },
                    modifier = Modifier.testTag("next_onboarding_button")
                ) {
                    Text(if (step < slides.lastIndex) "Next" else "Open Studio")
                }
            }
        }
    }
}
