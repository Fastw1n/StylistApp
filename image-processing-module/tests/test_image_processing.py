import pytest

from app.image_processing import clamp, map_yolo_class_to_category, process_uploaded_image_bytes


@pytest.mark.parametrize(
    ("class_name", "expected"),
    [
        ("shirt", "top"),
        ("jeans", "bottom"),
        ("sneakers", "shoes"),
        ("jacket", "outerwear"),
        ("bag", "accessory"),
        ("unknown-class", None),
        (None, None),
    ],
)
def test_map_yolo_class_to_category(class_name, expected):
    assert map_yolo_class_to_category(class_name) == expected


def test_clamp_limits_value_to_bounds():
    assert clamp(5, 0, 10) == 5
    assert clamp(-1, 0, 10) == 0
    assert clamp(12, 0, 10) == 10


def test_process_uploaded_image_bytes_missing_weights_returns_error(tmp_path):
    with pytest.raises(FileNotFoundError):
        process_uploaded_image_bytes(b"image", str(tmp_path / "missing.pt"))
