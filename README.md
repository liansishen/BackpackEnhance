# BackpackEnhance

[中文](README_zh.md)

A backpack enhancement mod for the GTNH modpack.

## Features

- **Backpack overlay** — When you open chests, machines, or other inventory GUIs, a floating panel appears so you can view and use items in your backpacks.
- **Creative inventory** — The overlay appears on the creative inventory's Survival Inventory tab and hides when another creative tab is selected.
- **Multiple backpacks** — All backpacks in the player inventory are shown as tabs; click a tab to switch.
- **Position memory** — The overlay can be dragged freely; each GUI remembers its own position.
- **Shift quick transfer** — While the overlay is expanded, Shift-clicking from a container or the player inventory prefers the backpack of the currently selected tab; Shift-clicking from the overlay prefers slots in the open chest or machine.
- **Scrollable large backpacks** — Backpacks taller than six rows use a vertical viewport. The mouse wheel scrolls the viewport over occupied slots, empty slots, slot gaps, and the scrollbar.
- **Forestry-native behavior** — Forestry backpack filters are enforced by both the overlay preview and the server. The title bar mode button cycles through Normal, Locked, Receive, and Resupply when available.
- **Hotkey** — Press **T** (rebindable in Controls) in a GUI to minimize or expand the overlay.
- **AE2 wireless terminals** — Bound, powered terminals within wireless range appear as tabs. ME entries show aggregate counts and support left/right click, Shift transfer, drag deposit, double-click collection, and hotbar keys. AE2 security and terminal power costs apply on the server.
- **Wireless display** — Wireless tabs use a five-row viewport and compact quantity text. The selected tab is remembered when closing and reopening inventory GUIs.
- **Item search** — The search field filters wireless entries and highlights matching items in ordinary backpacks while preserving slot positions. NEI search syntax is used when NEI is installed; otherwise the field matches item names. NEI's double-click inventory search also highlights overlay slots. Double-click the overlay search field to cycle through matching backpack tabs and scroll to the first match; wireless tabs participate using their already loaded inventory. Clicking the overlay search field releases NEI input focus. Right-click clears the field; Escape or Enter releases focus.

## Compatibility

Supported backpack mods (GTNH builds):

- **Adventure Backpack** (`AdventureBackpack2`) — adventure backpacks
- **Minecraft Backpack Mod** (`Minecraft-Backpack-Mod` / Brad's backpacks) — standard backpacks
- **Forestry** (`ForestryMC`, GTNH fork) — T1/T2 specialized backpacks and 125-slot naturalist backpacks
- **Applied Energistics 2** (`appliedenergistics2`, GTNH fork) — registered wireless item terminals, including compatible Wireless Crafting Terminal items

Only backpacks in the player inventory are shown as overlay tabs. If a backpack mod is not installed, it is simply ignored.

### Wireless inventory synchronization

Terminal availability is checked on the server every ten ticks and before item actions. Only the selected, expanded wireless tab subscribes to ME inventory updates. Its initial inventory is sent in bounded chunks, followed by changed item quantities. Container-to-container GUI switches reuse valid terminal connections; selecting a terminal again refreshes its current inventory. Closing the overlay session detaches inventory listeners.

When an AE terminal is open, wireless tabs connected to the same ME network are hidden and their overlay inventory listeners are detached. Tabs for other networks remain available.

Wireless tabs provide stored-item access. NEI recipe lookup works over their items. NEI recipe auto-fill, material/tool borrowing, ME crafting requests, fluids, and terminal view-cell filtering are outside the current wireless integration.

## License

This project is licensed under the [MIT License](LICENSE).
