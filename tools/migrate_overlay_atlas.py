"""Split a legacy 256x256 overlay atlas into independently editable RGBA textures."""

import argparse
from pathlib import Path

from PIL import Image, ImageDraw


CROPS = {
    "panel": (0, 0, 32, 32),
    "title": (32, 0, 48, 16),
    "slot": (0, 32, 18, 50),
    "slot_hover": (18, 32, 34, 48),
    "tab_normal": (0, 56, 24, 74),
    "tab_selected": (24, 56, 48, 74),
    "button_normal": (0, 80, 12, 92),
    "button_hover": (12, 80, 24, 92),
    "button_disabled": (0, 80, 12, 92),
    "icons/minimize": (24, 80, 36, 92),
    "arrow_normal": (0, 96, 10, 114),
    "arrow_disabled": (10, 96, 20, 114),
}

GLYPHS = {
    "expand": (20, 20, 7, 6, ["####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."]),
    "arrow_left": (10, 18, 3, 5, ["...#", "..#.", ".#..", "#...", ".#..", "..#.", "...#"]),
    "arrow_right": (10, 18, 3, 5, ["#...", ".#..", "..#.", "...#", "..#.", ".#..", "#..."]),
    "mode_normal": (12, 12, 3, 2, ["#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"]),
    "mode_locked": (12, 12, 3, 2, ["#....", "#....", "#....", "#....", "#....", "#....", "#####"]),
    "mode_receive": (12, 12, 3, 2, ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"]),
    "mode_resupply": (12, 12, 3, 2, [".####", "#....", "#....", ".###.", "....#", "....#", "####."]),
}


def icon_images(color):
    images = {}
    for name, (width, height, left, top, rows) in GLYPHS.items():
        image = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        for y, row in enumerate(rows):
            for x, pixel in enumerate(row):
                if pixel == "#":
                    image.putpixel((left + x, top + y), color)
        images["icons/" + name] = image
    return images


def arrow_hover(normal, theme):
    palette = {
        (154, 159, 180, 255): (156, 211, 255, 255),
        (173, 176, 196, 255): (218, 255, 255, 255),
        (105, 109, 136, 255): (112, 140, 186, 255),
    } if theme == "modernity" else {(160, 160, 160, 255): (192, 192, 192, 255)}
    image = normal.copy()
    for y in range(image.height):
        for x in range(image.width):
            color = image.getpixel((x, y))
            image.putpixel((x, y), palette.get(color, color))
    return image


def modernity_scrollbars():
    # The creative inventory uses a narrow recessed track behind a fixed 12x15 thumb.
    track = Image.new("RGBA", (12, 15), (0, 0, 0, 0))
    draw = ImageDraw.Draw(track)
    draw.rectangle((3, 0, 8, 14), fill="#F2F2F2")
    draw.rectangle((4, 1, 7, 13), fill="#9A9FB4")
    draw.rectangle((4, 1, 7, 2), fill="#696D88")
    images = {"scrollbar_track": track}
    for name, face, highlight, shadow in (
        ("scrollbar_thumb", "#9A9FB4", "#ADB0C4", "#696D88"),
        ("scrollbar_thumb_hover", "#9CD3FF", "#DAFFFF", "#708CBA"),
    ):
        image = Image.new("RGBA", (12, 15), "#413F54")
        draw = ImageDraw.Draw(image)
        draw.rectangle((1, 1, 10, 11), fill=highlight)
        draw.rectangle((2, 2, 9, 10), fill=face)
        draw.rectangle((1, 12, 10, 13), fill=shadow)
        images[name] = image
    disabled = Image.new("RGBA", (12, 15), (0, 0, 0, 0))
    draw = ImageDraw.Draw(disabled)
    draw.rectangle((0, 0, 11, 13), fill="#413F54")
    draw.rectangle((1, 1, 10, 11), fill="#878FA5")
    draw.rectangle((2, 2, 9, 10), fill="#696D88")
    draw.line((1, 12, 10, 12), fill="#696D88")
    images["scrollbar_thumb_disabled"] = disabled
    return images


def split_atlas(source, destination, theme="default"):
    with Image.open(source) as image:
        atlas = image.convert("RGBA")
    if atlas.size != (256, 256):
        raise ValueError("The legacy atlas must be 256x256 pixels")
    textures = {name: atlas.crop(bounds) for name, bounds in CROPS.items()}
    for name in ("tab_normal", "tab_selected"):
        old = textures[name]
        image = old.crop((0, 0, 22, 18))
        image.paste(old.crop((13, 0, 24, 18)), (11, 0))
        textures[name] = image
    minimize_colors = textures["icons/minimize"].getcolors(144)
    icon_color = max((count, color) for count, color in minimize_colors if color[3] > 0)[1]
    textures.update(icon_images(icon_color))
    textures["arrow_hover"] = arrow_hover(textures["arrow_normal"], theme)
    textures["tab_strip"] = Image.new("RGBA", (16, 16), atlas.getpixel((48, 0)))
    textures["tab_accent"] = Image.new("RGBA", (22, 18), (0, 0, 0, 0))
    ImageDraw.Draw(textures["tab_accent"]).rectangle((0, 0, 1, 17), fill=(255, 255, 255, 255))
    textures["scrollbar_track"] = Image.new("RGBA", (12, 15), (42, 42, 42, 255))
    for name, button in (
        ("scrollbar_thumb", "button_normal"),
        ("scrollbar_thumb_hover", "button_hover"),
        ("scrollbar_thumb_disabled", "arrow_disabled"),
    ):
        textures[name] = textures[button].resize((12, 15), Image.Resampling.NEAREST)
    if theme == "modernity":
        textures.update(modernity_scrollbars())
    for name in ("search", "search_focused"):
        image = Image.new("RGBA", (16, 16), (160, 160, 160, 255))
        ImageDraw.Draw(image).rectangle((1, 1, 14, 14), fill=(0, 0, 0, 255))
        textures[name] = image
    tooltip = Image.new("RGBA", (8, 8), atlas.getpixel((1, 128)))
    ImageDraw.Draw(tooltip).rectangle((1, 1, 6, 6), fill=atlas.getpixel((0, 128)))
    textures["tooltip"] = tooltip
    for name, image in textures.items():
        target = destination / (name + ".png")
        target.parent.mkdir(parents=True, exist_ok=True)
        image.save(target)
    print(f"Wrote {len(textures)} RGBA textures to {destination}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("atlas", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--theme", choices=("default", "modernity"), default="default")
    args = parser.parse_args()
    split_atlas(args.atlas, args.output, args.theme)
