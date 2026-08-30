# Cloud AI in Gomoku

The release app has no built-in provider key. A player can open **Settings** and
enter a compatible HTTPS Chat Completions endpoint, model name, and their own
API key. The key is encrypted with the Android Keystore and stored only on that
device. It is never put in source control, the APK, logs, exports, or analytics.

## Runtime behavior

- In **AI Match**, the local engine generates legal candidate moves and validates
  the board. When cloud AI is configured and available, the model may choose only
  from those candidates and write a short in-character reply.
- Local move selection is immediate when cloud AI is unavailable, slow, invalid,
  or not configured. Rules and win detection always remain local.
- Review uses cloud AI first, then the downloaded on-device model, then a concise
  deterministic review. A failed request never blocks a game.

## Release guidance

User-provided keys are appropriate only for this early self-hosted release.
Before operating a commercial service, replace direct provider access with a
backend proxy or short-lived, scoped tokens. Send only the minimum board summary
needed for the feature; do not upload account identifiers, device identifiers,
contacts, or long-term-memory content.

If a key was ever pasted into a chat, source file, issue, screenshot, or build
log, revoke it at the provider and create a replacement.
