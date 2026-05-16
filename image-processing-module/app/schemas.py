from pydantic import BaseModel
from typing import Optional, List, Dict, Any


class SegmentItemResponse(BaseModel):
    filename: str
    image_base64: str
    category: Optional[str] = None
    subcategory: Optional[str] = None
    season: Optional[str] = None
    warmth_level: Optional[int] = None
    confidence: Optional[float] = None
    colors: List[dict] = []
    ml_meta: Dict[str, Any] = {}


class SegmentResponse(BaseModel):
    items: List[SegmentItemResponse]