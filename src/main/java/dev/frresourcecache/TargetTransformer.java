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
    public byte[] transform(Module module, ClassLoader classLoader, String className, Class<?> clazz,
            ProtectionDomain protectionDomain, byte[] classBytes) {
        if (!CompatibilityRegistry.TARGET_CLASS.equals(className)) {
            return null;
        }

        String classHash = TargetTransformer.sha256(classBytes);
        if (!ClassFileValidator.isPlausibleResourceHandle(
                classBytes,
                CompatibilityRegistry.TARGET_CLASS,
                CompatibilityRegistry.REQUIRED_METHODS,
                CompatibilityRegistry.REQUIRED_FIELD_REFERENCES)) {
            this.rejected.set(true);
            Log.warn("Fast Rendering ResourceHandle failed structural compatibility checks "
                    + "(SHA-256 " + classHash + "); leaving it unchanged");
            return null;
        }

        CompatibilityRegistry.SupportedBuild build = CompatibilityRegistry.find(classHash);
        String payloadResource = CompatibilityRegistry.COMPATIBILITY_PAYLOAD;
        String compatibilityLabel;
        if (build == null) {
            compatibilityLabel = "untested compatibility mode";
            Log.warn("Fast Rendering ResourceHandle hash is not recognized (SHA-256 " + classHash + ").");
            Log.warn("The class passed structural compatibility checks, so caching will be enabled in compatibility mode.");
            Log.warn("This Fast Rendering build or fork has not been tested; disable FR Resource Cache first if resource-loading problems occur.");
        }
        else {
            payloadResource = build.payloadResource;
            compatibilityLabel = build.label;
        }

        byte[] payloadBytes = this.loadPayload(payloadResource);
        if (payloadBytes == null) {
            this.rejected.set(true);
            return null;
        }
        this.applied.set(true);
        Log.info("installed packed-stream hook into Fast Rendering ResourceHandle (" + compatibilityLabel + ")");
        return payloadBytes;
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
