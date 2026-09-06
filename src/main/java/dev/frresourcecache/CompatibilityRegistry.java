package dev.frresourcecache;

import java.util.List;
import java.util.Set;

final class CompatibilityRegistry {
    static final String TARGET_CLASS = "com/genir/renderer/overrides/loading/ResourceHandle";
    static final String COMPATIBILITY_PAYLOAD =
            "/payload/com/genir/renderer/overrides/loading/ResourceHandle.v1.class.bin";
    static final Set<String> REQUIRED_METHODS = Set.of(
            "<init>:(Lcom/genir/renderer/overrides/loading/ResourceHandle$FileHandle;)V",
            "getString:()Ljava/lang/String;",
            "getFilePath:()Ljava/nio/file/Path;",
            "read:()I",
            "close:()V");
    static final Set<String> REQUIRED_FIELD_REFERENCES = Set.of(
            "com/genir/renderer/overrides/loading/ResourceHandle$FileHandle.file:Ljava/io/File;",
            "com/genir/renderer/overrides/loading/ResourceHandle$FileHandle.cachedContents:Ljava/lang/String;");
    static final List<SupportedBuild> SUPPORTED_BUILDS = List.of(
            new SupportedBuild(
                    "6b29c9f634b756a1e0d0bdd3a69bd6f62758b62ec3d3a1d6f8635be628a60600",
                    "Fast Rendering 0.8.5rc2 / 0.8.7rc1 / 0.8.7",
                    COMPATIBILITY_PAYLOAD));

    private CompatibilityRegistry() {
    }

    static SupportedBuild find(String text) {
        for (SupportedBuild build : SUPPORTED_BUILDS) {
            if (!build.sha256Hex.equalsIgnoreCase(text)) continue;
            return build;
        }
        return null;
    }

    static final class SupportedBuild {
        final String sha256Hex;
        final String label;
        final String payloadResource;

        SupportedBuild(String text, String otherText, String thirdText) {
            this.sha256Hex = text;
            this.label = otherText;
            this.payloadResource = thirdText;
        }
    }
}
