# AGENTS.md

Instructions for future agents working on this repository.

## Project overview

Offline Utils (`offline-utils`) is a **server-side only** mod for **Fabric and NeoForge**
that restores premium skins and adds password authentication on offline-mode Minecraft
servers.

- Display/project name: Offline Utils
- Internal mod id: `offline-utils` on Fabric and `offline_utils` on NeoForge
  (NeoForge ids cannot contain `-`)
- Fabric main class: `fr.juke.OfflineUtils`
- NeoForge main class: `fr.juke.OfflineUtils` (annotated with `@Mod`)
- Config file: `config/offline-utils.json` (same file on both loaders)
- Password file: `config/offline-utils-auth.json`
- GitHub: https://github.com/jukefr/offline-utils
- Modrinth: https://modrinth.com/mod/offline-utils (slug `offline-utils`)

The mod is deliberately server-side only. Do not add client entrypoints, keybinds, or a
client-side requirement without a very good reason.

## Features and invariants

### Skin restoration

- The skin is resolved by username through the Mojang public APIs.
- The signed `textures` property is applied to a **copy** of the `GameProfile` during login.
  Some Minecraft versions pass an immutable profile, so never mutate it in place.
- Lookup happens on the login network thread through a mixin on
  `ServerLoginPacketListenerImpl#finishLoginAndWaitForClient` (Mojang mappings) or its Yarn
  equivalent on old branches.
- Never block a login indefinitely: requests have a timeout and a failed lookup falls through
  to the default skin.
- Skin restoration is cosmetic. It is **not** proof of premium account ownership. Call this
  out in docs and command output; do not turn it into an auth check.

### Password authentication

- Passwords are PBKDF2-HMAC-SHA256 with a random salt. Never store or log plain text.
- `config/offline-utils-auth.json` is keyed by lowercase username.
- Unauthenticated players are frozen and cannot move, chat, break/place blocks, interact, or
  run commands other than `/login` and `/register`.
- Freezing is implemented by cancelling serverbound packets in `ServerGamePacketMixin`.
- `/login` and `/register` must stay usable while pending.
- Operators reset passwords with `/offlineutils reset <player>`.
- Sessions are opt-in (`auth.sessionMinutes = 0` by default) and keyed by username + IP.
- This is a password gate, not online-mode authentication. Never describe it as Mojang
  account verification.

## The most important rule

**Do not change the config keys or defaults without a migration.**

Current config is version 1:

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

If a schema change is required, bump `configVersion`, keep the old keys readable, and add a
migration path in `OfflineUtilsConfig.sanitize()`.

## Supported versions and why

Statistics checked in November 2026:

- `mc-servers.com`: 26.3 36.2%, 26.2 20.4%, 1.21.11 13.8%, 26.1.2 4.9%, 1.21.1 4.7%,
  1.20.1 3.4%.
- bStats (2 Nov 2026, 191,920 servers): 26.3 28.4%, 1.21.11 21.8%, 26.2 20.0%,
  26.1.2 6.5%, 1.21.4 3.4%.

The first release targets the versions that cover roughly 80% of servers:

| Loader | Versions | Build branch |
|---|---|---|
| Fabric | 1.21.1 | `1.21.1` |
| Fabric | 1.21.11 | `1.21.11` |
| Fabric | 26.1, 26.1.1, 26.1.2 | `26.1.2` |
| Fabric | 26.2, 26.3 | `main` (26.3) |
| NeoForge | 1.21.1 | `neoforge-1.21.1` |
| NeoForge | 1.21.11 | `neoforge-1.21.11` |
| NeoForge | 26.1.2 | `neoforge-26.1.2` |
| NeoForge | 26.2, 26.3 | `neoforge-26.3` |

Fabric's technical minimum is 1.16.5, and NeoForge's is 1.20.1, but those versions are only a
few percent of servers. Do not add them just to say they are supported; add a branch only when
there is demand and the port is tested.

