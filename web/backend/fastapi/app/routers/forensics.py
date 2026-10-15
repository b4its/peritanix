"""Anti-fraud image forensics router (ELA + EXIF + duplicate detection)."""
from __future__ import annotations

import base64

from fastapi import APIRouter, HTTPException

from ..schemas import ForensicRequest, ForensicResponse
from ..services.epidemiology import image_forensics

router = APIRouter(prefix="/internal/forensics", tags=["forensics"])


@router.post("/image-audit", response_model=ForensicResponse)
def image_audit(req: ForensicRequest) -> ForensicResponse:
    try:
        raw = base64.b64decode(req.image_base64)
    except Exception:
        raise HTTPException(status_code=422, detail="invalid base64 image") from None
    res = image_forensics.forensic_audit(
        raw,
        known_hashes=req.known_hashes,
        ela_threshold=req.ela_threshold,
        duplicate_hamming_max=req.duplicate_hamming_max,
    )
    return ForensicResponse(**res.__dict__)
