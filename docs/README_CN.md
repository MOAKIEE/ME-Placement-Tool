# ME 放置工具

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.4.10-orange.svg)](https://files.minecraftforge.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](../LICENSE)

[🇬🇧 English](../README.md)

一个 Minecraft Forge 模组，为 Applied Energistics 2 添加放置工具，让你可以直接从 ME 网络中放置方块、AE2 部件和流体。

## 功能特性

- **ME 网络集成** - 通过安全终端或 ME 控制器链接到你的 AE 网络
- **3x3 幽灵格子 GUI** - 最多可配置 9 种不同的物品/流体进行放置
- **智能放置** - 自动处理：
  - 普通方块
  - AE2 线缆部件（总线、面板等）
  - 流体（作为流体方块放置到世界中）
- **格子选择** - 使用滚轮切换配置的格子
- **HUD 显示** - 显示当前选中的物品和网络状态
- **JEI 集成** - 可直接从 JEI 拖拽物品/流体到幽灵格子

## 前置要求

- Minecraft 1.20.1
- Forge 47.4.10+
- Applied Energistics 2 (AE2)
- JEI（可选，用于拖拽到幽灵格子功能）

## 安装方法

1. 从 [Releases](../../releases) 页面下载最新版本
2. 将 `.jar` 文件放入 `mods` 文件夹
3. 确保已安装 AE2

## 使用方法

### 链接到 ME 网络

1. 合成 ME 放置工具
2. 潜行 + 右键点击 **ME 控制器** 或 **安全终端** 进行链接
3. 工具现在可以访问你的 ME 网络物品库存

### 配置格子

1. 右键（对着空气）打开配置 GUI
2. 在 3x3 网格中放入物品或流体（幽灵格子）
3. 物品不会被消耗 - 它们只是定义从网络中放置什么

### 放置物品

1. 使用滚轮选择格子
2. 右键点击方块进行放置
3. 物品/流体将从链接的 ME 网络中提取

### 操作按键

| 操作 | 按键 |
|------|------|
| 打开 GUI | 右键（对着空气） |
| 放置物品 | 右键（对着方块） |
| 切换格子 | 滚轮 |
| 链接网络 | 潜行 + 右键点击控制器/安全终端 |

## 从源码构建

```bash
git clone https://github.com/yourusername/ME-Placement-Tool.git
cd ME-Placement-Tool
./gradlew build
```

构建的 jar 文件将在 `build/libs/` 目录下。

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](../LICENSE) 文件。

## 致谢

- **Applied Energistics 2 团队** - 提供了优秀的 AE2 模组和 API
- **Minecraft Forge 团队** - 提供了模组开发框架

## 贡献

欢迎贡献！请随时提交 Pull Request。
