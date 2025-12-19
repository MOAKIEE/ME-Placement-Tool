# ME Placement Tool

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.4.10-orange.svg)](https://files.minecraftforge.net/)
[![License](https://img.shields.io/badge/License-LGPL%203.0-blue.svg)](LICENSE)

[🇨🇳 中文文档](docs/README_CN.md)

A Minecraft Forge mod that adds a placement tool for Applied Energistics 2, allowing you to place blocks, AE2 parts, and fluids directly from your ME network.

## Features

- **ME Network Integration** - Links to your ME network via ME Wireless Access Point
- **Smart Placement** - Place items directly from your ME network:
  - Regular blocks - placed as normal blocks
  - AE2 cable parts (buses, panels, etc.) - attached to cables automatically
  - Fluids - placed as fluid source blocks in the world
- **3x3 Configuration GUI** - Configure up to 9 different items/fluids to place
- **HUD Overlay** - Shows currently selected item and network connection status
- **JEI Integration** - Drag items/fluids from JEI directly into configuration slots

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

### Linking to ME Network

1. Craft the ME Placement Tool
2. Place the tool into an **ME Wireless Access Point** to link
3. The tool will now have access to your ME network's inventory

### Configuring Items

1. Right-click with the tool (in air) to open the configuration GUI
2. Place items or fluids in the 3x3 grid
3. Items are not consumed - they define what to place from the network

### Placing Items

1. Use Shift + Left/Right-click (in air) to select a configured slot
2. Right-click on a block to place the selected item
3. Items/fluids are extracted from the linked ME network

### Controls

| Action | Key |
|--------|-----|
| Open GUI | Right-click (in air) |
| Place Item | Right-click (on block) |
| Previous Slot | Shift + Left-click (in air) |
| Next Slot | Shift + Right-click (in air) |
| Link to Network | Place into ME Wireless Access Point |

## Building from Source

```bash
git clone https://github.com/yourusername/ME-Placement-Tool.git
cd ME-Placement-Tool
./gradlew build
```

The built jar will be in `build/libs/`.

## License

This project is licensed under the GNU Lesser General Public License v3.0 (LGPL-3.0-only).

You are free to use this mod in modpacks under the terms of the LGPL-3.0-only license.

See the `LICENSE` file in the repository root for the full license text.

## Credits

- **Applied Energistics 2 Team** - For the amazing AE2 mod and API
- **Minecraft Forge Team** - For the modding framework

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
