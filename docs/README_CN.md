# ME 放置工具

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.4.10-orange.svg)](https://files.minecraftforge.net/)
[![License](https://img.shields.io/badge/License-LGPL%203.0-blue.svg)](../LICENSE)

[🇬🇧 English](../README.md)

一个 Minecraft Forge 模组，为 Applied Energistics 2 添加放置工具。可以直接从 ME 网络放置方块、AE2 线缆部件和流体。

## 功能

### ME 放置工具
- 直接从 ME 网络放置物品
- 支持普通方块、AE2 线缆部件（总线、面板等）和流体
- 18 格配置槽，按 G 键打开轮盘快速选择
- JEI 集成 - 可直接拖拽物品到配置格
- HUD 显示当前选择和网络状态

### ME 多方块放置工具
- 使用 BFS 算法一次放置多个方块
- 可调放置数量：1、8、64、256、1024
- 双层轮盘菜单：内圈选数量，外圈选物品
- 放置前预览
- 支持撤销（Ctrl + 左键）

### ME 线缆放置工具
- 高效放置 AE2 线缆
- 三种模式：直线、填充和分支
- 智能染色系统：优先使用 副手 > ME 网络 > 背包 中的染料
- 支持“光谱的钥匙”升级卡进行免费染色

## 前置要求

- Minecraft 1.20.1
- Forge 47.4.10+
- Applied Energistics 2

## 操作

| 工具 | 操作 | 按键 |
|------|------|------|
| 通用 | 打开 GUI | 右键（对着空气） |
| 通用 | 放置 | 右键（对着方块） |
| 通用 | 轮盘菜单 | 按住 G |
| 通用 | 链接网络 | 放入 ME 无线访问点 |
| 多方块 | 撤销 | Ctrl + 左键 |

## 配置

配置文件：`config/meplacementtool-common.toml`

```toml
# 能量设置（单位：AE）
mePlacementToolEnergyCapacity = 100000
mePlacementToolEnergyCost = 50
multiblockPlacementToolEnergyCapacity = 1000000
multiblockPlacementToolBaseEnergyCost = 200

# 黑名单物品
itemBlacklist = []
```

## 致谢 & 鸣谢

### 代码参考与许可证

#### ME 多方块放置工具
基于 **Theta-Dev** 的 **[Construction Wand](https://github.com/Theta-Dev/ConstructionWand)**。

- **[The MIT License (MIT)](https://github.com/Theta-Dev/ConstructionWand/blob/1.21/LICENSE)**

#### 轮盘菜单 (G 键)
基于 **baileyholl** 的 **[Ars Nouveau](https://github.com/baileyholl/Ars-Nouveau)**。

- **[GNU Lesser General Public License v3.0](https://github.com/baileyholl/Ars-Nouveau/blob/main/license.txt)**

#### Applied Energistics 2
使用了 **[Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2)** API 并参考了其实现。

- Licensed under the [GNU Lesser General Public License v3.0 (LGPL-3.0)](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/main/LICENSE).<br>
- The API is licensed under the MIT License.

### 资源与许可证

- **GUI 材质**：基于 **Applied Energistics 2** 材质修改。
  - Copyright (c) 2020, Ridanisaurus Rid
  - Copyright (c) 2013 - 2020 AlgorithmX2 et al
  - License: **[CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/)**

- **物品模型和材质**：由 **_leng (麦淇淋)** 创作。
  - Copyright (c) 2025-2026 _leng
  - License: **[CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/)**


- **源码**：
  - License **GNU Lesser General Public License v3.0** (LGPL-3.0-only).

## 贡献

欢迎贡献！可以提交 Issue 或 Pull Request。
