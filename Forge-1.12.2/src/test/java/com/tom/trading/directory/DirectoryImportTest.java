package com.tom.trading.directory;

import org.junit.Test;
import java.io.StringReader;
import java.util.*;
import static org.junit.Assert.*;

public class DirectoryImportTest {
    private final UUID world = UUID.fromString("07238993-06cb-477c-be4b-2458bba30ce1");
    private final DimensionManifest manifest = new DimensionManifest(world, Collections.singletonList(
            new DimensionManifest.Dimension(7, "ExampleProvider", "AoA_Abyss", "")));
    private String input() {
        return "{\"format\":1,\"worldId\":\"" + world + "\",\"manifestDigest\":\"" + manifest.manifestDigest
                + "\",\"complete\":false,\"entries\":[{\"dimension\":7,\"x\":1,\"y\":64,\"z\":2,"
                + "\"machineId\":null,\"ownerId\":null,\"ownerName\":\"Owner\",\"name\":\"Shop\"}]}";
    }
    private DirectoryImport read(String json) throws Exception {
        return DirectoryImport.read(new StringReader(json), world, manifest.manifestDigest, manifest);
    }
    @Test public void validImportIsOnlyAHintAndPreservesIncompleteCoverage() throws Exception {
        // Shared signature vector with the Python scanner; no JSON ordering/escaping dependency.
        assertEquals("471e719b46a48689f141419f32642ac3ccf1e080d337c12dff1eb53eb8549632", manifest.manifestDigest);
        DirectoryImport result = read(input());
        assertEquals(1, result.hints.size()); assertFalse(result.complete);
        assertEquals(MachineDirectoryEntry.Evidence.HINT, result.hints.get(0).evidence);
        assertNull(result.hints.get(0).machineId);
        assertEquals(7, result.hints.get(0).address.dimension);
    }
    @Test public void rejectsWrongWorldDigestDimensionDuplicateFieldAndPrivatePayload() throws Exception {
        List<String> invalid = Arrays.asList(
                input().replace(world.toString(), UUID.randomUUID().toString()),
                input().replace(manifest.manifestDigest, String.join("", Collections.nCopies(64, "0"))),
                input().replace("\"dimension\":7", "\"dimension\":8"),
                input().replace("\"x\":1", "\"x\":1,\"x\":2"),
                input().replace("\"name\":\"Shop\"", "\"name\":\"Shop\",\"SaleStock\":{}"),
                input().replace("\"y\":64", "\"y\":256"), input() + "{}"
        );
        for (String value : invalid) {
            try { read(value); fail("Invalid import accepted"); }
            catch (java.io.IOException | IllegalArgumentException | IllegalStateException expected) { }
        }
    }
    @Test public void folderEscapeIsRejectedBeforeAnyFilesystemWrites() throws Exception {
        java.nio.file.Path root = new java.io.File(".").getCanonicalFile().toPath();
        for (String value : Arrays.asList("../outside", "C:/outside", "/outside", "folder\nname")) {
            try { DimensionManifest.resolveFolder(root, value); fail("Unsafe folder accepted"); }
            catch (java.io.IOException expected) { }
        }
        assertEquals(root.resolve("AoA_Abyss"), DimensionManifest.resolveFolder(root, "AoA_Abyss"));
    }
}
