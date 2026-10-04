package com.tom.trading.directory;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;
import com.tom.trading.tile.TileVendingMachine;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.util.math.BlockPos;

/** Explicit operator tooling; never scans region files on a running server. */
public final class DirectoryCommand extends CommandBase {
    @Override public String getName() { return "ttndirectory"; }
    @Override public int getRequiredPermissionLevel() { return 2; }
    @Override public String getUsage(ICommandSender sender) {
        return "/ttndirectory status | export | import <file.json> | reindex | renew <dim> <x> <y> <z> | special add <dim> <x> <y> <z> | special remove <uuid> | special list [page]";
    }
    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        // Also enforce on direct execution, before touching any world or persisted membership.
        if (!sender.canUseCommand(getRequiredPermissionLevel(), getName())) throw new CommandException("commands.generic.permission");
        if (args.length == 0) throw new WrongUsageException(getUsage(sender));
        WorldServer overworld = DimensionManager.getWorld(0);
        MachineDirectoryData data = MachineDirectory.get(overworld);
        if (data == null) throw new CommandException("Directory is unavailable");
        if (args[0].equals("status") && args.length == 1) {
            sender.sendMessage(new TextComponentString("TTN directory: " + data.size() + " entries, " + data.conflictCount()
                    + " identity conflicts; last backup coverage=" + data.isBackfillComplete()
                    + "; supported=" + data.isSupported() + "; " + data.getProblem()));
            return;
        }
        if (!data.isSupported()) throw new CommandException("Directory is unavailable: " + data.getProblem());
        try {
            switch (args[0]) {
                case "special": {
                    special(sender, args, data);
                    return;
                }
                case "export": {
                    if (args.length != 1) throw new WrongUsageException(getUsage(sender));
                    DimensionManifest manifest = DimensionManifest.capture(overworld, data);
                    Path folder = exchangeFolder(overworld);
                    Files.createDirectories(folder);
                    Path output = folder.resolve("manifest.json");
                    if (Files.exists(output)) throw new IOException("manifest.json exists; archive it before re-exporting");
                    try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
                        DimensionManifest.JSON.toJson(manifest, writer);
                    }
                    data.setManifestDigest(manifest.manifestDigest);
                    overworld.getMapStorage().saveAllData();
                    sender.sendMessage(new TextComponentString("Exported " + output
                            + ". Stop the server, then back up the ENTIRE world before running the offline scanner."));
                    return;
                }
                case "import": {
                    if (args.length != 2 || !args[1].matches("[A-Za-z0-9_-]{1,80}\\.json"))
                        throw new WrongUsageException(getUsage(sender));
                    Path folder = exchangeFolder(overworld);
                    Path input = folder.resolve(args[1]).toRealPath();
                    if (!input.getParent().equals(folder) || Files.size(input) > 8 * 1024 * 1024L)
                        throw new IOException("Unsafe or oversized import file");
                    DimensionManifest manifest = DimensionManifest.capture(overworld, data);
                    DirectoryImport imported;
                    try (Reader reader = new InputStreamReader(new BoundedInputStream(Files.newInputStream(input), 8 * 1024 * 1024),
                            StandardCharsets.UTF_8)) {
                        imported = DirectoryImport.read(reader, data.getWorldId(), data.getManifestDigest(), manifest);
                    }
                    data.importHints(imported.hints, imported.complete);
                    sender.sendMessage(new TextComponentString("Imported " + imported.hints.size()
                            + " hints; backup coverage=" + imported.complete + ". Live targets still require verification."));
                    return;
                }
                case "reindex": {
                    if (args.length != 1) throw new WrongUsageException(getUsage(sender));
                    // Known addresses only. Unknown loaded tiles self-register; no whole-world or disk scan.
                    for (MachineDirectoryEntry entry : data.snapshot()) data.enqueue(entry.address);
                    sender.sendMessage(new TextComponentString("Queued bounded loaded-target reconciliation; no chunks will be loaded."));
                    return;
                }
                case "renew": {
                    if (args.length != 5) throw new WrongUsageException(getUsage(sender));
                    MachineAddress address = new MachineAddress(parseInt(args[1]), parseInt(args[2]), parseInt(args[3]), parseInt(args[4]));
                    WorldServer target = DimensionManager.getWorld(address.dimension);
                    Chunk chunk = target == null ? null : target.getChunkProvider().getLoadedChunk(address.x >> 4, address.z >> 4);
                    TileEntity tile = chunk == null ? null : chunk.getTileEntity(address.pos(), Chunk.EnumCreateEntityType.CHECK);
                    if (!(tile instanceof TileVendingMachine) || tile.isInvalid()) throw new IOException("Load the real target first");
                    TileVendingMachine machine = (TileVendingMachine) tile;
                    if (!machine.isDataVersionSupported()) throw new IOException("Unsupported tile data must stay untouched");
                    machine.renewMachineIdentity(); MachineDirectory.observe(machine);
                    sender.sendMessage(new TextComponentString("Renewed machine identity at " + address + "; ownership and inventory unchanged."));
                    return;
                }
                default: throw new WrongUsageException(getUsage(sender));
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
            throw new CommandException("TTN directory: " + ex.getMessage());
        }
    }
    private void special(ICommandSender sender, String[] args, MachineDirectoryData data) throws CommandException, IOException {
        if (args.length == 6 && args[1].equals("add")) {
            MachineAddress address = new MachineAddress(parseInt(args[2]), parseInt(args[3]), parseInt(args[4]), parseInt(args[5]));
            WorldServer target = DimensionManager.getWorld(address.dimension);
            Chunk chunk = target == null ? null : target.getChunkProvider().getLoadedChunk(address.x >> 4, address.z >> 4);
            TileEntity tile = chunk == null ? null : chunk.getTileEntity(address.pos(), Chunk.EnumCreateEntityType.CHECK);
            if (!(tile instanceof TileVendingMachine) || tile.isInvalid()) throw new IOException("Load the real target first; this command does not load chunks");
            TileVendingMachine machine = (TileVendingMachine) tile;
            if (!machine.isDataVersionSupported()) throw new IOException("Unsupported tile data must stay untouched");
            if (!MachineDirectory.observe(machine)) throw new IOException("Target changed during observation; retry");
            MachineDirectoryEntry entry = data.get(address);
            if (entry == null || !java.util.Objects.equals(entry.machineId, machine.getMachineUuid()))
                throw new IOException("Target could not be registered");
            boolean changed = data.addSpecial(address, entry.machineId);
            sender.sendMessage(new TextComponentString("Currency & Tax: " + entry.machineId + (changed ? " added." : " already listed.")
                    + " Ownership, creative mode and trade rules unchanged. Refresh the directory to see changes."));
        } else if (args.length == 3 && args[1].equals("remove")) {
            UUID id = UUID.fromString(args[2]);
            if (!id.toString().equalsIgnoreCase(args[2])) throw new IllegalArgumentException("Use a full canonical machine UUID");
            sender.sendMessage(new TextComponentString("Currency & Tax: " + id
                    + (data.removeSpecial(id) ? " removed. Refresh the directory to see changes." : " is not listed.")));
        } else if ((args.length == 2 || args.length == 3) && args[1].equals("list")) {
            List<UUID> ids = data.specialMachines();
            int pages = Math.max(1, (ids.size() + 9) / 10);
            int page = args.length == 3 ? parseInt(args[2], 1, pages) : 1;
            sender.sendMessage(new TextComponentString("Currency & Tax: " + ids.size() + "/" + MachineDirectoryData.MAX_SPECIAL
                    + "; page " + page + "/" + pages + ". Remove with /ttndirectory special remove <uuid>"));
            for (int i = (page - 1) * 10; i < Math.min(ids.size(), page * 10); i++)
                sender.sendMessage(new TextComponentString(ids.get(i).toString()));
        } else throw new WrongUsageException(getUsage(sender));
    }
    @Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "status", "export", "import", "reindex", "renew", "special");
        if (args.length == 2 && args[0].equals("special")) return getListOfStringsMatchingLastWord(args, "add", "remove", "list");
        return java.util.Collections.emptyList();
    }
    private static Path exchangeFolder(WorldServer overworld) throws IOException {
        Path root = overworld.getSaveHandler().getWorldDirectory().getCanonicalFile().toPath();
        Path folder = root.resolve("ttn-directory").toFile().getCanonicalFile().toPath();
        if (!folder.getParent().equals(root)) throw new IOException("Exchange folder escapes world");
        return folder;
    }
    private static final class BoundedInputStream extends FilterInputStream {
        private int remaining;
        BoundedInputStream(InputStream stream, int limit) { super(stream); remaining = limit; }
        @Override public int read() throws IOException {
            int value = super.read();
            if (value != -1 && --remaining < 0) throw new IOException("Import byte limit exceeded");
            return value;
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            int count = in.read(bytes, offset, Math.min(length, remaining + 1));
            if (count > 0 && (remaining -= count) < 0) throw new IOException("Import byte limit exceeded");
            return count;
        }
    }
}
