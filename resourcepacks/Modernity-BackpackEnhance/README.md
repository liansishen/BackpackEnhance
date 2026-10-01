# Modernity — BackpackEnhance

适用于采用独立控件材质布局的 BackpackEnhance。将本目录复制到游戏 `resourcepacks/` 中启用，或将目录内容打包为 ZIP。

- `assets/backpackenhance/textures/gui/overlay/`：独立控件和状态图片。
- `assets/backpackenhance/gui/overlay.properties`：文字及搜索遮罩颜色。
- 面板、槽位等控件由 Modernity 图集无损裁切；滚动条具有常态、悬停和禁用态，翻页箭头具有独立的浅蓝色悬停态。
- 展开、翻页及模式标记使用固定 PNG，与最小化图标统一为 `#F2F2F2`，尺寸与图案独立于游戏字体。完整规则见上级[制作说明](../README.md)。
- 本布局适用于 0.3.0 之后的材质拆分版本。0.3.0 及更早版本使用旧的 `overlay.png`。

## 滚动条风格参考

参考 [Modernity-GTNH-UI](https://github.com/ModernityGTNH/Modernity-GTNH-UI) 创造物品栏的 `assets/minecraft/textures/gui/container/creative_inventory/tabs.png` 中 `(232, 0)` 的 12×15 普通滑块、`(244, 0)` 的禁用滑块，以及 `tab_items.png` 中的窄轨道。滑块按参考的 12×15 固定尺寸绘制；悬停配色沿用本适配包的浅蓝色按钮。滚动条始终显示，无法滚动时使用禁用态。

迁移工具使用 `--theme modernity` 可重新生成这组样式。

## 封面图标与来源

`pack.png` 保留 Modernity-GTNH-UI 2.9.X 封面的外边框，中央改为本适配包配色的 `B` 按钮；图片为 128×128 RGBA。

封面原作来自 [ModernityGTNH / Modernity-GTNH-UI](https://github.com/ModernityGTNH/Modernity-GTNH-UI)，作者与贡献者见上游项目。该封面及本次改编遵循上游 `LICENSE.txt` 标明的 [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/legalcode)；按许可提供，不附带担保。

Reload with F3+T after editing. Keep `pack.mcmeta`, `pack.png` and `assets/` at the ZIP root when distributing this pack.
