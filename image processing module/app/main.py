from fastapi import FastAPI, UploadFile, File, HTTPException
from .schemas import SegmentResponse, SegmentItemResponse
from .image_processing import process_uploaded_image_bytes

app = FastAPI(title="ML Service")


@app.post("/segment", response_model=SegmentResponse)
async def segment_image(
    image: UploadFile = File(...),
):
    if not image.content_type or not image.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Only image uploads are supported")

    try:
        image_bytes = await image.read()
        items = process_uploaded_image_bytes(
            image_bytes=image_bytes,
            weights_path="models/best.pt",
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

    return SegmentResponse(
        items=[SegmentItemResponse(**item) for item in items]
    )