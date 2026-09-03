# Fast Rendering Resource Cache

A persistent resource cache for Genir's [Fast Rendering](https://github.com/Halke1986/starsector-render) Starsector performance overhaul.

Fast Rendering normally opens thousands of small files from Starsector and its installed mods while the game starts. This Java agent hooks Fast Rendering's resource loader and stores eligible resources in a single packed cache. The first launch builds the cache; later launches can read unchanged resources from the pack instead of opening every source file again.

This is an add-on for Fast Rendering, not a standalone Starsector mod. It does not go in the `mods` directory and will not appear in the launcher's mod list.

## Requirements

- Starsector with a working Fast Rendering installation

The agent verifies Fast Rendering's resource-loader class before changing it. If the installed Fast Rendering build is not supported, the class is left unchanged and the game continues without resource caching.

## Installation

1. Install Fast Rendering. (Link is above)
2. Download the latest `fr-resource-cache-agent.jar` from this repository's [Releases](https://github.com/GaiusCassiusL/Starsector_FR-Resource-Cache/releases).
3. Copy the JAR into the Starsector `starsector-core` directory, alongside `fr.jar`, `fr.agent.jar`, and `fr.vmparams`:

   ```text
   Starsector/
   ├── mods/
   └── starsector-core/
       ├── fr-resource-cache-agent.jar
       ├── fr.agent.jar
       ├── fr.jar
       ├── fr.vmparams
       └── fr.bat
   ```

### Java 28 `Configure_Me.cmd` setup

If you use the [Mikohime Unofficial Java 28 Configurator](https://github.com/GaiusCassiusL/Starsector_Mikohime-Unofficial-Java28-Configurator) mod, run `Configure_Me.cmd` after copying the `fr-resource-cache-agent.jar` into `starsector-core`. The configuration script detects the JAR and gives you the option to enable the resource-cache agent . No manual `fr.vmparams` changes are required.

After the script finishes, start Starsector normally with the `Miko_Rouge.bat`

### Manual setup

If you are not using the Java 28 configuration script:

1. Open `starsector-core/fr.vmparams` in a text editor.
2. Find Fast Rendering's existing agent entry:

   ```text
   -javaagent:fr.agent.jar
   ```

3. Add the resource-cache agent on the line before `-javaagent:fr.agent.jar`:

   ```text
   -javaagent:fr-resource-cache-agent.jar=gameRoot=.,installRoot=..,cacheDir=..\\fr-resource-cache,flushDelaySeconds=30,memoryCacheMiB=128,maxFileSize=1048576
   -javaagent:fr.agent.jar
   ```

4. Save the file and start Starsector with `fr.bat`.

The first launch reads resources normally while building the cache. The improvement is most noticeable on subsequent launches.

## How it works

The cache is stored in `<Starsector>/fr-resource-cache` as:

- `resources.pack` — cached resource contents
- `resources.index` — source-file metadata and locations within the pack
- `cache.lock` — prevents multiple game processes from writing the cache at once

Cached entries are checked against the source file's size and modification time. Changed resources are read from their original files and written back to the cache automatically. Unsupported, oversized, or otherwise ineligible files continue through Fast Rendering's normal file-loading path.

The cache is an optimization only. If it cannot be initialized, read, or written, the agent falls back to the original files rather than preventing the game from loading.

## Confirming it is active

Look for messages beginning with `[FR Resource Cache]` in the Fast Rendering console. A successful installation reports messages similar to:

```text
[FR Resource Cache] agent v0.2.0 enabled; cache directory: ...
[FR Resource Cache] installed packed-stream hook into Fast Rendering ResourceHandle
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
| `Fast Rendering ResourceHandle is not the supported build` | Use an older version of Fast Rendering until the cache agent is updated. |
| `Fast Rendering ResourceHandle was never loaded` | Fast Rendering is not installed correctly or the game was not started with `fr.bat` or `Miko_Rouge.bat`. |
| `another process owns the cache` | Another Starsector process is using the cache. Close it and restart; this launch safely uses the original files. |
| The game does not start after editing `fr.vmparams` | Confirm the JAR is in `starsector-core`, the filename matches exactly, and each `-javaagent` entry is on its own line. |
| Startup is not faster on the first launch | This is expected: the initial launch must populate the cache. Compare later launches instead. |

<div align="center">

![American toad](https://a-z-animals.com/media/2021/05/American-Toad-header.jpg)

*Image source: [A-Z Animals](https://a-z-animals.com/).*

</div>
