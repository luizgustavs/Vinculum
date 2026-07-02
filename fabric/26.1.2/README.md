# Vinculum

Vinculum is a Fabric mod that helps players keep their Minecraft mod list aligned with the servers they join.

When a server is running Vinculum, it advertises its Minecraft version, loader version, and installed mods through the server list. The client compares that information with the local `mods` folder before joining, then shows a clear status screen for anything that is missing or using a different version.

## Features

- Detects missing server-required mods before joining.
- Highlights version mismatches between the client and server.
- Marks client-only mods separately so players know which entries do not block joining.
- Downloads missing Fabric mods from Modrinth when a matching file is available.
- Falls back to server-provided transfers when Modrinth cannot resolve a required mod and server transfers are enabled.
- Offers a full sync mode that can match the server mod set more closely.
- Backs up mismatched or extra local mods before replacing them.
- Shows sync progress directly in the Minecraft UI.

## How It Works

Server-side Vinculum gathers the active user mods and publishes a compact compatibility payload with the server status. Client-side Vinculum reads that payload, compares it against the player's installed mods, and opens a required-mods screen when action is needed.

Players can choose to sync only the mods required to join, or run a full sync when the server allows direct transfers. After downloads complete, the game should be restarted so Minecraft can load the updated mod set.

## Configuration

Vinculum creates a `modsync.json` config file in the Fabric config directory.

```json
{
  "transferPort": 9123,
  "allowServerTransfers": true
}
```

- `transferPort`: port used for server-hosted mod transfers.
- `allowServerTransfers`: enables or disables direct downloads from the server.

Both values can also be overridden with the `modsync.transferPort` and `modsync.allowServerTransfers` system properties, or the `MODSYNC_TRANSFER_PORT` and `MODSYNC_ALLOW_SERVER_TRANSFERS` environment variables.

## Requirements

- Minecraft 26.1.2
- Fabric Loader 0.19.3 or newer
- Fabric API
- Java 25 or newer

## License

This project is licensed under the MIT License. You may reuse, modify, and distribute it, provided that the original copyright notice and license are preserved.
