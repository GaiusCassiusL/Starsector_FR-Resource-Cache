package dev.frresourcecache;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
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

    byte[] get(String key, Path source, BasicFileAttributes attributes, ValidationMode validationMode) {
        Hot hot;
        synchronized (this) {
            hot = this.map.get(key);
            if (hot == null) {
                return null;
            }
            if (attributes.size() != hot.sourceSize
                    || attributes.lastModifiedTime().toMillis() != hot.sourceModified) {
                this.remove(key, hot);
                return null;
            }
        }
        if (validationMode != ValidationMode.METADATA) {
            Fingerprint.Algorithm algorithm = Fingerprint.algorithmFor(validationMode);
            try {
                byte[] sourceFingerprint =
                        Fingerprint.computeFromFile(algorithm, source, attributes.size());
                byte[] cachedFingerprint = Fingerprint.compute(algorithm, hot.bytes);
                if (!Arrays.equals(sourceFingerprint, cachedFingerprint)) {
                    synchronized (this) {
                        this.remove(key, hot);
                    }
                    return null;
                }
            }
            catch (Exception exception) {
                synchronized (this) {
                    this.remove(key, hot);
                }
                return null;
            }
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

    private void remove(String key, Hot expected) {
        if (this.map.get(key) == expected) {
            this.map.remove(key);
            this.currentBytes -= (long)expected.bytes.length;
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
