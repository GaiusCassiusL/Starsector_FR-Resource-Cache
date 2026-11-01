package dev.frresourcecache;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Verifies that unrecognized or incompatible Fast Rendering builds never break startup. */
public final class FallbackTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("frrc-test");
        try {
            FrResourceCacheAgent.premain("cacheDir=" + dir.resolve("c") + ",bogus=,memoryCacheMiB=notanumber", null);
            FrResourceCacheAgent.premain(null, null);

            TargetTransformer transformer = new TargetTransformer();
            byte[] garbage = {1, 2, 3, 4};
            check(transformer.transform(null, null, "other/Class", null, null, garbage) == null, "unrelated class");
            check(transformer.transform(null, null, CompatibilityRegistry.TARGET_CLASS, null, null, garbage) == null,
                    "garbage target class");
            check(transformer.transform(null, null, CompatibilityRegistry.TARGET_CLASS, null, null, null) == null,
                    "null target class");
            check(CompatibilityRegistry.find("0000") == null, "unknown hash");
            check(CompatibilityRegistry.find(
                    "6abd30edd511886ce2204c8f9948bf35ae21f1064d7a4bf2e80588fccbcac18c") != null, "0.9.8rc1 hash");

            if (args.length > 0) {
                try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(args[0])) {
                    byte[] real = zip.getInputStream(zip.getEntry(CompatibilityRegistry.TARGET_CLASS + ".class"))
                            .readAllBytes();
                    check(ClassFileValidator.isPlausibleResourceHandle(real, CompatibilityRegistry.TARGET_CLASS,
                            CompatibilityRegistry.REQUIRED_METHODS,
                            CompatibilityRegistry.REQUIRED_FIELD_REFERENCES), "real FR class passes structure check");
                    byte[] payload = transformer.transform(null, null, CompatibilityRegistry.TARGET_CLASS, null, null, real);
                    check(payload != null && payload.length > 0, "real FR class is replaced");
                }
            }

            Path file = dir.resolve("a.txt");
            Files.writeString(file, "hello");
            check(new String(ResourcePackCache.read(file.toFile())).equals("hello"), "read passthrough");
            System.out.println("OK");
        }
        finally {
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private static void check(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError("FAILED: " + name);
        }
    }
}
