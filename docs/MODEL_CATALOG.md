# 多模型制品与目录

阶段 A 认证同一 Qwen2.5/q4f16_1 家族的 0.5B、1.5B、3B 三档模型。Android
运行库通过 `android-mvp/mlc-package-config.multi-model.json` 一次编译三种模型逻辑，
权重仍在安装后下载，不进入 Git 或 APK。

## 生成运行库

在已配置 MLC、TVM、NDK 的 WSL 环境中：

```bash
export PACKAGE_CONFIG=/mnt/e/data/code/android_llm/android-mvp/mlc-package-config.multi-model.json
./scripts/package-android-wsl.sh
```

不设置 `PACKAGE_CONFIG` 时继续使用已完成真机认证的单模型配置。多模型首次打包需要
下载并编译约 0.5B、1.5B、3B 三套制品，耗时和磁盘需求明显高于单模型构建。

网络不稳定时可设置 `HF_ENDPOINT=https://hf-mirror.com`。项目 MLC 补丁为每个分片
增加了指数退避重试；下载后可运行 `scripts/validate-mlc-model.py MODEL_DIR`，逐项
验证 `tensor-cache.json` 声明的权重分片。

## 发布模型目录

生产环境应把每个准备好的 MLC 模型目录上传到自有 HTTPS CDN。先创建一个不提交
到 Git 的 spec，其中为每个模型填写实际目录、CDN 地址、`modelLib`、锁定的上游
revision、内存估算和上下文长度，然后运行：

```bash
python scripts/generate-model-catalog.py \
  --spec /secure/path/catalog-spec.json \
  --output /publish/model-catalog.json
```

生成器会枚举全部 tokenizer、配置和权重分片，记录文件大小与 SHA-256。SDK 的
`LocalLlmModelManager` 只接受 HTTPS 和完整 SHA-256，下载到 `.part` 后校验，再原子
发布；损坏文件不会被加载。

宿主 App 的基本流程：

```kotlin
val catalog = LocalLlmModelCatalog.parse(catalogJson)
val manager = LocalLlmModelManager(filesDir.resolve("models"), catalog)
val installed = manager.install("local/qwen2.5-1.5b-instruct@1")
client.load(installed)
```

切换模型时再次调用 `client.load(manager.resolveForLoad(id))`，SDK 会先卸载旧模型。
删除使用 `manager.delete(id)`；正在生成时必须先取消或等待终态。

## 安全边界

- catalog 进入正式发布前还需增加 Ed25519 签名；阶段 A 已完成逐文件 SHA-256。
- `sourceRevision` 必须是锁定 revision，不能填写 `main`。
- CDN 更新文件时必须发布新模型版本，不得原地替换同一逻辑版本。
- `estimatedMemoryBytes` 是下载前的保守筛选值，阶段 B 将由真机数据校准。
