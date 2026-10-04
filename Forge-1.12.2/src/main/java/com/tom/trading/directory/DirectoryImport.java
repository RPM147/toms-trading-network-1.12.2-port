package com.tom.trading.directory;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

/** Small, strict, streaming schema: no arbitrary JSON trees, nested payloads or item NBT. */
public final class DirectoryImport {
    public static final int MAX_HINTS = 10000;
    public final List<MachineDirectoryEntry> hints;
    public final boolean complete;
    private DirectoryImport(List<MachineDirectoryEntry> hints, boolean complete) { this.hints = hints; this.complete = complete; }

    public static DirectoryImport read(Reader input, UUID worldId, String manifestDigest,
                                       DimensionManifest manifest) throws IOException {
        JsonReader reader = new JsonReader(input); reader.setLenient(false);
        Set<String> fields = new HashSet<>();
        List<MachineDirectoryEntry> hints = new ArrayList<>();
        Set<MachineAddress> positions = new HashSet<>();
        boolean complete = false;
        reader.beginObject();
        while (reader.hasNext()) {
            String field = reader.nextName();
            if (!fields.add(field)) throw new IOException("Duplicate import field");
            switch (field) {
                case "format": if (reader.nextInt() != 1) throw new IOException("Unknown import format"); break;
                case "worldId": if (!worldId.equals(uuid(string(reader, 36)))) throw new IOException("Wrong source world"); break;
                case "manifestDigest":
                    if (!manifestDigest.equals(string(reader, 64)) || !manifestDigest.equals(manifest.manifestDigest))
                        throw new IOException("Dimension manifest changed; export and scan again");
                    break;
                case "complete": complete = reader.nextBoolean(); break;
                case "entries":
                    reader.beginArray();
                    while (reader.hasNext()) {
                        if (hints.size() >= MAX_HINTS) throw new IOException("Import entry limit exceeded");
                        MachineDirectoryEntry entry = readEntry(reader);
                        if (!manifest.containsSupported(entry.address.dimension)) throw new IOException("Unsupported dimension");
                        if (!positions.add(entry.address)) throw new IOException("Duplicate import address");
                        hints.add(entry);
                    }
                    reader.endArray(); break;
                default: throw new IOException("Unknown import field: " + field);
            }
        }
        reader.endObject();
        if (fields.size() != 5 || reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Incomplete import envelope");
        return new DirectoryImport(Collections.unmodifiableList(hints), complete);
    }

    private static MachineDirectoryEntry readEntry(JsonReader reader) throws IOException {
        Set<String> fields = new HashSet<>();
        int dimension = 0, x = 0, y = 0, z = 0;
        UUID machine = null, owner = null; String ownerName = "", name = "";
        reader.beginObject();
        while (reader.hasNext()) {
            String field = reader.nextName();
            if (!fields.add(field)) throw new IOException("Duplicate hint field");
            switch (field) {
                case "dimension": dimension = reader.nextInt(); break;
                case "x": x = reader.nextInt(); break;
                case "y": y = reader.nextInt(); break;
                case "z": z = reader.nextInt(); break;
                case "machineId": machine = nullableUuid(reader); break;
                case "ownerId": owner = nullableUuid(reader); break;
                case "ownerName": ownerName = string(reader, 128); break;
                case "name": name = string(reader, 128); break;
                default: throw new IOException("Unknown hint field");
            }
        }
        reader.endObject();
        if (fields.size() != 8) throw new IOException("Incomplete hint");
        return new MachineDirectoryEntry(new MachineAddress(dimension, x, y, z), machine, owner,
                ownerName, name, MachineDirectoryEntry.Evidence.HINT);
    }
    private static String string(JsonReader reader, int max) throws IOException {
        if (reader.peek() != JsonToken.STRING) throw new IOException("Expected string");
        String value = reader.nextString();
        if (value.length() > max) throw new IOException("String limit exceeded");
        return value;
    }
    private static UUID nullableUuid(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) { reader.nextNull(); return null; }
        return uuid(string(reader, 36));
    }
    private static UUID uuid(String value) throws IOException {
        UUID id = UUID.fromString(value);
        if (!id.toString().equals(value)) throw new IOException("Noncanonical UUID");
        return id;
    }
}
