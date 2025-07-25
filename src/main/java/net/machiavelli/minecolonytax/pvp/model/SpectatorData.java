package net.machiavelli.minecolonytax.pvp.model;

import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.GameType;

public record SpectatorData(GlobalPos originalPos, GameType originalGameMode) {
} 