# BrushSpeed

A Paper Minecraft plugin that lets you control how fast players brush suspicious sand and gravel blocks.

## Features

- Set brush speed per player — from 0.1x (very slow) up to 10x (instant)
- Permission-based speed tiers (VIP, Elite, etc.) configurable in `config.yml`
- Action bar progress indicator while brushing
- Admin command to set speed for other players
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

| Command | Description | Permission |
|---|---|---|
| `/brushspeed` | Show your current brush speed | `brushspeed.use` |
| `/brushspeed set <speed>` | Set your own brush speed (0.1–10.0) | `brushspeed.set` |
| `/brushspeed set <speed> <player>` | Set another player's brush speed | `brushspeed.set.others` |
| `/brushspeed reset` | Reset your speed to default | `brushspeed.use` |
| `/brushspeed reload` | Reload the config | `brushspeed.reload` |

Alias: `/bs`, `/bspeed`

## Permissions

| Permission | Default | Description |
|---|---|---|
| `brushspeed.use` | everyone | View own speed and reset |
| `brushspeed.set` | op | Set own speed |
| `brushspeed.set.others` | op | Set other players' speed |
| `brushspeed.reload` | op | Reload config |
| `brushspeed.speed.vip` | false | Gives VIP speed tier from config |
| `brushspeed.speed.elite` | false | Gives Elite speed tier from config |
| `brushspeed.speed.slow` | false | Gives slow speed tier from config |

## Configuration

```yaml
# 1.0 = vanilla, 2.0 = twice as fast, 0.5 = half speed
default-speed: 1.0

min-speed: 0.1
max-speed: 10.0

# Vanilla takes 10 strokes to excavate a block
base-strokes: 10

# Grant these speeds via brushspeed.speed.<tier> permission
permission-speeds:
  vip: 2.0
  elite: 5.0
  slow: 0.5

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

The plugin jar will be in `target/BrushSpeed-1.0.0.jar`.
