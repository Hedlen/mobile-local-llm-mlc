# 通用端侧大模型运行平台 PRD

- 版本：v1.0（最终评审版，待确认）
- 日期：2026-08-28
- 代号：Local LLM Runtime
- 状态：最终 PRD，等待实施确认；**尚未授权编码**

## 1. 产品定义

构建可嵌入 Android 与 iOS App 的通用端侧大模型平台，打通模型制备、分发、加载、推理、流式响应、结构化输出、会话、记忆与资源治理。上层以接近云端大模型 API 的方式提交 `model + messages + options`，接收增量 token、用量、性能与错误，无需理解 MLC、TVM、量化、KV cache 或移动 GPU 差异。

游戏只是首批调用方，不限定棋类。NPC 对话、角色记忆、剧情生成、局势解说、离线助手、摘要和信息抽取等由业务基于通用 API 实现，核心 SDK 不包含游戏规则或业务提示词。

## 2. 背景

云端 API 简单，但移动应用存在网络延迟、调用成本、隐私、离线不可用和服务依赖。端侧部署又涉及双平台构建、GPU 兼容、模型文件、内存、温度、取消和生命周期。项目核心价值是：**让本地模型像云端 API 一样容易调用，同时保留端侧能力边界和降级机制。**

## 3. 目标与非目标

### 3.1 目标

1. Android/iOS 真机离线运行约 1–3B 指令模型。
2. 提供 Chat Completions 风格的统一请求、流式事件、响应和错误契约。
3. Kotlin/Swift 共享版本化 JSON Schema，行为基本一致。
4. 封装 MLC 模型转换、量化、平台编译、下载、校验和版本治理。
5. 自动处理能力检测、上下文预算、内存、温度、取消和前后台切换。
6. 提供可选的会话、长期记忆、人格和 JSON Schema 输出。
7. 核心不绑定游戏类型；首期后端为 MLC LLM，并可被内部适配层替换。
8. 建立可验证的模型兼容体系，使同架构的新模型变体能够低成本接入，新架构和更大模型有明确准入流程。
9. 公共 API、模型清单和调度器从第一版支持多计算后端表达；P0 落地移动 GPU，NPU/ANE 作为独立后端逐厂商认证，上层调用方式不变。

### 3.2 非目标

- 不从零训练模型，不在手机上做全参数训练。
- 不承诺端侧模型具备云端模型同等质量；兼容调用体验，不等同模型能力。
- P0 不提供云推理、账号、同步、后台、计费或完整生产 HTTP Server。
- 不内置棋类引擎、世界观、NPC 规则或其他业务逻辑。
- 不保证所有 Android GPU/NPU 均支持同一模型制品。
- 不把“arm64 手机”视为统一加速平台；Qualcomm、MediaTek、Samsung 和 Apple 的专用加速器分别评估、编译和认证。
- 不承诺任意 Hugging Face 模型“下载即运行”；移动端仅加载与当前 SDK、已编译模型库和设备能力匹配的认证制品。
- 不承诺手机可以运行任意大参数模型；API 可兼容，实际运行受存储、内存、带宽、温度和后端约束。

## 4. 用户与场景

- 游戏开发者：离线 NPC、人物记忆、动态剧情、战局解说。
- App 开发者：本地摘要、改写、分类、抽取、问答。
- AI 工程师：把可信模型制备为双端可发布制品。
- 客户端工程师：使用熟悉的消息 API，不直接操作推理框架。

典型调用包括：流式角色回复；依据 JSON Schema 生成任务计划或情绪标签；无网摘要；按用户/存档/角色隔离记忆；依据设备选择 1.5B 或 3B；开发时在本地和云端兼容适配器间切换。

## 5. 产品原则

