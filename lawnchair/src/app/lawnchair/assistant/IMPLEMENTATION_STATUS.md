# Nexus Assistant — implementation status

## Integrated in `16-dev`

- The assistant screen is embedded in the launcher and registered in the manifest.
- Text/voice input, spoken responses, web search, flashlight, Internet/Wi-Fi panel, app launching, Bluetooth, settings, alarm and timer commands are connected.
- Lightweight local chat model: SmolLM2-135M-Instruct Q4_K_M GGUF (about 105 MB), downloaded to app-private storage.
- Local model inference uses the published Llamatik Android library and llama.cpp after the GGUF is loaded.
- Assistant speech output has two selectable paths: the phone's installed Android TTS voice for offline playback, or ElevenLabs through the authenticated Supabase gateway.
- The ElevenLabs API secret stays server-side; local TTS is the fallback when online synthesis fails.
- The multi-gigabyte Chatterbox download flow, unused ONNX inference class, and ONNX Runtime Android dependency have been removed to reduce storage use and APK size.
- Tools Studio includes Nexus Assistant, Quick Utility Hub, date/time conversion, QR generation, PDF-to-image, image compression, file renaming, password generation, image OCR, image-to-PDF, PDF reader, online radio, live TV, and the protected-text utility.
- Protected text now requires a password and uses AES-GCM with PBKDF2-HMAC-SHA256 (210,000 iterations) for new output. Legacy password-protected NXT1 text remains readable; old NXT1 text that had no real password is rejected rather than presented as secure.
- Added the missing `password_generator` manifest string resource that caused the prior Android resource-linking build to fail.

## Voice engine clarification

- Coqui TTS is **not yet integrated as an on-device Android inference engine**. The official Coqui TTS project is a Python TTS toolkit, not a tiny drop-in Android library; shipping it locally requires selecting/exporting a compatible model and wiring its tokenizer, inference runtime, and playback pipeline for Android.
- Until that compatible Coqui model/runtime is implemented and tested, local speech deliberately uses Android's installed TTS engine. This avoids a multi-gigabyte download and keeps speech available offline, but voice quality and language availability depend on the phone's installed voices.
- ElevenLabs is the optional online neural voice. It requires a valid Supabase session, server-side ElevenLabs configuration, and the applicable plan/credits.

## Remaining release checks

- Confirm the GitHub Actions Android build and formatting checks after the latest changes.
- Test local chat loading, ElevenLabs audio playback, screen-reader flows, low-storage handling, interrupted downloads, and tool actions on a physical Android device.
- The existing repository still needs the canonical app login/logout flow wired to `NexusCloudAssistant.saveSession(...)` before online AI and ElevenLabs can be considered verified for signed-in users.
- Android speech recognition may rely on network/system services; voice input is not guaranteed to be offline or unlimited.
- Standard third-party apps cannot directly switch Wi-Fi on/off on modern Android, so the assistant opens the Android Internet panel.
