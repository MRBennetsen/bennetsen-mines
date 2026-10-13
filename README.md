# bennetsen-mines

![Build Status](https://github.com/bennetsen/bennetsen-mines/actions/workflows/build.yml/badge.svg)
![Java Version](https://img.shields.io/badge/Java-25-orange.svg)
![Paper Version](https://img.shields.io/badge/Paper-1.26.x-blue.svg)
![License](https://img.shields.io/badge/license-MIT-green.svg)

A high-performance, asynchronous mine management plugin for modern **Paper (1.26.x)** servers built with **Java 25**. Designed as a modern, lag-free replacement for legacy plugins like MineResetLite.

---

## Key Features

- **Blazing Fast Resets via FAWE**: Interacts with the FastAsyncWorldEdit (FAWE) API to perform asynchronous block pattern replacements without freezing the main server tick loop.
- **FancyHolograms Integration**: Automatically hooks into FancyHolograms to create and live-update a 2-line display above your mines:
  - **Line 1**: Custom mine display name (supports color codes, e.g. `&a&lG&G Mines`)
  - **Line 2**: Live countdown ticker to next reset (e.g. `&7Resets in: &f09:45`)
- **Flexible Storage**: Complete YAML-based configuration in `mines.yml` with support for custom compositions, cuboid regions, reset timers, and hologram coordinates.
- **WorldEdit Region Redefinition**: Quickly define or reshape mines using your standard WorldEdit selection wand (`//wand`).
- **Modern Paper & Folia Ready**: Employs Folia-compatible schedulers with fallback to Paper's standard BukkitScheduler.

---

## Quick Start Guide

1. **Select Region**: Hold the WorldEdit wand (`//wand`) and select Point 1 (left-click) and Point 2 (right-click) of the mine cuboid.
2. **Create the Mine**:
   ```
   /bm create quarry &a&lG&G Quarry
   ```
3. **Configure Block Percentages**:
   ```
   /bm addblock quarry stone 80.0
   /bm addblock quarry coal_ore 15.0
   /bm addblock quarry diamond_ore 5.0
   ```
4. **Spawn the Live Countdown Hologram**:
   Stand at the spot where you want the hologram display and type:
   ```
   /bm setholo quarry
   ```
5. **Set the Reset Timer** (optional, default is 600s / 10m):
   ```
   /bm setinterval quarry 300
   ```

---

## Commands & Permissions

Permission node: `bennetsenmines.admin` (Default: OP)

| Command | Description |
| :--- | :--- |
| `/bm create <name> [displayName]` | Create a new mine from your current WorldEdit cuboid selection. |
| `/bm redefine <name>` | Redefine an existing mine's cuboid bounds to your current selection. |
| `/bm addblock <name> <material> <percentage>` | Add or update a block type's percentage in the mine. |
| `/bm removeblock <name> <material>` | Remove a block type from the mine's composition. |
| `/bm setholo <name>` | Set the mine's FancyHolograms display location to your current position. |
| `/bm removeholo <name>` | Remove the mine's hologram display. |
| `/bm reset <name>` | Trigger an immediate asynchronous FAWE reset. |
| `/bm setinterval <name> <seconds>` | Set the automatic reset interval (in seconds). |
| `/bm setname <name> <displayName>` | Update the visual display name of the mine. |
| `/bm list` | View all configured mines and their countdown statuses. |
| `/bm info <name>` | Display detailed coordinates, compositions, and timers for a mine. |
| `/bm delete <name>` | Delete a mine and its associated hologram. |
| `/bm reload` | Reload configurations and hologram positions from `mines.yml`. |

---

## Configuration (`mines.yml`)

The plugin stores all mine definitions and metadata in `plugins/bennetsen-mines/mines.yml`:

```yaml
mines:
  quarry:
    display-name: "&a&lG&G Mines"
    world: "world"
    region:
      min:
        x: 100
        y: 60
        z: 100
      max:
        x: 120
        y: 75
        z: 120
    reset-interval: 600
    composition:
      STONE: 80.0
      COAL_ORE: 15.0
      DIAMOND_ORE: 5.0
    hologram:
      world: "world"
      x: 110.5
      y: 77.0
      z: 110.5
      yaw: 0.0
      pitch: 0.0
```

---

## Requirements & Dependencies

- **Java**: Java 25 or newer
- **Server Software**: Paper 1.26.x (or compatible Paper/Folia build)
- **FastAsyncWorldEdit (FAWE)**: 2.16+ (or WorldEdit 7+)
- **FancyHolograms**: 2.10+

---

## Building from Source

To compile the plugin from source:

```bash
git clone https://github.com/bennetsen/bennetsen-mines.git
cd bennetsen-mines
mvn clean package
```

The output JAR file will be generated in `target/bennetsen-mines-1.0.0-SNAPSHOT.jar`.
