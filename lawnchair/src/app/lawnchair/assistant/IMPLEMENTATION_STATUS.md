# Nexus Assistant — implementation status

## Integrated in `16-dev`

- An in-launcher Compose assistant screen is registered in the manifest.
- `NexusAssistantLauncher.intent(context)` provides a reusable launch entry point.
- The Tools Studio source includes a Nexus Assistant entry button.
- Text commands route web search, flashlight, Internet settings, system settings, time, alarms/timers, Bluetooth, and opening apps by visible launcher label.
- Hindi speech input uses Android's recognizer and speech output uses Android TextToSpeech.
- The UI clearly identifies that Android TextToSpeech is the currently active voice engine.
- No large language model or voice model is bundled in the base APK.

## Not yet complete; do not describe as finished

- No verifiable Aliza Android dependency has been integrated.
- No Hugging Face voice model is currently downloaded by this version or used by the active speech engine.
- No Sherpa-ONNX/Piper inference runtime is integrated; the downloaded ONNX file from an earlier iteration alone could not produce speech.
- Android's speech recognizer may use an installed remote speech service; input is not guaranteed to be offline or unlimited.
- Wi-Fi commands open Android's Internet panel; regular third-party apps cannot directly toggle Wi-Fi on modern Android.
- General knowledge chat is not yet provided by a local LLM. Unknown text requests open web search.
- The repository has not been built or run on a physical device during this change, so build/runtime, accessibility and device-specific tests remain outstanding.

## Next work

1. Select a redistributable, commercially compatible voice model and compatible Android inference runtime.
2. Add model download progress, checksum/size validation, retry/cancel, storage checks and partial-download cleanup.
3. Add a local speech-recognition option with a system recognizer fallback.
4. Integrate and verify an Aliza package only after the exact project/artifact and licence can be confirmed.
5. Wire a launcher gesture/shortcut to the activity, then build and test on a real device.
