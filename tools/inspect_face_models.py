"""Inspect the bundled ONNX protobuf contracts without installing a Python ML runtime."""
from pathlib import Path
import hashlib
import json


def varint(data, pos):
    value = shift = 0
    while True:
        byte = data[pos]
        pos += 1
        value |= (byte & 127) << shift
        if byte < 128:
            return value, pos
        shift += 7


def fields(data):
    pos = 0
    result = {}
    while pos < len(data):
        tag, pos = varint(data, pos)
        number, wire = tag >> 3, tag & 7
        if wire == 0:
            value, pos = varint(data, pos)
        elif wire == 2:
            size, pos = varint(data, pos)
            value, pos = data[pos:pos + size], pos + size
        elif wire in (1, 5):
            size = 8 if wire == 1 else 4
            value, pos = data[pos:pos + size], pos + size
        else:
            raise ValueError(f"Unsupported protobuf wire type {wire}")
        result.setdefault(number, []).append(value)
    return result


def tensor_contract(raw):
    info = fields(raw)
    tensor = fields(fields(info[2][0])[1][0])
    shape = fields(tensor[2][0])
    dimensions = []
    for raw_dim in shape.get(1, []):
        dim = fields(raw_dim)
        dimensions.append(dim[1][0] if 1 in dim else dim.get(2, [b"?"])[0].decode())
    return {"name": info[1][0].decode(), "dtype": tensor[1][0], "shape": dimensions}


root = Path(__file__).resolve().parents[1] / "app/src/main/assets/models"
for name in ("det_500m.onnx", "w600k_mbf.onnx"):
    data = (root / name).read_bytes()
    graph = fields(fields(data)[7][0])
    print(json.dumps({
        "file": name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(),
        "inputs": [tensor_contract(raw) for raw in graph[11]],
        "outputs": [tensor_contract(raw) for raw in graph[12]],
        "first_operators": [fields(raw)[4][0].decode() for raw in graph[1][:8]],
    }, indent=2))
