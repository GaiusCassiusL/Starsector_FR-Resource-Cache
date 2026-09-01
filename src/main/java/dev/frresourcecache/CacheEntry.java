package dev.frresourcecache;


final class CacheEntry {
    final String key;
    final long sourceSize;
    final long sourceModified;
    final long offset;
    final int length;
    final int crc32;
    final ValidationMode validationMode;
    final Fingerprint.Algorithm fingerprintAlgorithm;
    final byte[] fingerprint;

    CacheEntry(String key, long sourceSize, long sourceModified, long offset, int length, int crc32,
            ValidationMode validationMode, Fingerprint.Algorithm fingerprintAlgorithm, byte[] fingerprint) {
        this.key = key;
        this.sourceSize = sourceSize;
        this.sourceModified = sourceModified;
        this.offset = offset;
        this.length = length;
        this.crc32 = crc32;
        this.validationMode = validationMode;
        this.fingerprintAlgorithm = fingerprintAlgorithm;
        this.fingerprint = fingerprint;
    }

    boolean matchesMetadata(long size, long modified) {
        return this.sourceSize == size && this.sourceModified == modified;
    }

    boolean matchesContent(byte[] content) {
        return Fingerprint.matches(this.fingerprintAlgorithm, this.fingerprint, content);
    }

    boolean withinBounds(long packSize) {
        return this.offset >= 0L && this.length >= 0 && this.offset + (long) this.length <= packSize;
    }
}
