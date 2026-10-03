package dev.frresourcecache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class AccessOrder {
    private static final int MAGIC = 0x46524F31;
    private static final int MAX_ENTRIES = 10_000_000;
    private static final int MAX_KEY_BYTES = 1_048_576;

    private AccessOrder() {
    }

    static Map<String, Integer> load(Path path) throws IOException {
        if (!Files.exists(path)) {
            return Map.of();
        }
        try (DataInputStream input =
                new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            if (input.readInt() != MAGIC) {
                throw new IOException("invalid access-order header");
            }
            int count = input.readInt();
            if (count < 0 || count > MAX_ENTRIES) {
                throw new IOException("invalid access-order entry count");
            }
            HashMap<String, Integer> order = new HashMap<>(Math.max(16, count * 2));
            for (int index = 0; index < count; index++) {
                int length = input.readInt();
                if (length < 0 || length > MAX_KEY_BYTES) {
                    throw new IOException("invalid access-order key length");
                }
                byte[] bytes = input.readNBytes(length);
                if (bytes.length != length) {
                    throw new EOFException("truncated access-order file");
                }
                order.putIfAbsent(new String(bytes, StandardCharsets.UTF_8), index);
            }
            return order;
        }
    }

    static void save(Path path, Map<String, Long> accessOrder) throws IOException {
        List<Map.Entry<String, Long>> entries = new ArrayList<>(accessOrder.entrySet());
        entries.sort(Comparator.comparingLong(Map.Entry::getValue));
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.deleteIfExists(temporary);
        try {
            try (DataOutputStream output = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(
                            temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)))) {
                output.writeInt(MAGIC);
                output.writeInt(entries.size());
                for (Map.Entry<String, Long> entry : entries) {
                    byte[] bytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
                    output.writeInt(bytes.length);
                    output.write(bytes);
                }
            }
            try (FileChannel channel =
                    FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            CacheIndex.moveReplace(temporary, path);
        }
        finally {
            Files.deleteIfExists(temporary);
        }
    }
}
