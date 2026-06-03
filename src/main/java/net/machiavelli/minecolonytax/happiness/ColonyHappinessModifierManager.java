package net.machiavelli.minecolonytax.happiness;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenHappinessHandler;
import com.minecolonies.api.entity.citizen.happiness.StaticHappinessModifier;
import com.minecolonies.api.entity.citizen.happiness.StaticHappinessSupplier;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.economy.policy.TaxPolicyManager;
import net.machiavelli.minecolonytax.events.random.RandomEventManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages injecting War-N-Taxes happiness modifiers into MineColonies' real citizen happiness system.
 *
 * <p><b>WARNING — CURRENTLY DISABLED (double-counting bug):</b></p>
 * <p>
 * This class injects a StaticHappinessModifier into each citizen's MineColonies happiness handler.
 * However, {@code TaxManager.calculateColonyAverageHappiness()} reads MineColonies happiness
 * (which would include the injected modifier) and THEN adds event+policy modifiers additively.
 * This causes the same modifiers to be applied twice:
 * <ul>
 *   <li>Once via MineColonies' weighted average (injected modifier)</li>
 *   <li>Once additively in calculateColonyAverageHappiness()</li>
 * </ul>
 * On a long-running War SMP, this compounds each tax cycle and nearly doubles the intended
 * happiness penalty (e.g., WAR_ECONOMY's -0.25 effectively becomes ~-0.45).
 * </p>
 * <p>
 * The additive approach in calculateColonyAverageHappiness() is preferred for War SMP because:
 * <ul>
 *   <li>Predictable, fixed impact regardless of other MC happiness factors</li>
 *   <li>Players already see happiness via WnT GUI, tax reports, and /wnt commands</li>
 *   <li>Less invasive — won't break across MineColonies version updates</li>
 * </ul>
 * </p>
 * <p>
 * If re-enabling, you must EITHER remove the additive modifiers from
 * calculateColonyAverageHappiness() OR strip the WnT modifier before reading MC happiness.
 * </p>
 */
public class ColonyHappinessModifierManager {

    private static final Logger LOGGER = MineColonyTax.LOGGER;

    /**
     * Unique identifier for the WnT happiness modifier injected into MineColonies.
     */
    public static final String WNT_MODIFIER_ID = "warntax";

    /**
     * Weight of the WnT modifier in MineColonies' weighted happiness calculation.
     * Set to 2.0 to give meaningful impact without overwhelming vanilla factors.
     * For reference: SECURITY=4.0, FOOD=3.0, HOMELESSNESS=3.0, SOCIAL=2.0, SCHOOL=1.0
     */
    public static final double WNT_MODIFIER_WEIGHT = 2.0;

    public static void updateColonyHappinessModifiers(IColony colony) {
        if (!TaxConfig.isHappinessTaxModifierEnabled()) {
            removeColonyHappinessModifiers(colony);
            return;
        }

        int colonyId = colony.getID();

        double policyModifier = TaxPolicyManager.getHappinessModifier(colonyId);
        double eventModifier = RandomEventManager.getHappinessModifier(colonyId);
        double combinedModifier = policyModifier + eventModifier;

        // MC happiness factor: 1.0 = neutral (skipped), >1.0 = positive, <1.0 = negative
        double factor = 1.0 + combinedModifier;
        factor = Math.max(0.1, Math.min(2.0, factor));

        // Avoid injecting a no-op modifier
        if (Math.abs(factor - 1.0) < 0.001) {
            removeColonyHappinessModifiers(colony);
            return;
        }

        int updatedCount = 0;
        int totalCitizens = 0;

        for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
            if (citizen != null && !citizen.isChild()) {
                totalCitizens++;
                try {
                    ICitizenHappinessHandler happinessHandler = citizen.getCitizenHappinessHandler();
                    if (happinessHandler != null) {
                        happinessHandler.addModifier(
                                new StaticHappinessModifier(WNT_MODIFIER_ID, WNT_MODIFIER_WEIGHT,
                                        new StaticHappinessSupplier(factor))
                        );
                        updatedCount++;
                    }
                } catch (Exception e) {
                    if (TaxConfig.showTaxGenerationLogs()) {
                        LOGGER.debug("Could not update WnT happiness modifier for citizen {} in colony {}: {}",
                                citizen.getName(), colony.getName(), e.getMessage());
                    }
                }
            }
        }

        if (TaxConfig.showTaxGenerationLogs() && updatedCount > 0) {
            LOGGER.info("Updated WnT happiness modifier for colony {} ({}/{} citizens): policy={}, events={}, factor={}",
                    colony.getName(), updatedCount, totalCitizens,
                    String.format("%.2f", policyModifier),
                    String.format("%.2f", eventModifier),
                    String.format("%.3f", factor));
        }
    }

    public static void removeColonyHappinessModifiers(IColony colony) {
        for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
            if (citizen != null && !citizen.isChild()) {
                try {
                    ICitizenHappinessHandler happinessHandler = citizen.getCitizenHappinessHandler();
                    if (happinessHandler != null) {
                        // MC skips factor 1.0 in getHappiness(), so re-adding at 1.0 neutralizes the modifier
                        happinessHandler.addModifier(
                                new StaticHappinessModifier(WNT_MODIFIER_ID, WNT_MODIFIER_WEIGHT,
                                        new StaticHappinessSupplier(1.0))
                        );
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static double getWntHappinessFactor(int colonyId) {
        double policyModifier = TaxPolicyManager.getHappinessModifier(colonyId);
        double eventModifier = RandomEventManager.getHappinessModifier(colonyId);
        double combined = policyModifier + eventModifier;
        return Math.max(0.1, Math.min(2.0, 1.0 + combined));
    }
}
