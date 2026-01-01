# ME Placement Tool

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1-orange.svg)](https://neoforged.net/)
[![License](https://img.shields.io/badge/License-LGPL%203.0-blue.svg)](LICENSE)

[🇨🇳 中文文档](docs/README_CN.md)

A Minecraft NeoForge mod that adds placement tools for Applied Energistics 2. Place blocks, AE2 cable parts, and fluids directly from your ME network with a single click.

## Features

### ME Placement Tool
- Place items directly from your ME network
- Supports regular blocks, AE2 cable parts (buses, panels), and fluids
- 18-slot configuration with radial menu for quick selection (Press G)
- JEI integration - drag items directly into config slots
- HUD overlay showing current selection and network status
- Memory Card support - auto-apply settings to placed blocks
- Mekanism Configuration Card support

### ME Multiblock Placement Tool
- Place multiple blocks at once using BFS algorithm
- Adjustable placement count: 1, 8, 64, 256, 1024
- Dual-layer radial menu: inner ring for count, outer ring for items
- Visual preview before placement
- Undo support (Ctrl + Left-click)
- NBT-aware placement with configurable whitelist

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1+
- Applied Energistics 2

## Optional Dependencies

- JEI (Just Enough Items) - for drag & drop support
- Mekanism - for Configuration Card support

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

# NBT ignore whitelist (for multiblock tool)
nbtIgnoreWhitelist = ["ae2:interface", "ae2:pattern_provider", ...]
```

## Acknowledgements

This project uses code from the following open source projects:

- **[Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2)** - ME network integration and wireless terminal implementation. Thanks to the AE2 team for the excellent mod and API.

- **[Ars Nouveau](https://github.com/baileyholl/Ars-Nouveau)** - Radial menu rendering implementation. The radial menu in this mod is inspired by and adapted from Ars Nouveau's GUI code.

- **[Construction Wand](https://github.com/Theta-Dev/ConstructionWand)** - Multi-block placement concepts and undo system design inspiration.

Special thanks to these projects for making their code available under open source licenses.

- **麦淇淋** - Thank you for helping us optimize the 3D models for the tools.

## License

This project is licensed under the **GNU Lesser General Public License v3.0** (LGPL-3.0-only).

You are free to use this mod in modpacks.

## Contributing

Contributions are welcome! Feel free to submit issues or pull requests.
