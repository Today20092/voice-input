"""Print ExecuTorch ET12 method geometry from pinned PTE files, without loading weights.

uv run tools/asr4all_geometry.py build/asr4all/s/asr_encoder.pte
Combine each variant's methods and PTE SHA-256 in app/src/main/assets/asr4all-geometry.json.
Field positions follow pytorch/executorch v1.2.0 schema/program.fbs.
"""

import json
import mmap
import struct
import sys
from pathlib import Path


def geometry(path: Path) -> dict:
    with (
        path.open("rb") as source,
        mmap.mmap(source.fileno(), 0, access=mmap.ACCESS_READ) as data,
    ):

        def integer(offset: int, fmt: str = "<I") -> int:
            return struct.unpack_from(fmt, data, offset)[0]

        def field(table: int, index: int) -> int:
            vtable = table - integer(table, "<i")
            entry = vtable + 4 + 2 * index
            if entry >= vtable + integer(vtable, "<H"):
                return 0
            offset = integer(entry, "<H")
            return table + offset if offset else 0

        def target(offset: int) -> int:
            return offset + integer(offset)

        def vector(table: int, index: int) -> list[int]:
            offset = field(table, index)
            if not offset:
                return []
            start = target(offset)
            return [start + 4 + 4 * i for i in range(integer(start))]

        def tensor(value: int) -> dict:
            assert integer(field(value, 0), "<B") == 5, "Expected tensor EValue"
            table = target(field(value, 1))
            dtype = field(table, 0)
            return {
                "dtype": integer(dtype, "<B") if dtype else 0,
                "shape": [integer(p, "<i") for p in vector(table, 2)],
            }

        assert data[4:8] == b"ET12", "Unsupported ExecuTorch file identifier"
        program = integer(0)
        result = {}
        for item in vector(program, 1):
            plan = target(item)
            name = target(field(plan, 0))
            method = data[name + 4 : name + 4 + integer(name)].decode()
            if method not in {"stream_c16r4", "pcec_c16", "pcec_c16_flush", "vad_dft"}:
                continue
            values = [target(p) for p in vector(plan, 2)]
            result[method] = {
                key: [tensor(values[integer(p)]) for p in vector(plan, index)]
                for key, index in (("inputs", 3), ("outputs", 4))
            }
        assert {"stream_c16r4", "pcec_c16", "pcec_c16_flush"} <= result.keys()
        return result


if __name__ == "__main__":
    print(json.dumps(geometry(Path(sys.argv[1])), separators=(",", ":")))
