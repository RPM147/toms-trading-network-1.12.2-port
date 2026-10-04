package com.tom.trading.remote;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class ExistingChunkStorageTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private File region(int header, int length, int compression, long size) throws Exception {
        File folder = new File(temp.getRoot(), "region"); folder.mkdir();
        File region = new File(folder, "r.-1.0.mca");
        try (RandomAccessFile output = new RandomAccessFile(region, "rw")) {
            output.setLength(size); output.seek(31 * 4); output.writeInt(header);
            if (size >= 8197) { output.seek(8192); output.writeInt(length); output.writeByte(compression); }
        }
        return region;
    }
    @Test public void missingTargetsNeverCreateFoldersOrFiles() throws Exception {
        assertFalse(ExistingChunkStorage.exists(temp.getRoot().toPath(), -1, 0));
        assertEquals(0, temp.getRoot().list().length);
    }
    @Test public void savedTargetIsReadOnlyAndDoesNotAuthorizeAnAdjacentMissingChunk() throws Exception {
        File file = region(2 << 8 | 1, 8, 2, 12288); byte[] before = Files.readAllBytes(file.toPath());
        assertTrue(ExistingChunkStorage.exists(temp.getRoot().toPath(), -1, 0));
        assertFalse(ExistingChunkStorage.exists(temp.getRoot().toPath(), -2, 0));
        assertArrayEquals(before, Files.readAllBytes(file.toPath()));
    }
    @Test public void corruptLengthsCompressionAndSectorRangesAreRejectedWithoutRepair() throws Exception {
        for (int[] input : new int[][] {{0, 8, 2}, {257, 8, 2}, {513, -1, 2}, {513, 4093, 2}, {513, 8, 3}, {513, 20_000_000, 2}}) {
            File file = region(input[0], input[1], input[2], 12288); byte[] before = Files.readAllBytes(file.toPath());
            assertFalse(ExistingChunkStorage.exists(temp.getRoot().toPath(), -1, 0));
            assertArrayEquals(before, Files.readAllBytes(file.toPath()));
        }
    }
    @Test public void forgeExtendedSectorCountsUseThePinnedLegacyLengthRule() throws Exception {
        int length = 256 * 4096; // One extra sector, matching Forge 2859, even at an exact boundary.
        region(2 << 8 | 255, length, 2, 259L * 4096);
        assertTrue(ExistingChunkStorage.exists(temp.getRoot().toPath(), -1, 0));
        region(2 << 8 | 255, length, 2, 258L * 4096);
        assertFalse(ExistingChunkStorage.exists(temp.getRoot().toPath(), -1, 0));
    }
}
