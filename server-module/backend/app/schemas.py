from pydantic import BaseModel
from typing import Optional, List, Dict, Any

class AuthRequest(BaseModel):
    email: str
    password: str

class RegisterRequest(AuthRequest):
    name: Optional[str] = None

class UserProfileDto(BaseModel):
    user_id: str
    email: str
    name: Optional[str] = None

class BodyProfileDto(BaseModel):
    skin_tone: Optional[str] = None
    eye_color: Optional[str] = None
    hair_color: Optional[str] = None
    height_cm: Optional[int] = None
    weight_kg: Optional[int] = None
    chest_cm: Optional[int] = None
    waist_cm: Optional[int] = None

class BodyProfileRequest(BodyProfileDto):
    pass

class AuthResponse(BaseModel):
    token: str
    user: UserProfileDto

class Color(BaseModel):
    name: str
    hex: Optional[str] = None
    share: Optional[float] = None

class Attributes(BaseModel):
    category: Optional[str] = None
    subcategory: Optional[str] = None
    season: Optional[str] = None
    warmth_level: Optional[int] = None
    confidence: Optional[float] = None
    colors: List[Color] = []

class DraftCandidateResponse(BaseModel):
    candidate_id: str
    normalized_image_url: str
    category: Optional[str] = None
    subcategory: Optional[str] = None
    season: Optional[str] = None
    warmth_level: Optional[int] = None
    confidence: Optional[float] = None
    colors: List[Color] = []

class PrepareResponse(BaseModel):
    draft_id: str
    original_image_url: Optional[str] = None
    items: List[DraftCandidateResponse]
    needs_user_review: bool = True

class ConfirmSelectedItem(BaseModel):
    candidate_id: str
    user_overrides: Optional[Dict[str, Any]] = None

class ConfirmRequest(BaseModel):
    draft_id: str
    selected_items: List[ConfirmSelectedItem]

class ConfirmedItemDto(BaseModel):
    item_id: str
    normalized_image_url: str
    attributes: Attributes

class ConfirmResponse(BaseModel):
    items: List[ConfirmedItemDto]

class CancelRequest(BaseModel):
    draft_id: str

class ItemResponse(BaseModel):
    item_id: str
    normalized_image_url: str
    attributes: Attributes

class ClothingItemDto(BaseModel):
    item_id: str
    category: str
    subcategory: Optional[str] = None
    normalized_image_url: str
    season: Optional[str] = None
    warmth_level: Optional[int] = None

class ItemsListResponse(BaseModel):
    items: List[ClothingItemDto]
