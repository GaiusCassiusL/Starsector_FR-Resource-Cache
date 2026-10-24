package dev.frresourcecache;

import java.lang.instrument.Instrumentation;

public final class FrResourceCacheAgent {
    public static final String VERSION = "0.5.0-test.1";

    private FrResourceCacheAgent() {
    }

    public static void premain(String text, Instrumentation instrumentation) {
        try {
            AgentConfig settings = AgentConfig.parse(text);
            ResourcePackCache.initialize(settings);
            TargetTransformer targetTransformer = new TargetTransformer();
            instrumentation.addTransformer(targetTransformer, false);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    ResourcePackCache.shutdown();
                    targetTransformer.printSummary();
                }
                catch (Throwable throwable) {
                    Log.warn("shutdown failed: " + throwable);
                }
            }, "FR-Resource-Cache-Shutdown"));
            Log.info("agent v" + VERSION + " enabled; cache directory: " + String.valueOf(settings.cacheDir));
        }
        catch (Throwable throwable) {
            Log.warn("agent could not start; the game will run without FR Resource Cache: " + throwable);
        }
    }
}
