# ME Placement Tool

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.4.10-orange.svg)](https://files.minecraftforge.net/)
[![License](https://img.shields.io/badge/License-LGPL%203.0-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.2.0-brightgreen.svg)](../../releases)

[🇨🇳 中文文档](docs/README_CN.md)

A Minecraft Forge mod that adds placement tools for Applied Energistics 2, allowing you to place blocks, AE2 parts, and fluids directly from your ME network.

## Features

### ME Placement Tool
- **ME Network Integration** - Links to your ME network via ME Wireless Access Point
- **Smart Placement** - Place items directly from your ME network:
  - Regular blocks - placed as normal blocks
  - AE2 cable parts (buses, panels, etc.) - attached to cables automatically
  - Fluids - placed as fluid source blocks in the world
- **3x3 Configuration GUI** - Configure up to 9 different items/fluids to place
- **HUD Overlay** - Shows currently selected item and network connection status
- **JEI Integration** - Drag items/fluids from JEI directly into configuration slots

### ME Multiblock Placement Tool
- **Multi-block Placement** - Place multiple blocks at once with intelligent distribution
- **Intelligent Placement Algorithm** - Uses BFS to evenly distribute blocks across connected surfaces
- **Adjustable Placement Count** - Cycle through placement amounts: 1, 8, 64, 256, 1024 blocks
- **Preview System** - Visual preview of where blocks will be placed
- **Undo Functionality** - Press Ctrl + Left-click to undo the last placement operation
- **Energy-based Cost** - Energy consumption scales with placement count

## Requirements

- Minecraft 1.20.1
- Forge 47.4.10+
- Applied Energistics 2 (AE2)
- JEI (optional, for drag-and-drop support)

## Installation

1. Download the latest release from the [Releases](../../releases) page
2. Place the `.jar` file in your `mods` folder
3. Make sure you have AE2 installed

## Usage

### ME Placement Tool
Link to your ME network via Wireless Access Point, configure items in the GUI, and place blocks directly from your network by right-clicking on block surfaces.

### ME Multiblock Placement Tool
Link to your ME network via Wireless Access Point, configure items in the GUI, cycle through placement counts with Shift+Right-click, and intelligently place multiple blocks with preview and undo support.

## Configuration

The mod configuration file is located at `config/meplacementtool-common.toml`:

### Energy Settings
```toml
# Energy capacity for ME Placement Tool (default: 100000)
mePlacementToolEnergyCapacity = 100000

# Energy cost per placement for ME Placement Tool (default: 50)
mePlacementToolEnergyCost = 50

# Energy capacity for ME Multiblock Placement Tool (default: 1000000)
multiblockPlacementToolEnergyCapacity = 1000000

# Base energy cost for ME Multiblock Placement Tool (default: 200)
# Total cost = baseCost * placementCount / 64
multiblockPlacementToolBaseEnergyCost = 200
```

### Item Blacklist
```toml
# Items that cannot be placed (comma-separated item IDs)
# Example: ["minecraft:bedrock", "minecraft:command_block"]
itemBlacklist = []
```

### Controls

### ME Placement Tool
| Action | Key |
|--------|-----|
| Open GUI | Right-click (in air) |
| Place Item | Right-click (on block) |
| Previous Slot | Shift + Left-click (in air) |
| Next Slot | Shift + Right-click (in air) |
| Link to Network | Place into ME Wireless Access Point |

### ME Multiblock Placement Tool
| Action | Key |
|--------|-----|
| Open GUI | Right-click (in air) |
| Place Blocks | Right-click (on block) |
| Select Slot | Shift + Right-click (in air) |
| Cycle Placement Count | Shift + mouse wheel |
| Undo Last Placement | Ctrl + Left-click |
| Link to Network | Place into ME Wireless Access Point |


## License

This project is licensed under the GNU Lesser General Public License v3.0 (LGPL-3.0-only).

You are free to use this mod in modpacks under the terms of the LGPL-3.0-only license.

See the `LICENSE` file in the repository root for the full license text.

## Credits

- **Applied Energistics 2 Team** - For the amazing AE2 mod and API
- **Minecraft Forge Team** - For the modding framework

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