- API 优先：先稳定契约和状态机，再优化后端。
- 本地优先：推理默认零网络，不上传提示与响应。
- 能力显式：返回设备、模型和后端支持矩阵，不假装完全兼容云端。
- 确定降级：资源不足、过热、模型缺失或校验失败均有明确结果。
- 业务无关：领域知识由宿主注入。
- 制品可追踪：来源、许可证、量化和编译参数完整记录。
- 契约稳定：上层只依赖公共协议，平台和模型差异通过 capabilities、manifest 与错误暴露。
- 兼容可证明：任何“支持”必须有构建、真机运行、质量和资源报告，不能仅以成功转换为依据。

## 6. 范围

### 6.1 P0

- MLC 转换、量化、双平台编译、打包、清单、签名和哈希流水线。
- Android arm64-v8a、iOS arm64 真机运行。
- 模型安装/导入/校验/加载/卸载/删除/版本查询。
- Chat Completions 风格的流式与非流式调用。
- system/user/assistant 消息、采样、停止词、seed（支持时）、输出上限。
- JSON Object/JSON Schema 输出、校验及失败说明。
- 有界队列、取消、超时、进度、生命周期和错误治理。
- token 统计、上下文裁剪、摘要接口和性能统计。
- 可选本地会话、基础长期记忆与人格配置。
- 设备基准、推荐配置、内存和热降级。
- Kotlin SDK、Swift SDK、双端 Demo、测试与文档。
- 模型兼容验证工具、制品兼容矩阵和至少两种模型家族的验证样例（以 MLC 实际支持为准）。

### 6.2 P1/P2

P1：Embeddings/向量检索、Tool Calling、本地工具调度、多模型路由、LoRA、多模态、云端适配器、Unity/Flutter/React Native，以及首个有商业设备范围的厂商 NPU 后端 Spike。

P2：按商业优先级产品化 Qualcomm QNN/Hexagon、MediaTek NeuroPilot/APU、Samsung NPU、Apple Core ML/ANE 等专用后端，以及 speculative decoding、共享前缀缓存、加密同步和桌面扩展。具体 SDK 名称与可用性以实施时厂商公开/授权工具链为准。

## 7. API 契约

采用自有命名空间的 Chat Completions 风格，不宣称 100% OpenAI API 兼容。

```json
{
  "model": "qwen2.5-1.5b-instruct-q4",
  "messages": [
    {"role": "system", "content": "你是游戏中的向导。"},
    {"role": "user", "content": "概括当前任务。"}
  ],
  "temperature": 0.7,
  "top_p": 0.9,
  "max_output_tokens": 256,
  "stream": true,
  "response_format": {"type": "text"}
}
```

响应包括 `id`、`created_at`、`model`、`backend`、`accelerator`、`artifact_id`、`choices`/`delta`、`finish_reason`、token usage、首 token、prefill/decode 速度和降级 warnings。

模型名称使用逻辑 ID 与版本约束，不让业务依赖文件名，例如 `local/qwen-1.5b-instruct@1`。SDK 解析为当前平台的具体制品。请求可声明 `requirements`（如结构化输出、最低上下文、语言或工具能力）；无法满足时在生成前失败，不以低能力模型静默替代。

流式状态机：`queued → model_loading? → started → delta* → usage → completed`；异常终态为 `cancelled | timeout | failed`。每个请求只有一个终态，取消后不得继续回调 token。

P0 参数：`model/messages/temperature/top_p/seed/max_output_tokens/stop/stream/response_format/metadata`。不支持的参数返回 `unsupported_parameter`，不得静默忽略。

结构化输出优先使用运行时约束解码；不可用时解析、校验并至多修复一次。响应标明 `native_constrained`、`validated_after_generation` 或 `fallback_failed`。

Kotlin/Swift 等价接口：

- `initialize`、`capabilities`、`benchmark.run`
- `models.list/install/import/verify/load/unload/delete`
- `chat.completions.create/stream`
- `requests.cancel`
- `sessions.create/get/list/delete`
- `memory.add/search/update/delete/clear/export`
- `tokenizer.count`、`diagnostics.snapshot`

### 7.1 跨平台契约要求

