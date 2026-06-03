package net.machiavelli.minecolonytax.espionage;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

public class SpyEntity extends PathfinderMob {

    // ---- State machine ----
    public enum SpyState { INFILTRATING, FLEEING }

    private static final ResourceLocation FLEE_SPEED_MODIFIER =
            ResourceLocation.fromNamespaceAndPath("minecolonytax", "spy_flee_speed");

    // Ticks until escape is confirmed (must be outside border for this many consecutive checks)
    private static final int ESCAPE_CONFIRM_TICKS = 60;

    // ---- Mission fields ----
    private String missionId = "";
    private java.util.UUID ownerPlayerId = null;
    private int targetColonyId = -1;
    private String missionType = "";
    private String displayNameStr = "Suspicious Citizen";

    // ---- State fields ----
    private SpyState currentState = SpyState.INFILTRATING;
    private BlockPos fleeTarget = null;
    private int fleeAttemptTicks = 0;
    private int escapeConsecutiveTicks = 0;
    private boolean needsFleeRecompute = false;

    // ---- Intel progress timer (in-game ticks) ----
    private int intelCheckTimer = 0;
    private static final int INTEL_CHECK_INTERVAL = 1200; // every 60 sec (20*60)

    // ---- Detection check timer ----
    // Detection still checked every 60 sec (same interval)

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

    public void setMissionData(String missionId, java.util.UUID ownerId, int targetColonyId, String missionType) {
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

    // Spy entities must never despawn due to distance from players
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // ---- Tick ----

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
        // Orphan check
        if (this.tickCount > 100 && !missionId.isEmpty() && this.tickCount % 6000 == 0) {
            if (!SpyManager.isMissionActive(missionId)) {
                this.discard();
                return;
            }
        }

        intelCheckTimer++;
        if (intelCheckTimer >= INTEL_CHECK_INTERVAL) {
            intelCheckTimer = 0;

            // Progressive intel advancement
            if ("SCOUT".equals(missionType) && targetColonyId != -1) {
                SpyManager.progressIntel(missionId);
            }

            // Guard detection check — triggers flee instead of instant kill
            checkGuardDetection();
        }
    }

    private void tickFleeing() {
        fleeAttemptTicks++;

        // Re-compute flee target if needed (e.g., after server restart)
        if (needsFleeRecompute) {
            needsFleeRecompute = false;
            computeFleeTarget();
        }

        // Smoke particles every 5 ticks
        if (this.tickCount % 5 == 0) {
            ((ServerLevel) this.level()).sendParticles(
                    ParticleTypes.SMOKE,
                    this.getX(), this.getY() + 1.0, this.getZ(),
                    3, 0.2, 0.2, 0.2, 0.02);
        }

        // Check if escaped
        checkEscapeSuccess();

        // Flee timeout — spy is caught
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
        double totalChance = baseChance + modifier;

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

    // ---- Hurt override: trigger flee on player or guard damage ----

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (!level().isClientSide && result && currentState != SpyState.FLEEING) {
            if (isPlayerOrGuardDamage(source)) {
                enterFleeState();
            }
        }
        return result;
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

    // ---- Flee state machine ----

    private void enterFleeState() {
        if (currentState == SpyState.FLEEING) return;
        currentState = SpyState.FLEEING;
        fleeAttemptTicks = 0;
        escapeConsecutiveTicks = 0;

        // Speed boost via attribute modifier
        var speedAttr = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(FLEE_SPEED_MODIFIER);
            double boost = speedAttr.getBaseValue() * (TaxConfig.getFleeSpeedMultiplier() - 1.0);
            speedAttr.addTransientModifier(new AttributeModifier(
                    FLEE_SPEED_MODIFIER,
                    boost,
                    AttributeModifier.Operation.ADD_VALUE));
        }

        // Change display name to "Fleeing Spy!" in red
        this.setCustomName(Component.literal("Fleeing Spy!").withStyle(
                net.minecraft.ChatFormatting.RED));
        this.setCustomNameVisible(true);

        computeFleeTarget();

        // Notify SpyManager — sets mission to FLEEING, notifies defenders
        if (!missionId.isEmpty()) {
            SpyManager.onSpyDetected(missionId);
        }
    }

    private void computeFleeTarget() {
        if (targetColonyId == -1) {
            // Fallback: flee directly away from nearest player
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

        // Edge case: spy is exactly at colony center
        if (outwardDir.lengthSqr() < 0.001) {
            outwardDir = new Vec3(1, 0, 0);
        }

        // Walk outward until outside colony + buffer
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
            // Fallback: 100 blocks in outward direction
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
            // Can't determine colony border, just escape after buffer distance
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

    // ---- Death handling ----

    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (!this.level().isClientSide && !missionId.isEmpty()) {
            // Only call onSpyKilled if not already handled by flee timeout or escape
            if (SpyManager.isMissionActive(missionId)) {
                SpyManager.onSpyKilled(missionId);
            }
        }
    }

    // ---- Interaction ----

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            // Delegated to a client-only class so SpyEntity (classloaded during entity
            // registration on the dedicated server) never references client GUI types.
            net.machiavelli.minecolonytax.client.MctClientNetHandlers.openSpyDialog();
            return InteractionResult.sidedSuccess(true);
        }
        return InteractionResult.sidedSuccess(false);
    }

    // ---- NBT ----

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
        // If loaded in flee state, re-compute target next tick (colony may have moved)
        if (currentState == SpyState.FLEEING) {
            needsFleeRecompute = true;
            // Restore flee name
            this.setCustomName(Component.literal("Fleeing Spy!").withStyle(
                    net.minecraft.ChatFormatting.RED));
            this.setCustomNameVisible(true);
        }
    }
}
