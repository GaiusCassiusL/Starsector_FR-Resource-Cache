package dev.frresourcecache;

import java.util.List;
import java.util.Set;

final class CompatibilityRegistry {
    static final String TARGET_CLASS = "com/genir/renderer/overrides/loading/ResourceHandle";
    static final String COMPATIBILITY_PAYLOAD =
            "/payload/com/genir/renderer/overrides/loading/ResourceHandle.v1.class.bin";
    static final Set<String> REQUIRED_METHODS = Set.of(
            "<init>:(Ljava/io/File;Lcom/genir/renderer/overrides/loading/FileLoader$ResourceLocation;Ljava/lang/String;)V",
            "getString:()Ljava/lang/String;",
            "getFile:()Ljava/io/File;",
            "getFilePath:()Ljava/nio/file/Path;",
            "getLocation:()Lcom/genir/renderer/overrides/loading/FileLoader$ResourceLocation;",
            "isCoreFile:()Z");
    static final Set<String> REQUIRED_FIELD_REFERENCES = Set.of(
            "com/genir/renderer/overrides/loading/ResourceHandle.file:Ljava/io/File;",
            "com/genir/renderer/overrides/loading/ResourceHandle.cachedString:Ljava/lang/String;");
    static final List<SupportedBuild> SUPPORTED_BUILDS = List.of(
            new SupportedBuild(
                    "6abd30edd511886ce2204c8f9948bf35ae21f1064d7a4bf2e80588fccbcac18c",
                    "Fast Rendering 0.9.8rc1 / 1.0.0rc1",
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
