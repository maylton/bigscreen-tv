#!/usr/bin/env python3
"""Draws the TV banner and launcher icons (needs pycairo and the Rubik font)."""

import math
from pathlib import Path

import cairo

RES = Path(__file__).resolve().parent.parent / "app/src/main/res"
BACKGROUND = (0x0A / 255, 0x0F / 255, 0x0F / 255)
SURFACE = (0x19 / 255, 0x21 / 255, 0x20 / 255)
PRIMARY = (0x9B / 255, 0xD0 / 255, 0xCC / 255)


def rounded(ctx, x, y, w, h, r):
    ctx.new_sub_path()
    ctx.arc(x + w - r, y + r, r, -math.pi / 2, 0)
    ctx.arc(x + w - r, y + h - r, r, 0, math.pi / 2)
    ctx.arc(x + r, y + h - r, r, math.pi / 2, math.pi)
    ctx.arc(x + r, y + r, r, math.pi, 3 * math.pi / 2)
    ctx.close_path()


def tv(ctx, cx, cy, size, line):
    """Outline of a TV on its stand, centered at (cx, cy)."""
    w, h = size, size * 0.62
    ctx.set_line_width(line)
    rounded(ctx, cx - w / 2, cy - h / 2 - size * 0.08, w, h, size * 0.08)
    ctx.stroke()
    ctx.move_to(cx - w * 0.22, cy + h / 2 + size * 0.06)
    ctx.line_to(cx + w * 0.22, cy + h / 2 + size * 0.06)
    ctx.stroke()


def banner():
    w, h = 640, 360
    surface = cairo.ImageSurface(cairo.FORMAT_ARGB32, w, h)
    ctx = cairo.Context(surface)
    gradient = cairo.LinearGradient(0, 0, w, h)
    gradient.add_color_stop_rgb(0, *SURFACE)
    gradient.add_color_stop_rgb(1, *BACKGROUND)
    ctx.set_source(gradient)
    ctx.paint()
    ctx.set_source_rgb(*PRIMARY)
    ctx.set_line_cap(cairo.LINE_CAP_ROUND)
    tv(ctx, w / 2, 120, 120, 9)
    ctx.select_font_face("Rubik", cairo.FONT_SLANT_NORMAL, cairo.FONT_WEIGHT_BOLD)
    ctx.set_font_size(68)
    text = "BIG SCREEN"
    extents = ctx.text_extents(text)
    ctx.move_to((w - extents.width) / 2 - extents.x_bearing, 270)
    ctx.show_text(text)
    out = RES / "drawable-xhdpi/banner.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    surface.write_to_png(str(out))


def icon(size, folder):
    surface = cairo.ImageSurface(cairo.FORMAT_ARGB32, size, size)
    ctx = cairo.Context(surface)
    rounded(ctx, 0, 0, size, size, size * 0.22)
    ctx.set_source_rgb(*SURFACE)
    ctx.fill()
    ctx.set_source_rgb(*PRIMARY)
    ctx.set_line_cap(cairo.LINE_CAP_ROUND)
    tv(ctx, size / 2, size / 2, size * 0.58, max(2, size * 0.07))
    out = RES / folder / "ic_launcher.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    surface.write_to_png(str(out))


if __name__ == "__main__":
    banner()
    for size, folder in ((48, "mipmap-mdpi"), (72, "mipmap-hdpi"), (96, "mipmap-xhdpi"),
                         (144, "mipmap-xxhdpi"), (192, "mipmap-xxxhdpi")):
        icon(size, folder)
