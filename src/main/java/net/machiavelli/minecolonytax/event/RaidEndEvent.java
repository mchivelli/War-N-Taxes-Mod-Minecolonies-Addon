package net.machiavelli.minecolonytax.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

public class RaidEndEvent extends Event {
    private final ServerPlayer raider;
    public RaidEndEvent(ServerPlayer raider) {
        this.raider = raider;
    }
    public ServerPlayer getRaider() {
        return raider;
    }
}