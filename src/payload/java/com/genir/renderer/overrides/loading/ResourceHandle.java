package com.genir.renderer.overrides.loading;

import dev.frresourcecache.ResourcePackCache;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ResourceHandle {
    private final File file;
    private final Path filePath;
    private final FileLoader.ResourceLocation location;

    private String cachedString = null;

    public ResourceHandle(File file, FileLoader.ResourceLocation location, String extension) {
        this.file = file;
        this.filePath = file.toPath();
        this.location = location;

        try {
            if (preloadString(extension)) {
                getString();
            }
        } catch (IOException ignored) {
        }
    }

    public boolean isCoreFile() {
        return !location.ResourceLocation_isMod;
    }

    synchronized public String getString() throws IOException {
        if (cachedString == null) {
            String text = null;
            try {
                byte[] bytes = ResourcePackCache.read(file);
                text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            } catch (Exception | LinkageError e) {
                text = null;
            }

            if (text == null) {
                try {
                    text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    text = LoadingUtils.readStreamAsString_vanilla(new FileInputStream(file));
                }
            }

            cachedString = text.indexOf('\r') < 0 ? text : text.replace("\r", "");
        }

        return cachedString;
    }

    public File getFile() {
        return file;
    }

    public Path getFilePath() {
        return filePath;
    }

    public FileLoader.ResourceLocation getLocation() {
        return location;
    }

    private boolean preloadString(String extension) {
        return switch (extension) {
            case "txt", "json", "csv", "skill", "faction", "variant", "skin", "system", "wpn", "ship", "proj" -> true;
            default -> false;
        };
    }
}
