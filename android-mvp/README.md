# Local LLM Android MVP

第一期只验证 Android arm64 手机上的 MLC 端侧文本推理。锁定的 MLC 源码位于 `../vendor/mlc-llm`，commit 为 `9fa644f54b04983adea4d0168f49fc6af4a893ba`。

默认模型为 `Qwen2.5-0.5B-Instruct-q4f16_1-MLC`，逻辑 ID 为 `local/qwen2.5-0.5b-instruct@1`，上下文 2048。编译后参数约 265 MB，基础运行内存估算约 314 MB。小模型用于先验证端侧闭环，后续可替换为其他认证制品。

## 构建

已验证工具链为 Windows JDK 17、Android SDK 34/35、WSL2 Ubuntu、NDK r27、Python 3.12、Rust 1.98 和源码构建 TVM。原生库同时包含 Android OpenCL runtime，并保留 Vulkan 相关构建能力。

```powershell
wsl.exe -d Ubuntu -- bash /mnt/e/data/code/android_llm/scripts/package-android-wsl.sh
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
cd E:\data\code\android_llm\android-mvp
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

`package-android-wsl.sh` 生成 `dist/lib/mlc4j`。debug APK 已验证包含 arm64 JNI 库和模型配置。当前权重未内嵌，App 首次运行需从模型 URL 下载；量产必须将 URL 替换为自有 CDN，下载完成后才能做飞行模式离线验证。停止操作已调用 MLC JSON FFI abort，不只是停止 UI。

验收要求：连续 20 次流式对话、取消终态正确、无崩溃，并记录加载时间、prefill/decode token/s、峰值内存和设备信息。

当前产物：`app/build/outputs/apk/debug/app-debug.apk`，约 140 MB。Android 16
arm64 真机已完成模型下载、离线运行、取消和连续 20 轮对话验证，详见
`DEVICE_TEST_REPORT.md`。宿主 App 接入请优先阅读仓库根目录 `README.md` 与
`docs/INTEGRATION.md`。
