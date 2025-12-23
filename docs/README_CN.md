# ME 放置工具

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.4.10-orange.svg)](https://files.minecraftforge.net/)
[![License](https://img.shields.io/badge/License-LGPL%203.0-blue.svg)](../LICENSE)
[![Version](https://img.shields.io/badge/Version-1.2.0-brightgreen.svg)](../../releases)

[🇬🇧 English](../README.md)

一个 Minecraft Forge 模组，为 Applied Energistics 2 添加放置工具，让你可以直接从 ME 网络中放置方块、AE2 部件和流体。

## 功能特性

### ME 放置工具
- **ME 网络集成** - 通过 ME 无线访问点链接到你的 AE 网络
- **智能放置** - 直接从 ME 网络放置物品：
  - 普通方块 - 作为正常方块放置
  - AE2 线缆部件（总线、面板等）- 自动附着到线缆上
  - 流体 - 作为流体源方块放置到世界中
- **3x3 配置界面** - 最多可配置 9 种不同的物品/流体进行放置
- **HUD 显示** - 显示当前选中的物品和网络连接状态
- **JEI 集成** - 可直接从 JEI 拖拽物品/流体到配置格子

### ME 多方块放置工具
- **多方块放置** - 一次放置多个方块，智能分布
- **智能放置算法** - 使用 BFS 算法在相连表面上均匀分布方块
- **可调放置数量** - 循环切换放置数量：1、8、64、256、1024 个方块
- **预览系统** - 可视化预览方块将要放置的位置
- **撤销功能** - 按 Ctrl + 左键撤销最后一次放置操作

## 前置要求

- Minecraft 1.20.1
- Forge 47.4.10+
- Applied Energistics 2 (AE2)
- JEI（可选，用于拖拽功能）

## 安装方法

1. 从 [Releases](../../releases) 页面下载最新版本
2. 将 `.jar` 文件放入 `mods` 文件夹
3. 确保已安装 AE2

## 使用方法

### ME 放置工具
通过无线访问点链接到 ME 网络，在 GUI 中配置物品，通过右键点击方块表面直接从网络放置方块。

### ME 多方块放置工具
通过无线访问点链接到 ME 网络，在 GUI 中配置物品，使用 Shift+右键切换放置数量，智能放置多个方块并支持预览和撤销。

## 配置文件

模组配置文件位于 `config/meplacementtool-common.toml`：

### 能量设置
```toml
# ME 放置工具的能量容量（默认：100000）
mePlacementToolEnergyCapacity = 100000

# ME 放置工具每次放置的能量消耗（默认：50）
mePlacementToolEnergyCost = 50

# ME 多方块放置工具的能量容量（默认：1000000）
multiblockPlacementToolEnergyCapacity = 1000000

# ME 多方块放置工具的基础能量消耗（默认：200）
# 总消耗 = 基础消耗 * 放置数量 / 64
multiblockPlacementToolBaseEnergyCost = 200
```

### 物品黑名单
```toml
# 不能放置的物品（逗号分隔的物品 ID）
# 示例：["minecraft:bedrock", "minecraft:command_block"]
itemBlacklist = []
```

## 操作按键

### ME 放置工具
| 操作 | 按键 |
|------|------|
| 打开界面 | 右键（对着空气） |
| 放置物品 | 右键（对着方块） |
| 切换上一个 | Shift + 左键（对着空气） |
| 切换下一个 | Shift + 右键（对着空气） |
| 链接网络 | 放入 ME 无线访问点 |

### ME 多方块放置工具
| 操作 | 按键 |
|------|------|
| 打开界面 | 右键（对着空气） |
| 放置方块 | 右键（对着方块） |
| 选择槽位 | Shift + 右键（对着空气） |
| 切换放置数量 | Shift + 右键（对着空气） |
| 撤销放置 | Ctrl + 左键 |
| 链接网络 | 放入 ME 无线访问点 |

## 从源码构建

```bash
git clone https://github.com/yourusername/ME-Placement-Tool.git
cd ME-Placement-Tool
./gradlew build
```

构建的 jar 文件将在 `build/libs/` 目录下。

## 许可证

本项目采用 GNU 较宽松公共许可证 第3版（LGPL-3.0-only）。

你可以在遵循 LGPL-3.0-only 条款的前提下在模组包中使用此模组。

完整许可请参阅仓库根目录的 `LICENSE` 文件。

## 致谢

- **Applied Energistics 2 团队** - 提供了优秀的 AE2 模组和 API
- **Minecraft Forge 团队** - 提供了模组开发框架

## 贡献

欢迎贡献！请随时提交 Pull Request。
