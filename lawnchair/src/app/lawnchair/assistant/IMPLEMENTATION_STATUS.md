# Nexus Assistant — implementation status

## Integrated in `16-dev`

- Added `NexusAssistantActivity.kt` as an in-launcher Compose screen.
- Registered the activity in `lawnchair/AndroidManifest.xml` and declared microphone permission.
- Added `NexusAssistantLauncher.intent(context)` for launcher gesture/entry-point wiring.
- Added a Nexus Assistant entry button inside Tools Studio.
- Added text commands for web search, flashlight toggle, Internet settings panel, general settings, time, and installed-app launch by visible app name.
- Added Hindi speech recognition through Android's `RecognizerIntent` and speech output through Android `TextToSpeech`.
- The base APK does not bundle a neural voice model, helping keep its installed size low.

## Not yet complete; do not describe as finished

- No Aliza package dependency has been connected: the user has not supplied a verifiable Aliza package repository/artifact, and a similarly named GitHub project could not be identified reliably.
- A Piper ONNX model is not downloaded or executed yet. Hugging Face's `remiai3/TTS_MODELS_FOR_APP` repository currently shows CC-BY-NC 4.0; this is not suitable for commercial use without appropriate permission. A verified compatible model and its license must be selected.
- No Sherpa-ONNX/Piper inference runtime is integrated, so generated voice remains Android TextToSpeech.
- Android's `RecognizerIntent` may use an installed remote speech service. It is not guaranteed to be offline or unlimited.
- Wi-Fi commands open Android's Internet panel; standard third-party apps cannot directly switch Wi-Fi on/off on modern Android.
- The repository has not been built or run on a device as part of this change. Build, runtime, accessibility, and voice tests remain required.

## Next work

1. Choose a redistributable, commercially compatible voice model plus Android inference runtime.
2. Implement actual download progress, checksum/size validation, retry/cancel, storage checks, and partial-download cleanup.
3. Add a local speech-recognition option and keep an Android recognizer fallback.
4. Extend command routing carefully, confirming destructive or privacy-impacting actions with the user.
5. Wire a visible launcher gesture/shortcut to this activity, then build and device-test the result.