## Branch layout

`main` tracks the latest supported Minecraft version (currently 26.3). One branch per
supported line:

| Branch | Minecraft | Notes |
|---|---|---|
| `1.21.1` | 1.21.1 | Fabric, Mojang mappings |
| `1.21.11` | 1.21.11 | Fabric, Mojang mappings |
| `26.1.2` | 26.1–26.1.2 | Fabric, unobfuscated |
| `main` | 26.2–26.3 | Fabric, unobfuscated |
| `neoforge-1.21.1` | 1.21.1 | NeoForge |
| `neoforge-1.21.11` | 1.21.11 | NeoForge |
| `neoforge-26.1.2` | 26.1.2 | NeoForge |
| `neoforge-26.3` | 26.2–26.3 | NeoForge |

Use **Mojang official mappings** on every Fabric branch too. It keeps the shared code close to
the NeoForge version and avoids translating Yarn names from scratch. Loom supports
`mappings loom.officialMojangMappings()` on 1.21.x.

## Toolchains

| Branch range | Gradle | Build JDK | Loom / plugin | Java target |
|---|---|---|---|---|
| 1.21.1 – 1.21.11 Fabric | 9.8.2 | 25 | Loom 1.19.4, Mojang mappings | 21 |
| 26.x Fabric | 9.8.2 | 25 | Loom 1.19.4, no mappings | 25 |
| NeoForge 1.21.x | 9.2.1 | 21 | ModDevGradle 2.0.148 | 21 |
| NeoForge 26.x | 9.2.1 | 25 | ModDevGradle 2.0.148 | 25 |

Build commands:

```bash
# Fabric 1.21.x
JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH \
./gradlew build --no-daemon

# Fabric 26.x
JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH \
./gradlew build --no-daemon

# NeoForge 1.21.x
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
PATH=/usr/lib/jvm/java-21-openjdk/bin:$PATH \
./gradlew build --no-daemon

# NeoForge 26.x
JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH \
./gradlew build --no-daemon
```

## Code layout

Shared code lives in `fr.juke`:

- `OfflineUtils` — loader entrypoint (Fabric `ModInitializer`; NeoForge `@Mod`)
- `config.OfflineUtilsConfig` — config load/save and clamping
- `auth.AuthManager` — pending logins, commands, sessions, timeouts
- `auth.PasswordStore` — PBKDF2 hashes on disk
- `command.AuthCommands` — `/login`, `/register`, `/offlineutils`
- `skin.SkinService` — Mojang lookup, cache, signed textures
- `util.Paths` — loader-specific config directory
- `mixin.ServerLoginMixin` — applies the skin during login
- `mixin.ServerGamePacketMixin` — blocks gameplay packets while pending

Only `Paths` and the entrypoint should differ between Fabric and NeoForge. If you find
yourself forking more files, move the difference behind a small platform helper instead.

### API differences to remember

- **26.x authlib** uses record accessors: `profile.name()`, `profile.properties()`,
  `profile.id()`.
- **1.21.x authlib** uses JavaBean getters: `profile.getName()`, `profile.getProperties()`,
  `profile.getId()`.
- **26.x permissions** use `source.permissions().hasPermission(Permissions.COMMANDS_...)`;
  1.21.x uses `source.hasPermission(level)`.
- **26.x** `GameProfile` may be immutable; always rebuild it.
- **26.x** login listener field is `authenticatedProfile`; the injected method is still
  `finishLoginAndWaitForClient(GameProfile)`.
- **ServerPlayer#getGameProfile()** is `getName()` → `name()` inside the profile depending on
  authlib version.
- NeoForge uses `FMLPaths.CONFIGDIR.get()` in `Paths`; Fabric uses
  `FabricLoader.getInstance().getConfigDirectory().toPath()`.

### Mixin compatibility level

- 26.x: `JAVA_25`
- 1.21.x: `JAVA_21`

