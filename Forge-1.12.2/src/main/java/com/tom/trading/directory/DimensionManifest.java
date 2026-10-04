package com.tom.trading.directory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Provider metadata discovery only: never initDimension, getChunkLoader or loadChunk. */
public final class DimensionManifest {
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public final int format = 1;
    public final String worldId;
    public final List<Dimension> dimensions;
    public final String manifestDigest;

    public static final class Dimension {
        public final int id;
        public final String provider, folder, problem;
        Dimension(int id, String provider, String folder, String problem) {
            this.id = id; this.provider = provider; this.folder = folder; this.problem = problem;
        }
        public boolean supported() { return problem.isEmpty(); }
    }

    DimensionManifest(UUID worldId, List<Dimension> dimensions) {
        this.worldId = worldId.toString(); this.dimensions = Collections.unmodifiableList(new ArrayList<>(dimensions));
        StringBuilder signature = new StringBuilder("ttn-directory-manifest-v1\n").append(this.worldId).append('\n');
        for (Dimension d : dimensions) signature.append(d.id).append('\t').append(d.provider).append('\t')
                .append(d.folder).append('\t').append(d.problem).append('\n');
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(signature.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
            manifestDigest = hex.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public static DimensionManifest capture(WorldServer overworld, MachineDirectoryData data) throws IOException {
        if (!overworld.isCallingFromMinecraftThread() || !data.isSupported()) throw new IllegalStateException("Directory unavailable");
        Path root = overworld.getSaveHandler().getWorldDirectory().getCanonicalFile().toPath();
        // Pinned Forge 2859 exposes registered (not only loaded) dimensions through this internal API.
        Integer[] ids = DimensionManager.getStaticDimensionIDs();
        Arrays.sort(ids);
        List<Dimension> rows = new ArrayList<>();
        Map<String, Integer> folders = new HashMap<>();
        for (int id : ids) {
            String providerName = "unknown", folder = "", problem = "";
            try {
                WorldServer loaded = DimensionManager.getWorld(id);
                WorldProvider provider = loaded == null ? DimensionManager.createProviderFor(id) : loaded.provider;
                providerName = provider.getClass().getName();
                String saveFolder = provider.getSaveFolder();
                if (saveFolder == null && id != 0) throw new IOException("Non-overworld provider has no folder");
                folder = saveFolder == null ? "" : saveFolder.replace('\\', '/');
                Path target = resolveFolder(root, folder);
                if (loaded != null && !loaded.getChunkSaveLocation().getCanonicalFile().toPath().equals(target))
                    throw new IOException("Chunk loader folder differs from provider");
                String key = target.toString().toLowerCase(Locale.ROOT);
                Integer previous = folders.putIfAbsent(key, rows.size());
                if (previous != null) {
                    Dimension other = rows.get(previous);
                    rows.set(previous, new Dimension(other.id, other.provider, other.folder, "Duplicate save folder"));
                    throw new IOException("Duplicate save folder");
                }
            } catch (RuntimeException | LinkageError | IOException ex) {
                // Do not publish uncontrolled exception text in the canonical signature.
                problem = "Unsupported provider or storage mapping";
                try { resolveFolder(root, folder); } catch (IOException | RuntimeException invalid) { folder = ""; }
            }
            rows.add(new Dimension(id, providerName, folder, problem));
        }
        return new DimensionManifest(data.getWorldId(), rows);
    }

    public static Path resolveFolder(Path root, String folder) throws IOException {
        if (folder.startsWith("/") || folder.contains(":") || folder.contains("..")
                || folder.indexOf('\t') >= 0 || folder.indexOf('\n') >= 0 || folder.indexOf('\r') >= 0)
            throw new IOException("Unsafe save folder");
        Path path = root.resolve(folder).toFile().getCanonicalFile().toPath();
        if (!path.startsWith(root)) throw new IOException("Save folder escapes world");
        return path;
    }

    public boolean containsSupported(int dimension) {
        for (Dimension entry : dimensions) if (entry.id == dimension) return entry.supported();
        return false;
    }
}
