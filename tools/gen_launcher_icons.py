"""Rasterize a simple K monogram into mipmap densities for launcher fallbacks."""
from __future__ import annotations

import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res"

# density -> px (legacy launcher) / foreground canvas for adaptive
SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}
FG_SIZES = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}

BG = (28, 25, 23, 255)  # #1C1917
DISC = (41, 37, 36, 255)  # #292524
FG = (250, 250, 249, 255)  # #FAFAF9
ACCENT = (245, 158, 11, 255)  # #F59E0B


def png_chunk(tag: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)


def write_png(path: Path, w: int, h: int, rgba: bytes) -> None:
    raw = bytearray()
    stride = w * 4
    for y in range(h):
        raw.append(0)
        raw.extend(rgba[y * stride : (y + 1) * stride])
    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    data = b"".join(
        [
            b"\x89PNG\r\n\x1a\n",
            png_chunk(b"IHDR", ihdr),
            png_chunk(b"IDAT", zlib.compress(bytes(raw), 9)),
            png_chunk(b"IEND", b""),
        ]
    )
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def blend(dst: list[int], x: int, y: int, w: int, color: tuple[int, int, int, int], a: float = 1.0) -> None:
    if x < 0 or y < 0 or x >= w:
        return
    i = (y * w + x) * 4
    if i < 0 or i + 3 >= len(dst):
        return
    src_a = (color[3] / 255.0) * a
    if src_a <= 0:
        return
    for c in range(3):
        dst[i + c] = int(dst[i + c] * (1 - src_a) + color[c] * src_a)
    dst[i + 3] = int(min(255, dst[i + 3] + src_a * 255))


def fill_rect(px: list[int], w: int, h: int, x0: float, y0: float, x1: float, y1: float, color) -> None:
    for y in range(max(0, int(y0)), min(h, int(math.ceil(y1)))):
        for x in range(max(0, int(x0)), min(w, int(math.ceil(x1)))):
            blend(px, x, y, w, color)


def fill_circle(px: list[int], w: int, h: int, cx: float, cy: float, r: float, color) -> None:
    r2 = r * r
    for y in range(max(0, int(cy - r - 1)), min(h, int(cy + r + 2))):
        for x in range(max(0, int(cx - r - 1)), min(w, int(cx + r + 2))):
            d = (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2
            if d <= r2:
                blend(px, x, y, w, color)
            elif d <= (r + 0.75) ** 2:
                a = 1.0 - (math.sqrt(d) - r)
                if a > 0:
                    blend(px, x, y, w, color, a)


def fill_poly(px: list[int], w: int, h: int, pts: list[tuple[float, float]], color) -> None:
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    minx, maxx = int(min(xs)), int(math.ceil(max(xs)))
    miny, maxy = int(min(ys)), int(math.ceil(max(ys)))
    n = len(pts)
    for y in range(max(0, miny), min(h, maxy + 1)):
        for x in range(max(0, minx), min(w, maxx + 1)):
            # barycentric / winding for triangle-ish quads via even-odd
            inside = False
            j = n - 1
            for i in range(n):
                xi, yi = pts[i]
                xj, yj = pts[j]
                if ((yi > y) != (yj > y)) and (x < (xj - xi) * (y - yi) / (yj - yi + 1e-9) + xi):
                    inside = not inside
                j = i
            if inside:
                blend(px, x, y, w, color)


def draw_k_icon(size: int, with_bg: bool) -> bytes:
    px = [0] * (size * size * 4)
    if with_bg:
        fill_rect(px, size, size, 0, 0, size, size, BG)
    # geometry in 108-unit adaptive space, scaled
    s = size / 108.0
    fill_circle(px, size, size, 54 * s, 54 * s, 28 * s, DISC)
    # stem
    fill_rect(px, size, size, 42 * s, 38 * s, 48.5 * s, 70 * s, FG)
    # upper arm
    fill_poly(
        px,
        size,
        size,
        [(48.2 * s, 52.2 * s), (66.5 * s, 38.5 * s), (70.8 * s, 43.8 * s), (55.8 * s, 54.5 * s)],
        FG,
    )
    # lower arm
    fill_poly(
        px,
        size,
        size,
        [(53.8 * s, 52.8 * s), (70.8 * s, 68.2 * s), (66.2 * s, 73 * s), (48.2 * s, 55.8 * s)],
        FG,
    )
    # accent
    fill_poly(
        px,
        size,
        size,
        [(48.2 * s, 52.2 * s), (55.8 * s, 54.5 * s), (53.8 * s, 52.8 * s)],
        ACCENT,
    )
    return bytes(px)


def main() -> None:
    for folder, size in SIZES.items():
        out = ROOT / folder
        write_png(out / "ic_launcher.png", size, size, draw_k_icon(size, with_bg=True))
        write_png(out / "ic_launcher_round.png", size, size, draw_k_icon(size, with_bg=True))
    for folder, size in FG_SIZES.items():
        out = ROOT / folder
        write_png(out / "ic_launcher_foreground.png", size, size, draw_k_icon(size, with_bg=False))
    # also a drawable bitmap for wallpaper picker thumbnail
    thumb = ROOT / "drawable"
    write_png(thumb / "ic_wallpaper_thumbnail.png", 192, 192, draw_k_icon(192, with_bg=True))
    print("icons written")


if __name__ == "__main__":
    main()