- Kotlin 与 Swift 的字段、默认值、可空性、枚举、错误码和事件顺序由同一 Schema 生成或校验。
- 公共协议遵循语义化版本；新增可选字段向后兼容，删除/改义字段只能进入主版本升级。
- 未识别的响应字段应被旧客户端安全忽略；未识别的请求字段按严格模式返回错误。
- 平台差异只能出现在 `capabilities`、`performance`、`warnings` 和明确错误中，不改变业务状态机。
- 提供 golden contract tests，Android/iOS 每次发布必须共同通过。

## 8. 模型与制品

- 框架：固定 revision 的 MLC LLM、TVM Unity、MLCEngine。
- 默认候选：Qwen2.5-1.5B-Instruct MLC `q4f16_1`。
- 高配候选：Qwen2.5-3B-Instruct MLC `q4f16_1`。
- 最终模型、量化和上下文以固定版本兼容性及真机数据为准。
- 权重不提交 Git，默认不打入基础 App 包。

每个制品包含量化权重、tokenizer、chat template、MLC 配置、平台模型库、默认参数、manifest、签名、SHA-256、模型来源/revision/许可证/NOTICE、MLC/TVM revision 和完整编译参数。Android 与 iOS 平台库分别构建；只有通过测试的 runtime/model/platform 组合可以加载。

`manifest` 至少声明：公共模型 ID/版本、模型家族与架构、参数规模、量化、上下文上限、词表/tokenizer 哈希、chat template 哈希、权重分片、所需模型库 ID、最低 runtime/API 版本、计算后端、目标 OS/ABI、GPU/NPU/ANE 能力与驱动要求、估算 RAM/专用内存/存储、支持功能、许可证和制品哈希。加载器先匹配 manifest，再打开权重。

安装支持 HTTPS 断点续传和本地导入，下载前检查空间，完成后校验签名和哈希，支持原子切换与回滚。模型位于 App 私有目录；模型删除与用户数据删除相互独立。

### 8.1 商店发布与全设备可用策略

- **代码随商店发布**：MLC/TVM runtime、预编译模型库、tokenizer 可执行组件和 NPU 厂商库属于代码，必须随 App、App Store/Google Play 管理的模块或正式 SDK 版本发布，不从自有 CDN 动态下载执行。
- **权重按需分发**：量化权重、配置、模板和其他纯数据可由签名 CDN 或商店资产机制按需下载，但其用途必须属于已审核功能，且只能匹配 App 内已有的模型库。
- **新权重不一定发版**：若架构、参数形状、量化和模型库 ID 已被当前 App 覆盖，新权重经认证后可只更新目录与数据制品。
- **新架构通常需要发版**：新增算子、参数形状、推理逻辑或 NPU 后端时，重新编译运行库并通过商店更新；不得用远程 `.so`、framework、DEX/JAR 绕过审核。
- **远程目录只做选择**：服务端兼容目录可以启停某个已认证制品、提供 URL/哈希/策略和设备黑名单，但不能远程宣告当前二进制不具备的能力。

“各种设备都能使用”定义为产品功能分级可达，而不是所有设备本地运行同一个模型：

| 路径 | 适用设备 | 能力 |
|---|---|---|
| Local Pro | 高端且专项认证 | 3B 或实验性更大模型、较长上下文 |
| Local Standard | 主流认证设备 | 1–1.5B 4-bit、核心离线能力 |
| Local Lite | 资源较低但仍满足最低本地门槛 | 更小模型、短上下文、短输出、部分任务 |
| Remote/Hybrid | 有网但本地不支持或用户选择云端 | 由上层云适配器提供，须获用户同意并披露数据路径 |
| Deterministic Fallback | 无网且本地不支持/模型未安装 | 规则、预置文案、传统算法；保证 App 核心流程不中断 |

若产品要求“所有受支持手机都必须完全离线使用大模型”，则必须额外选定一个可在最低配置 CPU/GPU 上认证的 Lite 模型，并相应降低最低系统/设备覆盖或模型能力；不存在既保持 1–3B 体验又覆盖所有存量手机的可靠方案。

### 8.2 模型兼容分级

