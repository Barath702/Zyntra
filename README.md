# ZYNTRA

**ZYNTRA** is a mobile-first, offline-first digital audio workstation (DAW) for Android. It combines a Jetpack Compose studio interface with a native C++17 audio engine built on Oboe, with support for MIDI hardware and hands-on music creation.

> **Project status:** Active development. Features are documented from the current source tree; a stable release and device-wide compatibility have not yet been independently verified.

## Features

- **Native audio engine:** C++17 audio processing with Oboe, synthesis, sampling, and a drum rack.
- **Session workflow:** Scene and clip-based arrangement, clip launching, transport synchronization, and MIDI capture/overdub.
- **Piano roll:** Note drawing, selection and erasing, quantized editing, resizing, velocity, and automation workflows.
- **Drum sequencing:** Pad-based drum programming and sample playback.
- **Instruments and effects:** Synth and sampler instruments with insert/master effects, including EQ, dynamics, time-based effects, modulation, saturation, distortion, bitcrushing, filtering, and stereo processing.
- **MIDI hardware:** USB/BLE MIDI integration where supported by the Android device and connected hardware.
- **Export:** WAV audio rendering and Standard MIDI File export, plus the project's JSON interchange format.
- **Offline-first workflow:** Core music-making features are designed to work locally without requiring an account or cloud service.

## Technology

| Area | Technology |
|---|---|
| Platform | Android |
| UI | Kotlin, Jetpack Compose |
| Audio engine | C++17, Oboe, Android NDK |
| Native build | CMake |
| Build system | Gradle |
| Minimum Android SDK | 29 |
| Compile / target SDK | 36 |

## Getting started

### Requirements

- Android Studio with a compatible Android SDK
- JDK 17 or newer
- Gradle compatible with the project's Android Gradle Plugin
- Android NDK `28.2.13676358`
- CMake `4.1.2`
- Android SDK Platform 36

Open the repository in Android Studio and allow Gradle sync to complete. Make sure the NDK and CMake versions above are installed through SDK Manager.

### Build from the command line

The repository currently contains Gradle wrapper properties but does not include the `gradlew` launcher scripts or wrapper JAR. Until the wrapper is added, use an installed compatible Gradle version from the repository root:

```bash
# Build the debug APK
gradle :app:assembleDebug

# Run local unit tests
gradle :app:testDebugUnitTest

# Build a release APK (requires a release keystore)
KEYSTORE_PATH=/absolute/path/to/upload-key.jks STORE_PASSWORD='your-store-password' KEY_PASSWORD='your-key-password' gradle :app:assembleRelease
```

The debug APK is normally written to `app/build/outputs/apk/debug/`.

## Release signing

Release signing reads credentials from the environment variables `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`. The configured key alias is `upload`. Keep the keystore and passwords private. Never commit signing files, credentials, API keys, or local environment files.

## Project files and compatibility

ZYNTRA stores projects locally and supports project serialization/export. The current codebase retains the historical `.notecraft.json` extension for compatibility with existing project data. This legacy extension and internal class names do not change the displayed app name, which is **ZYNTRA**.

## Contributing

Bug reports, reproducible crash reports, performance observations, and focused pull requests are welcome. When reporting an issue, include the Android version and device model, steps to reproduce, expected and actual behavior, and relevant logs with personal information and secrets removed.

Please do not include private project files, copyrighted sample packs, signing keys, or credentials in issues.

## License

The original source code in this repository is licensed under the [MIT License](LICENSE).

The MIT license applies only to material for which the project contributors hold the necessary rights. Third-party libraries, fonts, audio samples, presets, demo projects, artwork, and other bundled assets may have separate licenses or usage terms. Their inclusion here does not relicense them. Verify and document the applicable license and attribution for each such item before redistributing the project or publishing builds.

## Acknowledgements

ZYNTRA uses open-source libraries and Android platform technologies. See `gradle/libs.versions.toml` and the relevant upstream projects for their individual licenses.
