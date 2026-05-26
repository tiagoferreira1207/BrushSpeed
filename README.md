# BrushSpeed

A Paper Minecraft plugin that lets OPs control how fast players brush suspicious sand and gravel blocks.

## Features

- Set brush speed for **all players at once** with a single command
- Set brush speed per player individually
- Speed range: 0.1x (very slow) up to 10x (instant)
- Action bar progress indicator while brushing
- All messages customizable in `config.yml`

## Requirements

- Paper 1.20.4 or newer (uses `PlayerBrushEvent` from Paper API)
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
| `/brushspeed setall <speed>` | Set brush speed for **all players** at once | OP only |
| `/brushspeed resetall` | Reset everyone back to default speed | OP only |
| `/brushspeed set <speed>` | Set your own brush speed (0.1–10.0) | OP only |
| `/brushspeed set <speed> <player>` | Set a specific player's brush speed | OP only |
| `/brushspeed reset` | Reset your own speed to default | OP only |
| `/brushspeed reload` | Reload the config | OP only |

Alias: `/bs`, `/bspeed`

## Speed priority

When a player brushes, the speed is resolved in this order:

1. **Individual speed** — set via `/brushspeed set <speed> <player>`
2. **Global speed** — set via `/brushspeed setall <speed>`
3. **Config default** — `default-speed` in `config.yml`

> Using `/brushspeed setall` clears all individual overrides so everyone gets the same speed.

## Permissions

All permissions default to **OP only**.

| Permission | Description |
|---|---|
| `brushspeed.use` | View own brush speed |
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

The plugin jar will be in `target/BrushSpeed-1.1.0.jar`.
