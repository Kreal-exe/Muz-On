"""Draws the still parts of a demo animation: the violet background and a phone around the screen.

    python3 tools/demo/frame.py <phone|fold> <out-dir>

Writes bg.png (the background), body.png (the phone with a transparent, rounded hole for the
screen) and screen.txt ("x y w h" of the hole), for tools/demo/make.sh to lay a screen recording into.
"""
import sys
from PIL import Image, ImageDraw, ImageFilter

kind, out = sys.argv[1], sys.argv[2]
# Canvas, screen size in pixels of the recording, and how tall the screen is drawn.
if kind == "phone":
    W, H, sw, sh, draw_h, radius = 868, 1344, 1080, 2420, 1180, 46
else:
    W, H, sw, sh, draw_h, radius = 1120, 1188, 2172, 2352, 1020, 40
scale = draw_h / sh
w, h = round(sw * scale), draw_h
x, y = (W - w) // 2, (H - h) // 2
bezel = 16

# The icon's violet, lighter at the top, as on the feature graphic and the icon.
bg = Image.new("RGB", (W, H))
top, mid, low = (0x9E, 0x78, 0xFF), (0x7B, 0x4F, 0xF2), (0x4A, 0x26, 0xC0)
px = bg.load()
for j in range(H):
    t = j / (H - 1)
    a, b, u = (top, mid, t * 2) if t < 0.5 else (mid, low, (t - 0.5) * 2)
    c = tuple(round(a[i] + (b[i] - a[i]) * u) for i in range(3))
    for i in range(W):
        px[i, j] = c
# A soft shadow under the phone.
shadow = Image.new("L", (W, H), 0)
ImageDraw.Draw(shadow).rounded_rectangle((x - bezel, y - bezel + 18, x + w + bezel, y + h + bezel + 18), radius + bezel, fill=120)
shadow = shadow.filter(ImageFilter.GaussianBlur(28))
bg.paste((30, 10, 70), (0, 0), shadow)
bg.save(f"{out}/bg.png")

body = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(body)
d.rounded_rectangle((x - bezel, y - bezel, x + w + bezel, y + h + bezel), radius + bezel, fill=(18, 16, 24, 255))
d.rounded_rectangle((x - bezel + 3, y - bezel + 3, x + w + bezel - 3, y + h + bezel - 3), radius + bezel - 3, outline=(70, 62, 92, 255), width=2)
# The hole the screen shows through, with the screen's own rounded corners.
hole = Image.new("L", (W, H), 0)
ImageDraw.Draw(hole).rounded_rectangle((x, y, x + w, y + h), radius, fill=255)
body.putalpha(Image.composite(Image.new("L", (W, H), 0), body.getchannel("A"), hole))
# Front camera.
if kind == "phone":
    ImageDraw.Draw(body).ellipse((W // 2 - 9, y + 16, W // 2 + 9, y + 34), fill=(8, 8, 12, 255))
body.save(f"{out}/body.png")
open(f"{out}/screen.txt", "w").write(f"{x} {y} {w} {h}\n")
