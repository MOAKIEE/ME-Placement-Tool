# ME Placement Tool

[![Modrinth](https://img.shields.io/modrinth/dt/uDNrWncj?style=flat&logo=modrinth&label=Modrinth)](https://modrinth.com/project/uDNrWncj)
[![CurseForge](https://img.shields.io/curseforge/dt/1408718?style=flat&logo=curseforge&label=CurseForge)](https://www.curseforge.com/minecraft/mc-mods/me-placement-tool)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.4.10-orange.svg)](https://files.minecraftforge.net/)
[![License](https://img.shields.io/badge/License-LGPL%203.0-blue.svg)](LICENSE)

[🇨🇳 中文文档](docs/README_CN.md)

A Minecraft Forge mod that adds placement tools for Applied Energistics 2. Place blocks, AE2 cable parts, and fluids directly from your ME network with a single click.

## Features

### ME Placement Tool
- Place items directly from your ME network
- Supports regular blocks, AE2 cable parts (buses, panels), and fluids
- 18-slot configuration with radial menu for quick selection (Press G)
- JEI integration - drag items directly into config slots
- HUD overlay showing current selection and network status

### ME Multiblock Placement Tool
- Place multiple blocks at once using BFS algorithm
- Adjustable placement count: 1, 8, 64, 256, 1024
- Dual-layer radial menu: inner ring for count, outer ring for items
- Visual preview before placement
- Undo support (Ctrl + Left-click)

### ME Cable Placement Tool
- Efficiently place AE2 cables
- Three models: line, fill and branch
- Smart dyeing system: uses dye from Offhand > ME Network > Inventory
- Supports "Spectral Key" upgrade for free coloring

## Requirements

- Minecraft 1.20.1
- Forge 47.4.10+
- Applied Energistics 2

## Controls

### General Controls (All Tools)
- **Right-click (in air)**: Open configuration GUI
- **Right-click (on block)**: Place item / Use tool
- **Hold G**: Open Radial Menu (Quick select items/modes)
- **Link to Network**: Place the tool into an ME Wireless Access Point to bind it to your network.

### ME Multiblock Placement Tool
- **Ctrl + left-click**: Undo last operation

## Configuration

The mod offers a comprehensive configuration file located at `config/meplacementtool-common.toml`.

### Available Settings

- **Energy Management**: 
  - Configure the internal energy capacity for each tool type.
  - thorough adjustments for energy cost per placement action.

- **Item Blacklist**: 
  - Define a list of items that are forbidden from being placed by the tools.

- **NBT Matching Rules**:
  - **Mod Whitelist**: Specify Mod IDs that require strict NBT matching.
  - **Item Whitelist**: Specify exact items (e.g., `ae2:facade`) that must preserve NBT data during network searches.
  - By default, `ae2:facade` is enabled to ensure correct facade textures are placed.

## Acknowledgements & Credits

### Code References & Licenses

#### ME Multiblock Placement Tool
Based on **[Construction Wand](https://github.com/Theta-Dev/ConstructionWand)** by **Theta-Dev**.

- **[The MIT License (MIT)](https://github.com/Theta-Dev/ConstructionWand/blob/1.21/LICENSE)**

#### Radial Menu (G Key)
Based on **[Ars Nouveau](https://github.com/baileyholl/Ars-Nouveau)** by **baileyholl**.

- **[GNU Lesser General Public License v3.0](https://github.com/baileyholl/Ars-Nouveau/blob/main/license.txt)**

#### Applied Energistics 2
Uses the **[Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2)** API and references its implementation.

- Licensed under the [GNU Lesser General Public License v3.0 (LGPL-3.0)](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/main/LICENSE).<br>
- The API is licensed under the MIT License.

### Assets & Licenses

- **GUI Textures**: Modified based on **Applied Energistics 2** textures.
  - Copyright (c) 2020, Ridanisaurus Rid
  - Copyright (c) 2013 - 2020 AlgorithmX2 et al
  - License: **[CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/)**

- **Item Models and Textures**: Created by **_leng (麦淇淋)**.
  - Copyright (c) 2025-2026 _leng
  - License: **[CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/)**


- **Source**：
  - License **GNU Lesser General Public License v3.0** (LGPL-3.0-only).


## Contributing

Contributions are welcome! Feel free to submit issues or pull requests.
