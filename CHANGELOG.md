# Changelog

## v0.5.0

### Added

- Added bounded asynchronous persistence for first-launch cache misses.
- Added persistent startup access-order tracking and access-ordered pack compaction.
- Added Fast Rendering v0.8.8 and v0.9.6 to the explicitly recognized compatible releases.

### Changed

- Cache hits now reuse one source metadata snapshot instead of repeating size and modification-time filesystem calls.
- The writer queue defaults to 256 entries and 32 MiB; saturated writes are skipped rather than blocking startup.
- Shutdown now drains queued writes (waiting at most 10 seconds) before flushing the index and reports the pack size before closing it.

### Fixed

- Existing entries now upgrade when the configured validation mode changes from `metadata` to `sampled` or `strong`.
- Memory-cache hits now enforce sampled or strong fingerprints, detecting same-size, same-timestamp source changes.
- Scheduled index flushes and queued writes can no longer race pack closure during normal shutdown.
- Fixed the cache-read fallback in the injected `ResourceHandle`, which called a method absent from Fast Rendering v0.9.6.
- Agent startup, hook installation, and the injected `ResourceHandle` now fall back to original files on any error, so unrecognized or incompatible Fast Rendering builds cannot prevent the game from running.
- Cached text decoding now matches Fast Rendering v0.9.6: strict UTF-8 first, then lenient decoding.
- The access-order file is now saved with each index flush and merged with the previous order, so short, failed, or force-closed launches no longer replace a good order.

## v0.4

### Added

- Added compatibility mode for unknown Fast Rendering builds and forks that retain the required `ResourceHandle` structure.
- Added Fast Rendering v0.8.7 to the explicitly recognized compatible releases.

### Changed

- Unknown `ResourceHandle` hashes now use the standard cache payload when structural compatibility checks pass, rather than being rejected solely because their version is unrecognized.
- Compatibility validation now checks the target class, superclass, required public instance methods, and required `FileHandle` field references before applying the cache hook.
- Untested compatible builds now emit prominent startup warnings so users can identify compatibility mode in `starsector.log`.

### Compatibility

- Fast Rendering v0.8.5rc2, v0.8.7rc1, and v0.8.7 are explicitly recognized by their shared `ResourceHandle` SHA-256 hash.
- Structurally incompatible Fast Rendering builds remain unchanged and continue without FR Resource Cache.

## v0.3

### Added

- Added detailed cache statistics for hits, misses, bypasses, bytes, and latency, grouped by file extension and source category.
- Added optional per-resource trace logging with `trace=true` or `trace=<path>`.
- Added verbose shutdown statistics with `statsVerbose=true`.
- Added `sampled` and `strong` fingerprint validation modes alongside the existing `metadata` mode.
- Added automatic pack compaction with configurable thresholds and `auto` (default), `manual`, or `disabled` modes.
- Added a byte-weighted, access-order memory cache with exact accounting and LRU eviction.
- Added `.txt`, `.xml`, `.java`, and `.rules` to the default cached extensions.
- Added an extensible Fast Rendering compatibility registry and structural class validation.
- Added crash-safe compaction using temporary files, recovery markers, backups, and atomic replacement where supported.

### Changed

- Upgraded the cache index to format v2 with per-entry validation and fingerprint metadata.
- Changed resource reads to verify source metadata before and after reading, retrying once when a file changes during the read.
- Changed hot memory-cache entries to verify the source file’s size and modification time before reuse.
- Changed compatibility checks from one hard-coded class hash to a registry of supported hashes backed by structural validation.

### Fixed

- Fixed changing source files from being recorded when they remain unstable after a retry.
- Fixed `ResourcePackCache.shutdown()` leaving the cache enabled after its pack channel had closed.
- Fixed `ResourceHandle` stream lifecycle behavior, including double-close, read-after-close, and unused-stream handling.

### Compatibility

- Maintained Fast Rendering's resource resolution and mod precedence.
- Maintained safe fallback to original files for unsupported, invalid, or unavailable cache data.
- Unsupported or structurally invalid Fast Rendering classes are left unchanged.
- Existing v0.2 cache indexes are rejected and rebuilt automatically because they are incompatible with v0.3.

## v0.2

### Added

- Added a standalone resource-cache agent that launches with Starsector's bundled Java.
- Added metadata-based cache validation using file size and modification time.
- Added an append-only resource pack and aggregate cache statistics at shutdown.

### Changed

- Removed the dependency on Mikohime's Java upgrader.

### Limitations

- Supported one hard-coded Fast Rendering `ResourceHandle` hash.
- Did not compact the append-only cache pack.
- Did not provide trace logging or detailed statistics.
