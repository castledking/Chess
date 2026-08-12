<p align="center">
  <img src="https://castled.codes/assets/chess-banner.png" alt="Chess" width="637">
</p>

<h3 align="center">Complete timed chess matches in Minecraft's native Paper dialog UI</h3>

<p align="center">
  <a href="https://github.com/castledking/Chess/actions/workflows/build.yml"><img src="https://github.com/castledking/Chess/actions/workflows/build.yml/badge.svg" alt="Build verification"></a>
  <a href="https://discord.com/invite/pCKdCX6nYr"><img src="https://img.shields.io/badge/Discord-Community-5865F2?style=flat&logo=discord&logoColor=white" alt="Discord community"></a>
  <a href="https://github.com/castledking/Chess/issues"><img src="https://img.shields.io/badge/GitHub-Issues-181717?style=flat&logo=github" alt="GitHub issues"></a>
</p>

Chess turns Paper's native dialog system into a live, clickable 8x8 board. Players can challenge
each other, choose a time control, and play a complete match without inventory menus, physical
boards, or extra runtime dependencies.

## Features

- A native Paper dialog with 64 individually clickable squares.
- Legal-move, selected-square, last-move, and check highlighting.
- Castling, en passant, promotion, checkmate, stalemate, and insufficient-material draws.
- Seventeen clock presets, including increment modes from `1|1` through `15|10`.
- Player-relative board orientation plus an in-game flip control.
- Live clocks, captured-piece displays, draw offers, and resign confirmation.
- A bundled board resource pack with automatic ResourcePackManager integration.
- A pack-free Unicode fallback when `ui.dialog.use-glyphs` is disabled.
- Configurable sounds, dialog labels, player messages, request expiry, and UI behavior.
- Folia-safe scheduling for commands, game state, clocks, dialogs, and pack delivery.

## The board in action

<p align="center">
  <img src="https://castled.codes/assets/chess-dialog.png" alt="A timed Chess match in Minecraft's native dialog UI" width="353">
</p>

## Requirements

- Java 21.
- Paper 1.21.11, or Folia based on the same API.
- Clients must accept the resource pack for the textured board. A Unicode fallback is available.

Chess uses Paper's native Dialog API and therefore does not run on a standard Spigot server.
ResourcePackManager is optional; the chess engine and resource pack are bundled into `Chess.jar`.

## Installation

1. Download `Chess.jar` and place it in the server's `plugins` directory.
2. Start or restart the server once to generate `plugins/Chess/settings.yml`, `messages.yml`, and
   `resourcepack.zip`.
3. Configure resource-pack delivery:
   - With ResourcePackManager installed, Chess registers its bundled pack automatically.
   - Without it, host `plugins/Chess/resourcepack.zip` and set its public URL at
     `resource-pack.url` in `settings.yml`.
   - To manage or merge the pack yourself, set `resource-pack.use-resourcepack` to `false`.
4. Restart after changing configuration files.

## Commands

| Command | Description |
| --- | --- |
| `/chess duel <player> <time>` | Send a timed duel request. |
| `/chess accept <player>` | Accept that player's request and open the board. |
| `/chess decline <player>` | Decline that player's request. |
| `/chess open` | Reopen the board for your active game. |
| `/chess help` | Show the command reference in game. |

The time argument is minutes, optionally followed by a `|` and increment seconds. Available modes
are `1`, `1|1`, `2|1`, `3`, `3|2`, `5`, `5|2`, `5|5`, `10`, `10|5`, `15|10`, `30`, `60`,
`120`, `180`, `240`, and `300`. Tab completion lists every valid mode.

There are no permission nodes in version 1.0.0; every player can use `/chess`.

## Configuration

- `settings.yml` controls duel expiry, draw and resign timing, sounds, board rendering, and
  resource-pack delivery.
- `messages.yml` contains player-facing chat messages and MiniMessage dialog labels.

The board can follow each player's color, show or hide coordinates, legal moves, last moves,
captured pieces, and clocks, and use textured glyphs or standard Unicode pieces.

## Building

Chess uses Java 21 and Gradle. Its bundled rules engine comes from the
[PocketChess](https://github.com/dxzell/PocketChess) API and common modules, currently pinned to
commit `f7288f0431c60eb889070bd3ced80d02905169ec` by CI.

```bash
git clone https://github.com/dxzell/PocketChess.git
mvn --file PocketChess/pom.xml --projects pocket-chess-api,pocket-chess-common --also-make install -DskipTests
gradle clean build
```

The publishable shaded artifact is `build/libs/Chess.jar`.

## Support

- [Report a bug](https://github.com/castledking/Chess/issues)
- [Join the Discord community](https://discord.com/invite/pCKdCX6nYr)
- [Download releases](https://github.com/castledking/Chess/releases)
