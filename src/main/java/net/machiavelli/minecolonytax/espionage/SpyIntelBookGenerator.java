package net.machiavelli.minecolonytax.espionage;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class SpyIntelBookGenerator {

    private static final String SEP = "--------------------";

    public static ItemStack createIntelReport(SpyMission mission, String targetColonyName) {
        SpyIntelData intel = mission.getMissionIntel();
        if (intel == null || "KILLED".equals(mission.getStatus())) {
            return null;
        }

        List<String> pages = new ArrayList<>();
        pages.add(buildCoverPage(mission, targetColonyName));

        if (intel.isEarlyAvailable()) {
            pages.add(buildObservationsPage(intel, mission));
        }
        if (intel.isMidAvailable()) {
            pages.add(buildTacticalPage(intel));
        }
        if (intel.isLateAvailable()) {
            pages.addAll(buildDossierPages(intel));
        }
        if (intel.isEarlyAvailable()) {
            pages.add(buildAssessmentPage(intel));
        }

        pages.add("[ Notes ]\n" + SEP + "\n\n\n\n\n\n\n\n\n\n");

        ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
        CompoundTag tag = new CompoundTag();

        ListTag pagesNbt = new ListTag();
        for (String page : pages) {
            pagesNbt.add(StringTag.valueOf(page));
        }
        tag.put("pages", pagesNbt);
        book.setTag(tag);

        book.setHoverName(Component.literal("Intelligence: " + targetColonyName)
                .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.DARK_GRAY)));

        return book;
    }

    private static String buildCoverPage(SpyMission mission, String targetColonyName) {
        long totalMs = System.currentTimeMillis() - mission.getStartTime();
        long hours = totalMs / 3600000L;
        long mins  = (totalMs % 3600000L) / 60000L;
        String duration = hours > 0 ? hours + "h " + mins + "m" : mins + "m";

        double dx = mission.getDestX() - mission.getSourceX();
        double dz = mission.getDestZ() - mission.getSourceZ();
        int distance = (int) Math.sqrt(dx * dx + dz * dz);
        String dir = getCardinalDirection(dx, dz);

        String opLabel = switch (mission.getMissionType()) {
            case "SCOUT"         -> "Reconnaissance";
            case "SABOTAGE"      -> "Sabotage";
            case "BRIBE"         -> "Bribery";
            case "STEAL_SECRETS" -> "Intel Theft";
            default              -> mission.getMissionType();
        };

        String outcome = switch (mission.getStatus()) {
            case "COMPLETED" -> "Returned safely";
            case "ESCAPED"   -> "Escaped - pursued";
            case "RECALLED"  -> "Recalled by command";
            default          -> mission.getStatus();
        };

        int tier = mission.getMissionIntel() != null ? mission.getMissionIntel().getIntelTier() : 0;
        String tierLabel = switch (tier) {
            case 1  -> "Preliminary";
            case 2  -> "Considerable";
            case 3  -> "Thorough";
            default -> "None gathered";
        };

        return "Intelligence: " + targetColonyName + "\n"
                + SEP + "\n\n"
                + "Settlement: " + targetColonyName + "\n"
                + "Location:   " + mission.getDestX() + ", " + mission.getDestZ() + "\n"
                + "Distance:   " + distance + " bl " + dir + "\n"
                + "Operation:  " + opLabel + "\n"
                + "Outcome:    " + outcome + "\n"
                + "Knowledge:  " + tierLabel + "\n"
                + "Time:       " + duration + "\n\n"
                + "Handle with discretion.\n"
                + "Add notes on the final\n"
                + "page before signing.";
    }

    private static String buildObservationsPage(SpyIntelData intel, SpyMission mission) {
        String name = intel.getTargetColonyName() != null ? intel.getTargetColonyName() : "The settlement";
        StringBuilder sb = new StringBuilder();
        sb.append("Field Observations\n").append(SEP).append("\n\n");

        if (intel.getCitizenCount() >= 0 && intel.getBuildingCount() >= 0) {
            String size = intel.getCitizenCount() >= 20 ? "large"
                    : intel.getCitizenCount() >= 10 ? "modest" : "small";
            sb.append(name).append(" is a ").append(size)
              .append(" settlement.\nOur agent counted ")
              .append(intel.getCitizenCount()).append(" souls\ndwelling in ")
              .append(intel.getBuildingCount()).append(" structures.\n\n");
        } else if (intel.getCitizenCount() >= 0) {
            sb.append("Agent counted ").append(intel.getCitizenCount())
              .append(" souls within\nthe settlement.\n\n");
        } else if (intel.getBuildingCount() >= 0) {
            sb.append("The settlement contains\n")
              .append(intel.getBuildingCount()).append(" structures.\n\n");
        }

        sb.append("Conflict:\n");
        if (intel.isAtWar()) {
            sb.append("The settlement is at war.\nTheir forces may be\nstretched thin.");
        } else {
            sb.append("No active conflict was\nobserved. The settlement\nstands at peace.");
        }

        return sb.toString();
    }

    private static String buildTacticalPage(SpyIntelData intel) {
        StringBuilder sb = new StringBuilder();
        sb.append("Military & Wealth\n").append(SEP).append("\n\n");

        sb.append("Defences:\n");
        if (intel.getGuardCount() >= 0) {
            if (intel.getCitizenCount() > 0) {
                double ratio = (double) intel.getGuardCount() / intel.getCitizenCount() * 100;
                if (ratio >= 30) {
                    sb.append(intel.getGuardCount()).append(" guards - heavily\nfortified. Caution advised.");
                } else if (ratio >= 15) {
                    sb.append(intel.getGuardCount()).append(" guards - well\ndefended.");
                } else {
                    sb.append("Only ").append(intel.getGuardCount()).append(" guards -\nlightly defended.");
                }
            } else {
                sb.append(intel.getGuardCount()).append(" guards observed.");
            }
        } else {
            sb.append("Could not be determined.");
        }
        sb.append("\n\n");

        sb.append("Morale:\n");
        if (intel.getHappiness() >= 0) {
            double h = intel.getHappiness();
            sb.append(String.format("%.1f", h)).append("/10 - ");
            if (h >= 8)      sb.append("Thriving.\nHigh spirits bolster their defence.");
            else if (h >= 5) sb.append("Content.\nMorale is adequate.");
            else if (h >= 3) sb.append("Strained.\nResolve may falter under pressure.");
            else             sb.append("Revolt brewing.\nRipe for exploitation.");
        } else {
            sb.append("Could not be assessed.");
        }
        sb.append("\n\n");

        sb.append("Treasury:\n");
        if (intel.getTaxBalance() >= 0) {
            int t = intel.getTaxBalance();
            sb.append(t).append(" coins - ");
            if (t >= 10000)     sb.append("Overflowing\nwith coin. Wealthy target.");
            else if (t >= 5000) sb.append("Prosperous.");
            else if (t >= 1000) sb.append("Adequately funded.");
            else if (t > 0)     sb.append("Strained finances.");
            else                sb.append("Coffers are bare.");
        } else {
            sb.append("Could not be obtained.");
        }

        return sb.toString();
    }

    private static List<String> buildDossierPages(SpyIntelData intel) {
        List<String> pages = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        sb.append("The Ruling Hand\n").append(SEP).append("\n\n");

        String ownerName = intel.getOwnerName() != null ? intel.getOwnerName() : "Unknown";
        sb.append("Lord:    ").append(ownerName).append("\n");
        if (intel.getOwnerSdmBalance() >= 0) {
            sb.append("Coin:    ").append(intel.getOwnerSdmBalance()).append(" coins\n\n");
        } else {
            sb.append("Coin:    (not available)\n\n");
        }

        if (intel.getOfficerFinances() != null && !intel.getOfficerFinances().isEmpty()) {
            sb.append("Officers:\n");
            for (SpyIntelData.OfficerFinanceInfo o : intel.getOfficerFinances()) {
                sb.append("  ").append(o.playerName).append("\n");
                if (o.sdmBalance >= 0) {
                    sb.append("    ").append(o.sdmBalance).append(" coins\n");
                } else {
                    sb.append("    (offline)\n");
                }
            }
        } else {
            sb.append("No officers identified.");
        }
        pages.add(sb.toString());

        if (intel.getColonyMembers() != null && !intel.getColonyMembers().isEmpty()) {
            StringBuilder rp = new StringBuilder();
            rp.append("Known Members\n").append(SEP).append("\n");
            rp.append("(* = present)\n\n");

            int count = 0;
            for (SpyIntelData.ColonyMemberInfo m : intel.getColonyMembers()) {
                if (count > 0 && count % 7 == 0) {
                    pages.add(rp.toString());
                    rp = new StringBuilder();
                    rp.append("Known Members (cont.)\n").append(SEP).append("\n\n");
                }
                boolean isOwner   = "Owner".equals(m.rankName);
                boolean isOfficer = "Officer".equals(m.rankName);
                String suffix = isOwner ? " (Lord)" : isOfficer ? " (Officer)" : "";
                rp.append(m.playerName).append(suffix);
                if (m.isOnline) rp.append(" *");
                rp.append("\n");
                count++;
            }

            long online = intel.getColonyMembers().stream().filter(m -> m.isOnline).count();
            int total   = intel.getColonyMembers().size();
            rp.append("\n").append(total).append(" members total");
            if (online > 0) rp.append(", ").append(online).append(" present");
            pages.add(rp.toString());
        }

        return pages;
    }

    private static String buildAssessmentPage(SpyIntelData intel) {
        int threatScore = 0;
        List<String> findings = new ArrayList<>();

        if (intel.isEarlyAvailable()) {
            if (intel.getCitizenCount() >= 20) {
                threatScore += 2;
                findings.add("Large population (" + intel.getCitizenCount() + ")");
            } else if (intel.getCitizenCount() >= 10) {
                threatScore += 1;
                findings.add("Mid-size (" + intel.getCitizenCount() + " souls)");
            } else if (intel.getCitizenCount() >= 0) {
                findings.add("Small (" + intel.getCitizenCount() + " souls)");
            }
            if (intel.isAtWar()) findings.add("At war - forces divided");
        }

        if (intel.isMidAvailable()) {
            if (intel.getGuardCount() >= 10) {
                threatScore += 3;
                findings.add("Heavy guard (" + intel.getGuardCount() + ")");
            } else if (intel.getGuardCount() >= 5) {
                threatScore += 2;
                findings.add("Moderate guard (" + intel.getGuardCount() + ")");
            } else if (intel.getGuardCount() >= 0) {
                threatScore += 1;
                findings.add("Light guard (" + intel.getGuardCount() + ")");
            }
            if (intel.getHappiness() >= 0 && intel.getHappiness() < 3)
                findings.add("Citizenry in revolt");
            if (intel.getTaxBalance() >= 5000) {
                threatScore += 1;
                findings.add("Well-funded (" + intel.getTaxBalance() + " coins)");
            } else if (intel.getTaxBalance() >= 0 && intel.getTaxBalance() < 500)
                findings.add("Financially strained");
        }

        String threatLabel   = threatScore >= 5 ? "HIGH" : threatScore >= 3 ? "MODERATE" : "LOW";
        String threatContext = threatScore >= 5
                ? "Exercise caution. Thorough preparation is warranted."
                : threatScore >= 3
                ? "A capable opponent. Strike with adequate force."
                : "Should yield without great resistance.";

        StringBuilder sb = new StringBuilder();
        sb.append("Our Assessment\n").append(SEP).append("\n\n");
        sb.append("Threat: ").append(threatLabel).append("\n");
        sb.append(threatContext).append("\n\n");

        if (!findings.isEmpty()) {
            sb.append("Of note:\n");
            for (String f : findings) sb.append("- ").append(f).append("\n");
        }

        sb.append("\nYour agent has served\nfaithfully.");
        return sb.toString();
    }

    private static String getCardinalDirection(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        if (angle < 0) angle += 360;
        if (angle < 22.5 || angle >= 337.5) return "(E)";
        if (angle < 67.5)  return "(SE)";
        if (angle < 112.5) return "(S)";
        if (angle < 157.5) return "(SW)";
        if (angle < 202.5) return "(W)";
        if (angle < 247.5) return "(NW)";
        if (angle < 292.5) return "(N)";
        return "(NE)";
    }
}
