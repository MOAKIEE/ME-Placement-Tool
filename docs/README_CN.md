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

## 致谢

本项目使用或参考了以下开源项目的代码：

- **[Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2)** - ME 网络集成和无线终端实现。感谢 AE2 团队提供的优秀模组和 API。

- **[Ars Nouveau（新生魔艺）](https://github.com/baileyholl/Ars-Nouveau)** - 轮盘菜单渲染实现。本模组的轮盘菜单参考并改编自 Ars Nouveau 的 GUI 代码。

- **[Construction Wand（建筑手杖）](https://github.com/Theta-Dev/ConstructionWand)** - 多方块放置概念和撤销系统设计的灵感来源。

感谢这些项目以开源许可证提供代码。

## 许可证

本项目采用 **GNU 宽松通用公共许可证第 3 版**（LGPL-3.0-only）。

可在模组包中自由使用本模组。

## 贡献

欢迎贡献！可以提交 Issue 或 Pull Request。
