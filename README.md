# Offline Utils

**Premium skin restoration and password authentication for offline-mode Minecraft servers — on Fabric and NeoForge.**

[![Modrinth](https://img.shields.io/modrinth/dt/offline-utils?style=flat-square&logo=modrinth&label=Modrinth)](https://modrinth.com/mod/offline-utils)
[![GitHub release](https://img.shields.io/github/v/release/jukefr/offline-utils?style=flat-square&logo=github)](https://github.com/jukefr/offline-utils/releases)
[![License](https://img.shields.io/badge/license-GPL--3.0-blue?style=flat-square)](https://github.com/jukefr/offline-utils/blob/main/LICENSE)

Offline Utils is a server-side mod for offline-mode Minecraft servers. It brings back the
Mojang skins of premium players and adds a simple password gate so a username cannot be used
by anyone who types it first. No client mod is required.

## Features

- **Premium skin restoration** — looks up the Mojang profile for an offline player's username
  and applies the signed skin texture before the player joins the world.
- **Password authentication** — players must run `/register` on their first join and `/login`
  afterwards. Unauthenticated players cannot move, chat, break blocks, or interact.
- **Account management** — operators can reset a password with `/offlineutils reset <player>`.
- **Optional login sessions** — remember a player on the same IP for a configurable number of
  minutes.
- **Same config and commands on Fabric and NeoForge**.
- **Server-side only** — no client mod, no resource pack.

## Installation

1. Install **Fabric Loader** or **NeoForge** on your Minecraft server.
2. Download the jar for your Minecraft version from [Modrinth](https://modrinth.com/mod/offline-utils)
   or [GitHub Releases](https://github.com/jukefr/offline-utils/releases).
3. Put the jar in your server's `mods/` folder.
4. On Fabric, also install [Fabric API](https://modrinth.com/mod/fabric-api).
5. Start the server. The config is created at `config/offline-utils.json`.
6. Set `online-mode=false` in `server.properties` if it is not already off.

> This is a **server-side** mod. Players do not need to install anything.

## Supported versions

| Loader | Minecraft versions |
|---|---|
| Fabric | 1.21.1, 1.21.11, 26.1–26.1.2, 26.2–26.3 |
| NeoForge | 1.21.1, 1.21.11, 26.1.2, 26.2–26.3 |

Use the jar that matches your Minecraft version. The 26.2–26.3 jars are built from 26.3 and
declare a range; the 26.1 jars are built from 26.1.2. NeoForge publishes 26.1.2, not 26.1 or
26.1.1.

## Configuration

The config file is `config/offline-utils.json`:

```json
{
  "configVersion": 1,
  "auth": {
    "enabled": true,
    "minPasswordLength": 6,
    "maxPasswordLength": 128,
    "loginTimeoutSeconds": 120,
    "maxAttempts": 5,
    "sessionMinutes": 0
  },
  "skins": {
    "enabled": true,
    "timeoutMillis": 3000,
    "cacheMinutes": 360
  }
}
```

| Key | Description |
|---|---|
| `auth.enabled` | Turns the password gate on or off. |
| `auth.minPasswordLength` | Shortest accepted password. |
| `auth.maxPasswordLength` | Longest accepted password. |
| `auth.loginTimeoutSeconds` | How long a player may stay unauthenticated before being kicked. |
| `auth.maxAttempts` | Failed `/login` attempts before being kicked. |
| `auth.sessionMinutes` | `0` requires a password on every join. A positive value remembers the player on the same IP for that many minutes. |
| `skins.enabled` | Turns premium skin restoration on or off. |
| `skins.timeoutMillis` | Timeout for each Mojang API request. |
| `skins.cacheMinutes` | How long a resolved skin is cached in memory. |

The config is read at startup. Restart the server after editing it, or run
`/offlineutils reload` as an operator.

## Commands

| Command | Who | Description |
|---|---|---|
| `/register <password> <password>` | Players | Creates a password on first join. |
| `/login <password>` | Players | Unlocks the account on later joins. |
| `/offlineutils reload` | Operators | Reloads `config/offline-utils.json`. |
| `/offlineutils status` | Operators | Shows whether auth and skins are enabled and how many accounts exist. |
| `/offlineutils reset <player>` | Operators | Deletes a player's password so they can register again. |

Passwords are stored as salted PBKDF2 hashes in `config/offline-utils-auth.json`. Plain-text
passwords are never written to disk.

## How skin restoration works

Offline-mode servers do not contact Mojang, so the player profile they create has no skin
textures. Offline Utils looks up the username through the public Mojang APIs and copies the
signed `textures` property onto the profile during login. Because the lookup is by username,
the skin is **cosmetic only**: it does not prove that the player owns the premium account.
The password module is what protects the username on your server.

Clients that cannot reach Mojang's skin servers will still show the default skin. A failed or
timed-out lookup never blocks a login.

## Security notes

- This mod is for offline-mode servers. It is a password gate, not Mojang authentication.
- Use a unique password per server; the username alone is not a secret.
- Keep `config/offline-utils-auth.json` private. It contains password hashes.
- Operators can reset any password locally. There is no email recovery.
- Session reuse is off by default because IP addresses can change and shared IPs exist.

## FAQ

**Do players need the mod installed?**
No. It runs entirely on the server.

**Does Fabric require Fabric API?**
Yes. NeoForge does not need an extra API mod.

**Do skins work for cracked players?**
The lookup is by username, so a player using someone else's premium username will also get
that skin. This is cosmetic and is not an authentication check.

**Can I use this on an online-mode server?**
The password gate can run, but skin restoration is skipped because Mojang already provides
skins there.

**I forgot my password.**
Ask a server operator to run `/offlineutils reset <yourname>`, then register again.

**Where do I report bugs or request features?**
Use the [issue tracker](https://github.com/jukefr/offline-utils/issues).

## Links

- [Modrinth](https://modrinth.com/mod/offline-utils)
- [GitHub Releases](https://github.com/jukefr/offline-utils/releases)
- [Source code](https://github.com/jukefr/offline-utils)
- [Issues](https://github.com/jukefr/offline-utils/issues)

## License

GPL-3.0. See [LICENSE](https://github.com/jukefr/offline-utils/blob/main/LICENSE).
