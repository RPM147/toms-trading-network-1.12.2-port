package com.tom.trading.trade;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Server-thread only. Flushes every record; does not claim fsync or crash-atomic inventory persistence. */
final class RotatingTradeLog implements Closeable {
    private final Path directory;
    private final long maxBytes;
    private final int archives;
    private OutputStream output;
    private long bytes;
    private boolean needsSeparator;
    private boolean closed;

    RotatingTradeLog(Path directory, long maxBytes, int archives) throws IOException {
        if (maxBytes < 1 || archives < 1 || archives > 16) throw new IllegalArgumentException("Invalid log retention");
        this.directory = directory;
        this.maxBytes = maxBytes;
        this.archives = archives;
        Files.createDirectories(directory);
        open();
    }

    void append(String json) throws IOException {
        if (closed) throw new IOException("Transaction log is closed");
        if (json.indexOf('\n') >= 0 || json.indexOf('\r') >= 0) throw new IOException("Multiline transaction log entry");
        byte[] entry = (json + "\n").getBytes(StandardCharsets.UTF_8);
        if (entry.length > maxBytes) throw new IOException("Transaction log entry exceeds file limit");
        if (bytes > maxBytes - entry.length - (needsSeparator ? 1 : 0)) rotate();
        // Preserve an interrupted final line, but never concatenate the next successful receipt onto it.
        if (needsSeparator) {
            output.write('\n');
            bytes++;
            needsSeparator = false;
        }
        output.write(entry);
        // No receipt is retained in an unbounded queue, and normal shutdown needs no asynchronous drain.
        output.flush();
        bytes += entry.length;
    }

    private void rotate() throws IOException {
        output.close();
        output = null;
        Files.deleteIfExists(file(archives));
        for (int index = archives - 1; index >= 0; index--) {
            if (Files.exists(file(index))) Files.move(file(index), file(index + 1), StandardCopyOption.REPLACE_EXISTING);
        }
        open();
    }

    private Path file(int index) {
        return directory.resolve(index == 0 ? "transactions.jsonl" : "transactions." + index + ".jsonl");
    }

    private void open() throws IOException {
        Path file = file(0);
        bytes = Files.exists(file) ? Files.size(file) : 0L;
        needsSeparator = false;
        if (bytes > 0) {
            try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ)) {
                ByteBuffer last = ByteBuffer.allocate(1);
                channel.position(bytes - 1);
                needsSeparator = channel.read(last) == 1 && last.get(0) != '\n';
            }
        }
        output = new BufferedOutputStream(Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND));
    }

    @Override public void close() throws IOException {
        closed = true;
        if (output != null) {
            OutputStream previous = output;
            output = null;
            previous.close();
        }
    }
}
