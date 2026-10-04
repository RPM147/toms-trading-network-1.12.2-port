package com.tom.trading.remote;

import com.tom.trading.directory.MachineAddress;
import java.util.Objects;
import java.util.UUID;

/** A selected metadata row, not a permission token. A null machine ID requests legacy resolution only. */
public final class RemoteOpenRequest {
    public final UUID screenId, worldId, machineId;
    public final long requestId, revision;
    public final int playerDimension;
    public final MachineAddress address;
    public RemoteOpenRequest(UUID screenId, long requestId, UUID worldId, long revision, int playerDimension,
                             MachineAddress address, UUID machineId) {
        this.screenId = Objects.requireNonNull(screenId); this.worldId = Objects.requireNonNull(worldId);
        this.address = Objects.requireNonNull(address); this.machineId = machineId;
        if (requestId <= 0 || revision <= 0) throw new IllegalArgumentException("Invalid open request");
        this.requestId = requestId; this.revision = revision; this.playerDimension = playerDimension;
    }
    public boolean matches(RemoteOpenRequest other) {
        return other != null && screenId.equals(other.screenId) && requestId == other.requestId
                && worldId.equals(other.worldId) && revision == other.revision && playerDimension == other.playerDimension
                && address.equals(other.address) && Objects.equals(machineId, other.machineId);
    }
}
