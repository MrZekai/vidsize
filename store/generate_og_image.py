#!/usr/bin/env python3
"""Create the 1200×630 Vidsize social preview with Pillow."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / "og-image.png"
INDIGO = "#4F46E5"
INK = "#0F1222"
MUTED = "#596173"


def font(size, bold=False):
    names = (["C:/Windows/Fonts/arialbd.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"]
             if bold else ["C:/Windows/Fonts/arial.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"])
    for name in names:
        if Path(name).exists():
            return ImageFont.truetype(name, size)
    raise RuntimeError("Install Arial or DejaVu Sans to render the social image")


def main():
    image = Image.new("RGB", (1200, 630), "#F6F6FB")
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((32, 32, 1168, 598), radius=38, fill="#FFFFFF", outline="#E5E6F0", width=2)
    draw.rounded_rectangle((82, 78, 170, 166), radius=25, fill="#EEE DFF".replace(" ", ""))
    # Draw the hourglass/play mark used by the app's SVG at social-preview scale.
    draw.polygon([(111, 104), (143, 104), (149, 110), (137, 122), (149, 137),
                  (143, 143), (111, 143), (105, 137), (117, 122), (105, 110)],
                 fill=INDIGO)
    draw.polygon([(120, 114), (120, 133), (136, 123)], fill="#EEEDFF")
    draw.text((190, 86), "Vidsize", font=font(60, True), fill=INK)
    draw.text((84, 226), "Compress video", font=font(72, True), fill=INK)
    draw.text((84, 315), "to any size", font=font(72, True), fill=INDIGO)
    draw.text((86, 450), "On-device · Offline · Free", font=font(34), fill=MUTED)
    draw.rounded_rectangle((905, 197, 1090, 442), radius=32, fill="#F1F0FF")
    for y, width in [(260, 115), (316, 82), (372, 52)]:
        draw.rounded_rectangle((940, y, 940 + width, y + 22), radius=11, fill=INDIGO)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUT, optimize=True)


if __name__ == "__main__":
    main()
