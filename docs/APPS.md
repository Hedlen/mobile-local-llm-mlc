# Repository apps

## `local-llm-sdk`: reusable engine component

This is the product-neutral API used by host applications. It owns model lifecycle, streaming, cancellation, model catalog/install primitives and public request/event types. Host apps must not depend on MLC/TVM implementation types.

## `app`: test sample only

The `android-mvp/app` package (`ai.mlc.mlcchat`) exists to test model downloads, chat, benchmarks and device compatibility. It is documentation/sample code, not a consumer product and should not be distributed to end users.

## `gomoku-mvp`: first standalone application

The `ai.mlc.gomoku` package is independently installable. It owns its model files, downloads Qwen2.5 0.5B on first use, runs inference through `local-llm-sdk`, supports offline play after installation, and exposes automatic visual move analysis when enabled.

Debug APKs:

- Test sample: `android-mvp/app/build/outputs/apk/debug/app-debug.apk`
- Gomoku: `android-mvp/gomoku-mvp/build/outputs/apk/debug/gomoku-mvp-debug.apk`
