package net.machiavelli.minecolonytax.pvp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.machiavelli.minecolonytax.pvp.model.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import javax.annotation.Nullable;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class PvPManager {

    public static final PvPManager INSTANCE = new PvPManager();

    // Map and Arena Data
    public final Map<String, PvPMap> arenaMapsByName = new HashMap<>();
    public String defaultMapName = null;
    public final Set<String> lockedMaps = new HashSet<>();

    // Battle State
    public final Map<String, ActiveBattle> activeBattles = new HashMap<>();
    public final Map<String, TeamBattle> pendingTeamBattles = new HashMap<>();
    public final Map<UUID, BattleRequest> pendingRequests = new HashMap<>();

    // Player State
    @Deprecated // No longer used - inventory save/restore removed to fix duplication glitch
    public final Map<UUID, ItemStack[]> playerInventories = new HashMap<>();
    @Deprecated // No longer used - inventory save/restore removed to fix duplication glitch
    public final Map<UUID, ItemStack[]> playerArmor = new HashMap<>();
    public final Map<UUID, SpectatorData> spectatorData = new HashMap<>();
    public final Map<UUID, GameType> playerOriginalGameModes = new HashMap<>();
    public final Map<String, List<UUID>> activeSpectators = new HashMap<>();
    public final Map<UUID, PlayerPvPStats> playerStats = new HashMap<>();

    // Battle Timers and Cooldowns
    public final Map<String, Integer> battleTimers = new ConcurrentHashMap<>();
    public final Map<String, Map<UUID, Float>> battleDamage = new ConcurrentHashMap<>();
    public final Map<String, Integer> lastNotificationTime = new ConcurrentHashMap<>();
    public final Map<UUID, Long> challengeCooldown = new HashMap<>();
    public final Map<UUID, Long> teamBattleCooldown = new HashMap<>();
    public final Map<String, Integer> teamBattleCountdownNotifiers = new ConcurrentHashMap<>();
    public final Map<UUID, Long> lastFriendlyFireNotifications = new HashMap<>();
    
    // Defeated player tracking (UUID -> battle ID)
    public final Map<UUID, String> defeatedPlayers = new ConcurrentHashMap<>();

    // Constants
    public static final ScheduledExecutorService BATTLE_END_SCHEDULER = Executors.newScheduledThreadPool(1);
    public static final File ARENA_DATA_FILE = new File("config/warntax/pvp_arena_data.json");
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PvPManager() {
        // Private constructor for singleton
    }

    @Nullable
    public ActiveBattle getActiveBattle(ServerPlayer player) {
        return activeBattles.values().stream()
                .filter(battle -> battle.getAllPlayers().contains(player.getUUID()))
                .findFirst()
                .orElse(null);
    }

    public boolean isPlayerBusy(UUID playerId) {
        // Check if player is in an active battle
        for (ActiveBattle battle : activeBattles.values()) {
            if (battle.getAllPlayers().contains(playerId)) {
                return true;
            }
        }
        // Check if player has a pending duel request
        if (pendingRequests.containsKey(playerId) || pendingRequests.values().stream().anyMatch(req -> req.getTargetPlayers().contains(playerId))) {
            return true;
        }
        return false;
    }
} 