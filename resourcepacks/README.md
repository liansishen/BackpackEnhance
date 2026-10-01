# 悬浮窗材质制作 / Overlay resource packs

材质位于 `assets/backpackenhance/textures/gui/overlay/`，每个控件或状态对应一张 PNG。颜色配置位于 `assets/backpackenhance/gui/overlay.properties`。使用 Minecraft 1.7.10 的 `pack_format: 1`。

## 参考包

- **默认主题**：默认材质随模组内置。材质作者可手动运行 `./gradlew exportDefaultResourcePack`（Windows 使用 `./gradlew.bat`），在 `build/resourcepacks/BackpackEnhance-Default/` 导出制作参考。源材质统一维护在 `src/main/resources/assets/backpackenhance/`；`resourcepacks/BackpackEnhance-Default/` 只保存参考包的图标、元数据和说明。
- **Modernity 适配**：`resourcepacks/Modernity-BackpackEnhance/` 是可以直接复制到游戏中的完整材质包。
- 编辑参考包的副本。打包 ZIP 时，`pack.mcmeta`、`pack.png` 和 `assets/` 放在 ZIP 根目录。
- 可只提供需要替换的 PNG。其余文件沿用较低优先级的材质包或模组内置资源。切换材质包或按 F3+T 后，图片与颜色会重新加载。

## 打包与发布

运行 `./gradlew packageResourcePacks`（Windows 使用 `./gradlew.bat`）生成可直接安装的 Modernity 适配包，`build` 也会执行打包。输出为 `build/resourcepacks/Modernity-BackpackEnhance-<版本>.zip`。

默认使用项目版本号，可用 `-PresourcePackVersion=<版本>` 指定文件名中的版本。ZIP 根目录包含 `pack.mcmeta`、`pack.png`、说明文件和 `assets/`；直接放入游戏的 `resourcepacks/` 即可启用。

推送通过主分支 CI 验证的版本标签后，发布工作流在 JAR 发布完成后将 Modernity ZIP 上传到同一个 GitHub Release。发行 ZIP 的版本号与标签一致，默认材质由模组内置提供。

## 图片规范

以下尺寸为逻辑像素。PNG 可使用等比例整数倍分辨率，例如槽位 18×18、36×36 或 72×72；界面尺寸及点击区域始终保持 18×18。保存为 RGB/RGBA PNG，带透明度时使用 RGBA，避免灰度 PNG。

九宫格的四角保持固定尺寸，四边沿长度伸缩，中心沿两轴伸缩。边框参数的顺序为上、左、下、右，单位均为逻辑像素。边框参数与布局由代码定义。

| 文件 | 基础尺寸 | 绘制规则 |
| --- | --- | --- |
| `panel.png` | 32×32 | 九宫格，边框 2、2、4、2；内容绘制后重绘外框 |
| `title.png` | 16×16 | 整图伸缩到标题条，保持旧版规则 |
| `tab_strip.png` | 16×16 | 整图伸缩到标签区域；可设为透明以显示面板底色 |
| `slot.png` | 18×18 | 固定尺寸槽位 |
| `slot_hover.png` | 16×16 | 绘制在物品上的半透明悬停覆盖层 |
| `tab_normal.png` / `tab_selected.png` | 22×18 | 未选中 / 选中标签；标签间距 2 像素，物品图标为居中的 14×14，上下各留 2 像素 |
| `tab_accent.png` | 22×18 | 透明背景上的白色遮罩，绘制时乘以背包颜色；默认覆盖左侧两列 |
| `button_normal.png` / `button_hover.png` / `button_disabled.png` | 12×12 | 标题按钮状态；禁用态用于等待林业模式切换 |
| `icons/minimize.png` | 12×12 | 最小化按钮上的图标，保留图片自身颜色 |
| `arrow_normal.png` / `arrow_hover.png` / `arrow_disabled.png` | 10×18 | 翻页按钮背景，按可用及悬停状态选择图片，保留图片自身颜色 |
| `scrollbar_track.png` | 12×15 | 九宫格，边框 3、4、1、4，纵向伸缩；Modernity 的窄轨道位于图片中央 |
| `scrollbar_thumb.png` / `scrollbar_thumb_hover.png` / `scrollbar_thumb_disabled.png` | 12×15 | 固定大小的滑块；悬停和拖动使用 hover，无法滚动时使用 disabled |
| `search.png` / `search_focused.png` | 16×16 | 九宫格，四边各 1；包括输入区域外侧的 1 像素边框 |
| `tooltip.png` | 8×8 | 九宫格，四边各 1 |

