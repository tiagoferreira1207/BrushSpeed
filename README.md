# BrushSpeed

A Paper Minecraft plugin that lets OPs control how fast players brush suspicious sand and gravel blocks — globally, per player, or on a **specific brush item**.

## Features

- **Enchant a specific brush** with a custom speed — the speed travels with the item
- Set brush speed for **all players at once** with a single command
- Set brush speed per player individually
- Speed range: 0.1x (very slow) up to 10x (instant)
- Action bar progress indicator while brushing
- All messages customizable in `config.yml`

## Requirements

- Paper 1.21 or newer (uses `PlayerBrushEvent` from Paper API)
- Java 21+

## Installation

1. Download the latest `.jar` from [Releases](../../releases)
2. Drop it into your server's `plugins/` folder
3. Restart the server
4. Edit `plugins/BrushSpeed/config.yml` to your liking and run `/brushspeed reload`

## Commands

| Command | Description | Who can use |
|---|---|---|
| `/brushspeed` | Show your current brush speed | OP only |
| `/brushspeed enchant <speed>` | Apply a speed to the brush you're holding | OP only |
| `/brushspeed disenchant` | Remove the custom speed from your held brush | OP only |
| `/brushspeed setall <speed>` | Set brush speed for **all players** at once | OP only |
| `/brushspeed resetall` | Reset everyone back to default speed | OP only |
| `/brushspeed set <speed>` | Set your own brush speed (0.1–10.0) | OP only |
| `/brushspeed set <speed> <player>` | Set a specific player's brush speed | OP only |
| `/brushspeed reset` | Reset your own speed to default | OP only |
| `/brushspeed reload` | Reload the config | OP only |

Alias: `/bs`, `/bspeed`

## Speed priority

When a player brushes, the speed is resolved in this order:

1. **Item speed** — set via `/brushspeed enchant` on that specific brush
2. **Individual speed** — set via `/brushspeed set <speed> <player>`
3. **Global speed** — set via `/brushspeed setall <speed>`
4. **Config default** — `default-speed` in `config.yml`

> The item speed always wins. A brush enchanted with 5x will always brush at 5x, regardless of any global or player settings.

## How to enchant a brush

1. Hold a brush in your main hand
2. Run `/brushspeed enchant 3` (or any speed from 0.1 to 10.0)
3. The brush now shows `⚡ Brush Speed: 3x` in its lore
4. Only that brush brushes at that speed — other brushes are unaffected

To remove the speed: hold the brush and run `/brushspeed disenchant`.

The speed is stored in the item's data (PersistentDataContainer), so it survives server restarts, trades, chest storage, and anvil renames.

## Permissions

All permissions default to **OP only**.

| Permission | Description |
|---|---|
| `brushspeed.use` | View own brush speed |
| `brushspeed.enchant` | Apply/remove speed on a specific brush item |
| `brushspeed.set` | Set own brush speed |
| `brushspeed.set.others` | Set another player's brush speed |
| `brushspeed.setall` | Set brush speed for all players at once |
| `brushspeed.reload` | Reload the config |

## Configuration

```yaml
# 1.0 = vanilla, 2.0 = twice as fast, 0.5 = half speed
default-speed: 1.0

min-speed: 0.1
max-speed: 10.0

# Vanilla takes 10 strokes to excavate a block
base-strokes: 10

show-progress-bar: true
```

## How it works

The plugin intercepts Paper's `PlayerBrushEvent` (fired on each brush stroke against suspicious sand/gravel). For any speed other than 1.0, it cancels the vanilla stroke and counts strokes itself — applying the speed multiplier to determine how many strokes are actually needed. Once the threshold is reached, it manually excavates the block: fetches the loot from the block entity, drops it, replaces the block with normal sand/gravel, and plays the dig sound and particles.

**Speed > 1.0:** fewer strokes needed → excavates faster  
**Speed < 1.0:** more strokes needed → excavates slower

## Building from source

```bash
mvn package
```

The plugin jar will be in `target/BrushSpeed-1.2.0.jar`.