Do not lower these without testing; Mixin rejects class files newer than the configured level.

## Testing

The fastest smoke test is a real dedicated server:

```bash
mkdir -p run
printf 'eula=true\n' > run/eula.txt
printf 'online-mode=false\nserver-port=25599\nwhite-list=false\nnetwork-compression-threshold=-1\n' > run/server.properties
JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH \
./gradlew runServer --no-daemon
```

Skin restoration was verified against the real Mojang API by sending a login-start packet
for a known premium username and checking that the returned `GameProfile` contains a signed
`textures` property. A full join test also needs the configuration-state handshake; if you
write a bot for it, keep it out of the repository.

Mixins are validated at runtime only when their target classes load. A useful temporary check
is to call `Class.forName` on `ServerLoginPacketListenerImpl` and
`ServerGamePacketListenerImpl` during startup, then remove the check before committing.

## Git workflow

1. Make the change on `main` first.
2. Backport to every affected branch, keeping each era's mappings and Java target intact.
3. Build every branch.
4. Bump `mod_version` in `gradle.properties` on every affected branch.
5. Update the Minecraft dependency range in `fabric.mod.json` / `neoforge.mods.toml`.
6. Tag and release.

Use temporary worktrees for isolated builds:

```bash
git worktree add -f /tmp/opencode/ou-1.21.11 1.21.11
rm -rf /tmp/opencode/ou-1.21.11/build /tmp/opencode/ou-1.21.11/.gradle
git worktree remove --force /tmp/opencode/ou-1.21.11
```

## Release ranges

| Release key | Versions | Built from |
|---|---|---|
| `1.21.1` | 1.21.1 | `1.21.1` |
| `1.21.11` | 1.21.11 | `1.21.11` |
| `26.1` | 26.1, 26.1.1, 26.1.2 | `26.1.2` |
| `26.2` | 26.2, 26.3 | `main` |

Jar naming:

```
offline-utils-<modversion>+mc<range>.jar
offline-utils-<modversion>+mc<version>-neoforge.jar
```

Examples: `offline-utils-1.0.0+mc1.21.11.jar`,
`offline-utils-1.0.0+mc26.2-26.3.jar`,
`offline-utils-1.0.0+mc1.21.1-neoforge.jar`.

GitHub releases use one tag per Fabric range, with NeoForge jars uploaded to the matching
release. Modrinth needs separate versions per loader because the loader field differs.

## Modrinth

- Project slug: `offline-utils`
- Environment: `server_only`
- Project body mirrors `README.md`
- Loaders: `["fabric"]` or `["neoforge"]`
- Use exact game versions for NeoForge; ranges are fine for Fabric metadata but list every
  supported game version in Modrinth's `game_versions`.

The token lives in `.env` (`MODRINTH_TOKEN=...`), must stay `chmod 600`, and must never be
printed or committed. If the project does not exist yet, create it in the Modrinth dashboard
before publishing versions; the API is for versions, not first-time project creation.

## Common pitfalls

- **Mutating GameProfile properties** — 26.x profiles can be immutable. Rebuild the profile.
- **Blocking login forever** — keep the Mojang timeout small and fail open.
- **Treating skins as auth** — never do this. Usernames are user-controlled in offline mode.
- **Yarn vs Mojang mappings** — this project uses Mojang mappings everywhere to keep code
  shared. Do not mix in Yarn names on 1.21.x branches.
- **Authlib accessors** — record accessors on 26.x, getters on 1.21.x.
- **Permissions API** — changed in 26.x.
- **NeoForge mod id** — `offline_utils`, not `offline-utils`.
- **NeoForge mixins** — register the config in `neoforge.mods.toml` with `[[mixins]]`.
- **Config path** — always go through `fr.juke.util.Paths`.
- **Plain-text passwords** — never log them, even at debug level.
- **`.env` and `run/`** — both are gitignored. Keep them out of commits.
