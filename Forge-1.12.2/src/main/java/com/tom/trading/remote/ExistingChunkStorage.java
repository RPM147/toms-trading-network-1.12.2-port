package com.tom.trading.remote;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

/** Read-only target precheck. Vanilla RegionFile/RegionFileCache can create or repair files. */
public final class ExistingChunkStorage {
    private ExistingChunkStorage() {}

    public static boolean exists(Path dimensionFolder, int chunkX, int chunkZ) throws IOException {
        Path root = dimensionFolder.toFile().getCanonicalFile().toPath();
        Path file = root.resolve("region").resolve("r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".mca")
                .toFile().getCanonicalFile().toPath();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) return false;
        try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
            if (input.length() < 8192) return false;
            input.seek(4L * ((chunkX & 31) + (chunkZ & 31) * 32));
            int location = input.readInt(), sector = location >>> 8, sectors = location & 255;
            if (sector < 2 || sectors == 0 || (long) sector * 4096 + 5 > input.length()) return false;
            input.seek((long) sector * 4096);
            int length = input.readInt(), compression = input.readUnsignedByte();
            if (length <= 1 || length > 16 * 1024 * 1024 || (compression != 1 && compression != 2)) return false;
            // Forge 2859's extended-sector format, not a vanilla 255-sector size ceiling.
            long allocated = sectors == 255 ? ((long) length + 4) / 4096 + 1 : sectors;
            return (long) length + 4 <= allocated * 4096 && ((long) sector + allocated) * 4096 <= input.length();
        }
    }
}
