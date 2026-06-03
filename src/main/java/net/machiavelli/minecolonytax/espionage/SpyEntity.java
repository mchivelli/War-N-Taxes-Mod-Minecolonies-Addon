package net.machiavelli.minecolonytax.espionage;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.UUID;

public class SpyEntity extends PathfinderMob {

    public enum SpyState { INFILTRATING, FLEEING }

    private static final UUID FLEE_SPEED_MODIFIER_ID =
            UUID.fromString("a7b6c5d4-e3f2-1a0b-9c8d-7e6f5a4b3c2d");
    private static final String FLEE_SPEED_MODIFIER_NAME = "spy_flee_speed";

    // Must be outside the colony border for this many consecutive ticks to confirm escape
    private static final int ESCAPE_CONFIRM_TICKS = 60;

    private String missionId = "";
    private UUID ownerPlayerId = null;
    private int targetColonyId = -1;
    private String missionType = "";
    private String displayNameStr = "Suspicious Citizen";

    private SpyState currentState = SpyState.INFILTRATING;
    private BlockPos fleeTarget = null;
    private int fleeAttemptTicks = 0;
    private int escapeConsecutiveTicks = 0;
    private boolean needsFleeRecompute = false;

    private int intelCheckTimer = 0;
    // Intel and detection checks share the same interval (20 ticks/sec * 60 sec)
    private static final int INTEL_CHECK_INTERVAL = 1200;

