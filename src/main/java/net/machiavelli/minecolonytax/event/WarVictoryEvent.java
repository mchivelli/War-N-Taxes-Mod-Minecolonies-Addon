package net.machiavelli.minecolonytax.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

public class WarVictoryEvent extends Event {
    private final ServerPlayer winner;
    public WarVictoryEvent(ServerPlayer winner) {
        this.winner = winner;
    }
    public ServerPlayer getWinner() {
        return winner;
    }
}