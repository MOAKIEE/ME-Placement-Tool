# ME Placement Tool

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.4.10-orange.svg)](https://files.minecraftforge.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

[🇨🇳 中文文档](docs/README_CN.md)

A Minecraft Forge mod that adds a placement tool for Applied Energistics 2, allowing you to place blocks, AE2 parts, and fluids directly from your ME network.

## Features

- **ME Network Integration** - Links to your AE network via Security Terminal or ME Controller
- **3x3 Ghost Slot GUI** - Configure up to 9 different items/fluids to place
- **Smart Placement** - Automatically handles:
  - Regular blocks
  - AE2 cable parts (buses, panels, etc.)
  - Fluids (as fluid blocks in the world)
- **Slot Selection** - Scroll wheel to switch between configured slots
- **HUD Overlay** - Shows currently selected item and network status
- **JEI Integration** - Drag items/fluids from JEI directly into ghost slots

## Requirements

- Minecraft 1.20.1
- Forge 47.4.10+
- Applied Energistics 2 (AE2)
- JEI (optional, for drag-to-ghost slot support)

## Installation

1. Download the latest release from the [Releases](../../releases) page
2. Place the `.jar` file in your `mods` folder
3. Make sure you have AE2 installed

## Usage

### Linking to ME Network

1. Craft the ME Placement Tool
2. Sneak + Right-click on an **ME Controller** or **Security Terminal** to link
3. The tool will now have access to your ME network's inventory

### Configuring Slots

1. Right-click with the tool to open the configuration GUI
2. Place items or fluids in the 3x3 grid (ghost slots)
3. Items are not consumed - they define what to place from the network

### Placing Items

1. Use scroll wheel to select a slot
2. Right-click on a block to place the selected item
3. Items/fluids are extracted from the linked ME network

### Controls

| Action | Key |
|--------|-----|
| Open GUI | Right-click (in air) |
| Place Item | Right-click (on block) |
| Switch Slot | Scroll Wheel |
| Link to Network | Sneak + Right-click on Controller/Security Terminal |

## Building from Source

```bash
git clone https://github.com/yourusername/ME-Placement-Tool.git
cd ME-Placement-Tool
./gradlew build
```

The built jar will be in `build/libs/`.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Credits

- **Applied Energistics 2 Team** - For the amazing AE2 mod and API
- **Minecraft Forge Team** - For the modding framework

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