| 等级 | 定义 | 发布方式 |
|---|---|---|
| Certified | 已在指定双端/设备矩阵通过转换、质量、性能、稳定性测试 | 可进入正式模型目录 |
| Convertible | MLC 已支持该架构，可转换/量化，但尚未完成本项目认证 | 仅开发者模式，不面向终端用户 |
| Adapter Required | 新架构、特殊算子、tokenizer 或推理逻辑未被当前 MLC/适配层支持 | 新增实现、重新编译 SDK 并完整回归 |
| Unsupported | 超出设备资源、许可证不允许、转换失败或质量不达标 | 明确拒绝加载 |

“同架构”也不自动等于兼容：词表变化、RoPE、attention、滑动窗口、MoE、量化、chat template 或特殊 token 变化均需重新验证。模型兼容由自动探测加人工认证共同决定。

MLC 移动端采用预编译模型库：权重可以在安装后下载，但对应模型架构/推理逻辑必须已包含在当前 Android/iOS 运行库中。已有架构的新权重变体通常只需重新转换、量化和认证；全新架构通常需要扩展 MLC/适配代码、重新编译并随新版 SDK/App 发布。模型目录不得把“远程有权重”误报为“当前 App 可运行”。

### 8.3 新模型接入流程

1. 读取并校验来源 revision、配置、tokenizer、许可证和权重完整性。
2. 识别架构及 MLC 支持状态；新架构进入 Adapter Required 流程。
3. 生成多个候选量化，执行困惑度/任务集或等价质量回归。
4. 为 Android/iOS 编译模型库并生成 manifest；禁止使用本地 JIT 作为生产依赖。
5. 在代表性真机测量加载、首 token、吞吐、内存、热量和稳定性。
6. 通过安全、输出格式、多语言和业务无关黄金集后签名发布。
7. 将结果写入机器可读兼容目录；失败制品不得被生产 SDK 发现。

### 8.4 更大参数模型策略

- 公共 API 与参数规模无关，理论上可描述 7B、8B 或更大模型；“API 兼容”不代表“当前手机可运行”。
- 模型目录为每个制品声明硬性与建议资源，安装前预检存储，加载前预检可用内存/GPU，运行中持续热治理。
- P0 正式支持目标仍为 1–3B；7B/8B 仅作为 Pro/实验候选，必须单独通过准入，不能作为基础体验承诺。
- 支持权重分片下载、分片哈希和原子安装，解决大文件恢复与更新；P0 不做跨设备 tensor parallel。
- 同一逻辑模型可有多种量化/上下文/平台变体，由 resolver 依据能力和用户策略选择，但不得未经同意自动下载更大制品。
- 若本机不满足要求，返回 `model_resource_unsupported`；未来可由上层显式选择较小本地模型或云端适配器。

## 9. 架构

```text
游戏 / App / Demo
       ↓
Local LLM Public API
Kotlin / Swift / Versioned JSON Schema
       ├─ Request Scheduler：队列、取消、超时、生命周期
       ├─ Context Manager：模板、token、裁剪、摘要
       ├─ Output Guard：JSON Schema、停止条件、错误
       ├─ Session & Memory：可选、加密、可删除
       ├─ Model Manager：下载、校验、版本、能力
       └─ InferenceEngine
                ↓
          MLCEngine Adapter
       Android Vulkan / iOS Metal
```

核心对象不得引用 MLC 类型。内部 `InferenceEngine` 抽象 load、tokenize、prefill、decode、stream、cancel、unload、capabilities，以便未来增加其他后端。

公共层再增加 `ModelResolver` 与 `ArtifactProvider`：前者根据逻辑模型、能力需求和设备选择制品，后者负责下载/导入。这样更换模型仓库、增加其他模型家族或新增后端不影响上层调用。

计算后端使用插件式 `BackendProvider` 注册，统一提供 `probe → compatibility → estimate → load → generate → cancel → unload`。P0 实现 `MlcGpuBackend`；未来的 `QnnBackend`、`CoreMlBackend` 或其他厂商后端必须复用同一公共请求/事件/错误协议，但可以拥有不同权重格式和编译制品。

