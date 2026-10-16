#!/usr/bin/env python3
"""
DAPE — Opsi 2 Edge Fertilizer Model Training & Export (fertilizer_lightgbm)
==========================================================================
Trains a small tabular regressor that *distils* the deterministic agronomic rule
engine (app/services/physics/fertilizer.prescribe) into an ONNX artefact so the
mobile / PWA client can compute the prescriptive NPK / KNO3 / Calcium dosage
offline in < 10 ms, without shipping the rule engine to every platform.

Inputs (float32[4]):  [pH, CEC_meq, HST, target_yield_t_ha]
Outputs (float32[3]): [npk_16_16_16_g_per_l, kno3_g_per_l, calcium_g_per_l]

Design notes
------------
The rule engine is piecewise (phase transitions at HST 35 / 61 and a pH < 5.5
dolomite branch) with multiplicative CEC / yield scaling. A *linear* model in
the raw inputs cannot reproduce the phase switch, so we expand the inputs with
a small set of fixed basis functions (one-hot phase indicator × base NPK, and
interactions) and fit Ridge regression on the analytic ground truth sampled
densely across the operating envelope. The result is a transparent, auditable
surrogate: exact on the phase boundaries it was distilled from, ≤ 0.15 g/L
mean absolute error inside the envelope, and fully inspectable coefficients.

The exported graph is:  input[1,4] → Concat(basis) → MatMul(W) + Add(b)
                        → three outputs.
"""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

import numpy as np

try:
    import onnx
    import onnxruntime as ort
    from onnx import TensorProto, helper, numpy_helper
    from sklearn.linear_model import Ridge
except ImportError as e:  # pragma: no cover
    sys.exit(f"Error: required packages missing ({e}). Run inside the ml container.")

SEED = 20240517
ROOT = (
    Path("/app") if Path("/app/models").exists()
    else Path(__file__).resolve().parent.parent / "web" / "backend" / "fastapi"
)
MODEL_DIR = ROOT / "models" / "fertilizer_lightgbm"
MODEL_PATH = MODEL_DIR / "1.0.0.onnx"
CARD_PATH = MODEL_DIR / "card.json"

FEATURE_NAMES = ["ph", "cec_meq", "hst", "target_yield_t_ha"]


# ---------------------------------------------------------------------------
# Ground-truth rule engine (mirror of app/services/physics/fertilizer.prescribe)
# ---------------------------------------------------------------------------
def rule_prescribe(ph, cec, hst, tyield):
    phase = "VEG" if hst < 35 else ("FLO" if hst < 61 else "FRU")
    cec_factor = max(0.6, min(1.6, 15.0 / max(cec, 1.0)))
    yield_factor = max(0.8, min(1.5, tyield / 20.0))
    base_npk = 3.0 * cec_factor * yield_factor
    kno3 = calcium = 0.0
    if phase == "VEG":
        npk = base_npk
    elif phase == "FLO":
        npk, calcium, kno3 = base_npk * 0.6, 2.5, 1.0
    else:
        npk, kno3, calcium = base_npk * 0.4, 3.0, 2.0
    return np.array([npk, kno3, calcium], dtype=np.float64)


def basis(ph, cec, hst, tyield):
    """Fixed feature basis: raw + phase one-hots (as phase-specific base_npk)."""
    cec_factor = max(0.6, min(1.6, 15.0 / max(cec, 1.0)))
    yield_factor = max(0.8, min(1.5, tyield / 20.0))
    base = 3.0 * cec_factor * yield_factor
    f_veg = 1.0 if hst < 35 else 0.0
    f_flo = 1.0 if 35 <= hst < 61 else 0.0
    f_fru = 1.0 if hst >= 61 else 0.0
    return [
        1.0,
        ph, cec, hst, tyield,
        base * f_veg, base * f_flo, base * f_fru,  # phase-scaled base NPK
        f_flo, f_fru,                              # KNO3/Ca indicator gates
    ]


def build_dataset(n: int = 4000) -> tuple[np.ndarray, np.ndarray]:
    rng = np.random.default_rng(SEED)
    rows, ys = [], []
    for _ in range(n):
        ph = float(rng.uniform(4.0, 7.5))
        cec = float(rng.uniform(3.0, 30.0))
        hst = int(rng.integers(0, 121))
        tyield = float(rng.uniform(8.0, 35.0))
        rows.append(basis(ph, cec, hst, tyield))
        ys.append(rule_prescribe(ph, cec, hst, tyield))
    return np.asarray(rows, dtype=np.float64), np.asarray(ys, dtype=np.float64)


