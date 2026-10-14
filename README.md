# bennetsen-mines

![Build Status](https://github.com/bennetsen/bennetsen-mines/actions/workflows/build.yml/badge.svg)
![Java Version](https://img.shields.io/badge/Java-25-orange.svg)
![Paper Version](https://img.shields.io/badge/Paper-1.26.x-blue.svg)
![License](https://img.shields.io/badge/license-MIT-green.svg)

A high-performance, asynchronous mine management plugin for modern **Paper (1.26.x)** servers built with **Java 25**. Designed as a modern, lag-free replacement for legacy plugins like MineResetLite.

---

## Key Features

- **Blazing Fast Resets via FAWE**: Interacts directly with the FastAsyncWorldEdit (FAWE) API to perform asynchronous block replacements without freezing the main server tick loop.
- **Anti-Suffocation Evacuation**: Safely evacuates any players standing inside the mine cuboid to a safe evacuation point (`/bm setspawn`) or top surface when the mine resets. Strictly one-way: no player teleport commands into the mine.
- **WorldGuard Localized Alerts**: Link an existing WorldGuard region (`/bm setwg <mine> <region>`) so countdown ActionBars, Titles, and Sound alerts (at 60s, 30s, 10s, 5s-1s) are sent **only to players in that mine's zone**—zero server-wide chat spam.
- **Customizable FancyHolograms Displays**: Hook into FancyHolograms to create live-updating displays above mines with fully customizable lines and dynamic placeholders (`%name%`, `%time%`, `%seconds%`, `%volume%`, `%world%`, `%status%`).
- **Status-Only GUI (`/mines` or `/bm gui`)**: An informational chest GUI showing all active prison mines, countdown timers, and ore compositions—strictly informational with zero player teleportation.
- **WorldEdit Region Redefinition**: Define or reshape mines in seconds using your standard WorldEdit selection wand (`//wand`).
- **Paper & Folia Ready**: Employs Folia GlobalRegionScheduler with fallback to Paper BukkitScheduler.

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
4. **Set Evacuation Spawn Point (Anti-Suffocation)**:
   Stand on safe ground outside or above the mine:
   ```
   /bm setspawn quarry
   ```
5. **Link WorldGuard Region** (Optional, for localized alerts):
   ```
   /bm setwg quarry quarry_region
   ```
6. **Spawn the Live Countdown Hologram**:
   Stand at the spot where you want the hologram display:
   ```
   /bm setholo quarry
   ```
7. **View Status Overview**:
   ```
   /mines
   ```

---

## Commands & Permissions

- **Admin Permission**: `bennetsenmines.admin` (Default: OP)
- **Player Command**: `/mines` (or `/minestatus` - accessible to all players)

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/mines` (or `/bm gui`) | Default (All) | Open the status-only GUI displaying all mines, timers, and ores. |
| `/bm create <name> [displayName]` | Admin | Create a new mine from your current WorldEdit cuboid selection. |
| `/bm redefine <name>` | Admin | Redefine an existing mine's cuboid bounds to your current selection. |
| `/bm addblock <name> <material> <percentage>` | Admin | Add or update a block type's percentage in the mine. |
| `/bm removeblock <name> <material>` | Admin | Remove a block type from the mine's composition. |
| `/bm setspawn <name>` | Admin | Set the safe evacuation point when the mine resets. |
| `/bm removespawn <name>` | Admin | Remove the custom evacuation point (falls back to WG / safe surface). |
| `/bm setwg <name> <region>` | Admin | Link a WorldGuard region for localized alerts and sounds. |
| `/bm removewg <name>` | Admin | Unlink WorldGuard region (falls back to proximity detection). |
| `/bm setholo <name>` | Admin | Set the mine's FancyHolograms display location to your current position. |
| `/bm removeholo <name>` | Admin | Remove the mine's hologram display. |
| `/bm sethololine <name> <line#> <text>` | Admin | Customize a specific line on the hologram. |
| `/bm addhololine <name> <text>` | Admin | Append a new line to the mine's hologram. |
| `/bm resethololines <name>` | Admin | Reset hologram lines back to default 2-line template. |
| `/bm reset <name>` | Admin | Trigger an immediate asynchronous FAWE reset. |
| `/bm setinterval <name> <seconds>` | Admin | Set the automatic reset interval (in seconds). |
| `/bm setname <name> <displayName>` | Admin | Update the visual display name of the mine. |
| `/bm list` | Admin | View all configured mines, countdowns, and hologram/spawn statuses. |
| `/bm info <name>` | Admin | Display detailed coordinates, compositions, and timers for a mine. |
| `/bm delete <name>` | Admin | Delete a mine and its associated hologram. |
| `/bm reload` | Admin | Reload configurations and hologram positions from `mines.yml`. |

---

## Hologram Placeholders

When customizing hologram lines with `/bm sethololine` or in `mines.yml`, the following placeholders are supported:

| Placeholder | Replaced With | Example |
| :--- | :--- | :--- |
| `%name%` | Custom formatted display name | `&a&lG&G Quarry` |
| `%id%` | Mine unique identifier | `quarry` |
| `%time%` | Formatted countdown | `09:45` or `01:15:00` |
| `%seconds%` | Total seconds remaining until reset | `585` |
| `%volume%` | Mine size in total blocks | `2400` |
| `%world%` | Bukkit world name | `world` |
| `%status%` | Current mine status | `&aActive` or `&eResetting...` |

---

## Evacuation Safety & WorldGuard Integration

### 1. Anti-Suffocation Evacuation
When a mine resets, the plugin detects players inside the mine cuboid and safely teleports them out with a subtle Enderman chime and title alert.

**Evacuation Destination Priority**:
1. Custom mine spawn point set via `/bm setspawn <mine>`.
2. WorldGuard region `teleport` or `spawn` flag (if a WorldGuard region is linked).
3. Centered safe surface 1.5 blocks above the top of the mine cuboid.

> **Note**: Players cannot use any command to teleport *into* mines. This protects prison progression and ensures players travel to mines on foot.

### 2. Localized Zone Alerts
By linking a WorldGuard region via `/bm setwg <mine> <region>`, countdown alerts at `60s`, `30s`, `10s`, `5s, 4s, 3s, 2s, 1s` are sent **only to players standing inside that WorldGuard region** (or within 8 blocks of the mine if unlinked).
- **ActionBars**: Live digital clock ticker (`&e&l[Mine] &7resets in &f00:30`).
- **Audio Chimes**: Rising note block plings from 5s down to 1s.
- **Titles**: Prominent 3-2-1 countdown and anvil reset chime.

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
    spawn:
      world: "world"
      x: 110.5
      y: 78.0
      z: 98.5
      yaw: 180.0
      pitch: 0.0
    worldguard-region: "quarry_mine"
    hologram:
      world: "world"
      x: 110.5
      y: 77.0
      z: 110.5
      yaw: 0.0
      pitch: 0.0
      lines:
        - "%name%"
        - "&7Status: %status%"
        - "&7Resets in: &f%time%"
```

---

## Requirements & Dependencies

- **Java**: Java 25 or newer
- **Server Software**: Paper 1.26.x (or compatible Paper/Folia build)
- **FastAsyncWorldEdit (FAWE)**: 2.16+ (or WorldEdit 7+)
- **FancyHolograms**: 2.10+
- **WorldGuard**: 7.0+ (optional, for localized region alerts & fallback evacuation)

---

## Building from Source

To compile the plugin from source:

```bash
git clone https://github.com/bennetsen/bennetsen-mines.git
cd bennetsen-mines
mvn clean package
```

The output JAR file will be generated in `target/bennetsen-mines-1.0.0-SNAPSHOT.jar`.
