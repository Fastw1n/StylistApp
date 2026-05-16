from fastapi.testclient import TestClient

from app import main as main_module


def test_segment_valid_image_returns_items(monkeypatch):
    monkeypatch.setattr(
        main_module,
        "process_uploaded_image_bytes",
        lambda image_bytes, weights_path: [
            {
                "filename": "shirt.png",
                "image_base64": "cG5n",
                "category": "top",
                "subcategory": "shirt",
                "season": None,
                "warmth_level": None,
                "confidence": 0.95,
                "colors": [],
                "ml_meta": {"yolo_class_name": "shirt"},
            }
        ],
    )
    client = TestClient(main_module.app)

    response = client.post("/segment", files={"image": ("shirt.png", b"image", "image/png")})

    assert response.status_code == 200
    payload = response.json()
    assert payload["items"][0]["filename"] == "shirt.png"
    assert payload["items"][0]["category"] == "top"
    assert payload["items"][0]["ml_meta"]["yolo_class_name"] == "shirt"


def test_segment_invalid_content_type_returns_400():
    client = TestClient(main_module.app)

    response = client.post("/segment", files={"image": ("notes.txt", b"text", "text/plain")})

    assert response.status_code == 400


def test_segment_empty_file_returns_400():
    client = TestClient(main_module.app)

    response = client.post("/segment", files={"image": ("empty.png", b"", "image/png")})

    assert response.status_code == 400
    assert response.json()["detail"] == "Empty image upload"


def test_segment_model_error_returns_500(monkeypatch):
    def fail_processing(image_bytes, weights_path):
        raise RuntimeError("model failed")

    monkeypatch.setattr(main_module, "process_uploaded_image_bytes", fail_processing)
    client = TestClient(main_module.app)

    response = client.post("/segment", files={"image": ("shirt.png", b"image", "image/png")})

    assert response.status_code == 500
    assert response.json()["detail"] == "model failed"


def test_segment_response_contract(monkeypatch):
    monkeypatch.setattr(
        main_module,
        "process_uploaded_image_bytes",
        lambda image_bytes, weights_path: [
            {
                "filename": "shoes.png",
                "image_base64": "cG5n",
                "category": "shoes",
                "subcategory": "sneakers",
                "confidence": 0.8,
                "ml_meta": {"bbox": [1, 2, 3, 4]},
            }
        ],
    )
    client = TestClient(main_module.app)

    response = client.post("/segment", files={"image": ("shoes.png", b"image", "image/png")})

    assert response.status_code == 200
    item = response.json()["items"][0]
    assert set(item) == {
        "filename",
        "image_base64",
        "category",
        "subcategory",
        "season",
        "warmth_level",
        "confidence",
        "colors",
        "ml_meta",
    }
    assert item["colors"] == []
