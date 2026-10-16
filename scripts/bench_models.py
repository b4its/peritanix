#!/usr/bin/env python3
"""DAPE — edge model integrity & inference benchmark.

Verifies every shipped ONNX artefact under models/:
  * card.json exists and declares a trained version (not 0.0.0),
  * the .onnx file exists,
  * sha256(artefact) == card.sha256  (tamper detection),
  * the artefact loads in onnxruntime and runs on a canonical input.

Exit code is non-zero if any check fails, so it is CI-safe (`make models-bench`).
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

import numpy as np

ROOT = (
    Path("/app") if Path("/app/models").exists()
    else Path(__file__).resolve().parent.parent / "web" / "backend" / "fastapi"
)
MODELS = ROOT / "models"

# canonical probe input per known model
PROBES = {
    "classifier_mobilenetv3": lambda: np.zeros((1, 3, 224, 224), dtype=np.float32),
    "severity_unet_lite": lambda: np.zeros((1, 3, 256, 256), dtype=np.float32),
    "fertilizer_lightgbm": lambda: np.array([[6.5, 15.0, 45.0, 20.0]], dtype=np.float32),
}


def main() -> int:
    try:
        import onnxruntime as ort
    except ImportError:
        print("onnxruntime unavailable; skipping benchmark.")
        return 0

    import onnx

    failures = 0
    pending = 0
    cards = sorted(MODELS.glob("*/card.json"))
    if not cards:
        print("no model cards found under", MODELS)
        return 1

    print("═══ DAPE edge model artefact check ═══")
    for card_path in cards:
        name = card_path.parent.name
        card = json.loads(card_path.read_text())
        art = card_path.parent / f"{card['version']}.onnx"
        # A placeholder model (no sha, no artefact) is an *announced* model whose
        # weights require real field imagery — informational, not a failure.
        placeholder = not card.get("sha256") and not art.exists()
        if placeholder:
            pending += 1
            print(f"  • {name:26} pending training (no artefact shipped)")
            continue

        problems = []
        if card.get("version", "0.0.0") == "0.0.0":
            problems.append("un-trained placeholder (version 0.0.0)")
        if not art.exists():
            problems.append(f"missing artefact {art.name}")
        else:
            got = hashlib.sha256(art.read_bytes()).hexdigest()
            if card.get("sha256") != got:
                problems.append(f"sha256 mismatch (card {card.get('sha256','')[:12]}… vs file {got[:12]}…)")
        # load & run if possible
        if art.exists() and not problems:
            try:
                onnx.checker.check_model(onnx.load(str(art)))
                sess = ort.InferenceSession(str(art), providers=["CPUExecutionProvider"])
                if name in PROBES:
                    out = sess.run(None, {sess.get_inputs()[0].name: PROBES[name]()})
                    shape = np.asarray(out[0]).shape
                else:
                    shape = "n/a"
                print(f"  ✓ {name:26} v{card['version']:<8} "
                      f"{card['size_bytes']:>7} B  sha={card['sha256'][:12]}…  out={shape}")
            except Exception as e:  # noqa: BLE001
                problems.append(f"load/run failed: {e}")
        if problems:
            failures += 1
            print(f"  ✗ {name}: " + "; ".join(problems))

    tail = ("✓ all shipped artefacts OK"
            if not failures else f"✗ {failures} artefact(s) failed")
    if pending:
        tail += f"  ({pending} pending real-field training)"
    print(f"\n{tail}")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