调度优先级不是固定“NPU > GPU > CPU”，而是按当前请求能力、认证状态、模型质量、首 token、吞吐、内存、功耗和温度综合选择。未经认证的 NPU 不得因理论算力更高而自动启用。后端切换只发生在请求开始前；生成过程中不迁移 KV cache。

P0 采用**进程内 SDK**，请求响应 Schema 类似云 API，但不开放本机 TCP 端口。这样更符合移动系统后台限制，开销更低，也便于内存和取消治理。P1 可提供仅限开发测试的 localhost 桥。

## 10. 会话、记忆与人格（可选模块）

- 会话保存版本化消息、模型、参数与时间，按 token 预算保留最近消息并摘要旧上下文；宿主可完全接管存储。
- 记忆包含内容、类型、来源、时间、置信度、TTL 和 namespace；按 App、用户、存档、角色隔离。P0 使用结构化过滤/全文检索，向量检索列入 P1。
- 人格由 system prompt、措辞约束、示例、采样和安全规则组成；SDK 只组合和控制预算，不内置特定角色。
- 提供查看、纠正、删除、清空和导出；用户输入不得覆盖宿主声明的不可覆盖约束。

## 11. 兼容与资源治理

暂定 Android 10+/API 29、arm64-v8a、满足 MLC 要求的 Vulkan；iOS 16.4+、arm64、Metal。模拟器不用于发布性能验收。P0 的“支持设备”指经认证 GPU 路径可运行，不等同该 SoC 内的 NPU 已被使用。

| 层级 | 条件 | 配置/行为 |
|---|---|---|
| Unsupported | 算子/后端不兼容或内存不足 | 返回原因，由宿主选择模板或云端 |
| Lite | 低内存或热降频 | 1.5B 4-bit、2K、短输出 |
| Standard | 主流 6GB+ 且基准通过 | 1.5B 4-bit、4K、完整 P0 |
| Pro | 高端 8GB+ 或新款 iPhone | 3B 4-bit、4K–8K |
| Experimental | 经过专项认证的极高端设备 | 7B/8B 等独立制品；不纳入 P0 SLA |

设备分层基于能力检测和短基准，而非仅看型号。默认单模型、单生成任务，其余进入有界队列。切后台、低内存或严重过热时按策略取消/卸载。KV cache 与模型内存分别统计，禁止业务重复初始化运行时。

## 12. 隐私与安全

- 核心推理飞行模式可用；下载与推理权限分离。
- 提示、响应、会话和记忆默认不写普通日志。
- 持久化敏感数据加密，密钥由 Android Keystore/iOS Keychain 保护。
- 未通过签名和哈希校验的模型禁止加载。
- 每个实际发布模型单独审查许可证，不按系列名称推断。
- 支持按 namespace/单条/全部删除和数据导出。
- 诊断默认仅含性能与错误元数据，不含原文与本地路径。

## 13. 验收指标

技术 Spike 后通过 ADR 固化参考机型和最终门槛：

| 类别 | P0 目标 |
|---|---|
| 离线 | 飞行模式下安装后的加载、生成、会话、记忆通过 |
| 契约 | Android/iOS 对同一请求产生一致结构和事件状态机 |
| SDK 兼容 | N-1 SDK 可解析声明向后兼容的 N 版清单；遇到未知必需能力或不兼容制品时在加载前失败 |
| 首 token | Standard P50 ≤2.5s、P95 ≤5s（已加载） |
| 速度 | Standard P50 ≥8 token/s；Lite ≥4 token/s |
| 内存 | 1.5B 4-bit + 4K 目标 ≤2.2GB，以实测修订 |
| 取消 | 取消后 500ms 内停止业务 token 回调，终态唯一 |
| 稳定 | 100 次短请求、30 次加载/卸载无崩溃或显著泄漏 |
| 结构化 | 支持范围内 JSON Schema 有效率 ≥99% |
| 隐私 | 推理零网络；日志无提示、响应和记忆原文 |
| 安全 | 篡改模型、清单或哈希均被拒绝 |
| 模型扩展 | 至少两个 MLC 已支持模型家族走通接入流水线；第二家族可不作为默认下载 |
| 后端扩展 | 使用测试后端通过完整公共契约套件，证明新增 NPU provider 不要求修改上层 API |

