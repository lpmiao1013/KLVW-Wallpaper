import re
from pathlib import Path

root = Path(r"d:\lpm_pro\KLVW-Wallpaper\app\src\main\kotlin")
# Capture common Compose / UI string literals
patterns = [
    re.compile(r'Text\(\s*"([^"\\]*(?:\\.[^"\\]*)*)"'),
    re.compile(r'title\s*=\s*"([^"]+)"'),
    re.compile(r'subtitle\s*=\s*"([^"]+)"'),
    re.compile(r'label\s*=\s*"([^"]+)"'),
    re.compile(r'contentDescription\s*=\s*"([^"]+)"'),
    re.compile(r'NavTab\("[^"]+",\s*"[^"]+"'),
]

seen = []
for path in sorted(root.rglob("*.kt")):
    text = path.read_text(encoding="utf-8")
    for pat in patterns:
        for m in pat.finditer(text):
            s = m.group(1) if m.lastindex else m.group(0)
            if not s or len(s) < 2:
                continue
            if s.startswith("$") or s.startswith("%"):
                continue
            if s not in seen:
                seen.append(s)

out = Path(r"d:\lpm_pro\KLVW-Wallpaper\tools\ui_strings.txt")
out.write_text("\n".join(seen), encoding="utf-8")
print(f"wrote {len(seen)} strings -> {out}")
