package dev.frresourcecache;

import java.nio.file.Path;
import java.util.Locale;

enum SourceCategory {
    CORE,
    MOD,
    OTHER;

    private static final String MOD_SEGMENT = "mods";

    static SourceCategory classify(Path path, Path path2) {
        Path path3;
        try {
            path3 = path.relativize(path2);
        }
        catch (IllegalArgumentException exception) {
            return OTHER;
        }
        if (path3.getNameCount() == 0) {
            return OTHER;
        }
        String text = path3.getName(0).toString().toLowerCase(Locale.ROOT);
        if (text.equals(MOD_SEGMENT)) {
            return MOD;
        }
        if (text.contains("core")) {
            return CORE;
        }
        return OTHER;
    }

    String label() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