不要求两平台逐字生成相同文本；GPU 浮点差异可能改变采样结果。验收协议、约束、状态和质量范围一致。

## 14. 测试与错误

测试覆盖 API 契约、模型/SDK 版本组合、不同 tokenizer/chat template、模型损坏/回滚、分片断点续传、流式取消、超长上下文、结构化输出、前后台/低内存/热状态、数据库隔离与删除、安全攻击、性能和真机矩阵。至少覆盖 Android 中端/旗舰、最低支持 iPhone和近两代 iPhone。模型认证矩阵必须区分“可构建、可加载、可生成、质量合格、性能合格”五个状态。

稳定错误码至少包括：`invalid_request`、`unsupported_parameter/device`、`model_not_found/incompatible/integrity_failed/resource_unsupported`、`runtime_upgrade_required`、`insufficient_storage/memory`、`context_length_exceeded`、`queue_full`、`cancelled`、`timeout`、`thermal_restricted`、`structured_output_failed`、`backend_error`。错误携带是否可重试、建议动作和诊断 ID，不泄露原文或路径。

## 15. 里程碑与交付物

### 15.1 第一期 MVP（当前实施范围）

第一期只验证 Android 端侧推理闭环，不同时铺开全部平台能力：

- 单平台：Android 10+、arm64-v8a、Vulkan 真机；iOS 延后到第二期。
- 单后端：MLC LLM/MLCEngine Vulkan；不做 NPU、CPU 或云端后端。
- 单模型档：一个经 Spike 验证的 0.5B–1.5B 4-bit 指令模型；先以 Qwen2.5-1.5B 候选，若当前 MLC 不兼容则选择同级受支持模型并记录 ADR。
- 单用例：离线文本聊天；支持 system/user/assistant、流式输出、停止、取消和基础采样参数。
- 单模型来源：开发者通过本地文件/预置开发配置导入已转换权重；不建设生产 CDN、账号、签名服务或多版本目录。
- 最小 SDK：Kotlin `initialize/load/create/stream/cancel/unload/capabilities`，请求响应沿用最终公共 Schema 的兼容子集。
- 最小 Demo：模型选择/加载、聊天、停止生成、清空会话、性能与错误展示。
- 最小质量：单元测试、Android 构建、真机离线运行报告；不做长期记忆、人格、JSON Schema、Embedding、Tool Calling、多模型路由或商店发布。

第一期验收门：飞行模式下在一台认证 Android 真机成功加载模型并连续完成 20 次流式对话；取消终态正确；无崩溃；记录加载时间、首 token、decode token/s 和峰值内存。达到门槛并由用户确认效果后，才开始第二期。

第二期目标：iOS Metal 跑通、Android/iOS 契约测试、正式模型制品清单与安全下载。第三期以后再按产品反馈加入会话/记忆、多模型、更大模型、云端路由和 NPU 后端。

1. 技术 Spike：固定 MLC/TVM；1.5B/3B 转换编译；双端真机基准。
2. 协议骨架：Schema、错误、事件状态机、Kotlin/Swift SDK。
3. 制品流水线：量化、平台编译、manifest、签名、许可证。
4. 核心推理：加载、流式、取消、token、结构化输出。
5. 资源治理：能力、队列、内存、温度、前后台、诊断。
6. 数据模块：会话、记忆、namespace、加密、导出和删除。
7. Demo 与质量：聊天、JSON、模型管理、诊断、测试和真机报告。
8. 文档：集成、制备、API、ADR 和已知设备兼容表。
9. 扩展验证：第二模型家族接入、更大模型实验报告、N/N-1 兼容回归。

最终仓库包含源码、锁定依赖、构建脚本、Schema、测试、CI、示例和制备配置；权重通过独立制品清单管理。iOS 最终制品需要 macOS/Xcode 构建节点，当前 Windows 环境不能独立完成。

