import importlib.util
import sys
import types
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))


def install_stub(name: str, module):
    if name in sys.modules:
        return
    try:
        spec = importlib.util.find_spec(name)
    except ModuleNotFoundError:
        spec = None
    if spec is None:
        sys.modules[name] = module


cv2_stub = types.ModuleType("cv2")
cv2_stub.IMREAD_COLOR = 1
cv2_stub.INTER_NEAREST = 0
cv2_stub.INTER_LANCZOS4 = 4
cv2_stub.COLOR_BGR2RGB = 1
cv2_stub.imdecode = lambda arr, flags: None
cv2_stub.resize = lambda image, size, interpolation=None: image
cv2_stub.cvtColor = lambda image, code: image
install_stub("cv2", cv2_stub)

np_stub = types.ModuleType("numpy")
np_stub.ndarray = object
np_stub.uint8 = "uint8"
np_stub.float32 = "float32"
np_stub.newaxis = None
np_stub.frombuffer = lambda data, dtype=None: data
np_stub.clip = lambda value, lo, hi: value
np_stub.ones = lambda shape, dtype=None: []
np_stub.dstack = lambda values: values
install_stub("numpy", np_stub)

pil_stub = types.ModuleType("PIL")
image_stub = types.ModuleType("PIL.Image")


class FakeImage:
    def save(self, buf, format=None):
        buf.write(b"png")


image_stub.fromarray = lambda array: FakeImage()
pil_stub.Image = image_stub
install_stub("PIL", pil_stub)
install_stub("PIL.Image", image_stub)

ultralytics_stub = types.ModuleType("ultralytics")
ultralytics_stub.YOLO = lambda *args, **kwargs: None
install_stub("ultralytics", ultralytics_stub)
