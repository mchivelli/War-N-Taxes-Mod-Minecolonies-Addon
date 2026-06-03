package net.machiavelli.minecolonytax.db;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.economy.WarExhaustionManager;
import net.machiavelli.minecolonytax.occupation.OccupationManager;
import net.minecraft.server.MinecraftServer;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Database layer for war statistics.
 *
 * All public write methods are fire-and-forget — they submit work to a single
 * background thread and return immediately so the game thread is never blocked.
 *
 * Targets MySQL/MariaDB. Server admins copy the four credentials from the
 * Pterodactyl panel -> server -> Databases tab into minecolonytax.toml.
 * The website container connects to the same database with the same credentials.
 */
public class WarStatsDB {

    private static volatile Connection connection;
    private static volatile boolean initialized = false;

    /**
     * Bounded write queue. Capacity 1000 covers any realistic burst.
     * If the DB is offline long enough to fill the queue, new tasks are
     * dropped rather than growing the heap indefinitely.
     * Critical event writes (war end, kill, login) are small and fast so
     * they will almost never be dropped — only periodic snapshots will
     * accumulate during an outage.
     */
    private static final LinkedBlockingQueue<Runnable> WRITE_QUEUE = new LinkedBlockingQueue<>(1_000);
    private static final ExecutorService WRITER = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, WRITE_QUEUE,
            r -> { Thread t = new Thread(r, "WarStatsDB-Writer"); t.setDaemon(true); return t; },
            (task, executor) -> MineColonyTax.LOGGER.warn(
                    "WarStatsDB: write queue full ({}), dropping low-priority task", WRITE_QUEUE.size()));

    /** ms/tick above which we skip the colony snapshot to protect server TPS (~12 TPS threshold). */
    private static final float SNAPSHOT_TPS_SKIP_MS = 80.0f;

    /** Queue depth above which we skip the snapshot — DB is lagging, backpressure kicking in. */
    private static final int SNAPSHOT_QUEUE_SKIP_DEPTH = 200;

    // ==================== Lifecycle ====================

    public static void initialize() {
        if (!TaxConfig.isDatabaseEnabled()) {
            if (TaxConfig.isNormalLogging())
                MineColonyTax.LOGGER.info("WarStatsDB: disabled in config, skipping");
            return;
        }
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            MineColonyTax.LOGGER.error("WarStatsDB: MySQL JDBC driver not found — mysql-connector-j may not have been bundled correctly");
            return;
        }
        try {
            openConnection();
            createSchema();
            initialized = true;
            MineColonyTax.LOGGER.info("WarStatsDB connected: {}:{}/{}",
                    TaxConfig.getDatabaseHost(), TaxConfig.getDatabasePort(), TaxConfig.getDatabaseName());
        } catch (Exception e) {
            MineColonyTax.LOGGER.error("WarStatsDB: initialization failed — {}", e.getMessage());
            MineColonyTax.LOGGER.error("  Check Host/Port/Database/Username/Password in [Database] section of minecolonytax.toml");
        }
    }

    public static void shutdown() {
        initialized = false;
        WRITER.shutdown();
        try {
            if (!WRITER.awaitTermination(10, TimeUnit.SECONDS))
                WRITER.shutdownNow();
        } catch (InterruptedException ignored) {
            WRITER.shutdownNow();
        }
        closeQuietly(connection);
        MineColonyTax.LOGGER.info("WarStatsDB shut down");
    }

    public static boolean isInitialized() {
        return initialized;
    }

    // ==================== Connection ====================

    private static void openConnection() throws SQLException {
        String url = String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true"
                        + "&serverTimezone=UTC&autoReconnect=false&connectTimeout=5000&socketTimeout=30000",
                TaxConfig.getDatabaseHost(),
                TaxConfig.getDatabasePort(),
                TaxConfig.getDatabaseName());
        connection = DriverManager.getConnection(url, TaxConfig.getDatabaseUsername(), TaxConfig.getDatabasePassword());
        connection.setAutoCommit(true);
    }

    /**
     * Called on the writer thread. Returns the current connection,
     * reconnecting if it has been closed.
     *
     * NOTE: We deliberately do NOT call connection.isValid() here — that
     * sends a ping to the DB on every task and adds unnecessary latency.
     * If the connection is stale, the query itself will throw a
     * SQLException and the submit() wrapper will log the error.
     * A full reconnect-on-failure strategy can be layered on top later
     * if needed; for now we only reconnect on an explicit isClosed() state.
     */
    private static Connection conn() throws SQLException {
        if (connection == null || connection.isClosed()) {
            MineColonyTax.LOGGER.warn("WarStatsDB: connection closed, reconnecting...");
            closeQuietly(connection);
            openConnection();
        }
        return connection;
    }

    private static void closeQuietly(Connection c) {
        try {
            if (c != null && !c.isClosed()) c.close();
        } catch (SQLException ignored) {}
    }

    // ==================== Schema ====================

    private static void createSchema() throws SQLException {
        try (Statement s = connection.createStatement()) {

            s.execute("""
                    CREATE TABLE IF NOT EXISTS player_stats (
                        uuid                 VARCHAR(36)  PRIMARY KEY,
                        display_name         VARCHAR(64)  NOT NULL,
                        wars_won             INT          NOT NULL DEFAULT 0,
                        wars_lost            INT          NOT NULL DEFAULT 0,
                        war_stalemates       INT          NOT NULL DEFAULT 0,
                        times_declared_war   INT          NOT NULL DEFAULT 0,
                        times_targeted       INT          NOT NULL DEFAULT 0,
                        players_killed       INT          NOT NULL DEFAULT 0,
                        times_killed         INT          NOT NULL DEFAULT 0,
                        colonies_raided      INT          NOT NULL DEFAULT 0,
                        amount_raided        BIGINT       NOT NULL DEFAULT 0,
                        first_seen           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        last_seen            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            s.execute("""
                    CREATE TABLE IF NOT EXISTS war_history (
                        war_id                   VARCHAR(36)  PRIMARY KEY,
                        attacker_uuid            VARCHAR(36)  NOT NULL,
                        defender_uuid            VARCHAR(36)  NOT NULL,
                        attacker_colony_id       INT          NOT NULL,
                        defender_colony_id       INT          NOT NULL,
                        outcome                  VARCHAR(16)  NOT NULL,
                        started_at               DATETIME     NOT NULL,
                        ended_at                 DATETIME     NOT NULL,
                        duration_seconds         INT          NOT NULL,
                        attacker_initial_guards  INT          NOT NULL DEFAULT 0,
                        defender_initial_guards  INT          NOT NULL DEFAULT 0,
                        attacker_guards_lost     INT          NOT NULL DEFAULT 0,
                        defender_guards_lost     INT          NOT NULL DEFAULT 0,
                        reparations_paid         BIGINT       NOT NULL DEFAULT 0,
                        led_to_occupation        TINYINT(1)   NOT NULL DEFAULT 0
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            s.execute("""
                    CREATE TABLE IF NOT EXISTS colony_state (
                        colony_id                INT          PRIMARY KEY,
                        colony_name              VARCHAR(128) NOT NULL,
                        owner_uuid               VARCHAR(36)  NOT NULL,
                        citizen_count            INT          NOT NULL DEFAULT 0,
                        guard_count              INT          NOT NULL DEFAULT 0,
                        tax_balance              INT          NOT NULL DEFAULT 0,
                        war_status               VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',
                        is_occupied              TINYINT(1)   NOT NULL DEFAULT 0,
                        occupied_by_uuid         VARCHAR(36)  NULL,
                        immunity_expires_at      DATETIME     NULL,
                        reparations_expires_at   DATETIME     NULL,
                        recovery_expires_at      DATETIME     NULL,
                        last_updated             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            s.execute("""
                    CREATE TABLE IF NOT EXISTS raid_log (
                        id               BIGINT       PRIMARY KEY AUTO_INCREMENT,
                        raider_uuid      VARCHAR(36)  NOT NULL,
                        target_colony_id INT          NOT NULL,
                        amount_taken     BIGINT       NOT NULL DEFAULT 0,
                        raided_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            s.execute("""
                    CREATE TABLE IF NOT EXISTS server_state (
                        `key`   VARCHAR(64)   PRIMARY KEY,
                        value   VARCHAR(256)  NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            // Indexes — IF NOT EXISTS supported in MySQL 8.0.12+ and MariaDB 10.1.4+
            tryExec(s, "CREATE INDEX IF NOT EXISTS idx_ps_wars_won      ON player_stats (wars_won DESC)");
            tryExec(s, "CREATE INDEX IF NOT EXISTS idx_ps_killed        ON player_stats (players_killed DESC)");
            tryExec(s, "CREATE INDEX IF NOT EXISTS idx_ps_amount_raided ON player_stats (amount_raided DESC)");
            tryExec(s, "CREATE INDEX IF NOT EXISTS idx_ps_times_killed  ON player_stats (times_killed DESC)");
            tryExec(s, "CREATE INDEX IF NOT EXISTS idx_wh_attacker      ON war_history  (attacker_uuid, ended_at DESC)");
            tryExec(s, "CREATE INDEX IF NOT EXISTS idx_wh_defender      ON war_history  (defender_uuid, ended_at DESC)");
            tryExec(s, "CREATE INDEX IF NOT EXISTS idx_wh_ended_at      ON war_history  (ended_at DESC)");
            tryExec(s, "CREATE INDEX IF NOT EXISTS idx_rl_raider        ON raid_log     (raider_uuid, raided_at DESC)");
            tryExec(s, "CREATE INDEX IF NOT EXISTS idx_rl_target        ON raid_log     (target_colony_id, raided_at DESC)");
            tryExec(s, "CREATE INDEX IF NOT EXISTS idx_cs_war_status    ON colony_state (war_status)");
            tryExec(s, "CREATE INDEX IF NOT EXISTS idx_cs_owner         ON colony_state (owner_uuid)");
        }
    }

    private static void tryExec(Statement s, String sql) {
        try {
            s.execute(sql);
        } catch (SQLException ignored) {
            // index already exists or not supported — harmless
        }
    }

    // ==================== Public Write API ====================

    /** Upsert player name and last_seen. Call on every login. */
    public static void upsertPlayerLogin(UUID uuid, String displayName) {
        submit(() -> {
            try (PreparedStatement ps = conn().prepareStatement("""
                    INSERT INTO player_stats (uuid, display_name, first_seen, last_seen)
                    VALUES (?, ?, NOW(), NOW())
                    ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), last_seen = NOW()
                    """)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, displayName);
                ps.executeUpdate();
            }
        }, "upsertPlayerLogin");
    }

    /**
     * Record a player kill during war or raid.
     * Increments players_killed for the killer and times_killed for the victim.
     */
    public static void recordWarKill(UUID killerUUID, String killerName,
                                     UUID victimUUID, String victimName) {
        submit(() -> {
            Connection c = conn();
            ensurePlayer(c, killerUUID, killerName);
            ensurePlayer(c, victimUUID, victimName);
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE player_stats SET players_killed = players_killed + 1 WHERE uuid = ?")) {
                ps.setString(1, killerUUID.toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE player_stats SET times_killed = times_killed + 1 WHERE uuid = ?")) {
                ps.setString(1, victimUUID.toString());
                ps.executeUpdate();
            }
        }, "recordWarKill");
    }

    /**
     * Record a completed colony raid and update raider stats.
     * Call after PlayerWarDataManager.incrementRaidedColonies() has been called.
     */
    public static void recordRaid(UUID raiderUUID, String raiderName,
                                  int targetColonyId, long amountTaken) {
        submit(() -> {
            Connection c = conn();
            ensurePlayer(c, raiderUUID, raiderName);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO raid_log (raider_uuid, target_colony_id, amount_taken, raided_at) VALUES (?, ?, ?, NOW())")) {
                ps.setString(1, raiderUUID.toString());
                ps.setInt(2, targetColonyId);
                ps.setLong(3, amountTaken);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE player_stats
                    SET colonies_raided = colonies_raided + 1,
                        amount_raided   = amount_raided   + ?
                    WHERE uuid = ?
                    """)) {
                ps.setLong(1, amountTaken);
                ps.setString(2, raiderUUID.toString());
                ps.executeUpdate();
            }
        }, "recordRaid");
    }

    /**
     * Record a war declaration.
     * Attacker is always online; defender may be offline (write by UUID only).
     */
    public static void recordWarDeclared(UUID attackerUUID, String attackerName,
                                         UUID defenderUUID) {
        submit(() -> {
            Connection c = conn();
            ensurePlayer(c, attackerUUID, attackerName);
            ensurePlayerByUUID(c, defenderUUID);
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE player_stats SET times_declared_war = times_declared_war + 1 WHERE uuid = ?")) {
                ps.setString(1, attackerUUID.toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE player_stats SET times_targeted = times_targeted + 1 WHERE uuid = ?")) {
                ps.setString(1, defenderUUID.toString());
                ps.executeUpdate();
            }
        }, "recordWarDeclared");
    }

    /**
     * Record a war ending. Writes one row to war_history and updates
     * wars_won / wars_lost / war_stalemates for all online participants.
     *
     * @param warData        The WarData captured at endWar() time (still valid after remove).
     * @param outcome        "ATTACKER_WIN" | "DEFENDER_WIN" | "STALEMATE"
     * @param reparationsPaid Amount transferred as part of the outcome.
     * @param ledToOccupation Whether this war immediately led to an occupation.
     */
    public static void recordWarEnd(WarData warData, String outcome,
                                    long reparationsPaid, boolean ledToOccupation) {
        // Capture all values on the game thread before handing off to the writer
        final UUID   warId           = warData.getWarID();
        final UUID   attackerUUID    = warData.getAttacker();
        final UUID   defenderUUID    = warData.getDefender();
        final int    attackerColony  = warData.getAttackerColony() != null
                                       ? warData.getAttackerColony().getID() : -1;
        final int    defenderColony  = warData.getColony().getID();
        final long   startedAt       = warData.warStartTime;
        final long   endedAt         = System.currentTimeMillis();
        final int    durationSec     = (int) ((endedAt - startedAt) / 1000L);
        final int    atkInit         = warData.initialAttackerGuards;
        final int    defInit         = warData.initialDefenderGuards;
        final int    atkLost         = Math.max(0, warData.initialAttackerGuards - warData.remainingAttackerGuards);
        final int    defLost         = Math.max(0, warData.initialDefenderGuards - warData.remainingDefenderGuards);
        final Set<UUID> attackers    = Set.copyOf(warData.getAttackerLives().keySet());
        final Set<UUID> defenders    = Set.copyOf(warData.getDefenderLives().keySet());

        submit(() -> {
            Connection c = conn();

            // 1. Insert war record (IGNORE handles duplicate calls gracefully)
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT IGNORE INTO war_history
                      (war_id, attacker_uuid, defender_uuid, attacker_colony_id, defender_colony_id,
                       outcome, started_at, ended_at, duration_seconds,
                       attacker_initial_guards, defender_initial_guards,
                       attacker_guards_lost,   defender_guards_lost,
                       reparations_paid, led_to_occupation)
                    VALUES (?, ?, ?, ?, ?,
                            ?, FROM_UNIXTIME(?/1000), FROM_UNIXTIME(?/1000), ?,
                            ?, ?, ?, ?, ?, ?)
                    """)) {
                ps.setString(1,  warId.toString());
                ps.setString(2,  attackerUUID.toString());
                ps.setString(3,  defenderUUID.toString());
                ps.setInt   (4,  attackerColony);
                ps.setInt   (5,  defenderColony);
                ps.setString(6,  outcome);
                ps.setLong  (7,  startedAt);
                ps.setLong  (8,  endedAt);
                ps.setInt   (9,  durationSec);
                ps.setInt   (10, atkInit);
                ps.setInt   (11, defInit);
                ps.setInt   (12, atkLost);
                ps.setInt   (13, defLost);
                ps.setLong  (14, reparationsPaid);
                ps.setInt   (15, ledToOccupation ? 1 : 0);
                ps.executeUpdate();
            }

            // 2. Update participant win/loss/stalemate counters
            switch (outcome) {
                case "ATTACKER_WIN" -> {
                    batchIncrement(c, attackers, "wars_won");
                    batchIncrement(c, defenders, "wars_lost");
                }
                case "DEFENDER_WIN" -> {
                    batchIncrement(c, defenders, "wars_won");
                    batchIncrement(c, attackers, "wars_lost");
                }
                default -> {
                    Set<UUID> all = new HashSet<>();
                    all.addAll(attackers);
                    all.addAll(defenders);
                    batchIncrement(c, all, "war_stalemates");
                }
            }
        }, "recordWarEnd");
    }

    /**
     * Snapshot all known colonies into colony_state.
     * Called periodically from the TickScheduler task set up in MineColonyTax.
     */
    public static void snapshotAllColonies(MinecraftServer server) {
        if (!initialized) return;

        // Skip if server is struggling — collecting colony data on the game thread
        // while it is already overloaded makes things worse.
        float tickMs = server.getAverageTickTime();
        if (tickMs > SNAPSHOT_TPS_SKIP_MS) {
            if (TaxConfig.isNormalLogging())
                MineColonyTax.LOGGER.warn("WarStatsDB: skipping colony snapshot — server tick time {}ms (>{}ms threshold)",
                        (int) tickMs, (int) SNAPSHOT_TPS_SKIP_MS);
            return;
        }

        // Skip if the DB writer thread is backed up — no point collecting more data
        // that will just sit in the queue. Critical event writes (war end etc.) have
        // far smaller payloads and will clear quickly.
        int queueDepth = WRITE_QUEUE.size();
        if (queueDepth > SNAPSHOT_QUEUE_SKIP_DEPTH) {
            if (TaxConfig.isDebugLogging())
                MineColonyTax.LOGGER.debug("WarStatsDB: skipping colony snapshot — write queue backed up ({} tasks)", queueDepth);
            return;
        }

        // Collect colony data on the game thread before handing to the writer
        List<ColonySnapshot> snapshots = new ArrayList<>();
        try {
            for (net.minecraft.world.level.Level level : server.getAllLevels()) {
                for (IColony colony : IColonyManager.getInstance().getColonies(level)) {
                    if (colony == null) continue;
                    try {
                        UUID ownerUUID = colony.getPermissions().getOwner();
                        int citizens  = colony.getCitizenManager().getCitizens().size();
                        int guards    = (int) colony.getCitizenManager().getCitizens().stream()
                                .filter(c -> c.getJob() != null && c.getJob().isGuard())
                                .count();
                        int taxBal    = TaxManager.getStoredTaxForColony(colony);
                        String status = resolveWarStatus(colony.getID());
                        boolean occupied = OccupationManager.isOccupied(colony.getID());
                        OccupationManager.OccupationData occData = OccupationManager.getOccupation(colony.getID());
                        String occupierStr = occData != null ? occData.occupierUUID : null;
                        long immunityExp    = WarExhaustionManager.hasWarImmunity(colony.getID())
                                ? WarExhaustionManager.getRemainingImmunityHours(colony.getID()) * 3_600_000L
                                  + System.currentTimeMillis() : 0L;
                        long reparationsExp = WarExhaustionManager.hasReparations(colony.getID())
                                ? WarExhaustionManager.getRemainingReparationsHours(colony.getID()) * 3_600_000L
                                  + System.currentTimeMillis() : 0L;
                        long recoveryExp    = WarExhaustionManager.isInRecovery(colony.getID())
                                ? WarExhaustionManager.getRemainingRecoveryHours(colony.getID()) * 3_600_000L
                                  + System.currentTimeMillis() : 0L;

                        snapshots.add(new ColonySnapshot(
                                colony.getID(), colony.getName(),
                                ownerUUID != null ? ownerUUID.toString() : "",
                                citizens, guards, taxBal, status,
                                occupied, occupierStr,
                                immunityExp, reparationsExp, recoveryExp));
                    } catch (Exception e) {
                        MineColonyTax.LOGGER.debug("WarStatsDB: skipped colony {} during snapshot: {}",
                                colony.getID(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            MineColonyTax.LOGGER.error("WarStatsDB: colony snapshot collection failed: {}", e.getMessage());
            return;
        }

        if (snapshots.isEmpty()) return;

        submit(() -> {
            Connection c = conn();
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO colony_state
                      (colony_id, colony_name, owner_uuid, citizen_count, guard_count, tax_balance,
                       war_status, is_occupied, occupied_by_uuid,
                       immunity_expires_at, reparations_expires_at, recovery_expires_at, last_updated)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?,
                            IF(? > 0, FROM_UNIXTIME(?/1000), NULL),
                            IF(? > 0, FROM_UNIXTIME(?/1000), NULL),
                            IF(? > 0, FROM_UNIXTIME(?/1000), NULL),
                            NOW())
                    ON DUPLICATE KEY UPDATE
                      colony_name            = VALUES(colony_name),
                      owner_uuid             = VALUES(owner_uuid),
                      citizen_count          = VALUES(citizen_count),
                      guard_count            = VALUES(guard_count),
                      tax_balance            = VALUES(tax_balance),
                      war_status             = VALUES(war_status),
                      is_occupied            = VALUES(is_occupied),
                      occupied_by_uuid       = VALUES(occupied_by_uuid),
                      immunity_expires_at    = VALUES(immunity_expires_at),
                      reparations_expires_at = VALUES(reparations_expires_at),
                      recovery_expires_at    = VALUES(recovery_expires_at),
                      last_updated           = NOW()
                    """)) {
                for (ColonySnapshot snap : snapshots) {
                    ps.setInt   (1,  snap.colonyId);
                    ps.setString(2,  snap.colonyName);
                    ps.setString(3,  snap.ownerUUID);
                    ps.setInt   (4,  snap.citizenCount);
                    ps.setInt   (5,  snap.guardCount);
                    ps.setInt   (6,  snap.taxBalance);
                    ps.setString(7,  snap.warStatus);
                    ps.setInt   (8,  snap.isOccupied ? 1 : 0);
                    ps.setString(9,  snap.occupiedByUUID);
                    // immunity
                    ps.setLong  (10, snap.immunityExpiresMs);
                    ps.setLong  (11, snap.immunityExpiresMs);
                    // reparations
                    ps.setLong  (12, snap.reparationsExpiresMs);
                    ps.setLong  (13, snap.reparationsExpiresMs);
                    // recovery
                    ps.setLong  (14, snap.recoveryExpiresMs);
                    ps.setLong  (15, snap.recoveryExpiresMs);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }, "snapshotAllColonies");

        // Also refresh server_state
        updateServerState(server);
    }

    /** Update live server key/value state. */
    public static void updateServerState(MinecraftServer server) {
        if (!initialized) return;
        final int onlinePlayers = server.getPlayerList().getPlayerCount();
        final int activeWars    = net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS.size();
        final String modVersion = net.minecraftforge.fml.ModList.get()
                .getModContainerById(MineColonyTax.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("unknown");

        submit(() -> {
            Connection c = conn();
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO server_state (`key`, value) VALUES (?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value)")) {
                Object[][] rows = {
                        {"online_players",  String.valueOf(onlinePlayers)},
                        {"active_wars",     String.valueOf(activeWars)},
                        {"mod_version",     modVersion},
                        {"last_updated",    String.valueOf(System.currentTimeMillis())}
                };
                for (Object[] row : rows) {
                    ps.setString(1, (String) row[0]);
                    ps.setString(2, (String) row[1]);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }, "updateServerState");
    }

    // ==================== Helpers ====================

    /** Determine the DB outcome string from a WarData at the moment endWar() is called. */
    public static String determineOutcome(WarData warData) {
        String report = warData.getPenaltyReport();
        if (report == null || report.isEmpty()) return "STALEMATE";
        if (report.contains("TOTAL VICTORY") || report.contains("COUNTER-CONQUEST")) {
            return warData.getRemainingDefenderGuards() > 0 ? "DEFENDER_WIN" : "ATTACKER_WIN";
        }
        if (report.contains("Strategic Victory: Defenders")) return "DEFENDER_WIN";
        if (report.contains("Strategic Victory: Attackers")) return "ATTACKER_WIN";
        return "STALEMATE";
    }

    private static String resolveWarStatus(int colonyId) {
        if (WarExhaustionManager.isAtWar(colonyId))      return "AT_WAR";
        if (WarExhaustionManager.hasWarImmunity(colonyId)) return "IMMUNE";
        if (WarExhaustionManager.hasReparations(colonyId)) return "REPARATIONS";
        if (WarExhaustionManager.isInRecovery(colonyId))   return "RECOVERING";
        return "NORMAL";
    }

    /** INSERT IGNORE so first login wins; subsequent logins just update display_name via upsertPlayerLogin. */
    private static void ensurePlayer(Connection c, UUID uuid, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT IGNORE INTO player_stats (uuid, display_name, first_seen, last_seen)
                VALUES (?, ?, NOW(), NOW())
                """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name != null ? name : uuid.toString().substring(0, 8));
            ps.executeUpdate();
        }
    }

    /** Ensure a player row exists when only the UUID is available (defender offline). */
    private static void ensurePlayerByUUID(Connection c, UUID uuid) throws SQLException {
        ensurePlayer(c, uuid, uuid.toString().substring(0, 8));
    }

    /**
     * Increment a single stat column for a set of player UUIDs in one batch.
     * Column name is validated against a whitelist before use.
     */
    private static void batchIncrement(Connection c, Set<UUID> uuids, String column) throws SQLException {
        if (uuids.isEmpty()) return;
        // Whitelist — prevents SQL injection from any code path
        Set<String> allowed = Set.of("wars_won", "wars_lost", "war_stalemates",
                "players_killed", "times_killed", "colonies_raided",
                "times_declared_war", "times_targeted");
        if (!allowed.contains(column))
            throw new IllegalArgumentException("WarStatsDB: invalid column '" + column + "'");

        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE player_stats SET " + column + " = " + column + " + 1 WHERE uuid = ?")) {
            for (UUID uuid : uuids) {
                ps.setString(1, uuid.toString());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Submit a write task to the single-threaded writer. Logs errors; never retries. */
    private static void submit(SqlTask task, String name) {
        if (!initialized) return;
        WRITER.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                MineColonyTax.LOGGER.error("WarStatsDB [{}] write error: {}", name, e.getMessage());
            }
        });
    }

    @FunctionalInterface
    private interface SqlTask {
        void run() throws Exception;
    }

    // ==================== Data Transfer Object ====================

    private record ColonySnapshot(
            int colonyId, String colonyName, String ownerUUID,
            int citizenCount, int guardCount, int taxBalance,
            String warStatus, boolean isOccupied, String occupiedByUUID,
            long immunityExpiresMs, long reparationsExpiresMs, long recoveryExpiresMs
    ) {}
}
