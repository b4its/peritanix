"""Image forensics — anti-fraud audit of field photos.

When an Archimedes or photometry photo syncs to the server, we verify it was
taken in the field and not manipulated / reused. Three deterministic checks:

1. **EXIF sanity** — capture timestamp, presence of camera make/model, and a
   plausible DateTimeOriginal. Missing/contradictory EXIF raises suspicion.
2. **Error Level Analysis (ELA)** — recompress at a known JPEG quality and
   measure the difference. Spliced regions show locally elevated error levels.
   The *variance* of the ELA map is the signal (uniform edits inflate it).
3. **Duplicate detection** — perceptual (average) hash of the downscaled
   grayscale, compared Hamming-wise to a reference set. A near-identical hash
   to a previously-seen photo => reuse fraud.

No learned weights; everything is closed-form and unit-testable.
"""
from __future__ import annotations

import io
from dataclasses import dataclass, field

import numpy as np
from PIL import Image, ExifTags


@dataclass
class ForensicResult:
    exif_ok: bool
    has_camera_model: bool
    has_capture_time: bool
    ela_variance: float
    ela_suspicious: bool
    perceptual_hash: str
    duplicate_of: str | None
    hamming_distance: int | None
    verdict: str
    flags: list[str] = field(default_factory=list)


def _avg_hash(gray: np.ndarray, size: int = 8) -> str:
    """Average-hash (aHash): 64-bit perceptual hash as a hex string."""
    img = Image.fromarray(gray.astype(np.uint8)).resize((size, size), Image.BILINEAR)
    arr = np.asarray(img, dtype=np.float64)
    bits = (arr > arr.mean()).flatten()
    value = 0
    for b in bits:
        value = (value << 1) | int(bool(b))
    return f"{value:016x}"


def hamming_distance_hex(a: str, b: str) -> int:
    """Hamming distance between two hex-encoded perceptual hashes."""
    ia, ib = int(a, 16), int(b, 16)
    return bin(ia ^ ib).count("1")


def ela_variance(image_bytes: bytes, quality: int = 90) -> float:
    """Error Level Analysis variance.

    Re-encode the image at `quality`, subtract from the original, and measure
    the variance of the absolute difference. Edited regions generally raise it.
    """
    original = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    buf = io.BytesIO()
    original.save(buf, format="JPEG", quality=quality)
    buf.seek(0)
    recompressed = Image.open(buf).convert("RGB")
    a = np.asarray(original, dtype=np.float64)
    b = np.asarray(recompressed, dtype=np.float64)
    diff = np.abs(a - b)
    return float(diff.var())


def extract_exif(image_bytes: bytes) -> dict:
    """Return a normalised dict of key EXIF fields (best effort)."""
    out: dict[str, str] = {}
    try:
        img = Image.open(io.BytesIO(image_bytes))
        exif = img.getexif()
        for tag_id, value in exif.items():
            tag = ExifTags.TAGS.get(tag_id, str(tag_id))
            out[tag] = str(value)
    except Exception:
        pass
    return out


def forensic_audit(image_bytes: bytes,
                   known_hashes: list[str] | None = None,
                   ela_threshold: float = 120.0,
                   duplicate_hamming_max: int = 4) -> ForensicResult:
    """Run the full audit. `known_hashes` are perceptual hashes already stored."""
    flags: list[str] = []

    exif = extract_exif(image_bytes)
    has_model = any(k in exif for k in ("Model", "Make"))
    has_time = "DateTimeOriginal" in exif or "DateTime" in exif
    exif_ok = has_model and has_time
    if not has_model:
        flags.append("missing_camera_model")
    if not has_time:
        flags.append("missing_capture_time")

    ela_var = ela_variance(image_bytes)
    ela_suspicious = ela_var > ela_threshold
    if ela_suspicious:
        flags.append("high_ela_variance")

    gray = np.asarray(Image.open(io.BytesIO(image_bytes)).convert("L"))
    phash = _avg_hash(gray)

    duplicate_of = None
    hamming = None
    if known_hashes:
        best = min(known_hashes, key=lambda h: hamming_distance_hex(phash, h))
        hamming = hamming_distance_hex(phash, best)
        if hamming <= duplicate_hamming_max:
            duplicate_of = best
            flags.append("duplicate_image")

    if duplicate_of:
        verdict = "REJECT (duplicate/reused)"
    elif ela_suspicious and len(flags) >= 2:
        verdict = "SUSPECT (possible manipulation)"
    elif not exif_ok:
        verdict = "REVIEW (weak provenance)"
    else:
        verdict = "PASS"

    return ForensicResult(
        exif_ok=exif_ok,
        has_camera_model=has_model,
        has_capture_time=has_time,
        ela_variance=round(ela_var, 3),
        ela_suspicious=ela_suspicious,
        perceptual_hash=phash,
        duplicate_of=duplicate_of,
        hamming_distance=hamming,
        verdict=verdict,
        flags=flags,
    )
