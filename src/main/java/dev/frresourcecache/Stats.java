package dev.frresourcecache;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class Stats {
    final AtomicLong requests = new AtomicLong();
    final AtomicLong hits = new AtomicLong();
    final AtomicLong misses = new AtomicLong();
    final AtomicLong bypasses = new AtomicLong();
    final AtomicLong stale = new AtomicLong();
    final AtomicLong corrupt = new AtomicLong();
    final AtomicLong unstable = new AtomicLong();
    final AtomicLong hitBytes = new AtomicLong();
    final AtomicLong sourceBytes = new AtomicLong();
    private final ConcurrentHashMap<String, Counters> byExtension = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counters> byCategory = new ConcurrentHashMap<>();

    Stats() {
    }

    private Counters extension(String otherText) {
        return this.byExtension.computeIfAbsent(otherText, text -> new Counters());
    }

    private Counters category(SourceCategory sourceCategory) {
        return this.byCategory.computeIfAbsent(sourceCategory.label(), text -> new Counters());
    }

    void recordHit(String text, SourceCategory sourceCategory, long l, long l2) {
        this.requests.incrementAndGet();
        this.hits.incrementAndGet();
        this.hitBytes.addAndGet(l);
        this.extension(text).hits.incrementAndGet();
        this.extension(text).record(l, l2);
        this.category(sourceCategory).hits.incrementAndGet();
        this.category(sourceCategory).record(l, l2);
    }

    void recordMiss(String text, SourceCategory sourceCategory, long l, long l2) {
        this.requests.incrementAndGet();
        this.misses.incrementAndGet();
        this.sourceBytes.addAndGet(l);
        this.extension(text).misses.incrementAndGet();
        this.extension(text).record(l, l2);
        this.category(sourceCategory).misses.incrementAndGet();
        this.category(sourceCategory).record(l, l2);
    }

    void recordBypass(String text, SourceCategory sourceCategory) {
        this.requests.incrementAndGet();
        this.bypasses.incrementAndGet();
        this.extension(text).bypasses.incrementAndGet();
        this.category(sourceCategory).bypasses.incrementAndGet();
    }

    void recordStale(String text, SourceCategory sourceCategory) {
        this.stale.incrementAndGet();
        this.extension(text).stale.incrementAndGet();
        this.category(sourceCategory).stale.incrementAndGet();
    }

    void recordCorrupt(String text, SourceCategory sourceCategory) {
        this.corrupt.incrementAndGet();
        this.extension(text).corrupt.incrementAndGet();
        this.category(sourceCategory).corrupt.incrementAndGet();
    }

    void recordUnstable() {
        this.unstable.incrementAndGet();
    }

    String summaryLine(int n, String text) {
        return "summary: " + this.requests.get() + " requests, " + this.hits.get() + " hits (" + Stats.formatBytes(this.hitBytes.get()) + "), " + this.misses.get() + " misses (" + Stats.formatBytes(this.sourceBytes.get()) + " read from source), " + this.bypasses.get() + " bypasses, " + this.stale.get() + " stale, " + this.corrupt.get() + " corrupt, " + this.unstable.get() + " unstable(actively-changing), " + n + " indexed resources, pack size " + text;
    }

    List<String> verboseBreakdown() {
        return List.of(Stats.renderMap("by extension", this.byExtension), Stats.renderMap("by source", this.byCategory));
    }

    private static String renderMap(String text, Map<String, Counters> map) {
        StringBuilder builder = new StringBuilder(text).append(':').append(System.lineSeparator());
        map.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey)).forEach(entry ->
                builder.append("  ").append(entry.getValue().render(entry.getKey()))
                        .append(System.lineSeparator()));
        return builder.toString();
    }

    static String formatBytes(long l) {
        if (l < 1024L) {
            return l + " B";
        }
        if (l < 0x100000L) {
            return String.format(Locale.ROOT, "%.1f KiB", (double)l / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MiB", (double)l / 1048576.0);
    }

    static final class Counters {
        final AtomicLong hits = new AtomicLong();
        final AtomicLong misses = new AtomicLong();
        final AtomicLong bypasses = new AtomicLong();
        final AtomicLong stale = new AtomicLong();
        final AtomicLong corrupt = new AtomicLong();
        final AtomicLong bytes = new AtomicLong();
        final AtomicLong latencyNanos = new AtomicLong();
        final AtomicLong observations = new AtomicLong();

        Counters() {
        }

        void record(long l, long l2) {
            this.bytes.addAndGet(l);
            this.latencyNanos.addAndGet(l2);
            this.observations.incrementAndGet();
        }

        String render(String text) {
            long l = this.observations.get();
            double d = l == 0L ? 0.0 : (double)this.latencyNanos.get() / 1000.0 / (double)l;
            return String.format(Locale.ROOT, "%-24s hits=%-6d misses=%-6d bypass=%-6d stale=%-5d corrupt=%-4d bytes=%-10s avgLatency=%.1fus", text, this.hits.get(), this.misses.get(), this.bypasses.get(), this.stale.get(), this.corrupt.get(), Stats.formatBytes(this.bytes.get()), d);
        }
    }
}
