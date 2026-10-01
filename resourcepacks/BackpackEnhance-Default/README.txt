BackpackEnhance Default Resource Pack
====================================

Minecraft 1.7.10 (pack_format 1)

Generate the complete reference pack from the repository with:
  ./gradlew exportDefaultResourcePack
On Windows use ./gradlew.bat instead.

The output is build/resourcepacks/BackpackEnhance-Default/.
The repository's resourcepacks/BackpackEnhance-Default/ directory holds
only this README, pack.mcmeta and pack.png. Textures and colors are copied
from the mod's resources during export so there is one default source.

Copy the generated folder into your game's resourcepacks/ directory,
or zip its contents with pack.mcmeta at the ZIP root.

Edit individual component textures in:
  assets/backpackenhance/textures/gui/overlay/
Edit text and search colors in:
  assets/backpackenhance/gui/overlay.properties

The repository's resourcepacks/README.md documents logical dimensions,
nine-slice borders, fixed control icons, color layering and legacy migration.
Reload resource packs with F3+T after editing.
