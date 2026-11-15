#!/usr/bin/env python3
"""Generates the host's application icon (multi-size .ico, PNG-compressed frames) with the standard library only.

The design matches the Android launcher icon: a white mouse pointer on the brand teal, here on a rounded square.
Run from anywhere:  python3 scripts/generate-host-icon.py
"""
import math
import struct
import sys
import zlib
from pathlib import Path

OUTPUT = Path(__file__).resolve().parent.parent / "src/host/HomeCursor.Host.Tray/Assets/HomeCursor.ico"
SIZES = [16, 24, 32, 48, 64, 128, 256]

TEAL = (0x0F, 0x6E, 0x73)
WHITE = (255, 255, 255)

# Pointer outline from client/app/src/main/res/drawable/ic_launcher_foreground.xml (108 x 108 viewport),
# drawn there with a 3 unit round-joined stroke.
POINTER = [(38, 28), (38, 76), (49, 65), (57, 82), (64, 78.5), (56, 62), (71, 62)]
STROKE_RADIUS = 1.5
# Bounding box of the pointer including its stroke, used to centre and scale it inside the icon.
POINTER_CENTER = (54.5, 55.0)
POINTER_HEIGHT = 57.0
POINTER_FILL_RATIO = 0.62  # pointer height as a share of the icon size
CORNER_RADIUS_RATIO = 0.22


def inside_polygon(x, y, polygon):
    inside = False
    j = len(polygon) - 1
    for i, (xi, yi) in enumerate(polygon):
        xj, yj = polygon[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            inside = not inside
        j = i
    return inside


def distance_to_segment(px, py, a, b):
    ax, ay = a
    bx, by = b
    dx, dy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def in_pointer(x, y):
    if inside_polygon(x, y, POINTER):
        return True
    n = len(POINTER)
    return any(distance_to_segment(x, y, POINTER[i], POINTER[(i + 1) % n]) <= STROKE_RADIUS for i in range(n))


def in_rounded_square(x, y, size, radius):
    cx = min(max(x, radius), size - radius)
    cy = min(max(y, radius), size - radius)
    return math.hypot(x - cx, y - cy) <= radius


def render(size):
    """Returns RGBA bytes (straight alpha) for a size x size icon, antialiased by supersampling."""
    ss = 8 if size <= 64 else 4
    scale = POINTER_HEIGHT / (POINTER_FILL_RATIO * size)  # icon pixels -> pointer viewport units
    radius = CORNER_RADIUS_RATIO * size
    rows = bytearray()
    for py in range(size):
        rows.append(0)  # PNG filter type: none
        for px in range(size):
            bg = fg = 0
            for sy in range(ss):
                y = py + (sy + 0.5) / ss
                for sx in range(ss):
                    x = px + (sx + 0.5) / ss
                    if not in_rounded_square(x, y, size, radius):
                        continue
                    bg += 1
                    vx = POINTER_CENTER[0] + (x - size / 2) * scale
                    vy = POINTER_CENTER[1] + (y - size / 2) * scale
                    if in_pointer(vx, vy):
                        fg += 1
            if bg == 0:
                rows += bytes((0, 0, 0, 0))
                continue
            w = fg / bg
            rows += bytes(round(TEAL[c] * (1 - w) + WHITE[c] * w) for c in range(3))
            rows.append(round(255 * bg / (ss * ss)))
    return bytes(rows)


def png(size, raw):
    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)  # 8-bit RGBA
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


def build_ico(frames):
    out = bytearray(struct.pack("<HHH", 0, 1, len(frames)))
    offset = 6 + 16 * len(frames)
    for size, data in frames:
        dim = 0 if size >= 256 else size  # 0 means 256 in an ICO directory entry
        out += struct.pack("<BBBBHHII", dim, dim, 0, 0, 1, 32, len(data), offset)
        offset += len(data)
    for _, data in frames:
        out += data
    return bytes(out)


def main():
    frames = []
    for size in SIZES:
        print(f"rendering {size}x{size}...", file=sys.stderr)
        frames.append((size, png(size, render(size))))
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_bytes(build_ico(frames))
    print(f"Wrote {OUTPUT}")


if __name__ == "__main__":
    main()
