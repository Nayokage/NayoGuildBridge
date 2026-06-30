# NayoGuildBridge

A client-side Fabric mod for cleaner bridged chat in Guild.

## Features

- Clean formatting for bridge-bot messages in `Guild`.
- Source detection by markers (`[TG]`, `.`, etc.) with source prefixes.
- Separate styling for sender name and message body.
- Sender nick styling via plain color or Minecraft `§` codes.
- Nick and keyword highlighting (`word=§codes` rules).
- Blocklist filtering for unwanted content.
- Quote system: send to `POST /api/messages` (Discord/TG via webhook or br1dgebtw bot).
- Optional backend sync (`/api/ingest` + `/api/poll`) — see `bridge-site/QUOTES_FLOW.md`.
- Server deploy (PM2, same VDS as API): `bridge-site/DEPLOY_PM2.md`.

## Supported Versions

- Minecraft: `1.21.10`
- Java: `21`
- Fabric Loader: `0.18.4+`

## Dependencies

Required:

- [Fabric Loader](https://fabricmc.net/use/installer/)
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
- [YACL 3](https://modrinth.com/mod/yacl)

Optional:

- [Mod Menu](https://modrinth.com/mod/modmenu) — useful for opening the config from the mods list.

## Installation

1. Install Fabric Loader.
2. Put `NayoGuildBridge` and all required dependencies into your `mods` folder.
3. Launch the game.
4. Open the mod menu with `Right Shift` or `/bridge`.

## Config

Config is generated automatically at:

- `config/nayoguildbridge.json` 

## Remote Connection & Data Usage

NayoGuildBridge uses remote API and WebSocket connections to provide bridge functionality between Minecraft, Discord, and the bridge website.

### Connected Endpoints

HTTP API:

* https://api.fiokem.cc
* https://api.2297211.xyz

WebSocket:

* wss://api.fiokem.cc/ws
* wss://api.2297211.xyz/ws

### Purpose of the Connection

The remote connection is used for:

* Sending and receiving bridge messages
* Viewing Discord images in-game
* Replying to and quoting bridge messages
* Synchronizing guild bridge data

### Data Transmitted

The mod may transmit:

* Minecraft username
* Message content
* Guild information

The mod does not collect passwords, payment information, files, or other personal data.

### Open Source

All networking functionality is publicly available in the source code and can be reviewed by anyone:

https://github.com/Nayokage/NayoGuildBridge

## License

MIT