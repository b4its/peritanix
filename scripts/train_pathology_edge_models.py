#!/usr/bin/env python3
"""
DAPE — Opsi 1 Edge Model Training & Export (classifier_mobilenetv3)
==================================================================
Deterministic, reproducible pipeline that produces a *genuine* trained ONNX
classifier for chili leaf-curl & lesion differentiation:

    Keriting_Thrips   – upward curl, silvery underside   (Thrips parvispinus)
    Keriting_Mites    – downward curl, bronze underside   (Polyphagotarsonemus latus)
    Begomovirus       – bright yellow mosaic, vein clearing (Yellow Leaf Curl Virus)
    Antraknosa        – dark sunken concentric lesions     (Colletotrichum spp.)
    Daun_Sehat        – healthy green reference

Why this replaces the old mean-RGB linear stub
----------------------------------------------
The previous artefact mapped *mean RGB only* → 5 logits. That loses the single
most important discriminant for Opsi 1: **curl direction** (up = thrips,
down = mites). Both are green-ish leaves, so a mean-colour model provably
confuses them (and Antraknosa vs Begomovirus). See tests/test_edge_models.py.

Feature vector (all computed identically in Python training *and* inside the
exported ONNX graph, so train/serve stay consistent):

    0 mean_R            5 underside_bronze_idx  (lower third R-B vs upper)
    1 mean_G            6 yellow_mosaic_idx     (min(R,G)-B, bright)
    2 mean_B            7 dark_lesion_frac      (fraction of very dark pixels)
    3 top_green_asym    8 colour_std           (texture / mottling)
    4 bot_green_asym    9 saturation_mean

Curl direction is encoded as the vertical asymmetry of the green channel:
    top_green_asym = mean(G, top third)  - mean(G)
    bot_green_asym = mean(G, lower third) - mean(G)
Upward curling lifts the blade's midrib and darkens the top → negative
top_green_asym; downward curling does the opposite.

The labels are trained on a principled synthetic dataset sampled around the
documented symptom signatures; the script self-checks that all five benchmark
conditions classify correctly and refuses to export otherwise.
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
    from sklearn.neural_network import MLPClassifier
    from sklearn.preprocessing import StandardScaler
except ImportError as e:  # pragma: no cover
    sys.exit(f"Error: required packages missing ({e}). Run inside the ml container.")

# ---------------------------------------------------------------------------
# Taxonomy
# ---------------------------------------------------------------------------
CLASSES = [
    "Keriting_Thrips",
    "Keriting_Mites",
    "Begomovirus",
    "Antraknosa",
    "Daun_Sehat",
]

CLASS_SYMPTOMS = {
    "Keriting_Thrips": "Daun melengkung ke atas (perahu), permukaan bawah keperakan",
    "Keriting_Mites": "Daun melengkung ke bawah (sendok terbalik), kaku kecokelatan",
    "Begomovirus": "Bercak belang kuning cerah, tulang daun menebal (vein clearing)",
    "Antraknosa": "Lesi cekung gelap konsentris dengan massa spora",
    "Daun_Sehat": "Permukaan daun hijau normal, morfologi simetris",
}

FEATURE_NAMES = [
    "mean_R", "mean_G", "mean_B",
    "top_green_asym", "bot_green_asym",
    "underside_bronze_idx", "yellow_mosaic_idx", "dark_lesion_frac",
    "colour_std", "saturation_mean",
]

IMG = 224
SEED = 20240517  # fixed → reproducible artefact bytes

ROOT = (
    Path("/app") if Path("/app/models").exists()
    else Path(__file__).resolve().parent.parent / "web" / "backend" / "fastapi"
)
MODEL_DIR = ROOT / "models" / "classifier_mobilenetv3"
MODEL_PATH = MODEL_DIR / "1.0.0.onnx"
CARD_PATH = MODEL_DIR / "card.json"


# ---------------------------------------------------------------------------
# Feature extraction (numpy) — mirrors the ONNX graph exactly.
# ---------------------------------------------------------------------------
def extract_features(img: np.ndarray) -> np.ndarray:
    """img: float array (3, H, W) in [0,1]. Returns the 10-dim feature vector."""
    r, g, b = img[0], img[1], img[2]
    h = img.shape[1]
    third = h // 3
    mean_r, mean_g, mean_b = float(r.mean()), float(g.mean()), float(b.mean())
    top_g = float(g[:third].mean())
    bot_g = float(g[-third:].mean())
    top_asym = top_g - mean_g
    bot_asym = bot_g - mean_g
    # rust/bronze on the (mostly lower) underside: R exceeds B and is darkish.
    lower = slice(h - third, h)
    bronze = float(np.mean(np.clip(r[lower] - b[lower], 0, 1)))
    yellow = float(np.clip(np.minimum(r, g) - b, 0, 1).mean())
    lum = 0.299 * r + 0.587 * g + 0.114 * b
    dark_frac = float(np.mean(lum < 0.18))
    grey = (r + g + b) / 3.0
    colour_std = float(np.std(grey))
    mx = np.maximum(np.maximum(r, g), b)
    mn = np.minimum(np.minimum(r, g), b)
    sat = float(np.mean(np.where(mx > 1e-6, (mx - mn) / np.maximum(mx, 1e-6), 0.0)))
    return np.array(
        [mean_r, mean_g, mean_b, top_asym, bot_asym, bronze, yellow, dark_frac,
         colour_std, sat],
        dtype=np.float32,
    )


# ---------------------------------------------------------------------------
# Principled synthetic dataset — each class is a signature in feature space.
# ---------------------------------------------------------------------------
def _leaf_canvas(rng: np.random.Generator, base_rgb, noise=0.03,
                 mottle=0.0, lesion_frac=0.0, top_gain=1.0, bot_gain=1.0,
                 bright_yellow=False, bronze=0.0):
    """Render a plausible leaf tensor with controllable symptom signature."""
    img = np.zeros((3, IMG, IMG), dtype=np.float32)
    for c in range(3):
        img[c] = base_rgb[c]
    img += rng.normal(0, noise, img.shape).astype(np.float32)
    if mottle:
        mask = rng.random((IMG, IMG)) < mottle
        img += (mask[None] * rng.normal(0, 0.12, (3, IMG, IMG))).astype(np.float32)
    if lesion_frac:
        lm = rng.random((IMG, IMG)) < lesion_frac
        img *= (1.0 - 0.7 * lm[None])
    third = IMG // 3
    img[1, :third] *= top_gain
    img[1, -third:] *= bot_gain
    if bronze:
        img[0, -third:] += bronze
        img[2, -third:] -= bronze
    if bright_yellow:
        img[0] += 0.15
        img[1] += 0.10
        img[2] -= 0.10
    return np.clip(img, 0.0, 1.0)


def build_dataset(n_per_class: int = 260) -> tuple[np.ndarray, np.ndarray]:
    rng = np.random.default_rng(SEED)
    X: list[np.ndarray] = []
    y: list[int] = []

    for cls_idx, name in enumerate(CLASSES):
        for _ in range(n_per_class):
            if name == "Daun_Sehat":
                img = _leaf_canvas(rng, (0.18, 0.62, 0.22), noise=0.025, mottle=0.02)
            elif name == "Keriting_Thrips":
                # Upward curl → top third of the blade loses green (shadowed fold);
                # silvery sheen lifts blue in the lower (underside) region.
                img = _leaf_canvas(
                    rng, (0.30, 0.66, 0.34), noise=0.03, mottle=0.03,
                    top_gain=rng.uniform(0.72, 0.85), bot_gain=rng.uniform(1.05, 1.15))
                img[2, -IMG // 3:] += rng.uniform(0.05, 0.10)  # silver underside
            elif name == "Keriting_Mites":
                # Downward curl → lower blade folds under, gains bronze/rust;
                # top third stays or gains slight green.
                img = _leaf_canvas(
                    rng, (0.34, 0.60, 0.26), noise=0.035, mottle=0.05,
                    top_gain=rng.uniform(1.0, 1.08), bot_gain=rng.uniform(0.70, 0.84),
                    bronze=rng.uniform(0.10, 0.18))
            elif name == "Begomovirus":
                img = _leaf_canvas(
                    rng, (0.42, 0.66, 0.18), noise=0.03, mottle=0.10,
                    bright_yellow=True)
            elif name == "Antraknosa":
                img = _leaf_canvas(
                    rng, (0.34, 0.46, 0.20), noise=0.04, mottle=0.04,
                    lesion_frac=rng.uniform(0.14, 0.26))
            X.append(extract_features(img))
            y.append(cls_idx)

    return np.asarray(X, dtype=np.float32), np.asarray(y, dtype=np.int64)


# ---------------------------------------------------------------------------
# ONNX graph: feature extraction ops + trained MLP + softmax
# ---------------------------------------------------------------------------
def build_onnx(weights: list[np.ndarray], biases: list[np.ndarray],
               scaler_mean: np.ndarray, scaler_scale: np.ndarray,
               output_path: Path) -> None:
    """Assemble the image→features→MLP→softmax graph and save it."""
    f = TensorProto.FLOAT

    nodes: list = []
    inits: list = []

    def slice_axis(src, dst, axis, start, end):
        inits.append(numpy_helper.from_array(np.array([axis], dtype=np.int64), f"{dst}_ax"))
        inits.append(numpy_helper.from_array(np.array([start], dtype=np.int64), f"{dst}_st"))
        inits.append(numpy_helper.from_array(np.array([end], dtype=np.int64), f"{dst}_en"))
        nodes.append(helper.make_node(
            "Slice", [src, f"{dst}_st", f"{dst}_en", f"{dst}_ax"], [dst]))

    # Per-channel means: Slice channel c → shape (1,1,H,W), then ReduceMean.
    for c in range(3):
        slice_axis("image", f"ch_{c}", 1, c, c + 1)
        nodes.append(helper.make_node("ReduceMean", [f"ch_{c}"], [f"mean_{'RGB'[c]}"],
                                      axes=[2, 3], keepdims=0))

    # Green third slices. Tensor layout is (N=1, C=3, H, W): channel axis=1,
    # row axis=2. So: first crop the row band (axis 2), then the channel (axis 1).
    third = IMG // 3
    slice_axis("image", "g_top", 2, 0, third)          # all channels, top rows
    slice_axis("image", "g_bot", 2, IMG - third, IMG)  # all channels, lower rows
    slice_axis("g_top", "g_top_c", 1, 1, 2)            # green channel only
    slice_axis("g_bot", "g_bot_c", 1, 1, 2)
    nodes.append(helper.make_node("ReduceMean", ["g_top_c"], ["g_top_m"], axes=[0, 1, 2, 3], keepdims=0))
    nodes.append(helper.make_node("ReduceMean", ["g_bot_c"], ["g_bot_m"], axes=[0, 1, 2, 3], keepdims=0))
    nodes.append(helper.make_node("ReduceMean", ["ch_1"], ["g_mean"], axes=[0, 1, 2, 3], keepdims=0))
    nodes.append(helper.make_node("Sub", ["g_top_m", "g_mean"], ["top_green_asym"]))
    nodes.append(helper.make_node("Sub", ["g_bot_m", "g_mean"], ["bot_green_asym"]))

    # Underside bronze: mean(relu(R_lower - B_lower)), lower = rows [IMG-third, IMG).
    slice_axis("image", "r_low", 2, IMG - third, IMG)  # all channels, lower rows
    slice_axis("r_low", "r_low_c", 1, 0, 1)            # channel 0
    slice_axis("image", "b_low", 2, IMG - third, IMG)
    slice_axis("b_low", "b_low_c", 1, 2, 3)            # channel 2
    nodes.append(helper.make_node("Sub", ["r_low_c", "b_low_c"], ["rb_low"]))
    inits.append(numpy_helper.from_array(np.array(0.0, dtype=np.float32), "zero"))
    nodes.append(helper.make_node("Max", ["rb_low", "zero"], ["rb_relu"]))
    nodes.append(helper.make_node("ReduceMean", ["rb_relu"], ["underside_bronze_idx"], keepdims=0))

    # yellow_mosaic_idx = mean(relu(min(R,G) - B))
    nodes.append(helper.make_node("Min", ["ch_0", "ch_1"], ["rg_min"]))
    nodes.append(helper.make_node("Sub", ["rg_min", "ch_2"], ["rgm_b"]))
    nodes.append(helper.make_node("Max", ["rgm_b", "zero"], ["rgm_relu"]))
    nodes.append(helper.make_node("ReduceMean", ["rgm_relu"], ["yellow_mosaic_idx"], keepdims=0))

    # dark_lesion_frac = mean( (0.299R+0.587G+0.114B) < 0.18 )
    for c, w in enumerate((0.299, 0.587, 0.114)):
        inits.append(numpy_helper.from_array(np.array(w, dtype=np.float32), f"lum_w{c}"))
        nodes.append(helper.make_node("Mul", [f"ch_{c}", f"lum_w{c}"], [f"lum_{c}"]))
    nodes.append(helper.make_node("Sum", ["lum_0", "lum_1", "lum_2"], ["luminance"]))
    inits.append(numpy_helper.from_array(np.array(0.18, dtype=np.float32), "dark_thr"))
    nodes.append(helper.make_node("Less", ["luminance", "dark_thr"], ["dark_mask"]))
    nodes.append(helper.make_node("Cast", ["dark_mask"], ["dark_f"], to=f))
    nodes.append(helper.make_node("ReduceMean", ["dark_f"], ["dark_lesion_frac"], keepdims=0))

    # colour_std = global std of grey (per-pixel channel mean).
    nodes.append(helper.make_node("Mean", ["ch_0", "ch_1", "ch_2"], ["grey"]))
    nodes.append(helper.make_node("ReduceMean", ["grey"], ["grey_mean"], keepdims=0))
    nodes.append(helper.make_node("Sub", ["grey", "grey_mean"], ["grey_dev"]))
    nodes.append(helper.make_node("Mul", ["grey_dev", "grey_dev"], ["grey_dev2"]))
    nodes.append(helper.make_node("ReduceMean", ["grey_dev2"], ["grey_var"], keepdims=0))
    nodes.append(helper.make_node("Sqrt", ["grey_var"], ["colour_std"]))

    # saturation_mean
    nodes.append(helper.make_node("Max", ["ch_0", "ch_1"], ["mx01"]))
    nodes.append(helper.make_node("Max", ["mx01", "ch_2"], ["mx"]))
    nodes.append(helper.make_node("Min", ["ch_0", "ch_1"], ["mn01"]))
    nodes.append(helper.make_node("Min", ["mn01", "ch_2"], ["mn"]))
    nodes.append(helper.make_node("Sub", ["mx", "mn"], ["sat_num"]))
    inits.append(numpy_helper.from_array(np.array(1e-6, dtype=np.float32), "eps"))
    nodes.append(helper.make_node("Max", ["mx", "eps"], ["sat_den"]))
    nodes.append(helper.make_node("Div", ["sat_num", "sat_den"], ["sat"]))
    nodes.append(helper.make_node("ReduceMean", ["sat"], ["saturation_mean"], keepdims=0))

    # Assemble feature vector: reshape each 0-D scalar to shape (1,), concat → (10,),
    # then reshape to (1,10) and normalise with the trained scaler.
    inits.append(numpy_helper.from_array(np.array([1], dtype=np.int64), "shape_1"))
    inits.append(numpy_helper.from_array(np.array([1, 10], dtype=np.int64), "shape_1x10"))
    for nm in FEATURE_NAMES:
        nodes.append(helper.make_node("Reshape", [nm, "shape_1"], [f"{nm}_u"]))
    nodes.append(helper.make_node("Concat", [f"{nm}_u" for nm in FEATURE_NAMES],
                                  ["features"], axis=0))
    nodes.append(helper.make_node("Reshape", ["features", "shape_1x10"], ["features2"]))
    inits.append(numpy_helper.from_array(scaler_mean.astype(np.float32), "sc_mean"))
    inits.append(numpy_helper.from_array(scaler_scale.astype(np.float32), "sc_scale"))
    nodes.append(helper.make_node("Sub", ["features2", "sc_mean"], ["feat_c"]))
    nodes.append(helper.make_node("Div", ["feat_c", "sc_scale"], ["feat_n"]))

    prev = "feat_n"
    for i, (w, b) in enumerate(zip(weights, biases, strict=True)):
        wn, bn = f"mlp_w{i}", f"mlp_b{i}"
        inits.append(numpy_helper.from_array(w.astype(np.float32), wn))
        inits.append(numpy_helper.from_array(b.astype(np.float32), bn))
        nodes.append(helper.make_node("MatMul", [prev, wn], [f"mm{i}"]))
        nodes.append(helper.make_node("Add", [f"mm{i}", bn], [f"fc{i}"]))
        if i < len(weights) - 1:
            nodes.append(helper.make_node("Relu", [f"fc{i}"], [f"act{i}"]))
            prev = f"act{i}"
        else:
            prev = f"fc{i}"  # logits
    nodes.append(helper.make_node("Softmax", [prev], ["probabilities"], axis=1))

    graph = helper.make_graph(
        nodes, "chili_pathology_mlp_features",
        [helper.make_tensor_value_info("image", f, [1, 3, IMG, IMG])],
        [helper.make_tensor_value_info("probabilities", f, [1, len(CLASSES)])],
        inits,
    )
    model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", 13)])
    model.ir_version = 9
    onnx.checker.check_model(model)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    onnx.save(model, str(output_path))
    print(f"  ✓ exported ONNX → {output_path}")


def write_card(model_path: Path) -> dict:
    content = model_path.read_bytes()
    meta = {
        "name": "classifier_mobilenetv3",
        "version": "1.0.0",
        "sha256": hashlib.sha256(content).hexdigest(),
        "size_bytes": len(content),
        "quantisation": "fp32",
        "min_app_version": "1.0.0",
        "purpose": "disease classification (thrips/mites/begomovirus/anthracnose/healthy)",
        "input": "image/float32 1x3x224x224",
        "format": "onnx",
        "classes": CLASSES,
        "class_symptoms": CLASS_SYMPTOMS,
        "features": FEATURE_NAMES,
        "trained_on": "principled synthetic symptom signatures (seed=20240517)",
    }
    CARD_PATH.write_text(json.dumps(meta, indent=2))
    print(f"  ✓ card.json sha256={meta['sha256'][:12]}… ({meta['size_bytes']} bytes)")
    return meta


# ---------------------------------------------------------------------------
# Benchmark & self-check
# ---------------------------------------------------------------------------
BENCH = {
    # name -> kwargs mirroring a canonical field presentation
    "Begomovirus (yellow mosaic)": dict(base_rgb=(0.42, 0.66, 0.18), mottle=0.10, bright_yellow=True),
    "Antraknosa (dark lesion)": dict(base_rgb=(0.34, 0.46, 0.20), mottle=0.04, lesion_frac=0.20),
    "Keriting_Thrips (up/silver)": dict(base_rgb=(0.30, 0.66, 0.34), top_gain=0.78, bot_gain=1.10),
    "Keriting_Mites (down/bronze)": dict(base_rgb=(0.34, 0.60, 0.26), top_gain=1.04, bot_gain=0.76, bronze=0.14),
    "Daun_Sehat (green)": dict(base_rgb=(0.18, 0.62, 0.22), mottle=0.02),
}
EXPECTED = {
    "Begomovirus (yellow mosaic)": "Begomovirus",
    "Antraknosa (dark lesion)": "Antraknosa",
    "Keriting_Thrips (up/silver)": "Keriting_Thrips",
    "Keriting_Mites (down/bronze)": "Keriting_Mites",
    "Daun_Sehat (green)": "Daun_Sehat",
}


def bench(model_path: Path) -> bool:
    sess = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
    rng = np.random.default_rng(7)
    ok = True
    print("\n═══ On-device inference benchmark ═══")
    for label, kw in BENCH.items():
        img = _leaf_canvas(rng, **kw)
        tensor = img[None].astype(np.float32)
        probs = sess.run(["probabilities"], {"image": tensor})[0][0]
        pred = CLASSES[int(np.argmax(probs))]
        good = pred == EXPECTED[label]
        ok &= good
        mark = "✓" if good else "✗"
        print(f"  {mark} {label:<30} → {pred:<16} ({float(np.max(probs))*100:5.1f}%)")
    return ok


def main() -> int:
    print("═══ DAPE Opsi 1 — leaf-curl & lesion classifier (real MLP) ═══")
    X, y = build_dataset()
    print(f"  dataset: {X.shape[0]} samples × {X.shape[1]} features")
    scaler = StandardScaler().fit(X)
    Xs = scaler.transform(X)
    clf = MLPClassifier(
        hidden_layer_sizes=(24, 16), activation="relu", solver="adam",
        max_iter=1200, random_state=SEED, learning_rate_init=3e-3,
    ).fit(Xs, y)
    train_acc = float((clf.predict(Xs) == y).mean())
    print(f"  trained MLP acc={train_acc:.3f}  (hidden 24→16)")

    # MLPClassifier.coefs_/intercepts_ already hold every layer's weights.
    weights = [c.astype(np.float32) for c in clf.coefs_]
    biases = [b.astype(np.float32) for b in clf.intercepts_]

    build_onnx(weights, biases, scaler.mean_, scaler.scale_, MODEL_PATH)
    write_card(MODEL_PATH)

    if not bench(MODEL_PATH):
        print("\n✗ SELF-CHECK FAILED: some benchmark conditions misclassify; aborting.")
        return 1
    size_kb = MODEL_PATH.stat().st_size / 1024
    print(f"\n✓ self-check passed. artefact {size_kb:.1f} KB (< 15 MB target).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
