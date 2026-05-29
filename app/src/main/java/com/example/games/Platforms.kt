package com.example.games

data class PlatformDefinition(
    val displayName: String,
    val aliases: List<String>,
    val extensions: List<String>,
    val igdbPlatformIds: List<Int>
)

val GENERIC_ROM_EXTENSIONS = listOf(
    "7z", "bin", "cdi", "chd", "cue", "cso", "gb", "gba", "gbc", "gcm", "gen", "img", "iso", "md",
    "n64", "nds", "nes", "nrg", "pbp", "pkg", "rom", "sfc", "smc", "v64", "vpk", "wbfs", "wua",
    "wud", "wux", "xci", "zip", "z64"
)

val PLATFORM_DEFINITIONS = listOf(
    PlatformDefinition(
        displayName = "PlayStation",
        aliases = listOf("ps1", "psx", "playstation", "sonyplaystation"),
        extensions = listOf("bin", "chd", "cue", "img", "iso", "mdf", "pbp"),
        igdbPlatformIds = listOf(7)
    ),
    PlatformDefinition(
        displayName = "PlayStation 2",
        aliases = listOf("ps2", "playstation2", "sonyplaystation2"),
        extensions = listOf("bin", "chd", "cue", "img", "iso", "mdf", "nrg"),
        igdbPlatformIds = listOf(8)
    ),
    PlatformDefinition(
        displayName = "PlayStation 3",
        aliases = listOf("ps3", "playstation3", "sonyplaystation3"),
        extensions = listOf("iso", "pkg"),
        igdbPlatformIds = listOf(9)
    ),
    PlatformDefinition(
        displayName = "PlayStation 4",
        aliases = listOf("ps4", "playstation4", "sonyplaystation4"),
        extensions = listOf("iso", "pkg"),
        igdbPlatformIds = listOf(48)
    ),
    PlatformDefinition(
        displayName = "PlayStation 5",
        aliases = listOf("ps5", "playstation5", "sonyplaystation5"),
        extensions = listOf("iso", "pkg"),
        igdbPlatformIds = listOf(167)
    ),
    PlatformDefinition(
        displayName = "PSP",
        aliases = listOf("psp", "playstationportable"),
        extensions = listOf("cso", "iso", "pbp"),
        igdbPlatformIds = listOf(38)
    ),
    PlatformDefinition(
        displayName = "PS Vita",
        aliases = listOf("psvita", "vita", "playstationvita"),
        extensions = listOf("vpk"),
        igdbPlatformIds = listOf(46)
    ),
    PlatformDefinition(
        displayName = "Nintendo Entertainment System",
        aliases = listOf("nes", "nintendoentertainmentsystem"),
        extensions = listOf("fds", "nes", "zip"),
        igdbPlatformIds = listOf(18)
    ),
    PlatformDefinition(
        displayName = "Super Nintendo",
        aliases = listOf("snes", "supernintendo", "superfamicom"),
        extensions = listOf("fig", "sfc", "smc", "zip"),
        igdbPlatformIds = listOf(19)
    ),
    PlatformDefinition(
        displayName = "Nintendo 64",
        aliases = listOf("n64", "nintendo64"),
        extensions = listOf("n64", "v64", "z64"),
        igdbPlatformIds = listOf(4)
    ),
    PlatformDefinition(
        displayName = "Game Boy",
        aliases = listOf("gb", "gameboy"),
        extensions = listOf("gb", "zip"),
        igdbPlatformIds = listOf(33)
    ),
    PlatformDefinition(
        displayName = "Game Boy Color",
        aliases = listOf("gbc", "gameboycolor"),
        extensions = listOf("gbc", "zip"),
        igdbPlatformIds = listOf(22)
    ),
    PlatformDefinition(
        displayName = "Game Boy Advance",
        aliases = listOf("gba", "gameboyadvance"),
        extensions = listOf("gba", "zip"),
        igdbPlatformIds = listOf(24)
    ),
    PlatformDefinition(
        displayName = "Nintendo DS",
        aliases = listOf("ds", "nds", "nintendods"),
        extensions = listOf("nds", "zip"),
        igdbPlatformIds = listOf(20)
    ),
    PlatformDefinition(
        displayName = "Nintendo 3DS",
        aliases = listOf("3ds", "n3ds", "nintendo3ds"),
        extensions = listOf("3ds", "cci", "cxi"),
        igdbPlatformIds = listOf(37)
    ),
    PlatformDefinition(
        displayName = "Nintendo GameCube",
        aliases = listOf("gamecube", "gc", "ngc"),
        extensions = listOf("gcm", "iso"),
        igdbPlatformIds = listOf(21)
    ),
    PlatformDefinition(
        displayName = "Nintendo Wii",
        aliases = listOf("wii"),
        extensions = listOf("ciso", "iso", "wbfs"),
        igdbPlatformIds = listOf(5)
    ),
    PlatformDefinition(
        displayName = "Nintendo Wii U",
        aliases = listOf("wiiu", "nintendowiiu"),
        extensions = listOf("app", "wua", "wud", "wux"),
        igdbPlatformIds = listOf(41)
    ),
    PlatformDefinition(
        displayName = "Nintendo Switch",
        aliases = listOf("switch", "nintendoswitch", "nsw"),
        extensions = listOf("nsp", "nsz", "xci"),
        igdbPlatformIds = listOf(130)
    ),
    PlatformDefinition(
        displayName = "Sega Genesis",
        aliases = listOf("genesis", "megadrive", "segagenesis", "segamegadrive", "md"),
        extensions = listOf("bin", "gen", "md", "smd", "zip"),
        igdbPlatformIds = listOf(29)
    ),
    PlatformDefinition(
        displayName = "Sega CD",
        aliases = listOf("segacd", "megacd"),
        extensions = listOf("bin", "chd", "cue", "iso"),
        igdbPlatformIds = listOf(78)
    ),
    PlatformDefinition(
        displayName = "Sega Saturn",
        aliases = listOf("saturn", "segasaturn"),
        extensions = listOf("bin", "chd", "cue", "iso"),
        igdbPlatformIds = listOf(32)
    ),
    PlatformDefinition(
        displayName = "Dreamcast",
        aliases = listOf("dreamcast", "segadreamcast", "dc"),
        extensions = listOf("cdi", "chd", "gdi"),
        igdbPlatformIds = listOf(23)
    )
)

fun getPlatformDisplayName(platformId: String): String {
    return PLATFORM_DEFINITIONS.find { p ->
        p.aliases.any { it.equals(platformId, ignoreCase = true) }
    }?.displayName ?: platformId
}