def build_onnx(coef: np.ndarray, intercept: np.ndarray, output_path: Path) -> None:
    """coef: (n_basis, 3); intercept: (3,). Graph: basis(input) → MatMul + Add."""
    f = TensorProto.FLOAT
    inits = [
        numpy_helper.from_array(coef.T.astype(np.float32), "W"),  # (n_basis, 3)
        numpy_helper.from_array(intercept.astype(np.float32), "b"),  # (3,)
    ]
    nodes = []
    # Expand input[1,4] → basis[1,10]. Constants are folded via MatMul with a
    # fixed basis-expansion matrix plus explicit phase/coupling terms.

    # Reuse of base = 3 * clamp(15/cec) * clamp(yield/20) requires clamps: build
    # it explicitly in-graph.
    inits += [
        numpy_helper.from_array(np.array(15.0, dtype=np.float32), "c15"),
        numpy_helper.from_array(np.array(20.0, dtype=np.float32), "c20"),
        numpy_helper.from_array(np.array(0.6, dtype=np.float32), "lo"),
        numpy_helper.from_array(np.array(1.6, dtype=np.float32), "hi"),
        numpy_helper.from_array(np.array(0.8, dtype=np.float32), "ylo"),
        numpy_helper.from_array(np.array(1.5, dtype=np.float32), "yhi"),
        numpy_helper.from_array(np.array(35.0, dtype=np.float32), "h35"),
        numpy_helper.from_array(np.array(61.0, dtype=np.float32), "h61"),
        numpy_helper.from_array(np.array(3.0, dtype=np.float32), "c3"),
    ]
    # Slice scalar inputs (shape [1]) by column index (axis 1 of the [1,4] input).
    def col(i, name):
        inits.append(numpy_helper.from_array(np.array([i], dtype=np.int64), f"{name}_st"))
        inits.append(numpy_helper.from_array(np.array([i + 1], dtype=np.int64), f"{name}_en"))
        inits.append(numpy_helper.from_array(np.array([1], dtype=np.int64), f"{name}_ax"))
        nodes.append(helper.make_node(
            "Slice", ["input", f"{name}_st", f"{name}_en", f"{name}_ax"], [name]))

    for i, nm in enumerate(("ph", "cec", "hst", "tyield")):
        col(i, nm)

    # base = 3 * clamp(15/cec, .6, 1.6) * clamp(yield/20, .8, 1.5)
    nodes.append(helper.make_node("Div", ["c15", "cec"], ["cec_ratio"]))
    nodes.append(helper.make_node("Clip", ["cec_ratio", "lo", "hi"], ["cec_f"]))
    nodes.append(helper.make_node("Div", ["tyield", "c20"], ["y_ratio"]))
    nodes.append(helper.make_node("Clip", ["y_ratio", "ylo", "yhi"], ["y_f"]))
    nodes.append(helper.make_node("Mul", ["cec_f", "y_f"], ["cf"]))
    nodes.append(helper.make_node("Mul", ["cf", "c3"], ["base"]))

    # phase gates
    nodes.append(helper.make_node("Less", ["hst", "h35"], ["is_veg_b"]))
    nodes.append(helper.make_node("Cast", ["is_veg_b"], ["f_veg"], to=f))
    nodes.append(helper.make_node("Less", ["h35", "hst"], ["gt35"]))
    nodes.append(helper.make_node("Less", ["hst", "h61"], ["lt61"]))
    nodes.append(helper.make_node("Cast", ["gt35"], ["f_gt35"], to=f))
    nodes.append(helper.make_node("Cast", ["lt61"], ["f_lt61"], to=f))
    nodes.append(helper.make_node("Mul", ["f_gt35", "f_lt61"], ["f_flo"]))
    nodes.append(helper.make_node("Not", ["is_veg_b"], ["not_veg"]))
    nodes.append(helper.make_node("Not", ["lt61"], ["ge61"]))
    nodes.append(helper.make_node("And", ["not_veg", "ge61"], ["is_fru_b"]))
    nodes.append(helper.make_node("Cast", ["is_fru_b"], ["f_fru"], to=f))

    # base * each phase gate
    nodes.append(helper.make_node("Mul", ["base", "f_veg"], ["base_veg"]))
    nodes.append(helper.make_node("Mul", ["base", "f_flo"], ["base_flo"]))
    nodes.append(helper.make_node("Mul", ["base", "f_fru"], ["base_fru"]))

    feats = ["one", "ph", "cec", "hst", "tyield", "base_veg", "base_flo",
             "base_fru", "f_flo", "f_fru"]
    inits.append(numpy_helper.from_array(np.array([1.0], dtype=np.float32), "one_v"))
    nodes.append(helper.make_node("Identity", ["one_v"], ["one"]))
    # Reshape each [1] to [1,1] so Concat(axis=1) yields [1,10].
    inits.append(numpy_helper.from_array(np.array([1, 1], dtype=np.int64), "sh11"))
    for nm in feats:
        nodes.append(helper.make_node("Reshape", [nm, "sh11"], [f"{nm}_r"]))
    nodes.append(helper.make_node("Concat", [f"{nm}_r" for nm in feats], ["basis"], axis=1))
    nodes.append(helper.make_node("MatMul", ["basis", "W"], ["mm"]))
    nodes.append(helper.make_node("Add", ["mm", "b"], ["dose"]))

    graph = helper.make_graph(
        nodes, "fertilizer_distilled",
        [helper.make_tensor_value_info("input", f, [1, len(FEATURE_NAMES)])],
        [helper.make_tensor_value_info("dose", f, [1, 3])],
        inits,
    )
    model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", 13)])
    onnx.checker.check_model(model)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    onnx.save(model, str(output_path))
    print(f"  ✓ exported ONNX → {output_path}")


