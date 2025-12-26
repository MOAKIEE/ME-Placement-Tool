# ME Placement Tool

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

## Requirements

- Minecraft 1.20.1
- Forge 47.4.10+
- Applied Energistics 2

## Controls

| Tool | Action | Key |
|------|--------|-----|
| Both | Open GUI | Right-click (in air) |
| Both | Place | Right-click (on block) |
| Both | Radial Menu | Hold G |
| Both | Link to Network | Insert into ME Wireless Access Point |
| Multiblock | Undo | Ctrl + Left-click |

## Configuration

Config file: `config/meplacementtool-common.toml`

```toml
# Energy settings (in AE)
mePlacementToolEnergyCapacity = 100000
mePlacementToolEnergyCost = 50
multiblockPlacementToolEnergyCapacity = 1000000
multiblockPlacementToolBaseEnergyCost = 200

# Blacklisted items
itemBlacklist = []
```

## Acknowledgements

This project uses code from the following open source projects:

- **[Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2)** - ME network integration and wireless terminal implementation. Thanks to the AE2 team for the excellent mod and API.

- **[Ars Nouveau](https://github.com/baileyholl/Ars-Nouveau)** - Radial menu rendering implementation. The radial menu in this mod is inspired by and adapted from Ars Nouveau's GUI code.

- **[Construction Wand](https://github.com/Theta-Dev/ConstructionWand)** - Multi-block placement concepts and undo system design inspiration.

Special thanks to these projects for making their code available under open source licenses.

## License

This project is licensed under the **GNU Lesser General Public License v3.0** (LGPL-3.0-only).

You are free to use this mod in modpacks.

## Contributing

Contributions are welcome! Feel free to submit issues or pull requests.
