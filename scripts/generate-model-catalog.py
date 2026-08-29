#!/usr/bin/env python3
"""Generate a signed-ready SDK catalog from prepared MLC model directories."""

import argparse
import hashlib
import json
from pathlib import Path


def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--spec", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    spec = json.loads(args.spec.read_text(encoding="utf-8"))
    models = []
    for item in spec["models"]:
        directory = Path(item.pop("directory")).resolve()
        base_url = item.pop("baseUrl").rstrip("/")
        artifacts = []
        for path in sorted(p for p in directory.rglob("*") if p.is_file()):
            relative = path.relative_to(directory).as_posix()
            artifacts.append({
                "path": relative,
                "url": f"{base_url}/{relative}",
                "sha256": digest(path),
                "bytes": path.stat().st_size,
            })
        model = dict(item)
        model["downloadBytes"] = sum(a["bytes"] for a in artifacts)
        model["artifacts"] = artifacts
        models.append(model)
    output = {
        "schemaVersion": 1,
        "runtimeRevision": spec["runtimeRevision"],
        "models": models,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
