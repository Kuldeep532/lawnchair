# Nexus Launcher — implementation and verification status

This file separates source presence from functionality verified on a device.

## Present in source

- Nexus Assistant activity and its text/voice interface are included.
- The assistant uses Android speech recognition for voice input and the phone's installed Android TextToSpeech engine for local spoken output.
- A lightweight SmolLM2-135M-Instruct Q4_K_M GGUF model can be downloaded to app-private storage and loaded through Llamatik/llama.cpp for local chat.
- ElevenLabs speech requests are routed through the configured Supabase gateway. This depends on a valid user session, server-side provider setup, and any required entitlement/credits.
- Tools Studio includes navigation to Nexus Assistant, Quick Utility Hub, date/time conversion, QR generator, PDF-to-image, image compressor, file renamer, password generator, image OCR, image-to-PDF, PDF reader, TTS & Voice settings, online radio, live TV, and protected text.
- Secure Vault source includes biometric unlock, encrypted preferences, notes, documents, passwords, cards, and image-text extraction paths.
- About screen includes Nexus Launcher branding and Nexus Fusion UI v1.5 description.
- Protected text uses AES-GCM and PBKDF2-HMAC-SHA256 for new output; it rejects the known legacy format that did not use a real password.

## Voice engines — do not overstate readiness

- **Android local TTS:** Implemented, but language and offline availability depend on the device's installed voice data.
- **ElevenLabs:** Client code exists behind the Supabase gateway. It is not release-verified until the normal login/session-refresh/sign-out flow is connected and online playback succeeds on a device.
- **Chatterbox:** Not integrated for actual speech generation. The previous large download-only flow was removed. Tokenization, ONNX inference, decoding, and playback are not wired end to end.
- **Coqui TTS:** Not integrated as a native on-device Android inference engine.
- **Piper:** A voice catalog/downloader exists, but a working Android Piper inference and audio playback flow is not established by those files alone.

## Explicit release checks remaining

1. Wait for and confirm the GitHub Actions Android debug build and formatting result for the latest commit.
2. Test assistant, local model, TTS, online session handling, and Tools Studio actions on a physical Android device.
3. Verify screen-reader navigation and touch target descriptions.
4. Verify Secure Vault lock-on-background, biometric failure/cancellation, persisted files, OCR fallback, and encrypted storage behavior.
5. Verify online AI uses the real signed-in session, refreshes expired sessions, and clears them on sign-out.

A source file or UI shortcut is not proof that an end-to-end feature is complete. Do not mark the release ready until the checks above are completed.
