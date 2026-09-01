package dev.frresourcecache;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

final class MemoryCache {
    private final LinkedHashMap<String, Hot> map = new LinkedHashMap<>(256, 0.75f, true);
    private long currentBytes;
    private final long limit;

    MemoryCache(long limit) {
        this.limit = Math.max(0L, limit);
    }

    synchronized byte[] get(String key, Path source) {
        long modified;
        long size;
        Hot hot = this.map.get(key);
        if (hot == null) {
            return null;
        }
        try {
            size = Files.size(source);
            modified = Files.getLastModifiedTime(source, LinkOption.NOFOLLOW_LINKS).toMillis();
        } catch (Exception exception) {
            this.map.remove(key);
            this.currentBytes -= (long)hot.bytes.length;
            return null;
        }
        if (size != hot.sourceSize || modified != hot.sourceModified) {
            this.map.remove(key);
            this.currentBytes -= (long)hot.bytes.length;
            return null;
        }
        return hot.bytes;
    }

    synchronized void put(String key, byte[] bytes, long sourceSize, long sourceModified) {
        if (this.limit <= 0L || (long) bytes.length > this.limit) {
            return;
        }
        Hot hot = this.map.remove(key);
        if (hot != null) {
            this.currentBytes -= (long)hot.bytes.length;
        }
        this.map.put(key, new Hot(bytes, sourceSize, sourceModified));
        this.currentBytes += (long) bytes.length;
        this.evictToLimit();
    }

    synchronized void invalidate(String key) {
        Hot hot = this.map.remove(key);
        if (hot != null) {
            this.currentBytes -= (long)hot.bytes.length;
        }
    }

    synchronized long currentBytes() {
        return this.currentBytes;
    }

    synchronized int size() {
        return this.map.size();
    }

    private void evictToLimit() {
        Iterator<Map.Entry<String, Hot>> iterator = this.map.entrySet().iterator();
        while (this.currentBytes > this.limit && iterator.hasNext()) {
            Map.Entry<String, Hot> entry = iterator.next();
            this.currentBytes -= (long)entry.getValue().bytes.length;
            iterator.remove();
        }
    }

    private static final class Hot {
        final byte[] bytes;
        final long sourceSize;
        final long sourceModified;

        Hot(byte[] bytes, long sourceSize, long sourceModified) {
            this.bytes = bytes;
            this.sourceSize = sourceSize;
            this.sourceModified = sourceModified;
        }
    }
}
