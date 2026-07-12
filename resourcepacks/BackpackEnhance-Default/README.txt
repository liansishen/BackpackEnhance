BackpackEnhance Default Resource Pack
=====================================

Minecraft 1.7.10 (pack_format 1)

This pack mirrors the textures shipped inside the mod JAR. Use it as a
starting point for custom themes:

  1. Copy this folder into your game's resourcepacks/ directory
     (or zip the folder contents).
  2. Enable it in Options → Resource Packs (place above default).
  3. Edit:
       assets/backpackenhance/textures/gui/overlay.png

Atlas layout (256×256 PNG):
  (0,0)   32×32  panel nine-slice (top/left/right 2 px, bottom 4 px fixed)
  (32,0)  16×16  title-bar tile
  (48,0)   1×1   panel body fill
  (49,0)   1×1   title fill
  (50,0)   1×1   divider
  (0,32)  18×18  inventory slot
  (18,32) 16×16  slot hover highlight
  (0,56)  24×18  tab idle
  (24,56) 24×18  tab active
  (0,80)  12×12  minimize button
  (12,80) 12×12  minimize button hover
  (24,80) 12×12  minimize glyph (transparent bg)
  (0,96)  10×18  tab arrow button
  (10,96) 10×18  tab arrow disabled
  (0,128)  1×1   tooltip background
  (1,128)  1×1   tooltip border

Items and text are still rendered by the game (not in this texture).
Regenerate the default atlas from the repo with:
  python tools/gen_overlay_atlas.py
