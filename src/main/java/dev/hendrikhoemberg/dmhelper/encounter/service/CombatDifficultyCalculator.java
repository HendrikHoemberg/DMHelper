package dev.hendrikhoemberg.dmhelper.encounter.service;

import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantDto;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CombatDifficultyCalculator {

    public record DifficultyResult(String rating, int adjustedXp, int partyThreshold, String details) {}

    // XP thresholds per character level for Moderate difficulty.
    // Source: 2014 DMG pg. 82 "Encounter Difficulty XP Per Character" table.
    // TODO: Replace with 2024 DMG values when available from authoritative source (open5e srd-2024 or SRD 5.2) per §2.3.8.
    // The 2024 DMG uses Low/Moderate/High categories instead of Easy/Medium/Hard/Deadly.
    // Currently using 2014 Hard threshold as a reasonable approximation for prep-time guidance.
    private static final int[] MODERATE_XP = {50, 100, 150, 250, 500, 600, 750, 900, 1100, 1200, 1600, 2000, 2200, 2500, 2800, 3200, 3900, 4100, 4900, 5700};
    // High uses 2× Moderate as a rough heuristic
    private static final int HIGH_MULTIPLIER = 2;

    private static final Pattern LEVEL_PATTERN = Pattern.compile("(\\d+)\\s*$");

    private final StatBlockRepository statBlockRepo;

    public CombatDifficultyCalculator(StatBlockRepository statBlockRepo) {
        this.statBlockRepo = statBlockRepo;
    }

    public DifficultyResult calculate(List<PartyMember> party, List<CombatantDto> monsters) {
        if (party.isEmpty() || monsters.isEmpty()) {
            return new DifficultyResult("N/A", 0, 0, "No party or monsters");
        }

        int partyThreshold = computePartyThreshold(party);
        int totalXp = computeTotalMonsterXp(monsters);

        String rating;
        if (totalXp >= partyThreshold * HIGH_MULTIPLIER) {
            rating = "HIGH";
        } else if (totalXp >= partyThreshold) {
            rating = "MODERATE";
        } else {
            rating = "LOW";
        }

        return new DifficultyResult(rating, totalXp, partyThreshold,
                "Party threshold: " + partyThreshold + " XP, Monster total: " + totalXp + " XP");
    }

    private int computePartyThreshold(List<PartyMember> party) {
        int total = 0;
        for (PartyMember pm : party) {
            int level = extractLevel(pm.getClassAndLevel());
            int idx = Math.max(0, Math.min(level - 1, 19));
            total += MODERATE_XP[idx];
        }
        return total;
    }

    private int extractLevel(String classAndLevel) {
        if (classAndLevel == null || classAndLevel.isEmpty()) return 1;
        int totalLevel = 0;
        String[] parts = classAndLevel.split("/");
        for (String part : parts) {
            String trimmed = part.trim();
            Matcher m = LEVEL_PATTERN.matcher(trimmed);
            if (m.find()) {
                totalLevel += Integer.parseInt(m.group(1));
            }
        }
        return Math.max(1, totalLevel);
    }

    private int computeTotalMonsterXp(List<CombatantDto> monsters) {
        return monsters.stream().mapToInt(m -> {
            if (m.statBlockId() != null) {
                return statBlockRepo.findById(m.statBlockId())
                        .map(sb -> sb.getXp() > 0 ? sb.getXp() : estimateXpFromCr(sb.getCr()))
                        .orElse(200);
            }
            return 200;
        }).sum();
    }

    private int estimateXpFromCr(String cr) {
        if (cr == null || cr.isBlank()) return 200;
        return switch (cr.trim()) {
            case "0" -> 10;
            case "1/8" -> 25;
            case "1/4" -> 50;
            case "1/2" -> 100;
            case "1" -> 200;
            case "2" -> 450;
            case "3" -> 700;
            case "4" -> 1100;
            case "5" -> 1800;
            case "6" -> 2300;
            case "7" -> 2900;
            case "8" -> 3900;
            case "9" -> 5000;
            case "10" -> 5900;
            case "11" -> 7200;
            case "12" -> 8400;
            case "13" -> 10000;
            case "14" -> 11500;
            case "15" -> 13000;
            case "16" -> 15000;
            case "17" -> 18000;
            case "18" -> 20000;
            case "19" -> 22000;
            case "20" -> 25000;
            case "21" -> 33000;
            case "22" -> 41000;
            case "23" -> 50000;
            case "24" -> 62000;
            case "25" -> 75000;
            case "26" -> 90000;
            case "27" -> 105000;
            case "28" -> 120000;
            case "29" -> 135000;
            case "30" -> 155000;
            default -> 200;
        };
    }
}
