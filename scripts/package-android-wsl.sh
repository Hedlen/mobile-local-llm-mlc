#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/.." && pwd)"
mlc_source="$repo_root/vendor/mlc-llm"
android_project="$repo_root/android-mvp"
python_bin="${MLC_PYTHON:?Set MLC_PYTHON to the Python 3.12 executable with mlc_llm}"
ndk_root="${ANDROID_NDK:?Set ANDROID_NDK to Android NDK r27}"
tvm_build="${TVM_BUILD:?Set TVM_BUILD to the TVM build directory}"
model_url="HF://mlc-ai/Qwen2.5-0.5B-Instruct-q4f16_1-MLC"
local_model="${LOCAL_MODEL:?Set LOCAL_MODEL to the converted MLC model directory}"
model_lib="${MODEL_LIB:?Set MODEL_LIB to the compiled Android model library archive}"
package_config="/tmp/android-mlc-package-config.json"

test -x "$python_bin"
test -x "$ndk_root/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang"
test -f "$tvm_build/lib/libtvm_compiler.so"
test -f "$local_model/mlc-chat-config.json"
test -f "$model_lib"

apply_patch_once() {
  local source_dir="$1"
  local patch_file="$2"
  if git -C "$source_dir" apply --reverse --check "$patch_file" >/dev/null 2>&1; then
    return
  fi
  git -C "$source_dir" apply --check "$patch_file"
  git -C "$source_dir" apply "$patch_file"
}

apply_patch_once "$mlc_source" "$repo_root/patches/mlc-llm-mvp.patch"
apply_patch_once "$mlc_source/3rdparty/tvm" "$repo_root/patches/tvm-windows-mvp.patch"

export ANDROID_NDK="$ndk_root"
export PATH="${CARGO_HOME:-$HOME/.cargo}/bin:$PATH"
export TVM_NDK_CC="$ndk_root/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang"
export TVM_SOURCE_DIR="$mlc_source/3rdparty/tvm"
export MLC_LLM_SOURCE_DIR="$mlc_source"
export MLC_LLM_HOME="${MLC_LLM_HOME:-$HOME/.cache/mlc_llm}"
export MLC_LIBRARY_PATH="${MLC_LIBRARY_PATH:-$($python_bin -c 'import mlc_llm, pathlib; print(pathlib.Path(mlc_llm.__file__).parent)')}"
export TVM_LIBRARY_PATH="$tvm_build/lib"
export LD_LIBRARY_PATH="$tvm_build/lib:${LD_LIBRARY_PATH:-}"
export PYTHONPATH="$mlc_source/3rdparty/tvm/python:$mlc_source/python"
export SKIP_LOADING_MLCLLM_SO="1"
export MLC_REMOTE_MODEL_URL="$model_url"

sed -e "s#$model_url#$local_model#" \
    -e "s#../.tooling/model-libs/qwen2.5-0.5b-q4f16_1-android.tar#$model_lib#" \
    "$android_project/mlc-package-config.json" > "$package_config"

cd "$android_project"
"$python_bin" -m mlc_llm package \
    --package-config "$package_config" \
    --output "$android_project/dist"
