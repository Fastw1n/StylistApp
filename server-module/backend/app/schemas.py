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
    name: Optional[str] = None
    is_favorite: bool = False

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
    name: Optional[str] = None
    category: str
    subcategory: Optional[str] = None
    normalized_image_url: str
    season: Optional[str] = None
    warmth_level: Optional[int] = None
    is_favorite: bool = False

class ItemsListResponse(BaseModel):
    items: List[ClothingItemDto]

class FavoriteItemRequest(BaseModel):
    is_favorite: bool
    name: Optional[str] = None

class UpdateItemRequest(BaseModel):
    name: Optional[str] = None

class OutfitItemDto(BaseModel):
    item_id: str
    name: Optional[str] = None
    category: str
    subcategory: Optional[str] = None
    normalized_image_url: str

class OutfitDto(BaseModel):
    outfit_id: str
    name: Optional[str] = None
    items: List[OutfitItemDto]
    style: Optional[str] = None
    colors: List[str] = []
    season: Optional[str] = None

class CreateOutfitRequest(BaseModel):
    name: Optional[str] = None
    item_ids: List[str]
    style: Optional[str] = None
    colors: List[str] = []
    season: Optional[str] = None

class UpdateOutfitRequest(BaseModel):
    name: Optional[str] = None

class DeleteOutfitResponse(BaseModel):
    ok: bool

class OutfitsListResponse(BaseModel):
    outfits: List[OutfitDto]
