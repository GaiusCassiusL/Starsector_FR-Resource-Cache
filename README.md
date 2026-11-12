# Fast Rendering Resource Cache

A persistent resource cache for Genir's [Fast Rendering](https://github.com/Halke1986/starsector-render) Starsector performance overhaul.

Fast Rendering normally opens thousands of small files from Starsector and its installed mods while the game starts. This Java agent hooks Fast Rendering's resource loader and stores eligible resources in a single packed cache. The first launch builds the cache; later launches can read unchanged resources from the pack instead of opening every source file again.

This is an add-on for Fast Rendering, not a standalone Starsector mod. It does not go in the `mods` directory and will not appear in the launcher's mod list.

## License and disclaimer

Released into the public domain under [The Unlicense](LICENSE). Anyone may use, copy, modify, and redistribute it for any purpose.

This is an unofficial third-party add-on. It is not affiliated with or endorsed by Fast Rendering's author, Fractal Softworks, or the Starsector developers. It changes Fast Rendering's behavior at runtime and is provided as-is, without warranty. Disable it before reporting problems to the Fast Rendering author.

## Requirements

- Starsector with Fast Rendering 1.0.0 installed

Fast Rendering 1.0.0 is the supported release. Earlier versions are not supported: their `ResourceHandle` has a different shape, so the agent leaves them unchanged and the game runs without the cache.

The agent structurally validates Fast Rendering's resource-loader class before changing it. These builds are identified by hash. Other builds and forks that retain the required `ResourceHandle` API are enabled in untested compatibility mode with a warning; structurally incompatible builds are left unchanged and the game continues without resource caching.

## Installation

1. Install Fast Rendering. (Link is above)
2. Download `fr-resource-cache-agent.jar` from this repository's [Releases](https://github.com/GaiusCassiusL/Starsector_FR-Resource-Cache/releases).
3. Copy it into the Starsector `starsector-core` directory alongside `fr.jar` and `fr.vmparams`:

   ```text
   Starsector/
   ├── mods/
   └── starsector-core/
       ├── fr-resource-cache-agent.jar
       ├── fr.jar
       ├── fr.vmparams
       └── fr.bat
   ```

### Java 28 `Configure_Me.cmd` setup

