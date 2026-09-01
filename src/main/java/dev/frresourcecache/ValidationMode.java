package dev.frresourcecache;

import java.util.Locale;

enum ValidationMode {
    METADATA(0),
    SAMPLED(1),
    STRONG(2);

    final int id;

    private ValidationMode(int n2) {
        this.id = n2;
    }

    static ValidationMode fromId(int n) {
        for (ValidationMode validationMode : ValidationMode.values()) {
            if (validationMode.id != n) continue;
            return validationMode;
        }
        throw new IllegalArgumentException("unknown validation mode id " + n);
    }

    static ValidationMode parse(String text, ValidationMode validationMode) {
        if (text == null || text.isBlank()) {
            return validationMode;
        }
        switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "metadata": {
                return METADATA;
            }
            case "sampled": {
                return SAMPLED;
            }
            case "strong": {
                return STRONG;
            }
        }
        return validationMode;
    }
}
