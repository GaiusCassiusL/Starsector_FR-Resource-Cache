package dev.frresourcecache;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

public final class ResourcePackCache {
    private static final Object WRITE_LOCK = new Object();
    private static final ConcurrentHashMap<String, CacheEntry> ENTRIES = new ConcurrentHashMap<>();
    private static final AtomicBoolean DIRTY = new AtomicBoolean();
    private static final AtomicBoolean WRITE_WARNING_REPORTED = new AtomicBoolean();
    private static volatile Stats STATS = new Stats();
    private static volatile AgentConfig config;
    private static volatile boolean enabled;
    private static volatile MemoryCache hot;
    private static FileChannel packChannel;
    private static FileChannel lockChannel;
    private static FileLock processLock;
    private static ScheduledExecutorService scheduler;
    private static Path packPath;
    private static Path indexPath;

    private ResourcePackCache() {
    }

    private static void resetStaticStateForReinitialize() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        ResourcePackCache.closeQuietly();
        packChannel = null;
        lockChannel = null;
        processLock = null;
        ENTRIES.clear();
        DIRTY.set(false);
        WRITE_WARNING_REPORTED.set(false);
        STATS = new Stats();
        enabled = false;
        Log.closeTrace();
    }

    static void initialize(AgentConfig settings) {
        ResourcePackCache.resetStaticStateForReinitialize();
        config = settings;
        hot = new MemoryCache(settings.memoryLimit);
        if (settings.traceLogPath != null) {
            Log.enableTrace(settings.traceLogPath);
        }
        try {
            Files.createDirectories(settings.cacheDir);
            Path path = settings.cacheDir.resolve("cache.lock");
            lockChannel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            processLock = lockChannel.tryLock();
            if (processLock == null) {
                Log.warn("another process owns the cache; this launch will use original files");
                ResourcePackCache.closeQuietly();
                return;
            }
            packPath = settings.cacheDir.resolve("resources.pack");
            indexPath = settings.cacheDir.resolve("resources.index");
            PackCompactor.recoverIfNeeded(settings.cacheDir, packPath, indexPath);
            packChannel = FileChannel.open(packPath, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            ResourcePackCache.loadIndexOrRebuild();
            ResourcePackCache.maybeCompactAtStartup();
            enabled = true;
            scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "FR-Resource-Cache-Flush");
                thread.setDaemon(true);
                return thread;
            });
            if (settings.flushDelaySeconds > 0) {
                scheduler.scheduleWithFixedDelay(ResourcePackCache::flushQuietly, settings.flushDelaySeconds, settings.flushDelaySeconds, TimeUnit.SECONDS);
            }
            Log.info("ready with " + ENTRIES.size() + " indexed resources; pack size " + Stats.formatBytes(packChannel.size()) + "; validationMode=" + settings.validationMode.name().toLowerCase(Locale.ROOT));
        }
        catch (Throwable throwable) {
            enabled = false;
            ResourcePackCache.closeQuietly();
            Log.warn("could not initialize; original files will be used: " + String.valueOf(throwable));
        }
    }

    private static void loadIndexOrRebuild() throws IOException {
        try {
            CacheIndex.LoadResult loadResult = CacheIndex.load(indexPath, packChannel.size());
            ENTRIES.putAll(loadResult.entries);
            if (loadResult.migratedFromV1) {
                DIRTY.set(true);
                Log.info("migrated v1 cache index (" + loadResult.entries.size() + " entries) to the v2 format");
            }
        }
        catch (IOException exception) {
            ENTRIES.clear();
            DIRTY.set(true);
            Log.warn("cache index was rejected and will be rebuilt: " + exception.getMessage());
        }
    }

    private static void maybeCompactAtStartup() {
        boolean bl;
        AgentConfig settings = config;
        if (settings.compactionMode == AgentConfig.CompactionMode.DISABLED) {
            return;
        }
        boolean bl2 = bl = settings.compactionMode == AgentConfig.CompactionMode.MANUAL && !settings.forceCompact;
        if (bl) {
            return;
        }
        try {
            boolean bl3;
            long l = packChannel.size();
            boolean bl4 = bl3 = settings.forceCompact || PackCompactor.shouldCompact(l, ENTRIES, settings.deadByteRatioThreshold, settings.minDeadBytes);
            if (!bl3) {
                return;
            }
            Object object = WRITE_LOCK;
            synchronized (object) {
                PackCompactor.Result result = PackCompactor.compact(settings.cacheDir, packPath, indexPath, packChannel, ENTRIES);
                packChannel.close();
                packChannel = FileChannel.open(packPath, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
                ENTRIES.clear();
                ENTRIES.putAll(CacheIndex.load(indexPath, packChannel.size()).entries);
                DIRTY.set(false);
                Log.info("compacted pack: " + result.liveEntries + " entries kept, " + result.droppedEntries + " invalid entries dropped, " + Stats.formatBytes(result.deadBytesReclaimed) + " reclaimed");
                Log.trace(Log.Category.COMPACTED, "entries=" + result.liveEntries + " dropped=" + result.droppedEntries + " reclaimedBytes=" + result.deadBytesReclaimed);
            }
        }
        catch (IOException exception) {
            Log.warn("pack compaction failed; continuing without compacting: " + String.valueOf(exception));
        }
    }

    public static InputStream open(File file) throws IOException {
        long l;
        if (!enabled || file == null) {
            STATS.recordBypass("(none)", SourceCategory.OTHER);
            return Files.newInputStream(ResourcePackCache.requireNonNullFile(file).toPath(), new OpenOption[0]);
        }
        Path path = file.toPath().toAbsolutePath().normalize();
        String text = ResourcePackCache.extensionOf(path);
        AgentConfig settings = config;
        SourceCategory sourceCategory = SourceCategory.classify(settings.installRoot, path);
        if (!ResourcePackCache.eligible(path, text, settings)) {
            STATS.recordBypass(text, sourceCategory);
            return Files.newInputStream(path, new OpenOption[0]);
        }
        try {
            l = Files.size(path);
        }
        catch (IOException exception) {
            STATS.recordBypass(text, sourceCategory);
            return Files.newInputStream(path, new OpenOption[0]);
        }
        if (l > settings.maxFileSize) {
            STATS.recordBypass(text, sourceCategory);
            return Files.newInputStream(path, new OpenOption[0]);
        }
        return new ByteArrayInputStream(ResourcePackCache.readEligible(path, text, sourceCategory));
    }

    public static byte[] read(File file) throws IOException {
        if (!enabled || file == null) {
            STATS.recordBypass("(none)", SourceCategory.OTHER);
            return Files.readAllBytes(ResourcePackCache.requireNonNullFile(file).toPath());
        }
        Path path = file.toPath().toAbsolutePath().normalize();
        String text = ResourcePackCache.extensionOf(path);
        AgentConfig settings = config;
        SourceCategory sourceCategory = SourceCategory.classify(settings.installRoot, path);
        if (!ResourcePackCache.eligible(path, text, settings)) {
            STATS.recordBypass(text, sourceCategory);
            return Files.readAllBytes(path);
        }
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            long l = attributes.size();
            if (l > settings.maxFileSize) {
                STATS.recordBypass(text, sourceCategory);
                return Files.readAllBytes(path);
            }
        }
        catch (IOException exception) {
            STATS.recordBypass(text, sourceCategory);
            return Files.readAllBytes(path);
        }
        return ResourcePackCache.readEligible(path, text, sourceCategory);
    }

    private static File requireNonNullFile(File file) throws IOException {
        if (file == null) {
            throw new IOException("null file");
        }
        return file;
    }

    private static byte[] readEligible(Path path, String text, SourceCategory sourceCategory) throws IOException {
        byte[] bytes;
        BasicFileAttributes attributes;
        String otherText = ResourcePackCache.key(path);
        long l = System.nanoTime();
        byte[] otherBytes = hot.get(otherText, path);
        if (otherBytes != null) {
            STATS.recordHit(text, sourceCategory, otherBytes.length, System.nanoTime() - l);
            return otherBytes;
        }
        try {
            attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        }
        catch (IOException exception) {
            return Files.readAllBytes(path);
        }
        long l2 = attributes.size();
        long l3 = attributes.lastModifiedTime().toMillis();
        CacheEntry cacheEntry = ENTRIES.get(otherText);
        if (cacheEntry != null && cacheEntry.matchesMetadata(l2, l3) && (bytes = ResourcePackCache.tryServeFromPack(path, otherText, cacheEntry, text, sourceCategory, l2, l3, l)) != null) {
            return bytes;
        }
        return ResourcePackCache.readMissAndRecord(path, otherText, text, sourceCategory, l);
    }

    private static byte[] tryServeFromPack(Path path, String text, CacheEntry cacheEntry, String otherText, SourceCategory sourceCategory, long l, long l2, long l3) throws IOException {
        byte[] bytes;
        if (cacheEntry.validationMode != ValidationMode.METADATA) {
            try {
                bytes = Fingerprint.computeFromFile(cacheEntry.fingerprintAlgorithm, path, l);
            }
            catch (IOException exception) {
                return null;
            }
            if (!Arrays.equals(bytes, cacheEntry.fingerprint)) {
                ENTRIES.remove(text, cacheEntry);
                hot.invalidate(text);
                STATS.recordStale(otherText, sourceCategory);
                Log.trace(Log.Category.STALE, String.valueOf(path) + " (fingerprint mismatch, mode=" + String.valueOf((Object)cacheEntry.validationMode) + ")");
                return null;
            }
        }
        bytes = null;
        try {
            bytes = ResourcePackCache.readPacked(cacheEntry);
        }
        catch (IOException exception) {
            ResourcePackCache.reportWriteWarningOnce("pack read failed; affected resources will use original files: " + String.valueOf(exception));
        }
        if (bytes == null) {
            ENTRIES.remove(text, cacheEntry);
            hot.invalidate(text);
            STATS.recordCorrupt(otherText, sourceCategory);
            Log.trace(Log.Category.CORRUPT, path.toString());
            return null;
        }
        hot.put(text, bytes, l, l2);
        STATS.recordHit(otherText, sourceCategory, bytes.length, System.nanoTime() - l3);
        Log.trace(Log.Category.CACHED, String.valueOf(path) + " (persistent hit)");
        return bytes;
    }

    private static byte[] readMissAndRecord(Path path, String text, String otherText, SourceCategory sourceCategory, long l) throws IOException {
        StableRead stableRead = ResourcePackCache.readStable(path);
        STATS.recordMiss(otherText, sourceCategory, stableRead.content.length, System.nanoTime() - l);
        if (!stableRead.stable) {
            STATS.recordUnstable();
            Log.trace(Log.Category.BYPASSED, String.valueOf(path) + " (actively changing; not recorded)");
            hot.invalidate(text);
            return stableRead.content;
        }
        AgentConfig settings = config;
        if ((long)stableRead.content.length <= settings.maxFileSize) {
            try {
                ResourcePackCache.record(text, stableRead.size, stableRead.modified, stableRead.content, settings.validationMode);
            }
            catch (IOException exception) {
                ResourcePackCache.reportWriteWarningOnce("pack write failed; affected resources will use original files: " + String.valueOf(exception));
            }
        }
        hot.put(text, stableRead.content, stableRead.size, stableRead.modified);
        Log.trace(Log.Category.CACHED, String.valueOf(path) + " (new source read)");
        return stableRead.content;
    }

    private static void reportWriteWarningOnce(String text) {
        if (WRITE_WARNING_REPORTED.compareAndSet(false, true)) {
            Log.warn(text);
        }
    }

    private static StableRead readStable(Path path) throws IOException {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            byte[] bytes = Files.readAllBytes(path);
            BasicFileAttributes attributesAfter = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (ResourcePackCache.matches(attributes, attributesAfter)) {
                return new StableRead(bytes, attributesAfter.size(), attributesAfter.lastModifiedTime().toMillis(), true);
            }
        }
        catch (IOException exception) {
        }
        return ResourcePackCache.retryStableRead(path);
    }

    private static StableRead retryStableRead(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        byte[] bytes = Files.readAllBytes(path);
        BasicFileAttributes attributesAfter = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        boolean bl = ResourcePackCache.matches(attributes, attributesAfter);
        return new StableRead(bytes, attributesAfter.size(), attributesAfter.lastModifiedTime().toMillis(), bl);
    }

    private static boolean matches(BasicFileAttributes attributes, BasicFileAttributes attributesAfter) {
        return attributes.size() == attributesAfter.size() && attributes.lastModifiedTime().toMillis() == attributesAfter.lastModifiedTime().toMillis();
    }

    private static boolean eligible(Path path, String text, AgentConfig settings) {
        if (!path.startsWith(settings.installRoot) || path.startsWith(settings.cacheDir)) {
            return false;
        }
        return !text.isEmpty() && settings.extensions.contains(text);
    }

    private static String extensionOf(Path path) {
        String text = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        int n = text.lastIndexOf(46);
        return n >= 0 ? text.substring(n) : "";
    }

    private static byte[] readPacked(CacheEntry cacheEntry) throws IOException {
        if (!cacheEntry.withinBounds(packChannel.size())) {
            return null;
        }
        byte[] bytes = new byte[cacheEntry.length];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long l = cacheEntry.offset;
        while (buffer.hasRemaining()) {
            int n = packChannel.read(buffer, l + (long)(bytes.length - buffer.remaining()));
            if (n < 0) {
                return null;
            }
            if (n != 0) continue;
            Thread.yield();
        }
        return ResourcePackCache.crc(bytes) == cacheEntry.crc32 ? bytes : null;
    }

    private static void record(String text, long l, long l2, byte[] bytes, ValidationMode validationMode) throws IOException {
        Object object = WRITE_LOCK;
        synchronized (object) {
            CacheEntry cacheEntry = ENTRIES.get(text);
            if (cacheEntry != null && cacheEntry.matchesMetadata(l, l2)) {
                return;
            }
            long l3 = packChannel.size();
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            long l4 = l3;
            while (buffer.hasRemaining()) {
                int n = packChannel.write(buffer, l4 + (long)(bytes.length - buffer.remaining()));
                if (n != 0) continue;
                Thread.yield();
            }
            Fingerprint.Algorithm algorithm = Fingerprint.algorithmFor(validationMode);
            byte[] otherBytes = Fingerprint.compute(algorithm, bytes);
            ENTRIES.put(text, new CacheEntry(text, l, l2, l3, bytes.length, ResourcePackCache.crc(bytes), validationMode, algorithm, otherBytes));
            DIRTY.set(true);
        }
    }

    static void flushNow() {
        if (!enabled) {
            return;
        }
        Object object = WRITE_LOCK;
        synchronized (object) {
            if (!DIRTY.get()) {
                return;
            }
            Path path = ResourcePackCache.config.cacheDir.resolve("resources.index.tmp");
            try {
                packChannel.force(false);
                CacheIndex.save(indexPath, path, ENTRIES);
                DIRTY.set(false);
            }
            catch (Throwable throwable) {
                Log.warn("could not flush cache index: " + String.valueOf(throwable));
            }
        }
    }

    private static void flushQuietly() {
        try {
            ResourcePackCache.flushNow();
        }
        catch (Throwable throwable) {
            Log.warn("background flush failed: " + String.valueOf(throwable));
        }
    }

    static void shutdown() {
        ResourcePackCache.flushNow();
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        ResourcePackCache.closeQuietly();
        enabled = false;
        Log.info(STATS.summaryLine(ENTRIES.size(), packChannel == null ? "n/a" : ResourcePackCache.safePackSize()));
        if (config != null && ResourcePackCache.config.statsVerbose) {
            STATS.verboseBreakdown().forEach(Log::info);
        }
        Log.closeTrace();
    }

    private static String safePackSize() {
        try {
            return Stats.formatBytes(packChannel.size());
        }
        catch (IOException exception) {
            return "n/a";
        }
    }

    static Stats stats() {
        return STATS;
    }

    static Map<String, CacheEntry> entriesView() {
        return ENTRIES;
    }

    static boolean isEnabled() {
        return enabled;
    }

    static void invalidateHotForTesting(Path path) {
        hot.invalidate(ResourcePackCache.key(path.toAbsolutePath().normalize()));
    }

    private static void closeQuietly() {
        try {
            if (processLock != null) {
                processLock.release();
            }
        }
        catch (Throwable throwable) {
        }
        try {
            if (packChannel != null) {
                packChannel.close();
            }
        }
        catch (Throwable throwable) {
        }
        try {
            if (lockChannel != null) {
                lockChannel.close();
            }
        }
        catch (Throwable throwable) {
        }
    }

    private static int crc(byte[] bytes) {
        CRC32 crc32 = new CRC32();
        crc32.update(bytes);
        return (int)crc32.getValue();
    }

    private static String key(Path path) {
        String text = path.toString();
        return File.separatorChar == '\\' ? text.toLowerCase(Locale.ROOT) : text;
    }

    private static final class StableRead {
        final byte[] content;
        final long size;
        final long modified;
        final boolean stable;

        StableRead(byte[] bytes, long l, long l2, boolean bl) {
            this.content = bytes;
            this.size = l;
            this.modified = l2;
            this.stable = bl;
        }
    }
}
