# Nexus Launcher — implementation and verification status

This checklist records what is present in the source and what is still required before calling the launcher complete. A source file or shortcut is not proof that an end-to-end feature works.

## Present in source

- Nexus Assistant uses Android speech recognition, device actions, and local-first chat with the downloaded SmolLM2 GGUF model. The launcher and local chat do not require signing in. Model downloads are available under Settings > Models.
- Tools Studio links to Quick Utility Hub, date/time conversion, QR generation, PDF/image conversion, image compression, file renaming, password generation, OCR, PDF reading, voice settings, radio, live TV, and protected text.
- Secure Vault source includes biometric unlock, encrypted preferences, notes, documents, passwords, cards, and image-text extraction.
- Radio, Live TV playlist/player, Digital Wellbeing, launcher appearance and branding code are present.
- Protected text uses AES-GCM and PBKDF2-HMAC-SHA256 for new output; known legacy output that did not use a real password is rejected.

## Voice readiness

- **Android local TTS:** Implemented. Available languages and offline voice data depend on the phone.
- **Online neural voice:** Removed from this local-first build. Reader voice settings use the voice installed on the phone.
- **Chatterbox:** Not integrated end-to-end. Tokenization, ONNX inference, audio decoding and playback are not wired together.
- **Coqui TTS:** Not integrated as a native on-device Android inference engine.
- **Piper:** A voice catalog/downloader exists, but a compatible inference runtime and playback path are not established by the source alone.

## Build verification

The `Nexus Launcher Build and Verification` workflow compiles the Android debug APK and runs Spotless formatting checks. It is triggered manually and by relevant changes on `16-dev`; a successful run uploads a debug APK artifact for testing. A running workflow is not a successful build: check its result before distributing the APK.

## Release checks still required

1. Confirm a successful debug compilation and formatting check for the current commit.
2. Exercise Assistant commands, local model first download/re-download/interruption, inference, voice playback and network-failure fallback on a physical Android device.
3. Confirm the removed account, online AI, and online voice screens are absent from the built APK.
4. Exercise every Tools Studio destination; test file access, output saving, sharing, and back navigation.
5. Test Secure Vault biometric success/failure/cancellation, lock-on-background, data persistence, document/image OCR, and encrypted storage.
6. Test Radio and Live TV with reachable streams, playback errors, service lifecycle, and stop/return navigation.
7. Verify TalkBack/screen-reader labels, focus order, large font, contrast, and touch target sizes.
8. Confirm Digital Wellbeing limits and app exemptions work on supported Android versions.
9. Review manifest permissions, exported components, and any provider/API configuration before release.

## Honest release status

Do not describe the project as fully complete or release-ready until the checks above pass. This note is a checklist, not a claim that physical-device testing has happened.
