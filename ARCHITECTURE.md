# MVP 架构说明

第一期以官方 MLC Android 应用为运行基线，验证 `模型制品 → mlc4j/TVM Runtime → Vulkan → 流式 Chat Completions`。业务使用逻辑模型 ID，不依赖 Hugging Face 文件名。

- `android-mvp/`：第一期 Android 应用与模型打包配置。
- `vendor/mlc-llm/`：锁定的 MLC 源码，以及真实取消所需的最小 binding 修改。
- `PRD.md`：完整路线和第一期门禁。

Android 真机闭环验证后，已新增 `android-mvp/local-llm-sdk/`，抽取稳定的
`LocalLlmClient` Kotlin 门面。Demo 的文本聊天主路径通过该门面调用，公共包
不引用 MLC 类型。Swift 等价契约仍在第二期实现。

当前依赖方向：

```text
Demo / 游戏业务
      ↓
local-llm-sdk（公共 Kotlin 类型、状态、流式事件、错误）
      ↓
mlc4j（内部 MLC Adapter）
      ↓
TVM Runtime + Android GPU
```
