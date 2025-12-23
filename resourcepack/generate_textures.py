"""
Generate placeholder textures for Arcane Sigils resource pack.
Run: python generate_textures.py
"""

from PIL import Image, ImageDraw
import os

# Paths
FONT_DIR = "assets/arcanesigils/textures/font"
GUI_DIR = "assets/minecraft/textures/gui"

os.makedirs(FONT_DIR, exist_ok=True)
os.makedirs(GUI_DIR, exist_ok=True)

# Colors
BG_COLOR = (26, 26, 26, 200)  # Dark gray, semi-transparent
BAR_EMPTY = (42, 42, 42, 255)  # Dark gray
BAR_FILL_RED = (255, 68, 68, 255)  # Red for cooldowns
BAR_FILL_BLUE = (68, 68, 255, 255)  # Blue for buffs


def create_notification_bg():
    """Create notification background panel (200x16)"""
    img = Image.new("RGBA", (200, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # Dark rounded-ish rectangle
    draw.rectangle([0, 0, 199, 15], fill=BG_COLOR)
    # Subtle border
    draw.rectangle([0, 0, 199, 15], outline=(60, 60, 60, 255))
    img.save(f"{FONT_DIR}/notification_bg.png")
    print("Created notification_bg.png")


def create_progress_bars():
    """Create progress bar segments (60x4 each, 0-100% in 10% steps)"""
    width, height = 60, 4

    for percent in range(0, 101, 10):
        img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        draw = ImageDraw.Draw(img)

        # Background (empty part)
        draw.rectangle([0, 0, width - 1, height - 1], fill=BAR_EMPTY)

        # Filled part
        fill_width = int((percent / 100) * width)
        if fill_width > 0:
            # Gradient from red to green based on fill
            r = int(255 - (percent / 100) * 155)  # 255 -> 100
            g = int(68 + (percent / 100) * 187)   # 68 -> 255
            fill_color = (r, g, 68, 255)
            draw.rectangle([0, 0, fill_width - 1, height - 1], fill=fill_color)

        filename = f"bar_{percent}.png"
        img.save(f"{FONT_DIR}/{filename}")
        print(f"Created {filename}")


def create_boss_bar():
    """Create minimalist boss bar texture (256x256)"""
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Vanilla boss bar is 182 pixels wide, 5 pixels tall per bar
    # Each color has 2 rows: background (notched) and progress
    # Colors: PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE

    colors = [
        (255, 102, 170, 255),  # PINK
        (51, 102, 255, 255),   # BLUE
        (255, 68, 68, 255),    # RED
        (68, 255, 68, 255),    # GREEN
        (255, 255, 68, 255),   # YELLOW
        (170, 68, 255, 255),   # PURPLE
        (255, 255, 255, 255),  # WHITE
    ]

    bar_width = 182
    bar_height = 5
    y = 0

    for color in colors:
        # Background bar (darker version)
        bg_color = tuple(max(0, c - 80) if i < 3 else c for i, c in enumerate(color))
        draw.rectangle([0, y, bar_width - 1, y + bar_height - 1], fill=bg_color)
        y += bar_height

        # Progress bar (full color)
        draw.rectangle([0, y, bar_width - 1, y + bar_height - 1], fill=color)
        y += bar_height

    img.save(f"{GUI_DIR}/bars.png")
    print("Created bars.png (boss bar)")


if __name__ == "__main__":
    print("Generating placeholder textures...\n")
    create_notification_bg()
    create_progress_bars()
    create_boss_bar()
    print("\nDone! Textures created in:")
    print(f"  - {FONT_DIR}/")
    print(f"  - {GUI_DIR}/")
