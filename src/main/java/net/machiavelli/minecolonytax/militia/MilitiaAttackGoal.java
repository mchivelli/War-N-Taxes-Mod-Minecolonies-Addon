package net.machiavelli.minecolonytax.militia;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

/**
 * Custom attack goal for militia citizens that doesn't require ATTACK_DAMAGE attribute.
 * This goal handles pathfinding to targets and executing attacks manually.
 */
public class MilitiaAttackGoal extends Goal {
    private static final Logger LOGGER = LoggerFactory.getLogger(MilitiaAttackGoal.class);
    
    private final AbstractEntityCitizen citizen;
    private LivingEntity target;
    private int attackTime = -1;
    private final double speedModifier;
    private int ticksUntilNextPathRecalculation;
    private int ticksUntilNextAttack;
    private final int attackInterval = 20; // Attack every 20 ticks (1 second)
    
    public MilitiaAttackGoal(AbstractEntityCitizen citizen, double speedModifier) {
        this.citizen = citizen;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }
    
    @Override
    public boolean canUse() {
        LivingEntity livingentity = this.citizen.getTarget();
        if (livingentity == null) {
            return false;
        } else if (!livingentity.isAlive()) {
            return false;
        } else if (!this.citizen.level().getWorldBorder().isWithinBounds(livingentity.blockPosition())) {
            return false;
        } else {
            this.target = livingentity;
            return true;
        }
    }
    
    @Override
    public boolean canContinueToUse() {
        LivingEntity livingentity = this.citizen.getTarget();
        if (livingentity == null) {
            return false;
        } else if (!livingentity.isAlive()) {
            return false;
        } else if (!this.citizen.level().getWorldBorder().isWithinBounds(livingentity.blockPosition())) {
            return false;
        } else {
            return !(livingentity instanceof Player) || !livingentity.isSpectator() && !((Player)livingentity).isCreative();
        }
    }
    
    @Override
    public void start() {
        this.citizen.getNavigation().moveTo(this.target, this.speedModifier);
        this.citizen.setAggressive(true);
        this.ticksUntilNextPathRecalculation = 0;
        this.ticksUntilNextAttack = 0;
        this.attackTime = -1;
        LOGGER.debug("Militia {} started attacking target {}", this.citizen.getName().getString(), 
                    this.target != null ? this.target.getName().getString() : "null");
    }
    
    @Override
    public void stop() {
        LivingEntity livingentity = this.citizen.getTarget();
        if (livingentity != null && !livingentity.isAlive()) {
            this.target = null;
        }
        this.citizen.setAggressive(false);
        this.citizen.getNavigation().stop();
        LOGGER.debug("Militia {} stopped attacking", this.citizen.getName().getString());
    }
    
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
    
    @Override
    public void tick() {
        if (this.target == null) {
            return;
        }
        
        this.citizen.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
        double distanceToTarget = this.citizen.distanceToSqr(this.target.getX(), this.target.getY(), this.target.getZ());
        
        // Recalculate path if needed
        this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);
        if (this.ticksUntilNextPathRecalculation <= 0) {
            this.ticksUntilNextPathRecalculation = 4 + this.citizen.getRandom().nextInt(7);
            if (distanceToTarget > 1024.0D) {
                this.ticksUntilNextPathRecalculation += 10;
            } else if (distanceToTarget > 256.0D) {
                this.ticksUntilNextPathRecalculation += 5;
            }
            
            if (!this.citizen.getNavigation().moveTo(this.target, this.speedModifier)) {
                this.ticksUntilNextPathRecalculation += 15;
            }
        }
        
        // Handle attacking
        this.attackTime = Math.max(this.attackTime - 1, 0);
        this.ticksUntilNextAttack = Math.max(this.ticksUntilNextAttack - 1, 0);
        
        // Attack if in range and ready
        if (distanceToTarget <= 4.0D && this.ticksUntilNextAttack <= 0) {
            this.resetAttackCooldown();
            this.performAttack();
        }
    }
    
    protected void resetAttackCooldown() {
        this.ticksUntilNextAttack = this.attackInterval;
    }
    
    protected void performAttack() {
        if (this.target == null || !this.target.isAlive()) {
            return;
        }
        
        try {
            // Make sure citizen is holding a weapon
            ItemStack mainHand = this.citizen.getItemInHand(InteractionHand.MAIN_HAND);
            if (mainHand.isEmpty() || mainHand.getItem() != Items.WOODEN_SWORD) {
                // Give them a sword if they don't have one
                this.citizen.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_SWORD));
            }
            
            // Play attack animation
            this.citizen.swing(InteractionHand.MAIN_HAND);
            
            // Calculate damage (wooden sword base damage)
            float damage = 4.0F; // Wooden sword damage
            
            // Apply damage to target
            this.target.hurt(this.citizen.damageSources().mobAttack(this.citizen), damage);
            
            LOGGER.debug("Militia {} attacked {} for {} damage", 
                        this.citizen.getName().getString(), 
                        this.target.getName().getString(), 
                        damage);
            
            // Knockback effect
            double knockbackStrength = 0.4D;
            if (this.target instanceof LivingEntity) {
                LivingEntity livingTarget = (LivingEntity) this.target;
                double deltaX = this.target.getX() - this.citizen.getX();
                double deltaZ = this.target.getZ() - this.citizen.getZ();
                livingTarget.knockback(knockbackStrength, -deltaX, -deltaZ);
            }
            
        } catch (Exception e) {
            LOGGER.error("Error during militia attack by {}", this.citizen.getName().getString(), e);
        }
    }
}
