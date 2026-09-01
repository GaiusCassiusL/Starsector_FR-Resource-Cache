# Changelog

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
