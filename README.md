# NayoGuildBridge

A client-side Fabric mod for cleaner bridged chat in Guild.

## Features

- Clean formatting for bridge-bot messages in `Guild`.
- Source detection by markers (`[TG]`, `.`, etc.) with source prefixes.
- Separate styling for sender name and message body.
- Sender nick styling via plain color or Minecraft `§` codes.
- Nick and keyword highlighting (`word=§codes` rules).
- Blocklist filtering for unwanted content.
- Optional backend sync (`/api/ingest` + `/api/poll`). (Api dont work)

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

- `config/chatbridge.json`

## License

MIT