If you use the [Mikohime Unofficial Java 28 Configurator](https://github.com/GaiusCassiusL/Starsector_Mikohime-Unofficial-Java28-Configurator) mod, run `Configure_Me.cmd` after copying the `fr-resource-cache-agent.jar` into `starsector-core`. The configuration script detects the JAR and gives you the option to enable the resource-cache agent. No manual `fr.vmparams` changes are required.

After the script finishes, start Starsector normally with `Miko_Rouge.bat`.

### Manual setup

If you are not using the Java 28 configuration script:

1. Open `starsector-core/fr.vmparams` in a text editor.
2. Find Fast Rendering's existing agent entry:

   ```text
   -javaagent:fr.jar
   ```

   3. Add the resource-cache agent on the line before `-javaagent:fr.jar`, using the example for your operating system below.
   4. Save the file and start Starsector.

   #### Windows

   ```text
   -javaagent:fr-resource-cache-agent.jar=gameRoot=.,installRoot=..,cacheDir=..\fr-resource-cache,flushDelaySeconds=30,maxFileSize=1048576
   -javaagent:fr.jar
   ```

   Start Starsector with `fr.bat`.

   #### Linux

   ```text
   -javaagent:fr-resource-cache-agent.jar=gameRoot=.,installRoot=.,cacheDir=./fr-resource-cache,flushDelaySeconds=30,maxFileSize=1048576
   -javaagent:fr.jar
   ```

   Start Starsector with its normal Fast Rendering launch script.

The first launch reads resources normally while building the cache. The improvement is most noticeable on subsequent launches.

## Write behavior and validation

The agent writes new cache misses on one bounded background thread so the game can continue loading while the pack is updated. The queue retains at most 256 entries and 32 MiB by default. If either limit is reached, that entry is not persisted; the source read still succeeds and the agent can cache it on a later launch.

The agent also records the first-access order of eligible resources. When compaction is next required, live pack entries are rewritten in the previous launch's startup order to favor sequential reads.

Validation-mode changes take effect for existing entries. Switching from `metadata` to `sampled` or `strong` upgrades entries as they are used.

The in-memory cache is off by default. Set `memoryCacheMiB` to enable it; it then applies the selected validation mode.

Optional agent settings:

| Setting | Default | Purpose |
| --- | --- | --- |
| `memoryCacheMiB` | `0` | In-memory cache size in MiB (0 disables it, maximum 4096). |
| `flushDelaySeconds` | `30` | Delay before pending index changes are flushed. |
| `maxFileSize` | `1048576` | Largest file, in bytes, that is cached (maximum 16777216). |
| `extensions` | built-in list | Semicolon-separated file extensions to cache, e.g. `.json;.csv`. |
| `asyncWrites` | `true` | Set to `false` to persist cache misses synchronously. |
| `writeQueueEntries` | `256` | Maximum number of queued writes. |
| `writeQueueMiB` | `32` | Maximum source bytes retained by queued writes. |
| `validationMode` | `metadata` | Select `metadata`, `sampled`, or `strong` validation. |

## How it works

The cache is stored in `<Starsector>/fr-resource-cache` as:

- `resources.pack` — cached resource contents
- `resources.index` — source-file metadata and locations within the pack
- `resources.order` — first-access order used by the next compaction
- `cache.lock` — prevents multiple game processes from writing the cache at once

By default, cached entries are checked against the source file's size and modification time (`validationMode=metadata`). Changed resources are read from their original files and written back to the cache automatically. Unsupported, oversized, or otherwise ineligible files continue through Fast Rendering's normal file-loading path.

The cache is an optimization only. If it cannot be initialized, read, or written, the agent falls back to the original files rather than preventing the game from loading.

## Confirming it is active

Look for messages beginning with `[FR Resource Cache]` in the Fast Rendering console. A successful installation reports messages similar to:

```text
[FR Resource Cache] agent v0.6.1 enabled; cache directory: ...
[FR Resource Cache] installed packed-stream hook into Fast Rendering ResourceHandle (Fast Rendering 1.0.0)
```

A cache summary is printed when the game exits.

## Updating

Replace `fr-resource-cache-agent.jar` with the new release while the game is closed.

It is safe to delete the `fr-resource-cache` directory at any time while the game is closed. The agent recreates it on the next launch.

## Uninstalling

1. Remove `-javaagent:fr-resource-cache-agent.jar` from `fr.vmparams`.
2. Delete `starsector-core/fr-resource-cache-agent.jar`.
3. Optionally delete the generated `fr-resource-cache` directory.

No save data or mod files are changed by the cache.

## Troubleshooting

| Message or symptom | Meaning / fix |
| --- | --- |
| `Fast Rendering ResourceHandle hash is not recognized` | The class passed structural checks and caching was enabled in untested compatibility mode. Disable FR Resource Cache first if resource-loading problems occur. |
| `Fast Rendering ResourceHandle failed structural compatibility checks` | The installed build changed required APIs. Resource caching was not applied. |
| `Fast Rendering ResourceHandle was never loaded` | Fast Rendering is not installed correctly or the game was not started with `fr.bat` or `Miko_Rouge.bat`. |
| `another process owns the cache` | Another Starsector process is using the cache. Close it and restart; this launch safely uses the original files. |
| The game does not start after editing `fr.vmparams` | Confirm the JAR is in `starsector-core`, the filename matches exactly, and each `-javaagent` entry is on its own line. |
| Startup is not faster on the first launch | This is expected: the initial launch must populate the cache. Compare later launches instead. |

<div align="center">

![American toad](https://a-z-animals.com/media/2021/05/American-Toad-header.jpg)

*Image source: [A-Z Animals](https://a-z-animals.com/).*

</div>