标题条和标签区域沿用整图伸缩，制作花纹时需考虑其宽高会随窗口变化。展开面板始终预留滚动条空间；滑块尺寸固定为 12×15，轨道高度跟随可见行数。无法滚动时，禁用滑块位于轨道顶部。Modernity 使用创造物品栏的蓝灰色立体滑块、窄轨道及浅蓝色悬停状态。

默认九列布局每页显示 6 个标签，超过 6 个时显示翻页箭头。分页容量统一预留箭头空间；修改窗口宽度后，每页数量随可用宽度调整。

### 功能图标

展开、翻页和林业模式标记全部使用固定 PNG，显示不受游戏字体替换影响。默认主题和 Modernity 均提供以下图标：

| 文件 | 基础尺寸 |
| --- | --- |
| `icons/expand.png` | 20×20 |
| `icons/arrow_left.png` / `icons/arrow_right.png` | 10×18 |
| `icons/mode_normal.png` / `icons/mode_locked.png` / `icons/mode_receive.png` / `icons/mode_resupply.png` | 12×12 |

功能图标使用透明背景并保留 PNG 自身颜色。默认主题为 `#404040`，Modernity 为 `#F2F2F2`，各主题的展开、翻页、模式图标与最小化图标颜色一致。材质包未覆盖的图标使用模组内置 PNG。

## 颜色配置

`overlay.properties` 支持 `RRGGBB` 或 `AARRGGBB`，可带 `#` 前缀。六位颜色默认不透明。各材质包按从低到高优先级逐项覆盖，缺省项保留低优先级值，格式错误会记录警告并保留原值。

```properties
text=FF404040
search.text=FFE0E0E0
search.disabled=FF707070
search.hint=FF808080
search.shade=80000000
tooltip.text=FFFFFFFF
```

`search.shade` 控制普通背包搜索及 NEI 高亮中未匹配物品的覆盖色。物品图标、数量、原版光标和文字选区继续由 Minecraft 绘制。

## 迁移旧图集

0.3.0 及更早版本使用 `textures/gui/overlay.png`。独立控件布局使用上面的新路径，旧路径不再参与绘制。

安装 Pillow 后执行：

```text
python tools/migrate_overlay_atlas.py path/to/overlay.png path/to/pack/assets/backpackenhance/textures/gui/overlay
```

迁移工具接收旧版 256×256 图集，裁切原有控件，并生成固定图标、箭头悬停状态、滚动条、搜索框和标签色条遮罩。标签背景去掉中央两列，保留两侧边框，生成 22×18 图片。它会写入或覆盖输出目录中的 29 张 PNG，保留输入图集。图标颜色取自旧图集中的最小化图标。默认使用灰色滚动条；迁移 Modernity 时添加 `--theme modernity`，生成创造物品栏尺寸的蓝灰色滑块、禁用态及浅蓝色悬停态。颜色配置可从默认参考包复制。旧的两个无效颜色点不会迁移；标题外观来自旧图集中的 16×16 标题图片。

`src/test/resources/overlay/` 保存拆分前的默认及 Modernity 图集，用于像素回归测试。

## English summary

Run `./gradlew exportDefaultResourcePack` to generate the complete default template in `build/resourcepacks/BackpackEnhance-Default`. The Modernity directory is directly installable. Edit individual PNGs under `assets/backpackenhance/textures/gui/overlay/`; the table defines logical sizes and scaling. Higher-resolution PNGs keep the same GUI size. Colors are layered per key from `assets/backpackenhance/gui/overlay.properties`. Controls use fixed PNG icons independent of game fonts. Reload with F3+T. Legacy 256×256 atlases can be split with the migration command above (Python + Pillow required); add `--theme modernity` for Modernity scrollbar styling.
