package net.machiavelli.minecolonytax.pvp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.pvp.model.PlayerPvPStats;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Atomic JSON persistence for PvPManager player stats, original game-modes,
 * and original world positions. Splits stats off from arena-map data so the
 * existing pvp_arena_data.json file stays unchanged.
 *
 * <p>Persisted state survives server crashes and disconnect-during-defeat
 * windows so players stranded in SPECTATOR can be restored on next login.
 *
 * <p>NeoForge 1.21.1 port of the Forge 1.20.1 original. Self-registers on the
 * GAME event bus for {@link PlayerEvent.PlayerLoggedInEvent} so the stranded
 * player restore path requires no edits to PvPEventHandler.
 */
@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
public final class PvPStatsPersistence {

    private static final Logger LOGGER = LogManager.getLogger(PvPStatsPersistence.class);

    private PvPStatsPersistence() {}

    public static synchronized void save() {
        PvPManager mgr = PvPManager.INSTANCE;
        try {
            if (PvPManager.PVP_STATS_FILE.getParentFile() != null
                    && !PvPManager.PVP_STATS_FILE.getParentFile().exists()) {
                PvPManager.PVP_STATS_FILE.getParentFile().mkdirs();
            }

            JsonObject root = new JsonObject();

            // Player stats
            JsonObject statsObj = new JsonObject();
            for (Map.Entry<UUID, PlayerPvPStats> e : mgr.playerStats.entrySet()) {
                PlayerPvPStats s = e.getValue();
                if (s == null) continue;
                JsonObject o = new JsonObject();
                try {
                    o.addProperty("duelsWon", getIntField(s, "duelsWon"));
                    o.addProperty("duelsLost", getIntField(s, "duelsLost"));
                    o.addProperty("teamBattlesWon", getIntField(s, "teamBattlesWon"));
                    o.addProperty("teamBattlesLost", getIntField(s, "teamBattlesLost"));
                    o.addProperty("arenaKills", s.getArenaKills());
                    o.addProperty("arenaDeaths", s.getArenaDeaths());
                } catch (Exception ex) {
                    LOGGER.warn("Failed to serialize PvP stats for {}: {}", e.getKey(), ex.toString());
                    continue;
                }
                statsObj.add(e.getKey().toString(), o);
            }
            root.add("playerStats", statsObj);

            // Original game-modes
            JsonObject gmObj = new JsonObject();
            for (Map.Entry<UUID, GameType> e : mgr.playerOriginalGameModes.entrySet()) {
                if (e.getValue() == null) continue;
                gmObj.addProperty(e.getKey().toString(), e.getValue().getName());
            }
            root.add("originalGameModes", gmObj);

            // Original positions
            JsonObject posObj = new JsonObject();
            for (Map.Entry<UUID, GlobalPos> e : mgr.playerOriginalPositions.entrySet()) {
                GlobalPos gp = e.getValue();
                if (gp == null) continue;
                JsonObject p = new JsonObject();
                p.addProperty("dimension", gp.dimension().location().toString());
                p.addProperty("x", gp.pos().getX());
                p.addProperty("y", gp.pos().getY());
                p.addProperty("z", gp.pos().getZ());
                posObj.add(e.getKey().toString(), p);
            }
            root.add("originalPositions", posObj);

            // Atomic write: temp file + Files.move with ATOMIC_MOVE
            Path target = PvPManager.PVP_STATS_FILE.toPath();
            Path tmp = target.resolveSibling(PvPManager.PVP_STATS_FILE.getName() + ".tmp");
            Files.writeString(tmp, PvPManager.GSON.toJson(root));
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicFail) {
                // Some Windows configurations refuse ATOMIC_MOVE across handles — fall back to plain move
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ioe) {
            LOGGER.error("Failed to save PvP stats", ioe);
        } catch (Throwable t) {
            LOGGER.error("Unexpected error saving PvP stats", t);
        }
    }