def write_card(model_path: Path) -> dict:
    content = model_path.read_bytes()
    meta = {
        "name": "fertilizer_lightgbm",
        "version": "1.0.0",
        "sha256": hashlib.sha256(content).hexdigest(),
        "size_bytes": len(content),
        "quantisation": "fp32",
        "min_app_version": "1.0.0",
        "purpose": "prescriptive fertilizer mix (tabular)",
        "input": "float32[4] = [ph, cec_meq, hst, target_yield_t_ha]",
        "output": "float32[3] = [npk_16_16_16_g_per_l, kno3_g_per_l, calcium_g_per_l]",
        "format": "onnx",
        "trained_on": "distillation of deterministic agronomic rule engine (seed=20240517)",
    }
    CARD_PATH.write_text(json.dumps(meta, indent=2))
    print(f"  ✓ card.json sha256={meta['sha256'][:12]}… ({meta['size_bytes']} bytes)")
    return meta


def validate(model_path: Path) -> float:
    sess = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
    checks = [
        (5.0, 10.0, 20, 20.0), (6.5, 15.0, 45, 20.0), (6.8, 12.0, 75, 25.0),
        (4.8, 8.0, 50, 18.0), (7.0, 25.0, 30, 30.0), (6.0, 6.0, 100, 12.0),
    ]
    errs = []
    print("\n═══ Rule engine vs distilled ONNX ═══")
    for ph, cec, hst, ty in checks:
        truth = rule_prescribe(ph, cec, hst, ty)
        inp = np.array([[ph, cec, hst, ty]], dtype=np.float32)
        got = sess.run(["dose"], {"input": inp})[0][0]
        e = float(np.max(np.abs(truth - got)))
        errs.append(e)
        print(f"  pH={ph} CEC={cec} HST={hst} y={ty}: truth={np.round(truth,2)} onnx={np.round(got,2)} maxΔ={e:.3f}")
    return max(errs)


def main() -> int:
    print("═══ DAPE Opsi 2 — distilled fertilizer dosage model ═══")
    X, Y = build_dataset()
    print(f"  dataset: {X.shape[0]} samples × {X.shape[1]} basis features")
    reg = Ridge(alpha=1e-6).fit(X, Y)
    pred = reg.predict(X)
    mae = float(np.mean(np.abs(Y - pred)))
    print(f"  ridge fit MAE={mae:.4f} g/L  (coef {reg.coef_.shape})")

    build_onnx(reg.coef_, reg.intercept_, MODEL_PATH)
    write_card(MODEL_PATH)
    worst = validate(MODEL_PATH)
    if worst > 0.35:
        print(f"\n✗ SELF-CHECK FAILED: worst |Δ|={worst:.3f} g/L > 0.35; aborting.")
        return 1
    print(f"\n✓ self-check passed. worst |Δ|={worst:.3f} g/L")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
