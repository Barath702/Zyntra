package com.example

import android.app.Application
import com.example.core.audio.AudioEngineController
import com.example.core.common.SettingsRepository
import com.example.core.database.NoteCraftDatabase
import com.example.core.midi.MidiHardwareManager
import com.example.core.project.ProjectRepository

class NoteCraftApplication : Application() {
    val database by lazy { NoteCraftDatabase.getInstance(this) }
    val projectRepository by lazy { ProjectRepository(this, database) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val audioController by lazy { AudioEngineController(this) }
    val midiManager by lazy { MidiHardwareManager(this) }
}
