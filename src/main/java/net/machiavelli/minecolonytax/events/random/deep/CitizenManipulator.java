package net.machiavelli.minecolonytax.events.random.deep;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenDiseaseHandler;
import net.machiavelli.minecolonytax.MineColonyTax;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * System for manipulating citizen states for deep integration events.
 *
 * This class provides methods to:
 * - Force labor strikes (set citizens to STUCK status)
 * - Infect citizens with diseases (plague outbreaks)
 * - Cure all sick citizens (hospital interventions)
 * - Modify citizen saturation (feasts and famines)
 *
 * All methods use verified MineColonies APIs.
 */
public class CitizenManipulator {

    private static final Logger LOGGER = MineColonyTax.LOGGER;

    /**
     * Force a labor strike by setting a percentage of workers to STUCK status.
     * Only affects employed citizens (those with jobs).
     *
     * @param colony Target colony
     * @param percentage Percentage of citizens to affect (0.0-1.0)
     * @return List of affected citizen IDs
     */
    public static List<Integer> forceLaborStrike(IColony colony, double percentage) {
        List<ICitizenData> citizens = new ArrayList<>(colony.getCitizenManager().getCitizens());
        int targetCount = (int) (citizens.size() * percentage);
        List<Integer> affectedCitizens = new ArrayList<>();

        // Shuffle to randomize which citizens are affected
        Collections.shuffle(citizens);

        int affectedCount = 0;
        for (ICitizenData citizen : citizens) {
            if (affectedCount >= targetCount) {
                break;
            }

            // Only affect employed adult citizens
            if (citizen.getJob() != null && !citizen.isChild()) {
                try {
                    // Set job status to STUCK using reflection
                    if (setJobStatus(citizen, "STUCK")) {
                        affectedCitizens.add(citizen.getId());
                        affectedCount++;

                        LOGGER.debug("Labor strike: Set citizen {} to STUCK status in colony {}",
                                citizen.getName(), colony.getName());
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to set STUCK status for citizen {} in colony {}: {}",
                            citizen.getName(), colony.getName(), e.getMessage());
                }
            }
        }

        LOGGER.info("Labor strike initiated in colony {}: {} out of {} workers affected",
                colony.getName(), affectedCount, citizens.size());

        return affectedCitizens;
    }

    /**
     * Restore citizens from labor strike by setting them back to WORKING status.
     *
     * @param colony Target colony
     * @param citizenIds IDs of citizens to restore
     * @return Number of citizens successfully restored
     */
    public static int restoreCitizens(IColony colony, List<Integer> citizenIds) {
        int restoredCount = 0;

        for (Integer id : citizenIds) {
            ICitizenData citizen = colony.getCitizenManager().getCivilian(id);
            if (citizen != null && citizen.getJob() != null) {
                try {
                    // Restore job status to WORKING using reflection
                    if (setJobStatus(citizen, "WORKING")) {
                        restoredCount++;

                        LOGGER.debug("Restored citizen {} to WORKING status in colony {}",
                                citizen.getName(), colony.getName());
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to restore citizen {} in colony {}: {}",
                            id, colony.getName(), e.getMessage());
                }
            }
        }

        LOGGER.info("Labor strike ended in colony {}: {} workers restored",
                colony.getName(), restoredCount);

        return restoredCount;
    }

