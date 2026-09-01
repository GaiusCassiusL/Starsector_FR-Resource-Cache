package dev.frresourcecache;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

final class Fingerprint {
    static final int WINDOW = 4096;

    private Fingerprint() {
    }

    static Algorithm algorithmFor(ValidationMode validationMode) {
        switch (validationMode) {
            case METADATA: {
                return Algorithm.NONE;
            }
            case SAMPLED: {
                return Algorithm.SAMPLED_SHA256;
            }
            case STRONG: {
                return Algorithm.STRONG_SHA256;
            }
        }
        throw new AssertionError((Object)validationMode);
    }

    static byte[] compute(Algorithm algorithm, byte[] bytes) {
        switch (algorithm.ordinal()) {
            case 0: {
                return new byte[0];
            }
            case 1: {
                return Fingerprint.sampledDigest(bytes);
            }
            case 2: {
                return Fingerprint.digest(bytes, 0, bytes.length);
            }
        }
        throw new AssertionError((Object)algorithm);
    }

    static boolean matches(Algorithm algorithm, byte[] bytes, byte[] otherBytes) {
        if (algorithm == Algorithm.NONE) {
            return true;
        }
        return Arrays.equals(bytes, Fingerprint.compute(algorithm, otherBytes));
    }

    static byte[] computeFromFile(Algorithm algorithm, Path path, long l) throws IOException {
        switch (algorithm.ordinal()) {
            case 0: {
                return new byte[0];
            }
            case 1: {
                return Fingerprint.sampledDigestFromFile(path, l);
            }
            case 2: {
                byte[] bytes = Files.readAllBytes(path);
                return Fingerprint.digest(bytes, 0, bytes.length);
            }
        }
        throw new AssertionError((Object)algorithm);
    }

    private static byte[] sampledDigestFromFile(Path path, long l) throws IOException {
        MessageDigest digest = Fingerprint.newSha256();
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);){
            Fingerprint.addWindowFromChannel(digest, channel, 0L, l);
            Fingerprint.addWindowFromChannel(digest, channel, Math.max(0L, (l - 4096L) / 2L), l);
            Fingerprint.addWindowFromChannel(digest, channel, Math.max(0L, l - 4096L), l);
        }
        digest.update(Fingerprint.longBytes(l));
        return digest.digest();
    }

    private static void addWindowFromChannel(MessageDigest digest, FileChannel channel, long l, long l2) throws IOException {
        int n;
        long l3 = Math.min(l2, l + 4096L);
        if (l < 0L || l >= l3) {
            return;
        }
        int n2 = (int)(l3 - l);
        ByteBuffer buffer = ByteBuffer.allocate(n2);
        long l4 = l;
        while (buffer.hasRemaining() && (n = channel.read(buffer, l4 + (long)(n2 - buffer.remaining()))) >= 0) {
        }
        buffer.flip();
        digest.update(buffer);
    }

    private static byte[] sampledDigest(byte[] bytes) {
        MessageDigest digest = Fingerprint.newSha256();
        int n = bytes.length;
        Fingerprint.addWindow(digest, bytes, 0);
        Fingerprint.addWindow(digest, bytes, Math.max(0, (n - 4096) / 2));
        Fingerprint.addWindow(digest, bytes, Math.max(0, n - 4096));
        digest.update(Fingerprint.longBytes(n));
        return digest.digest();
    }

    private static byte[] longBytes(long l) {
        byte[] bytes = new byte[8];
        for (int i = 7; i >= 0; --i) {
            bytes[i] = (byte)l;
            l >>>= 8;
        }
        return bytes;
    }

    private static void addWindow(MessageDigest digest, byte[] bytes, int n) {
        int n2 = Math.min(bytes.length, n + 4096);
        if (n < 0 || n >= n2) {
            return;
        }
        digest.update(bytes, n, n2 - n);
    }

    private static byte[] digest(byte[] bytes, int n, int n2) {
        MessageDigest digest = Fingerprint.newSha256();
        digest.update(bytes, n, n2);
        return digest.digest();
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException exception) {
            throw new AssertionError((Object)exception);
        }
    }

    static enum Algorithm {
        NONE(0),
        SAMPLED_SHA256(1),
        STRONG_SHA256(2);

        final int id;

        private Algorithm(int n2) {
            this.id = n2;
        }

        static Algorithm fromId(int n) {
            for (Algorithm algorithm : Algorithm.values()) {
                if (algorithm.id != n) continue;
                return algorithm;
            }
            throw new IllegalArgumentException("unknown fingerprint algorithm id " + n);
        }
    }
}