    public SpyEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setCustomName(Component.literal(displayNameStr));
        this.setCustomNameVisible(false);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SpyFleeGoal(this, 1.0)); // Speed boost applied via attribute modifier in enterFleeState()
        this.goalSelector.addGoal(2, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    public void setMissionData(String missionId, UUID ownerId, int targetColonyId, String missionType) {
        this.missionId = missionId;
        this.ownerPlayerId = ownerId;
        this.targetColonyId = targetColonyId;
        this.missionType = missionType;
    }

    public String getMissionId() {
        return this.missionId;
    }

    public boolean isFleeing() {
        return currentState == SpyState.FLEEING;
    }

    public BlockPos getFleeTarget() {
        return fleeTarget;
    }

    // Spy entities must never despawn due to distance from players — missions would be lost
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) return;

        if (currentState == SpyState.INFILTRATING) {
            tickInfiltrating();
        } else {
            tickFleeing();
        }
    }

    private void tickInfiltrating() {
        // Discard if the manager no longer has this mission (e.g. completed server-side while chunk was unloaded)
        if (this.tickCount > 100 && !missionId.isEmpty() && this.tickCount % 6000 == 0) {
            if (!SpyManager.isMissionActive(missionId)) {
                this.discard();
                return;
            }
        }

        intelCheckTimer++;
        if (intelCheckTimer >= INTEL_CHECK_INTERVAL) {
            intelCheckTimer = 0;

            if ("SCOUT".equals(missionType) && targetColonyId != -1) {
                SpyManager.progressIntel(missionId);
            }

            checkGuardDetection();
        }
    }

    private void tickFleeing() {
        fleeAttemptTicks++;

        // Orphan check: if the mission has been removed server-side (e.g. a stale FLEEING entity reloads
        // after a restart, or the mission was forcibly cleaned up) discard rather than running until
        // FleeMaxSeconds. Matches the equivalent guard in tickInfiltrating.
        // See audit/defensive_03_espionage.md H2 / task fix #7.
        if (this.tickCount > 100 && !missionId.isEmpty() && this.tickCount % 6000 == 0) {
            if (!SpyManager.isMissionActive(missionId)) {
                this.discard();
                return;
            }
        }

        // Re-compute flee target if needed (e.g., after server restart when fleeTarget is stale)
        if (needsFleeRecompute) {
            needsFleeRecompute = false;
            computeFleeTarget();
        }

        if (this.tickCount % 5 == 0) {
            ((ServerLevel) this.level()).sendParticles(
                    ParticleTypes.SMOKE,
                    this.getX(), this.getY() + 1.0, this.getZ(),
                    3, 0.2, 0.2, 0.2, 0.02);
        }

        checkEscapeSuccess();

        int maxFleeTicks = TaxConfig.getFleeMaxSeconds() * 20;
        if (fleeAttemptTicks > maxFleeTicks) {
            if (!missionId.isEmpty()) {
                SpyManager.onSpyKilled(missionId);
            }
            this.discard();
        }
    }

    private void checkGuardDetection() {
        double baseChance = TaxConfig.getSpyDetectionBaseChance();
        double modifier = switch (missionType) {
            case "SCOUT" -> TaxConfig.getSpyScoutDetectionChance();
            case "SABOTAGE" -> TaxConfig.getSpySabotageDetectionChance();
            case "BRIBE" -> TaxConfig.getSpyBribeGuardsDetectionChance();
            case "STEAL" -> TaxConfig.getSpyStealSecretsDetectionChance();
            default -> 0.05;
        };
        double evasionReduction = 0.0;
        if (!missionId.isEmpty()) {
            try {
                int attackerColonyId = SpyManager.getAttackerColonyIdForMission(missionId);
                if (attackerColonyId != -1) {
                    evasionReduction = net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager
                            .getDetectionReductionChance(attackerColonyId);
                }
            } catch (Exception e) {
                // ColonyUpgradeManager unavailable — no evasion bonus
            }
        }
        double totalChance = Math.max(0.01, baseChance + modifier - evasionReduction);

        java.util.List<com.minecolonies.api.entity.citizen.AbstractEntityCitizen> nearbyEntities = level()
                .getEntitiesOfClass(
                        com.minecolonies.api.entity.citizen.AbstractEntityCitizen.class,
                        getBoundingBox().inflate(16.0));

        boolean guardNearby = nearbyEntities.stream()
                .anyMatch(e -> e.getCitizenData() != null
                        && e.getCitizenData().getJob() != null
                        && e.getCitizenData().getJob().getJobRegistryEntry().getKey().getPath()
                                .contains("guard"));

        if (guardNearby && this.random.nextDouble() < totalChance) {
            enterFleeState();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        // Guard/player hits trigger flee but never reduce HP — flee timeout is the only death path
        if (isPlayerOrGuardDamage(source)) {
            if (currentState != SpyState.FLEEING) enterFleeState();
            return false;
        }
        return super.hurt(source, amount);
    }

    private boolean isPlayerOrGuardDamage(DamageSource source) {
        net.minecraft.world.entity.Entity attacker = source.getEntity();
        if (attacker instanceof Player) return true;
        if (attacker instanceof com.minecolonies.api.entity.citizen.AbstractEntityCitizen citizen) {
            return citizen.getCitizenData() != null
                    && citizen.getCitizenData().getJob() != null
                    && citizen.getCitizenData().getJob().getJobRegistryEntry().getKey().getPath().contains("guard");
        }
        return false;
    }

    private void enterFleeState() {
        if (currentState == SpyState.FLEEING) return;
        currentState = SpyState.FLEEING;
        fleeAttemptTicks = 0;
        escapeConsecutiveTicks = 0;

        // Speed boost applied as an additive modifier rather than changing SpyFleeGoal's speedMod,
        // so the boost stacks correctly with any future attribute changes.
        var speedAttr = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(FLEE_SPEED_MODIFIER_ID);
            double boost = speedAttr.getBaseValue() * (TaxConfig.getFleeSpeedMultiplier() - 1.0);
            speedAttr.addTransientModifier(new AttributeModifier(
                    FLEE_SPEED_MODIFIER_ID,
                    FLEE_SPEED_MODIFIER_NAME,
                    boost,
                    AttributeModifier.Operation.ADDITION));
        }

        this.setCustomName(Component.literal("Fleeing Spy!").withStyle(
                net.minecraft.ChatFormatting.RED));
        this.setCustomNameVisible(true);

        computeFleeTarget();

        if (!missionId.isEmpty()) {
            SpyManager.onSpyDetected(missionId);
        }
    }

    private void computeFleeTarget() {
        if (targetColonyId == -1) {
            fleeTarget = this.blockPosition().offset(50, 0, 50);
            return;
        }

        IColony colony = IMinecoloniesAPI.getInstance().getColonyManager()
                .getColonyByWorld(targetColonyId, (ServerLevel) this.level());
        if (colony == null) {
            fleeTarget = this.blockPosition().offset(50, 0, 50);
            return;
        }

        BlockPos colonyCenter = colony.getCenter();
        Vec3 outwardDir = new Vec3(
                this.getX() - colonyCenter.getX(),
                0,
                this.getZ() - colonyCenter.getZ()).normalize();

        // Edge case: spy is exactly at colony center — pick an arbitrary direction
        if (outwardDir.lengthSqr() < 0.001) {
            outwardDir = new Vec3(1, 0, 0);
        }

        int escapeBuffer = TaxConfig.getFleeEscapeDistance();
        BlockPos candidate = this.blockPosition();
        int stepsOutside = 0;
        boolean foundBorder = false;

        for (int step = 1; step <= 200; step++) {
            int cx = (int) (this.getX() + outwardDir.x * step);
            int cz = (int) (this.getZ() + outwardDir.z * step);
            BlockPos testPos = new BlockPos(cx, 64, cz);

            if (!colony.isCoordInColony(this.level(), testPos)) {
                stepsOutside++;
                if (!foundBorder) foundBorder = true;
                if (stepsOutside >= escapeBuffer) {
                    // Adjust to ground
                    candidate = ((ServerLevel) this.level()).getHeightmapPos(
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, testPos);
                    break;
                }
            } else {
                stepsOutside = 0;
            }
        }

        if (!foundBorder) {
            int fallX = (int) (this.getX() + outwardDir.x * 100);
            int fallZ = (int) (this.getZ() + outwardDir.z * 100);
            BlockPos fallRaw = new BlockPos(fallX, 64, fallZ);
            candidate = ((ServerLevel) this.level()).getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, fallRaw);
        }

        fleeTarget = candidate;
    }

    private void checkEscapeSuccess() {
        if (targetColonyId == -1) {
            // No colony reference — treat reaching flee target as escape
            if (fleeTarget != null) {
                double distSq = this.distanceToSqr(fleeTarget.getX(), fleeTarget.getY(), fleeTarget.getZ());
                if (distSq < 9.0) { // within ~3 blocks of flee target
                    triggerEscape();
                }
            }
            return;
        }

        IColony colony = IMinecoloniesAPI.getInstance().getColonyManager()
                .getColonyByWorld(targetColonyId, (ServerLevel) this.level());
        if (colony == null) {
            triggerEscape();
            return;
        }

        boolean outsideColony = !colony.isCoordInColony(this.level(), this.blockPosition());
        if (outsideColony) {
            escapeConsecutiveTicks++;
            if (escapeConsecutiveTicks >= ESCAPE_CONFIRM_TICKS) {
                triggerEscape();
            }
        } else {
            escapeConsecutiveTicks = 0;
        }
    }

    private void triggerEscape() {
        if (!missionId.isEmpty()) {
            SpyManager.onSpyEscaped(missionId);
        }
        this.discard();
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (!this.level().isClientSide && !missionId.isEmpty()) {
            // Guard against double-call: flee timeout and escape already call onSpyKilled/onSpyEscaped
            if (SpyManager.isMissionActive(missionId)) {
                SpyManager.onSpyKilled(missionId);
            }
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SpyClientHandler.openSpyDialog());
            return InteractionResult.sidedSuccess(true);
        }
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("MissionId", missionId);
        if (ownerPlayerId != null) {
            tag.putUUID("OwnerPlayerId", ownerPlayerId);
        }
        tag.putInt("TargetColonyId", targetColonyId);
        tag.putString("MissionType", missionType);
        tag.putString("SpyDisplayName", displayNameStr);
        tag.putString("SpyState", currentState.name());
        tag.putInt("FleeAttemptTicks", fleeAttemptTicks);
        if (fleeTarget != null) {
            tag.putInt("FleeTargetX", fleeTarget.getX());
            tag.putInt("FleeTargetY", fleeTarget.getY());
            tag.putInt("FleeTargetZ", fleeTarget.getZ());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.missionId = tag.getString("MissionId");
        if (tag.contains("OwnerPlayerId")) {
            this.ownerPlayerId = tag.getUUID("OwnerPlayerId");
        }
        this.targetColonyId = tag.getInt("TargetColonyId");
        this.missionType = tag.getString("MissionType");
        if (tag.contains("SpyDisplayName")) {
            this.displayNameStr = tag.getString("SpyDisplayName");
            this.setCustomName(Component.literal(this.displayNameStr));
        }
        if (tag.contains("SpyState")) {
            try {
                this.currentState = SpyState.valueOf(tag.getString("SpyState"));
            } catch (IllegalArgumentException e) {
                this.currentState = SpyState.INFILTRATING;
            }
        }
        this.fleeAttemptTicks = tag.contains("FleeAttemptTicks") ? tag.getInt("FleeAttemptTicks") : 0;
        if (tag.contains("FleeTargetX")) {
            this.fleeTarget = new BlockPos(
                    tag.getInt("FleeTargetX"),
                    tag.getInt("FleeTargetY"),
                    tag.getInt("FleeTargetZ"));
        }
        // Re-compute flee target on next tick — the saved target may be stale after world reload
        if (currentState == SpyState.FLEEING) {
            needsFleeRecompute = true;
            this.setCustomName(Component.literal("Fleeing Spy!").withStyle(
                    net.minecraft.ChatFormatting.RED));
            this.setCustomNameVisible(true);
        }
    }
}
