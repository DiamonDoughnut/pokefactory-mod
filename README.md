# PokéFactory Legends

A NeoForge 1.21.1 Minecraft mod that integrates with Cobblemon to provide enhanced Pokédex tracking and statistics through a Go backend API with PostgreSQL database.

Designed for use with the backend and database build at https://github.com/DiamonDoughnut/pokefactory-server.

## Current Features

### Core Functionality
- **Automatic Pokédex Tracking**: Detects Pokemon captures in Cobblemon and syncs to backend database
- **Batched API Updates**: Efficient 30-second sync intervals with deduplication
- **Bidirectional Sync**: Full backup/restore capabilities between local and database
- **Player Statistics**: Track captures, shinies, and completion progress
- **Development Mode**: Single-player testing with simulated multiplayer behavior

### Architecture
```
Players ↔ Minecraft Server ↔ Go API ↔ PostgreSQL Database
```

### Commands
- **`/pftest`** (Op Level 2+): API testing, player management, Pokédex simulation
- **`/pfsync`** (Op Level 3): Database sync operations for rollback recovery

## Configuration

Edit `config/pokefactory_legends-common.toml`:
```toml
api_base_url = "http://localhost:8080/api/v1"
dev_mode = true
simulate_multiplayer = true
```

## Planned Features

### Legendary Pokemon Summoning
- **Crafting Items**: Special summoning items with unique recipes
- **Legendary Encounters**: Controlled spawning system for legendary Pokemon

### Pokedex Checking Block
- **Regional Completion**: Check completion status for all 9 regions
- **Certificates**: Earn completion certificates and bonuses
- **Progress Tracking**: Visual progress indicators and statistics

### Additional Features
Other exciting features will be revealed as they are discussed and approved by the community.

---

## Development Setup

This mod is built using the NeoForge MDK template. Credit to the NeoForged team for the excellent development framework.

### Installation

1. Clone this repository
2. Open in IntelliJ IDEA or Eclipse
3. Run `./gradlew build` to compile
4. Use `./gradlew runClient` for development testing

### IDE Setup

> **Note**: For Eclipse, use tasks in `Launch Group` instead of ones found in `Java Application`. A preparation task must run before launching the game.

If you encounter missing libraries, run:
- `gradlew --refresh-dependencies` to refresh cache
- `gradlew clean` to reset build state

### Mapping Names

This mod uses official Mojang mapping names, covered by their specific license. For the latest license text, refer to:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

## Resources

- **Community Documentation**: https://docs.neoforged.net/
- **NeoForged Discord**: https://discord.neoforged.net/
- **Cobblemon**: https://cobblemon.com/

## License

MIT License - See LICENSE file for details