package dev.frresourcecache;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;

final class PackCompactor {
    private static final String MARKER = "resources.compact.marker";
    private static final String TMP_PACK = "resources.pack.compact.tmp";
    private static final String TMP_INDEX = "resources.index.compact.tmp";
    private static final String BAK_PACK = "resources.pack.bak";
    private static final String BAK_INDEX = "resources.index.bak";

    private PackCompactor() {
    }

    static void recoverIfNeeded(Path path, Path path2, Path path3) {
        Path path4 = path.resolve(MARKER);
        Path path5 = path.resolve(BAK_PACK);
        Path path6 = path.resolve(BAK_INDEX);
        try {
            if (Files.exists(path4, LinkOption.NOFOLLOW_LINKS)) {
                if (PackCompactor.currentPairLooksConsistent(path2, path3)) {
                    Log.info("compaction completed before the marker was cleared; keeping the new pack/index");
                } else {
                    Log.warn("compaction was interrupted; restoring the pre-compaction pack/index pair");
                    if (Files.exists(path5, LinkOption.NOFOLLOW_LINKS)) {
                        CacheIndex.moveReplace(path5, path2);
                    }
                    if (Files.exists(path6, LinkOption.NOFOLLOW_LINKS)) {
                        CacheIndex.moveReplace(path6, path3);
                    }
                }
                Files.deleteIfExists(path4);
            }
        }
        catch (IOException exception) {
            Log.warn("compaction recovery failed, continuing with whatever is on disk: " + String.valueOf(exception));
        }
        PackCompactor.deleteQuietly(path.resolve(TMP_PACK));
        PackCompactor.deleteQuietly(path.resolve(TMP_INDEX));
    }

    private static boolean currentPairLooksConsistent(Path path, Path path2) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS) || !Files.exists(path2, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try {
            long l = Files.size(path);
            CacheIndex.load(path2, l);
            return true;
        }
        catch (IOException exception) {
            return false;
        }
    }

    static boolean shouldCompact(long l, Map<String, CacheEntry> map, double d, long l2) {
        long l3 = map.values().stream().mapToLong(cacheEntry -> cacheEntry.length).sum();
        long l4 = Math.max(0L, l - l3);
        if (l4 < l2) {
            return false;
        }
        double d2 = l == 0L ? 0.0 : (double)l4 / (double)l;
        return d2 >= d;
    }

    static Result compact(Path path, Path path2, Path path3, FileChannel channel, Map<String, CacheEntry> map) throws IOException {
        Path path4 = path.resolve(TMP_PACK);
        Path path5 = path.resolve(TMP_INDEX);
        Path path6 = path.resolve(MARKER);
        PackCompactor.deleteQuietly(path4);
        PackCompactor.deleteQuietly(path5);
        HashMap<String, CacheEntry> hashMap = new HashMap<String, CacheEntry>(Math.max(16, map.size() * 2));
        int n = 0;
        long l = 0L;
        long l2 = channel.size();
        try (FileChannel destinationChannel = FileChannel.open(path4, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.READ);){
            long l3 = 0L;
            for (CacheEntry cacheEntry : map.values()) {
                if (!cacheEntry.withinBounds(l2)) {
                    ++n;
                    continue;
                }
                byte[] bytes = PackCompactor.readAndValidate(channel, cacheEntry);
                if (bytes == null) {
                    ++n;
                    continue;
                }
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    int n2 = destinationChannel.write(buffer, l3 + (long)(bytes.length - buffer.remaining()));
                    if (n2 != 0) continue;
                    Thread.yield();
                }
                hashMap.put(cacheEntry.key, new CacheEntry(cacheEntry.key, cacheEntry.sourceSize, cacheEntry.sourceModified, l3, bytes.length, cacheEntry.crc32, cacheEntry.validationMode, cacheEntry.fingerprintAlgorithm, cacheEntry.fingerprint));
                l3 += (long)bytes.length;
                l += (long)bytes.length;
            }
            destinationChannel.force(true);
        }
        CacheIndex.writeToTemp(path5, hashMap);
        PackCompactor.forceFile(path5);
        PackCompactor.swap(path, path2, path3, path4, path5, path6);
        long l4 = Math.max(0L, l2 - l);
        return new Result(hashMap.size(), n, l4);
    }

    private static void swap(Path path, Path path2, Path path3, Path path4, Path path5, Path path6) throws IOException {
        Path path7 = path.resolve(BAK_PACK);
        Path path8 = path.resolve(BAK_INDEX);
        Files.write(path6, ("pending:" + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
        PackCompactor.forceFile(path6);
        if (Files.exists(path2, LinkOption.NOFOLLOW_LINKS)) {
            CacheIndex.moveReplace(path2, path7);
        }
        if (Files.exists(path3, LinkOption.NOFOLLOW_LINKS)) {
            CacheIndex.moveReplace(path3, path8);
        }
        CacheIndex.moveReplace(path4, path2);
        CacheIndex.moveReplace(path5, path3);
        Files.deleteIfExists(path6);
    }

    private static void forceFile(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE);){
            channel.force(true);
        }
    }

    private static byte[] readAndValidate(FileChannel channel, CacheEntry cacheEntry) throws IOException {
        byte[] bytes = new byte[cacheEntry.length];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long l = cacheEntry.offset;
        while (buffer.hasRemaining()) {
            int n = channel.read(buffer, l);
            if (n < 0) {
                return null;
            }
            if (n == 0) {
                Thread.yield();
                continue;
            }
            l += (long)n;
        }
        CRC32 crc32 = new CRC32();
        crc32.update(bytes);
        if ((int)crc32.getValue() != cacheEntry.crc32) {
            return null;
        }
        return bytes;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        }
        catch (IOException exception) {
        }
    }

    static final class Result {
        final int liveEntries;
        final int droppedEntries;
        final long deadBytesReclaimed;

        Result(int n, int n2, long l) {
            this.liveEntries = n;
            this.droppedEntries = n2;
            this.deadBytesReclaimed = l;
        }
    }
}
