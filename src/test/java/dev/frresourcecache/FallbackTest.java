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
                    "4643c5ab517301903523736bce50aaa65dc40e64196289cad0d502c575826dd4") != null, "0.9.6 hash");

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
