# Agent-style memory architecture

The runtime uses two bounded layers:

1. **Short-term context**: the active conversation remains in `historyMessages`. When it reaches 10 messages, only the latest 6 are retained. This prevents prompt growth and keeps the model responsive.
2. **Long-term memory**: before truncation, the local model is called with an extraction prompt. It keeps only durable preferences, goals, skill level, recurring constraints, and decisions (at most five Markdown bullets). Transient game moves and small talk are rejected. Valid bullets are appended to `agent-memory.md`, capped at 6000 characters.

On the next request, the compact memory is inserted as one `SYSTEM` message. It is advisory and used only when relevant. Extraction failure never blocks the user request and never writes raw conversation text.

This is intentionally an on-device, privacy-preserving approximation of the memory pattern used by agent systems: bounded working memory + model-written semantic memory + deterministic size limits. A future version can add importance scores, per-game namespaces, user consent, and encrypted storage without changing the SDK request API.
