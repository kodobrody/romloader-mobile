**Desktop version available here:** [https://github.com/kodobrody/romloader-desktop](https://github.com/kodobrody/romloader-desktop)
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

0. **IMPORTANT** For FTP, SFTP, and Nextcloud sources, name your platform folders using one of the aliases listed in the platform table below.
1. Install the and launch the app.
2. Complete onboarding:
   - Configure remote service connection
   - Configure optional IGDB credentials
3. Open the library and browse platforms/games.
4. Select games and start downloads.

## Platform Folder Naming (FTP/SFTP/Nextcloud)

When using filesystem-based services (FTP, SFTP, Nextcloud), Romloader maps folder names to known platforms using aliases.
Use one of the aliases below as your folder name.

| Platform | Accepted folder names (aliases) |
| --- | --- |
| PlayStation | ps1, psx, playstation, sonyplaystation |
| PlayStation 2 | ps2, playstation2, sonyplaystation2 |
| PlayStation 3 | ps3, playstation3, sonyplaystation3 |
| PlayStation 4 | ps4, playstation4, sonyplaystation4 |
| PlayStation 5 | ps5, playstation5, sonyplaystation5 |
| PSP | psp, playstationportable |
| PS Vita | psvita, vita, playstationvita |
| Nintendo Entertainment System | nes, nintendoentertainmentsystem |
| Super Nintendo | snes, supernintendo, superfamicom |
| Nintendo 64 | n64, nintendo64 |
| Game Boy | gb, gameboy |
| Game Boy Color | gbc, gameboycolor |
| Game Boy Advance | gba, gameboyadvance |
| Nintendo DS | ds, nds, nintendods |
| Nintendo 3DS | 3ds, n3ds, nintendo3ds |
| Nintendo GameCube | gamecube, gc, ngc |
| Nintendo Wii | wii |
| Nintendo Wii U | wiiu, nintendowiiu |
| Nintendo Switch | switch, nintendoswitch, nsw |
| Sega Genesis | genesis, megadrive, segagenesis, segamegadrive, md |
| Sega CD | segacd, megacd |
| Sega Saturn | saturn, segasaturn |
| Dreamcast | dreamcast, segadreamcast, dc |
| Xbox | xbox, microsoftxbox |
| Xbox 360 | xbox360, microsoftxbox360 |
| Xbox One | xboxone, microsoftxboxone |
| Xbox Series X/S | series-x-s, xboxseries, xboxseriesx, xboxseriess |
| Neo Geo | neogeo, neogeoaes, neogeomvs |
| Commodore 64 | c64, commodore64 |
| Sega Master System | sms, segamastersystem |
| Sega Game Gear | gamegear, segagamegear |
| Sega 32X | sega32x, 32x |

## Project

- Language: Kotlin
- UI: Jetpack Compose (Material 3)
- Build: Gradle (Kotlin DSL)

## Notes

- For HTTP endpoints on Android, network security policy may be required depending on device/API level and configuration.
- If a remote service is unreachable, verify protocol, host, port, and network reachability first.
