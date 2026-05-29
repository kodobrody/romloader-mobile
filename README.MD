# Romloader

Romloader is an Android app for browsing, managing, and downloading retro game files from remote services into your local library.

## Features

- Multi-service remote connection support:
  - FTP
  - SFTP
  - Nextcloud (WebDAV)
  - RomM API
- Flexible connection setup:
  - Service type selection
  - Hostname + port
  - Protocol selection (HTTP/HTTPS) for web-based services
  - RomM API token authentication
- Library browsing:
  - Platform-level navigation
  - Search
  - Downloaded-only filtering
- Download management:
  - Per-game and multi-select downloads
  - Queue-based download
  - Progress tracking
- Metadata support:
  - IGDB integration for game metadata
  - Cover artwork display in library views
- Local storage support:
  - App storage default path
  - Custom local folder selection

## RomM Support

Romloader can connect directly to RomM using:

- Protocol: HTTP or HTTPS
- Hostname/IP
- Port
- API Token

RomM is treated as an API-backed provider (not a raw filesystem), so the app maps RomM platforms and ROM files into browsable library entries.

## Getting Started

1. Build the app:

```bash
./gradlew :app:assembleDebug
```

2. Launch the app on your Android device/emulator.
3. Complete onboarding:
   - Configure remote service connection
   - Configure optional IGDB credentials
4. Open the library and browse platforms/games.
5. Select games and start downloads.

## Project

- Language: Kotlin
- UI: Jetpack Compose (Material 3)
- Build: Gradle (Kotlin DSL)

## Notes

- For HTTP endpoints on Android, network security policy may be required depending on device/API level and configuration.
- If a remote service is unreachable, verify protocol, host, port, and network reachability first.
