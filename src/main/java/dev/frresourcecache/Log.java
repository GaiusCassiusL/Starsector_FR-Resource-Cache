package dev.frresourcecache;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

final class Log {
    private static final String PREFIX = "[FR Resource Cache] ";
    private static volatile PrintWriter trace;

    private Log() {
    }

    static void info(String text) {
        System.out.println(PREFIX + text);
    }

    static void warn(String text) {
        System.err.println("[FR Resource Cache] WARNING: " + text);
    }

    static void enableTrace(Path path) {
        try {
            Files.createDirectories(path.getParent());
            BufferedWriter writerBuffer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            trace = new PrintWriter((Writer)writerBuffer, true);
            Log.trace(Category.INFO, "trace log opened");
        }
        catch (IOException exception) {
            Log.warn("could not open trace log " + String.valueOf(path) + ": " + String.valueOf(exception));
        }
    }

    static void trace(Category category, String text) {
        PrintWriter writer = trace;
        if (writer == null) {
            return;
        }
        writer.println(String.valueOf(Instant.now()) + " [" + String.valueOf((Object)category) + "] " + text);
    }

    static boolean traceEnabled() {
        return trace != null;
    }

    static void closeTrace() {
        PrintWriter writer = trace;
        if (writer != null) {
            writer.flush();
            writer.close();
            trace = null;
        }
    }

    static enum Category {
        CACHED,
        BYPASSED,
        STALE,
        CORRUPT,
        COMPACTED,
        INFO;

    }
}
