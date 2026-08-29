# Locked versions

- MLC LLM source: `9fa644f54b04983adea4d0168f49fc6af4a893ba`
- Android Gradle Plugin: `8.2.0` (inherited from the validated MLC Android sample)
- Gradle wrapper: `8.5`
- Kotlin Android plugin: `1.8.10`
- Compile SDK: `35`
- Minimum Android SDK: `26` in upstream sample; product support policy remains Android 10/API 29+
- MVP model: `mlc-ai/Qwen2.5-0.5B-Instruct-q4f16_1-MLC`
- Logical model ID: `local/qwen2.5-0.5b-instruct@1`
- Host compiler Python: `3.12.14`
- Host compiler TVM: source build at submodule commit `837cb9de1127b48ce48e4cefe09e83215b9d4ba7`
- Rust: `1.98.0`; Android target: `aarch64-linux-android`
- Android NDK: `r27`; JDK: `17`

The TVM C++ libraries, Python package, and `tvm_ffi` extension must all be built from the locked source tree before a release artifact is accepted.
