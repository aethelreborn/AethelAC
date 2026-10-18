#!/usr/bin/env python3
"""TEMPLATE: per-stage config + punishment editor.

USAGE: copy to /tmp/opencode/add<stage>.py, adjust:
  - KEYS: the new YAML block (comment + keys, 4-space indent).
  - ANCHOR: the existing key to insert AFTER (must exist in all 13 files).
  - OLD_VER/NEW_VER: config-version bump.
  - PUN_ANCHOR/PUN_NAME/PUN_GROUP: punishment binding.
Run from repo root: python3 /tmp/opencode/add<stage>.py
Idempotent (SKIP if already present) and validates with yaml.safe_load.
"""
import glob
import yaml

KEYS = """    # <comment describing the knobs>
    my-check-key-a: 5
    my-check-key-b: 10.0
    my-check-key-c: 60.0
"""
ANCHOR = "previous-key-name:"        # insert KEYS after the line starting with this
OLD_VER = 52
NEW_VER = 53

changed_cfg = []
for path in sorted(glob.glob("common/src/main/resources/config/*.yml")):
    with open(path, encoding="utf-8") as f:
        text = f.read()
    if "my-check-key-a" in text:
        print(f"SKIP cfg (already present): {path}")
        continue
    lines = text.splitlines(keepends=True)
    idx = next(i for i, l in enumerate(lines) if l.strip().startswith(ANCHOR))
    lines[idx] = lines[idx] + KEYS
    new = "".join(lines).replace(f"config-version: {OLD_VER}", f"config-version: {NEW_VER}")
    parsed = yaml.safe_load(new)
    assert parsed["config-version"] == NEW_VER, path
    assert parsed["<section>"]["my-check-key-a"] == 5, path
    with open(path, "w", encoding="utf-8") as f:
        f.write(new)
    changed_cfg.append(path)

PUN_ANCHOR = '- "PreviousCheck"'     # exact stripped line to insert after
PUN_NAME = "MyCheck"
PUN_GROUP = "Misc"                   # Misc / Combat / AimHeuristics / Autoclicker

changed_pun = []
for path in sorted(glob.glob("common/src/main/resources/punishments/*.yml")):
    with open(path, encoding="utf-8") as f:
        text = f.read()
    if f'"{PUN_NAME}"' in text:
        print(f"SKIP pun (already present): {path}")
        continue
    lines = text.splitlines(keepends=True)
    idx = next(i for i, l in enumerate(lines) if l.strip() == PUN_ANCHOR)
    lines[idx] = lines[idx] + f'      - "{PUN_NAME}"\n'
    new = "".join(lines)
    parsed = yaml.safe_load(new)
    assert PUN_NAME in parsed["Punishments"][PUN_GROUP]["checks"], path
    with open(path, "w", encoding="utf-8") as f:
        f.write(new)
    changed_pun.append(path)

print(f"changed {len(changed_cfg)} configs, {len(changed_pun)} punishment files")
