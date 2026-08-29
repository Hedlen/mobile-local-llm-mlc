# Upstream compatibility patches

Apply these patches after cloning submodules and before building TVM/MLC:

```bash
git -C vendor/mlc-llm apply ../../patches/mlc-llm-mvp.patch
git -C vendor/mlc-llm/3rdparty/tvm apply ../../../../patches/tvm-windows-mvp.patch
```

The packaging script detects an already-applied patch. Patches contain the
validated Android cancellation binding and compatibility changes required by
the locked MLC/TVM revisions.
