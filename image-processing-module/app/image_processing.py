from __future__ import annotations

import base64
import io
import uuid
from pathlib import Path

import cv2
import numpy as np
from PIL import Image
from ultralytics import YOLO


def composite_masked_rgb_on_white(
    crop_resized_rgb: np.ndarray,
    crop_resized_alpha: np.ndarray,
    canvas_size: int,
    new_w: int,
    new_h: int,
    px: int,
    py: int,
) -> np.ndarray:
    if crop_resized_alpha.ndim == 2:
        crop_resized_alpha = crop_resized_alpha[..., np.newaxis]

    fg = crop_resized_rgb.astype(np.float32)
    a = np.clip(crop_resized_alpha.astype(np.float32), 0, 255) / 255.0
    out_region = fg * a + 255.0 * (1.0 - a)

    canvas = np.ones((canvas_size, canvas_size, 3), dtype=np.uint8) * 255
    canvas[py:py + new_h, px:px + new_w] = np.clip(out_region, 0, 255).astype(np.uint8)
    return canvas


def clamp(v: int, lo: int, hi: int) -> int:
    return max(lo, min(hi, v))


def map_yolo_class_to_category(class_name: str | None) -> str | None:
    if not class_name:
        return None

    name = class_name.lower()

    top_names = {"tshirt", "t-shirt", "shirt", "blouse", "top", "hoodie", "sweater"}
    bottom_names = {"pants", "jeans", "trousers", "shorts", "skirt"}
    shoes_names = {"shoe", "shoes", "sneakers", "boots", "sandals"}
    outerwear_names = {"coat", "jacket", "outerwear", "blazer", "parka"}
    accessory_names = {"bag", "hat", "cap", "belt", "scarf", "glasses", "watch"}

    if name in top_names:
        return "top"
    if name in bottom_names:
        return "bottom"
    if name in shoes_names:
        return "shoes"
    if name in outerwear_names:
        return "outerwear"
    if name in accessory_names:
        return "accessory"

    return None


def process_uploaded_image_bytes(
    image_bytes: bytes,
    weights_path: str,
    canvas_size: int = 1024,
    conf_thres: float = 0.25,
    iou_thres: float = 0.7,
) -> list[dict]:
    weights = Path(weights_path)
    if not weights.is_file():
        raise FileNotFoundError(f"Нет файла весов: {weights}")

    arr = np.frombuffer(image_bytes, dtype=np.uint8)
    img_bgr = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if img_bgr is None:
        raise RuntimeError("Не удалось декодировать изображение")

    H, W = img_bgr.shape[:2]

    model = YOLO(str(weights))
    r = model.predict(
        source=img_bgr,
        conf=conf_thres,
        iou=iou_thres,
        verbose=False
    )[0]

    if r.boxes is None or len(r.boxes) == 0:
        return []

    if r.masks is None or r.masks.data is None:
        raise RuntimeError("Маски отсутствуют. Нужна YOLO-seg модель")

    masks_np = r.masks.data.cpu().numpy()
    detected_items: list[dict] = []

    for i, box in enumerate(r.boxes):
        x1, y1, x2, y2 = box.xyxy[0].cpu().numpy().astype(int).tolist()

        x1p = clamp(x1, 0, W - 1)
        y1p = clamp(y1, 0, H - 1)
        x2p = clamp(x2, 0, W - 1)
        y2p = clamp(y2, 0, H - 1)

        if x2p <= x1p or y2p <= y1p:
            continue

        mask_i = masks_np[i]
        if mask_i.shape != (H, W):
            mask_i = cv2.resize(mask_i, (W, H), interpolation=cv2.INTER_NEAREST)

        m_crop = (mask_i[y1p:y2p, x1p:x2p] > 0.5).astype(np.uint8) * 255
        crop_bgr = img_bgr[y1p:y2p, x1p:x2p].copy()

        crop_h, crop_w = crop_bgr.shape[:2]
        if crop_h == 0 or crop_w == 0:
            continue

        rgba = np.dstack([cv2.cvtColor(crop_bgr, cv2.COLOR_BGR2RGB), m_crop])

        scale = min(1.0, canvas_size / crop_w, canvas_size / crop_h)
        new_w = max(1, int(crop_w * scale))
        new_h = max(1, int(crop_h * scale))

        crop_rgb = rgba[:, :, :3]
        crop_alpha = rgba[:, :, 3:4]

        crop_resized_rgb = cv2.resize(crop_rgb, (new_w, new_h), interpolation=cv2.INTER_LANCZOS4)
        crop_resized_alpha = cv2.resize(crop_alpha, (new_w, new_h), interpolation=cv2.INTER_NEAREST)

        if crop_resized_alpha.ndim == 2:
            crop_resized_alpha = crop_resized_alpha[..., np.newaxis]

        px = (canvas_size - new_w) // 2
        py = (canvas_size - new_h) // 2

        canvas_rgb = composite_masked_rgb_on_white(
            crop_resized_rgb,
            crop_resized_alpha,
            canvas_size,
            new_w,
            new_h,
            px,
            py
        )

        filename = f"{uuid.uuid4().hex}.png"

        buf = io.BytesIO()
        Image.fromarray(canvas_rgb).save(buf, format="PNG")
        image_b64 = base64.b64encode(buf.getvalue()).decode("utf-8")

        cls_id = int(box.cls[0].cpu().item()) if box.cls is not None else None
        confidence = float(box.conf[0].cpu().item()) if box.conf is not None else None

        class_name = None
        if cls_id is not None and hasattr(model, "names"):
            if isinstance(model.names, dict):
                class_name = model.names.get(cls_id)
            elif isinstance(model.names, list) and 0 <= cls_id < len(model.names):
                class_name = model.names[cls_id]

        category = map_yolo_class_to_category(class_name)

        detected_items.append(
            {
                "filename": filename,
                "image_base64": image_b64,
                "category": category,
                "subcategory": class_name,
                "season": None,
                "warmth_level": None,
                "confidence": confidence,
                "colors": [],
                "ml_meta": {
                    "yolo_class_id": cls_id,
                    "yolo_class_name": class_name,
                    "bbox": [x1p, y1p, x2p, y2p],
                    "confidence": confidence,
                },
            }
        )

    return detected_items