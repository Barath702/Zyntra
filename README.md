# ZYNTRA

**ZYNTRA** is an offline-first Android digital audio workstation (DAW) powered by a native C++17 / Oboe low-latency audio engine, Jetpack Compose studio UI, and hardware USB/BLE MIDI integration.

## Highlights

- **Native C++17 Oboe Audio Engine:** Lock-free real-time audio graph with AAudio/OpenSL ES support, multi-track subtractive synthesis, polyphonic melodic sampling, and 16-pad drum rack playback.
- **Session View & Capture:** Multi-scene clip matrix, quantized clip launching, transport clock sync, and retrospective/live MIDI Capture with overdub support.
- **Piano Roll & Drum Sequencer:** 1–16 bar piano roll with Draw, Select (marquee multi-select), and Erase tools, quantized note editing, pitch-locked resizing, velocity/automation lanes, and isolated note auditioning.
- **DSP Effects & Mastering:** Up to 8 insert DSP effects per track and on the master bus (12 effect types including Parametric EQ, Compressor, Reverb, Delay, Chorus, Phaser, Flanger, Saturation, Distortion, Bitcrusher, Auto Filter, and Stereo Widener).
- **Audio & MIDI Export:** Offline 16/24/32-bit WAV stem/mix rendering to `Downloads/ZYNTRA` or user-selected folders, plus Standard MIDI File (`.mid`) and `.notecraft.json` project export.

## Release Summary (v1.0.0)

- Offline studio workflow with 64 built-in WAV samples, 12 drum kits, 12 synth presets, and 8 starter demo sets.
- Preserved project compatibility with existing Room local storage and `.notecraft.json` project files.
- Built APK output filename: `Zyntra.apk`.

## Building & Testing

### Prerequisites
- **Android SDK:** Compile/Target SDK 36, Min SDK 29
- **Android NDK:** `28.2.13676358`
- **CMake:** `4.1.2`
- **JDK:** 17+

### Gradle Commands
```bash
# Build debug APK (app/build/outputs/apk/debug/app-debug.apk)
gradle :app:assembleDebug

# Run local unit and Robolectric Compose UI tests
gradle :app:testDebugUnitTest

# Build release APK (requires release keystore env vars below)
KEYSTORE_PATH=/path/to/upload-key.jks STORE_PASSWORD=... KEY_PASSWORD=... gradle :app:assembleRelease
```

### Release Signing
Release builds read signing credentials from environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) with key alias `upload`. Private keystores and credentials are intentionally excluded from version control.

## License
No open-source license file has been added to this repository yet. Add a `LICENSE` file (for example MIT or Apache-2.0) before publishing publicly on GitHub.

