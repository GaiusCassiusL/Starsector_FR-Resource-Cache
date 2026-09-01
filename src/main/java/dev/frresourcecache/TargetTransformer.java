package dev.frresourcecache;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.ProtectionDomain;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;

final class TargetTransformer
implements ClassFileTransformer {
    private final AtomicBoolean applied = new AtomicBoolean();
    private final AtomicBoolean rejected = new AtomicBoolean();

    TargetTransformer() {
    }

    @Override
    public byte[] transform(Module module, ClassLoader classLoader, String text, Class<?> clazz, ProtectionDomain protectionDomain, byte[] bytes) {
        if (!"com/genir/renderer/overrides/loading/ResourceHandle".equals(text)) {
            return null;
        }
        String otherText = TargetTransformer.sha256(bytes);
        CompatibilityRegistry.SupportedBuild build = CompatibilityRegistry.find(otherText);
        if (build == null) {
            this.rejected.set(true);
            Log.warn("Fast Rendering ResourceHandle is not a supported build (SHA-256 " + otherText + "); leaving it unchanged");
            return null;
        }
        if (!ClassFileValidator.isPlausibleResourceHandle(bytes, "com/genir/renderer/overrides/loading/ResourceHandle", CompatibilityRegistry.REQUIRED_METHODS)) {
            this.rejected.set(true);
            Log.warn("Fast Rendering ResourceHandle matched a known hash but failed structural validation; leaving it unchanged");
            return null;
        }
        byte[] otherBytes = this.loadPayload(build.payloadResource);
        if (otherBytes == null) {
            this.rejected.set(true);
            return null;
        }
        this.applied.set(true);
        Log.info("installed packed-stream hook into Fast Rendering ResourceHandle (" + build.label + ")");
        return otherBytes;
    }

    void printSummary() {
        if (!this.applied.get() && !this.rejected.get()) {
            Log.warn("Fast Rendering ResourceHandle was never loaded; no resource caching was applied");
        }
    }

    private byte[] loadPayload(String resourcePath) {
        try (InputStream inputStream = TargetTransformer.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("replacement payload is missing: " + resourcePath);
            }
            return inputStream.readAllBytes();
        } catch (IOException exception) {
            Log.warn("could not load replacement payload; leaving Fast Rendering unchanged: " + exception);
            return null;
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new AssertionError((Object)exception);
        }
    }
}
