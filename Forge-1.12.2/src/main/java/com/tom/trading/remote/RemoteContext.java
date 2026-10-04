package com.tom.trading.remote;

import com.tom.trading.directory.MachineAddress;
import java.util.Objects;
import java.util.UUID;

/** Both dimensions are explicit; the window/session target is never chosen by the client. */
public final class RemoteContext {
    public final int window, playerDimension;
    public final UUID sessionId, worldId, machineId;
    public final MachineAddress address;
    public RemoteContext(int window, int playerDimension, UUID sessionId, UUID worldId, UUID machineId, MachineAddress address) {
        if (window < 1 || window > 100) throw new IllegalArgumentException("Invalid window");
        this.window = window; this.playerDimension = playerDimension;
        this.sessionId = Objects.requireNonNull(sessionId); this.worldId = Objects.requireNonNull(worldId);
        this.machineId = Objects.requireNonNull(machineId); this.address = Objects.requireNonNull(address);
    }
    public boolean matches(RemoteContext other) {
        return other != null && window == other.window && playerDimension == other.playerDimension
                && sessionId.equals(other.sessionId) && worldId.equals(other.worldId)
                && machineId.equals(other.machineId) && address.equals(other.address);
    }
}