## 16. 风险

| 风险 | 对策 |
|---|---|
| MLC/TVM 复杂且变化快 | 固定 revision、适配层、可复现构建、签名缓存制品 |
| Android Vulkan 碎片化 | 能力检测、真机矩阵、设备分层、明确降级 |
| 小模型弱于云端 | 能力显式、约束输出、业务评测、未来云端适配器 |
| API 被误解为完全兼容 | 兼容矩阵；不支持参数报错；自有命名空间 |
| 长上下文 OOM/过热 | token 预算、摘要、KV/输出限制、动态降级 |
| 模型下载体积大 | 首次按需下载、断点续传、1.5B 默认 |
| 模型/tokenizer 错配 | manifest 强绑定版本并在加载前校验 |
| 新模型架构无法只靠下载接入 | 兼容分级；模型库随 SDK/App 发布；新架构走适配和重新编译流程 |
| 大模型能安装但无法稳定运行 | 安装/加载双重资源预检、独立认证、运行中热治理，不承诺任意规模 |
| SDK、模型库和权重版本漂移 | 三方兼容矩阵、manifest 约束、N/N-1 回归和原子回滚 |
| “支持 NPU”表述失真 | 按厂商/SoC/OS/驱动/模型制品逐项认证；产品只展示实测启用的 accelerator |
| NPU 后端格式和算子不统一 | BackendProvider 隔离制品与运行时；公共 API 不暴露厂商类型；保留 GPU 降级 |

## 17. 待确认决策

如无逐项修改，确认后按以下建议执行：

1. 通用端侧 LLM SDK；游戏仅是示例和使用方。
2. 首期采用 MLC LLM/MLCEngine。
3. 提供 Chat Completions 风格的进程内 Kotlin/Swift SDK，不启动生产 HTTP Server。
4. 默认 Qwen2.5-1.5B-Instruct `q4f16_1`，3B 高配可选，以 Spike 为准；至少再验证一个 MLC 已支持模型家族，证明架构不被 Qwen 写死。
5. Android 10+/arm64/Vulkan；iOS 16.4+/Metal。
6. 模型首次使用时独立下载，不包含在基础 App 包。
7. 会话和记忆可选、仅本地、无云同步。
8. P0 原生 Kotlin/Swift；Unity/Flutter/React Native 为 P1。
9. Demo 仅展示聊天、JSON、模型管理和诊断，不做完整游戏。
10. 开源方式、正式名称、下载服务器和商店地区发布前确定。
11. P0 对外承诺 1–3B；7B/8B 只做实验认证，API 和制品系统为更大模型保留兼容能力。
12. P0 正式计算后端为 Android Vulkan GPU 与 iOS Metal GPU；架构从 P0 支持多后端，NPU/ANE 按厂商在 P1/P2 逐项实现和认证，不宣传“一次适配全部 NPU”。

## 18. 依据与技术基线

- MLC 模型编译由模型架构、量化、上下文/内存配置和目标平台共同决定；移动部署需要转换权重并编译模型库。
- MLC 官方 Android/iOS 流程使用 `mlc-package-config.json` 和 `mlc_llm package` 生成平台运行库、模型库与可选权重包。
- MLC 已支持架构上的新权重变体与全新模型架构是两类工作；后者需要新增模型定义与编译实现。
- 本 PRD 因此采用“稳定公共 API + 逻辑模型 ID + manifest + 预编译模型库 + 独立权重制品 + 认证矩阵”，而不是任意权重动态加载。

官方基线资料：

- MLC LLM Compile Model Libraries
- MLC LLM Package Libraries and Weights
- MLC LLM Android SDK
- MLC LLM iOS Swift SDK
- MLC LLM Define New Model Architectures

## 19. 确认门

只有用户明确回复“确认 PRD，可以开始编码”或提出修改并再次确认后，才进入实现。确认即接受第 17 节默认决策，除非回复逐项覆盖。
