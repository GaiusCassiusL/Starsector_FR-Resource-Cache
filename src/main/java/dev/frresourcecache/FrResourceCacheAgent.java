package dev.frresourcecache;

import java.lang.instrument.Instrumentation;

public final class FrResourceCacheAgent {
    public static final String VERSION = "0.4.0";

    private FrResourceCacheAgent() {
    }

    public static void premain(String text, Instrumentation instrumentation) {
        AgentConfig settings = AgentConfig.parse(text);
        ResourcePackCache.initialize(settings);
        TargetTransformer targetTransformer = new TargetTransformer();
        instrumentation.addTransformer(targetTransformer, false);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ResourcePackCache.shutdown();
            targetTransformer.printSummary();
        }, "FR-Resource-Cache-Shutdown"));
        Log.info("agent v" + VERSION + " enabled; cache directory: " + String.valueOf(settings.cacheDir));
    }
}
