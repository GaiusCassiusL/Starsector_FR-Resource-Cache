package com.genir.renderer.overrides.loading;

import dev.frresourcecache.ResourcePackCache;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class ResourceHandle extends InputStream {
    public static class FileHandle {
        public final File file = null;
        public String cachedContents;
    }

    private final FileHandle fileHandle;
    private InputStream fileStream;
    private boolean streamOpened;
    private boolean closed;

    public ResourceHandle(FileHandle fileHandle) {
        this.fileHandle = fileHandle;
    }

    public String getString() throws IOException {
        if (this.fileHandle.cachedContents == null) {
            try {
                this.fileHandle.cachedContents = new String(ResourcePackCache.read(this.fileHandle.file), StandardCharsets.UTF_8);
            }
            catch (Exception exception) {
                this.fileHandle.cachedContents =
                        FileLoader.readStringVanilla(new FileInputStream(this.fileHandle.file));
            }
            this.fileHandle.cachedContents = this.fileHandle.cachedContents.replaceAll("\\r", "");
        }
        return this.fileHandle.cachedContents;
    }

    public Path getFilePath() {
        return this.fileHandle.file.toPath();
    }

    private InputStream stream() throws IOException {
        if (this.closed) {
            throw new IOException("stream closed");
        }
        if (this.fileStream == null) {
            this.fileStream = ResourcePackCache.open(this.fileHandle.file);
            this.streamOpened = true;
        }
        return this.fileStream;
    }

    @Override
    public int read() throws IOException {
        return this.stream().read();
    }

    @Override
    public int read(byte[] bytes) throws IOException {
        return this.stream().read(bytes);
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        return this.stream().read(bytes, offset, length);
    }

    @Override
    public byte[] readAllBytes() throws IOException {
        return this.stream().readAllBytes();
    }

    @Override
    public byte[] readNBytes(int length) throws IOException {
        return this.stream().readNBytes(length);
    }

    @Override
    public long transferTo(OutputStream output) throws IOException {
        return this.stream().transferTo(output);
    }

    @Override
    public long skip(long count) throws IOException {
        return this.stream().skip(count);
    }

    @Override
    public int available() throws IOException {
        return this.stream().available();
    }

    @Override
    public void close() throws IOException {
        if (this.closed) {
            return;
        }
        this.closed = true;
        if (this.streamOpened && this.fileStream != null) {
            this.fileStream.close();
        }
    }
}
