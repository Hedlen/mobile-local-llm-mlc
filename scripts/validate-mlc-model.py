#!/usr/bin/env python3
"""Validate every weight shard declared by an MLC tensor-cache.json."""

import argparse
import hashlib
import json
from pathlib import Path


def md5(path: Path) -> str:
    value = hashlib.md5()  # nosec: upstream tensor-cache uses MD5 for corruption checks
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("model", type=Path)
    args = parser.parse_args()
    records = json.loads((args.model / "tensor-cache.json").read_text(encoding="utf-8"))["records"]
    missing = []
    corrupt = []
    total = 0
    for record in records:
        path = args.model / record["dataPath"]
        if not path.is_file():
            missing.append(record["dataPath"])
            continue
        total += path.stat().st_size
        expected = record.get("md5sum")
        if expected and md5(path) != expected:
            corrupt.append(record["dataPath"])
    if missing or corrupt:
        raise SystemExit(f"invalid: missing={missing}, corrupt={corrupt}")
    print(f"valid: {len(records)} shards, {total} bytes")


if __name__ == "__main__":
    main()
