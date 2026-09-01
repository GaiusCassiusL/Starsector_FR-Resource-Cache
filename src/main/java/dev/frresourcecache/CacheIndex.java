package dev.frresourcecache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

final class CacheIndex {
    private static final int MAGIC = 1179796225;
    private static final int FORMAT_V1 = 1;
    private static final int FORMAT_V2 = 2;
    private static final int MAX_ENTRIES = 4000000;

    private CacheIndex() {
    }

    static LoadResult load(Path path, long l) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return new LoadResult(new HashMap<String, CacheEntry>(), false);
        }
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path, new OpenOption[0])));){
            int n = input.readInt();
            int n2 = input.readInt();
            if (n != 1179796225) {
                throw new IOException("unrecognized index magic 0x" + Integer.toHexString(n));
            }
            if (n2 == 1) {
                LoadResult loadResult = new LoadResult(CacheIndex.readV1Body(input, l), true);
                return loadResult;
            }
            if (n2 != 2) throw new IOException("unsupported index format version " + n2);
            LoadResult loadResult = new LoadResult(CacheIndex.readV2Body(input, l), false);
            return loadResult;
        }
        catch (EOFException exception) {
            throw new IOException("truncated resource index", exception);
        }
    }

    private static Map<String, CacheEntry> readV1Body(DataInputStream input, long l) throws IOException {
        int n = input.readInt();
        CacheIndex.validateCount(n);
        HashMap<String, CacheEntry> hashMap = new HashMap<String, CacheEntry>(Math.max(16, n * 2));
        for (int i = 0; i < n; ++i) {
            int n2;
            int n3;
            long l2;
            long l3;
            long l4;
            String text = input.readUTF();
            CacheEntry cacheEntry = new CacheEntry(text, l4 = input.readLong(), l3 = input.readLong(), l2 = input.readLong(), n3 = input.readInt(), n2 = input.readInt(), ValidationMode.METADATA, Fingerprint.Algorithm.NONE, new byte[0]);
            if (!cacheEntry.withinBounds(l)) continue;
            hashMap.put(text, cacheEntry);
        }
        return hashMap;
    }

    private static Map<String, CacheEntry> readV2Body(DataInputStream input, long l) throws IOException {
        int n = input.readInt();
        CacheIndex.validateCount(n);
        HashMap<String, CacheEntry> hashMap = new HashMap<String, CacheEntry>(Math.max(16, n * 2));
        for (int i = 0; i < n; ++i) {
            String text = input.readUTF();
            long l2 = input.readLong();
            long l3 = input.readLong();
            long l4 = input.readLong();
            int n2 = input.readInt();
            int n3 = input.readInt();
            ValidationMode validationMode = ValidationMode.fromId(input.readByte());
            Fingerprint.Algorithm algorithm = Fingerprint.Algorithm.fromId(input.readByte());
            int n4 = input.readShort() & 0xFFFF;
            byte[] bytes = new byte[n4];
            input.readFully(bytes);
            CacheEntry cacheEntry = new CacheEntry(text, l2, l3, l4, n2, n3, validationMode, algorithm, bytes);
            if (!cacheEntry.withinBounds(l)) continue;
            hashMap.put(text, cacheEntry);
        }
        return hashMap;
    }

    private static void validateCount(int n) throws IOException {
        if (n < 0 || n > 4000000) {
            throw new IOException("invalid index entry count: " + n);
        }
    }

    static void writeToTemp(Path path, Map<String, CacheEntry> map) throws IOException {
        ArrayList<CacheEntry> arrayList = new ArrayList<CacheEntry>(map.values());
        arrayList.sort(Comparator.comparing(cacheEntry -> cacheEntry.key));
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)));){
            output.writeInt(1179796225);
            output.writeInt(2);
            output.writeInt(arrayList.size());
            for (CacheEntry cacheEntry2 : arrayList) {
                output.writeUTF(cacheEntry2.key);
                output.writeLong(cacheEntry2.sourceSize);
                output.writeLong(cacheEntry2.sourceModified);
                output.writeLong(cacheEntry2.offset);
                output.writeInt(cacheEntry2.length);
                output.writeInt(cacheEntry2.crc32);
                output.writeByte(cacheEntry2.validationMode.id);
                output.writeByte(cacheEntry2.fingerprintAlgorithm.id);
                output.writeShort(cacheEntry2.fingerprint.length);
                output.write(cacheEntry2.fingerprint);
            }
        }
    }

    static void save(Path path, Path path2, Map<String, CacheEntry> map) throws IOException {
        CacheIndex.writeToTemp(path2, map);
        CacheIndex.moveReplace(path2, path);
    }

    static void moveReplace(Path path, Path path2) throws IOException {
        try {
            Files.move(path, path2, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (AtomicMoveNotSupportedException exception) {
            Files.move(path, path2, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static final class LoadResult {
        final Map<String, CacheEntry> entries;
        final boolean migratedFromV1;

        LoadResult(Map<String, CacheEntry> map, boolean bl) {
            this.entries = map;
            this.migratedFromV1 = bl;
        }
    }
}