    public static synchronized void load() {
        PvPManager mgr = PvPManager.INSTANCE;
        if (!PvPManager.PVP_STATS_FILE.exists()) {
            if (TaxConfig.isNormalLogging()) {
                LOGGER.info("No PvP stats file found. Starting fresh.");
            }
            return;
        }
        try (Reader r = Files.newBufferedReader(PvPManager.PVP_STATS_FILE.toPath())) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            if (root == null) return;

            if (root.has("playerStats")) {
                JsonObject statsObj = root.getAsJsonObject("playerStats");
                for (String key : statsObj.keySet()) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        JsonObject o = statsObj.getAsJsonObject(key);
                        PlayerPvPStats s = new PlayerPvPStats();
                        setIntField(s, "duelsWon", o.has("duelsWon") ? o.get("duelsWon").getAsInt() : 0);
                        setIntField(s, "duelsLost", o.has("duelsLost") ? o.get("duelsLost").getAsInt() : 0);
                        setIntField(s, "teamBattlesWon", o.has("teamBattlesWon") ? o.get("teamBattlesWon").getAsInt() : 0);
                        setIntField(s, "teamBattlesLost", o.has("teamBattlesLost") ? o.get("teamBattlesLost").getAsInt() : 0);
                        setIntField(s, "arenaKills", o.has("arenaKills") ? o.get("arenaKills").getAsInt() : 0);
                        setIntField(s, "arenaDeaths", o.has("arenaDeaths") ? o.get("arenaDeaths").getAsInt() : 0);
                        mgr.playerStats.put(uuid, s);
                    } catch (Exception perEntry) {
                        LOGGER.warn("Skipping malformed PvP stats entry '{}': {}", key, perEntry.toString());
                    }
                }
            }

            if (root.has("originalGameModes")) {
                JsonObject gmObj = root.getAsJsonObject("originalGameModes");
                for (String key : gmObj.keySet()) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        String name = gmObj.get(key).getAsString();
                        GameType gt = GameType.byName(name, GameType.SURVIVAL);
                        mgr.playerOriginalGameModes.put(uuid, gt);
                    } catch (Exception perEntry) {
                        LOGGER.warn("Skipping malformed gamemode entry '{}': {}", key, perEntry.toString());
                    }
                }
            }

            if (root.has("originalPositions")) {
                JsonObject posObj = root.getAsJsonObject("originalPositions");
                for (String key : posObj.keySet()) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        JsonObject p = posObj.getAsJsonObject(key);
                        ResourceLocation dimLoc = ResourceLocation.parse(p.get("dimension").getAsString());
                        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimLoc);
                        BlockPos bp = new BlockPos(p.get("x").getAsInt(), p.get("y").getAsInt(), p.get("z").getAsInt());
                        mgr.playerOriginalPositions.put(uuid, GlobalPos.of(dim, bp));
                    } catch (Exception perEntry) {
                        LOGGER.warn("Skipping malformed original-position entry '{}': {}", key, perEntry.toString());
                    }
                }
            }

            if (TaxConfig.isNormalLogging()) {
                LOGGER.info("Loaded PvP stats: {} player records, {} stashed gamemodes, {} stashed positions",
                        mgr.playerStats.size(), mgr.playerOriginalGameModes.size(), mgr.playerOriginalPositions.size());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load PvP stats", e);
        }
    }

    /**
     * Stranded-player recovery on login. If a player has a persisted original
     * game-mode (and is NOT currently in an active battle), restore their
     * game-mode and original world position — covers the "stuck in SPECTATOR
     * after a crash / disconnect-during-defeat" case.
     *
     * <p>Self-registered on the GAME bus, so no PvPEventHandler edit is needed.
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PvPManager mgr = PvPManager.INSTANCE;
        try {
            UUID uuid = player.getUUID();
            if (mgr.getActiveBattle(player) == null
                    && mgr.playerOriginalGameModes.containsKey(uuid)) {
                GameType originalGm = mgr.playerOriginalGameModes.remove(uuid);
                GlobalPos origPos = mgr.playerOriginalPositions.remove(uuid);
                if (originalGm != null) {
                    player.setGameMode(originalGm);
                }
                if (origPos != null && player.getServer() != null) {
                    ServerLevel lvl = player.getServer().getLevel(origPos.dimension());
                    if (lvl != null) {
                        BlockPos bp = origPos.pos();
                        player.teleportTo(lvl, bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5,
                                player.getYRot(), player.getXRot());
                    }
                }
                player.sendSystemMessage(Component.literal(
                                "Restored your original state after a previous PvP battle interruption.")
                        .withStyle(ChatFormatting.GREEN));
                save();
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to restore stranded PvP player on login: {}", e.toString());
        }
    }

    private static int getIntField(Object obj, String field) throws Exception {
        Field f = obj.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.getInt(obj);
    }

    private static void setIntField(Object obj, String field, int value) throws Exception {
        Field f = obj.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.setInt(obj, value);
    }
}
