package dev.frresourcecache;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class AgentConfig {
    static final String DEFAULT_EXTENSIONS = ".ship;.variant;.skin;.wpn;.proj;.system;.faction;.skill;.paintjob;.json;.csv;.txt;.xml;.java;.rules";
    final Path gameRoot;
    final Path installRoot;
    final Path cacheDir;
    final long maxFileSize;
    final long memoryLimit;
    final int flushDelaySeconds;
    final Set<String> extensions;
    final ValidationMode validationMode;
    final CompactionMode compactionMode;
    final double deadByteRatioThreshold;
    final long minDeadBytes;
    final boolean forceCompact;
    final Path traceLogPath;
    final boolean statsVerbose;

    private AgentConfig(Path path, Path path2, Path path3, long l, long l2, int n, Set<String> set, ValidationMode validationMode, CompactionMode compactionMode, double d, long l3, boolean bl, Path path4, boolean bl2) {
        this.gameRoot = path;
        this.installRoot = path2;
        this.cacheDir = path3;
        this.maxFileSize = l;
        this.memoryLimit = l2;
        this.flushDelaySeconds = n;
        this.extensions = set;
        this.validationMode = validationMode;
        this.compactionMode = compactionMode;
        this.deadByteRatioThreshold = d;
        this.minDeadBytes = l3;
        this.forceCompact = bl;
        this.traceLogPath = path4;
        this.statsVerbose = bl2;
    }

    static AgentConfig parse(String arguments) {
        HashMap<String, String> options = new HashMap<>();
        if (arguments != null && !arguments.isBlank()) {
            for (String argument : arguments.split(",")) {
                int separator = argument.indexOf('=');
                if (separator > 0) {
                    options.put(argument.substring(0, separator).trim(),
                            unquote(argument.substring(separator + 1).trim()));
                }
            }
        }

        Path gameRoot = Paths.get(options.getOrDefault("gameRoot", ".")).toAbsolutePath().normalize();
        Path defaultInstallRoot = gameRoot.getParent() == null ? gameRoot : gameRoot.getParent();
        Path installRoot = resolve(gameRoot, options.get("installRoot"), defaultInstallRoot);
        Path cacheDir = resolve(gameRoot, options.get("cacheDir"), gameRoot.resolve("../fr-resource-cache"));
        long maxFileSize = parseLong(options.get("maxFileSize"), 1_048_576L, 1L, 16_777_216L);
        long memoryLimit = parseLong(options.get("memoryCacheMiB"), 128L, 0L, 4096L) * 1_048_576L;
        int flushDelaySeconds = (int) parseLong(options.get("flushDelaySeconds"), 30L, 0L, 3600L);

        HashSet<String> extensions = new HashSet<>();
        Arrays.stream(options.getOrDefault("extensions", DEFAULT_EXTENSIONS).split(";"))
                .map(String::trim)
                .filter(extension -> !extension.isEmpty())
                .map(extension -> extension.startsWith(".") ? extension : "." + extension)
                .map(extension -> extension.toLowerCase(Locale.ROOT))
                .forEach(extensions::add);

        ValidationMode validationMode = ValidationMode.parse(options.get("validationMode"), ValidationMode.METADATA);
        CompactionMode compactionMode = CompactionMode.parse(options.get("compaction"), CompactionMode.AUTO);
        double deadByteRatioThreshold = parseDouble(options.get("compactionDeadRatio"), 0.35, 0.05, 0.95);
        long minDeadBytes = parseLong(options.get("compactionMinDeadMiB"), 8L, 1L, 65_536L) * 1_048_576L;
        boolean forceCompact = parseBool(options.get("forceCompact"), false);

        String traceOption = options.get("trace");
        Path traceLogPath = null;
        if (traceOption != null && !traceOption.isBlank() && !traceOption.equalsIgnoreCase("false")) {
            Path defaultTracePath = cacheDir.resolve("resources.trace.log");
            traceLogPath = traceOption.equalsIgnoreCase("true")
                    ? defaultTracePath
                    : resolve(gameRoot, traceOption, defaultTracePath);
        }
        boolean statsVerbose = parseBool(options.get("statsVerbose"), false);

        return new AgentConfig(gameRoot, installRoot, cacheDir, maxFileSize, memoryLimit,
                flushDelaySeconds, Set.copyOf(extensions), validationMode, compactionMode,
                deadByteRatioThreshold, minDeadBytes, forceCompact, traceLogPath, statsVerbose);
    }

    private static Path resolve(Path path, String text, Path path2) {
        if (text == null || text.isBlank()) {
            return path2.toAbsolutePath().normalize();
        }
        Path path3 = Paths.get(AgentConfig.unquote(text), new String[0]);
        return (path3.isAbsolute() ? path3 : path.resolve(path3)).toAbsolutePath().normalize();
    }

    private static long parseLong(String text, long l, long l2, long l3) {
        if (text == null) {
            return l;
        }
        try {
            return Math.max(l2, Math.min(l3, Long.parseLong(text)));
        }
        catch (NumberFormatException exception) {
            return l;
        }
    }

    private static double parseDouble(String text, double d, double d2, double d3) {
        if (text == null) {
            return d;
        }
        try {
            return Math.max(d2, Math.min(d3, Double.parseDouble(text)));
        }
        catch (NumberFormatException exception) {
            return d;
        }
    }

    private static boolean parseBool(String text, boolean bl) {
        if (text == null) {
            return bl;
        }
        if (text.equalsIgnoreCase("true")) {
            return true;
        }
        if (text.equalsIgnoreCase("false")) {
            return false;
        }
        return bl;
    }

    private static String unquote(String text) {
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    static enum CompactionMode {
        AUTO,
        MANUAL,
        DISABLED;


        static CompactionMode parse(String text, CompactionMode compactionMode) {
            if (text == null || text.isBlank()) {
                return compactionMode;
            }
            switch (text.trim().toLowerCase(Locale.ROOT)) {
                case "auto": {
                    return AUTO;
                }
                case "manual": {
                    return MANUAL;
                }
                case "disabled": 
                case "off": 
                case "none": {
                    return DISABLED;
                }
            }
            return compactionMode;
        }
    }
}