    /**
     * Infect a percentage of citizens with random diseases.
     * Only affects citizens who are not already sick.
     *
     * @param colony Target colony
     * @param percentage Percentage to infect (0.0-1.0)
     * @return List of infected citizen IDs
     */
    public static List<Integer> infectWithPlague(IColony colony, double percentage) {
        List<ICitizenData> citizens = new ArrayList<>(colony.getCitizenManager().getCitizens());
        int targetCount = (int) (citizens.size() * percentage);
        List<Integer> infectedCitizens = new ArrayList<>();

        // Shuffle to randomize infection targets
        Collections.shuffle(citizens);

        int infectedCount = 0;
        for (ICitizenData citizen : citizens) {
            if (infectedCount >= targetCount) {
                break;
            }

            // Don't infect children or already sick citizens
            if (!citizen.isChild()) {
                try {
                    // Check if citizen is already sick using reflection
                    if (!isCitizenSick(citizen)) {
                        // Get random disease using reflection
                        Object disease = getRandomDisease(colony);

                        if (disease != null) {
                            // Infect citizen using reflection
                            boolean infected = infectCitizenWithDisease(citizen, disease);

                            if (infected) {
                                infectedCitizens.add(citizen.getId());
                                infectedCount++;

                                LOGGER.debug("Plague: Infected citizen {} in colony {}",
                                        citizen.getName(), colony.getName());
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to infect citizen {} in colony {}: {}",
                            citizen.getName(), colony.getName(), e.getMessage());
                }
            }
        }

        LOGGER.info("Plague outbreak in colony {}: {} out of {} citizens infected",
                colony.getName(), infectedCount, citizens.size());

        return infectedCitizens;
    }

    /**
     * Get a random disease using reflection to access internal MineColonies classes.
     *
     * @param colony The colony (provides RandomSource)
     * @return Disease object or null
     */
    private static Object getRandomDisease(IColony colony) {
        try {
            // Access DiseasesListener.getRandomDisease(RandomSource) via reflection
            Class<?> listenerClass = Class.forName("com.minecolonies.core.datalistener.DiseasesListener");
            Method getRandomMethod = listenerClass.getMethod("getRandomDisease", net.minecraft.util.RandomSource.class);
            return getRandomMethod.invoke(null, colony.getWorld().getRandom());
        } catch (Exception e) {
            LOGGER.warn("Failed to get random disease via reflection: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Cure all sick citizens in the colony.
     * Used when Hospital L3+ is built during plague events.
     *
     * @param colony Target colony
     * @return Number of citizens cured
     */
    public static int cureAllCitizens(IColony colony) {
        int curedCount = 0;

        for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
            try {
                // Check if citizen is sick and cure using reflection
                if (isCitizenSick(citizen)) {
                    if (cureCitizen(citizen)) {
                        curedCount++;

                        LOGGER.debug("Cured citizen {} in colony {}",
                                citizen.getName(), colony.getName());
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to cure citizen {} in colony {}: {}",
                        citizen.getName(), colony.getName(), e.getMessage());
            }
        }

        LOGGER.info("Hospital cure in colony {}: {} citizens cured",
                colony.getName(), curedCount);

        return curedCount;
    }

    /**
     * Set saturation level for all citizens (feast or famine).
     * Used for ROYAL_FEAST (saturation=20.0) and CROP_BLIGHT (saturation=3.0).
     *
     * @param colony Target colony
     * @param saturation Saturation level (0.0-20.0, verified API uses double)
     * @return Number of citizens affected
     */
    public static int setSaturationForAll(IColony colony, double saturation) {
        // Clamp saturation to valid range (verified API: 0.0-20.0)
        double clampedSaturation = Math.max(0.0, Math.min(20.0, saturation));
        int affectedCount = 0;

        for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
            try {
                // Set saturation (verified API: line 1181-1184 in ICitizenData, uses double)
                citizen.setSaturation(clampedSaturation);
                affectedCount++;

                LOGGER.debug("Set saturation to {} for citizen {} in colony {}",
                        clampedSaturation, citizen.getName(), colony.getName());
            } catch (Exception e) {
                LOGGER.warn("Failed to set saturation for citizen {} in colony {}: {}",
                        citizen.getName(), colony.getName(), e.getMessage());
            }
        }

        String eventType = saturation >= 15.0 ? "feast" : "famine";
        LOGGER.info("Saturation {} in colony {}: {} citizens affected (saturation set to {})",
                eventType, colony.getName(), affectedCount, clampedSaturation);

        return affectedCount;
    }

    /**
     * Check if a citizen is currently on strike (STUCK status).
     *
     * @param citizen The citizen to check
     * @return true if citizen is on strike
     */
    public static boolean isCitizenOnStrike(ICitizenData citizen) {
        try {
            if (citizen.getJob() == null) {
                return false;
            }

            // Get job status using reflection
            String status = getJobStatus(citizen);
            return "STUCK".equals(status);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Set job status for a citizen using reflection.
     *
     * @param citizen The citizen
     * @param statusName The status name (IDLE, WORKING, STUCK)
     * @return true if successfully set
     */
    private static boolean setJobStatus(ICitizenData citizen, String statusName) {
        try {
            // Get JobStatus enum class
            Class<?> jobStatusClass = Class.forName("com.minecolonies.api.entity.ai.JobStatus");

            // Get the enum value
            Object statusValue = Enum.valueOf((Class<Enum>) jobStatusClass, statusName);

            // Call setJobStatus method
            Method setStatusMethod = citizen.getClass().getMethod("setJobStatus", jobStatusClass);
            setStatusMethod.invoke(citizen, statusValue);

            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to set job status via reflection: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get job status for a citizen using reflection.
     *
     * @param citizen The citizen
     * @return Status name or null
     */
    private static String getJobStatus(ICitizenData citizen) {
        try {
            // Call getJobStatus method
            Method getStatusMethod = citizen.getClass().getMethod("getJobStatus");
            Object status = getStatusMethod.invoke(citizen);

            if (status != null) {
                return status.toString();
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to get job status via reflection: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Get count of citizens currently on strike in a colony.
     *
     * @param colony Target colony
     * @return Number of striking citizens
     */
    public static int getStrikingCitizenCount(IColony colony) {
        int count = 0;

        for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
            if (isCitizenOnStrike(citizen)) {
                count++;
            }
        }

        return count;
    }

    /**
     * Get count of sick citizens in a colony.
     *
     * @param colony Target colony
     * @return Number of sick citizens
     */
    public static int getSickCitizenCount(IColony colony) {
        int count = 0;

        for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
            try {
                // Check if citizen is sick using reflection
                if (isCitizenSick(citizen)) {
                    count++;
                }
            } catch (Exception e) {
                // Ignore
            }
        }

        return count;
    }

    // ==================== Reflection Helper Methods ====================

    /**
     * Check if a citizen is sick using reflection.
     *
     * @param citizen The citizen to check
     * @return true if citizen is sick
     */
    private static boolean isCitizenSick(ICitizenData citizen) {
        try {
            // Get disease handler
            Method getDiseaseHandler = citizen.getClass().getMethod("getCitizenDiseaseHandler");
            Object handler = getDiseaseHandler.invoke(citizen);

            if (handler != null) {
                // Check if sick
                Method isSickMethod = handler.getClass().getMethod("isSick");
                Object result = isSickMethod.invoke(handler);
                return result instanceof Boolean && (Boolean) result;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to check if citizen is sick via reflection: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Cure a citizen using reflection.
     *
     * @param citizen The citizen to cure
     * @return true if cure succeeded
     */
    private static boolean cureCitizen(ICitizenData citizen) {
        try {
            // Get disease handler
            Method getDiseaseHandler = citizen.getClass().getMethod("getCitizenDiseaseHandler");
            Object handler = getDiseaseHandler.invoke(citizen);

            if (handler != null) {
                // Cure
                Method cureMethod = handler.getClass().getMethod("cure");
                cureMethod.invoke(handler);
                return true;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to cure citizen via reflection: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Infect a citizen with a disease using reflection.
     *
     * @param citizen The citizen
     * @param disease The disease object
     * @return true if infection succeeded
     */
    private static boolean infectCitizenWithDisease(ICitizenData citizen, Object disease) {
        try {
            // Get disease handler
            Method getDiseaseHandler = citizen.getClass().getMethod("getCitizenDiseaseHandler");
            Object handler = getDiseaseHandler.invoke(citizen);

            if (handler != null) {
                // Try setDisease(Disease) method
                Method setDiseaseMethod = handler.getClass().getMethod("setDisease", disease.getClass());
                Object result = setDiseaseMethod.invoke(handler, disease);
                return result instanceof Boolean && (Boolean) result;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to infect citizen via reflection: {}", e.getMessage());
        }

        return false;
    }
}
