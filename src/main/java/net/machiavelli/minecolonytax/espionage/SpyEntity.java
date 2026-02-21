package net.machiavelli.minecolonytax.espionage;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;

import java.util.UUID;

public class SpyEntity extends PathfinderMob {

    private String missionId = "";
    private UUID ownerPlayerId = null;
    private int targetColonyId = -1;
    private String missionType = "";
    private String displayNameStr = "Suspicious Citizen";

    // Timer for intel gathering
    private int intelTimer = 0;

    public SpyEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setCustomName(Component.literal(displayNameStr));
        this.setCustomNameVisible(false); // Only visible when looking closely or interacting
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
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

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            intelTimer++;
            if (intelTimer >= 600) { // Every 30 seconds (20 ticks * 30)
                intelTimer = 0;
                if ("SCOUT".equals(missionType) && targetColonyId != -1) {
                    SpyManager.gatherIntel(targetColonyId);
                }
            }
            if (this.tickCount > 0 && this.tickCount % 1200 == 0) { // Every ~60 seconds
                checkGuardDetection();
            }
        }
    }

    private void checkGuardDetection() {
        double baseChance = net.machiavelli.minecolonytax.TaxConfig.getSpyDetectionBaseChance();
        double modifier = switch (missionType) {
            case "SCOUT" -> 0.05;
            case "SABOTAGE" -> 0.20;
            case "BRIBE" -> 0.15;
            case "STEAL" -> 0.10;
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
            if (!missionId.isEmpty()) {
                SpyManager.onSpyKilled(missionId);
            }
            this.discard();
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            net.minecraft.client.Minecraft.getInstance()
                    .setScreen(new net.machiavelli.minecolonytax.gui.SpyDialogScreen());
            return InteractionResult.sidedSuccess(true);
        }
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (!this.level().isClientSide && !missionId.isEmpty()) {
            SpyManager.onSpyKilled(missionId);
        }
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
    }
}
