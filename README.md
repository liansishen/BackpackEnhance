# BackpackEnhance

[中文](README_zh.md)

A backpack enhancement mod for the GTNH modpack.

## Features

- **Backpack overlay** — When you open chests, machines, or other inventory GUIs, a floating panel appears so you can view and use items in your backpacks.
- **Multiple backpacks** — All backpacks in the player inventory are shown as tabs; click a tab to switch.
- **Position memory** — The overlay can be dragged freely; each GUI remembers its own position.
- **Shift quick transfer** — While the overlay is expanded, Shift-clicking from a container or the player inventory prefers the backpack of the currently selected tab; Shift-clicking from the overlay prefers slots in the open chest or machine.
- **Scrollable large backpacks** — Backpacks taller than six rows use a vertical viewport. Scroll over the scrollbar or an empty backpack slot to move the viewport; scrolling over an occupied slot quick-transfers that stack.
- **Forestry-native behavior** — Forestry backpack filters are enforced by both the overlay preview and the server. The title bar mode button cycles through Normal, Locked, Receive, and Resupply when available.
- **Hotkey** — Press **T** (rebindable in Controls) in a GUI to minimize or expand the overlay.

## Compatibility

Supported backpack mods (GTNH builds):

- **Adventure Backpack** (`AdventureBackpack2`) — adventure backpacks
- **Minecraft Backpack Mod** (`Minecraft-Backpack-Mod` / Brad's backpacks) — standard backpacks
- **Forestry** (`ForestryMC`, GTNH fork) — T1/T2 specialized backpacks and 125-slot naturalist backpacks

Only backpacks in the player inventory are shown as overlay tabs. If a backpack mod is not installed, it is simply ignored.

## License

This project is licensed under the [MIT License](LICENSE).
