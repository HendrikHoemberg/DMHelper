package dev.hendrikhoemberg.dmhelper.campaign.readiness;

public enum ReadinessCategory {
    ENCOUNTER(
            "Encounters",
            "Hostile scenes need a runnable encounter.",
            "Prepare encounter",
            "Run without encounter"),
    STATBLOCK(
            "Participants",
            "Hostile participants need resolvable statblocks.",
            "Review participants",
            "Run without statblocks"),
    MAP(
            "Maps",
            "Required scenes need a playable or reference map.",
            "Add map",
            "Run without map"),
    RUNTIME_LINK(
            "Runtime links",
            "Linked content should resolve at the table.",
            "Open preparation",
            "Accept blocker"),
    OMISSION(
            "Omissions",
            "Declared omissions should be intentional.",
            "Open preparation",
            "Accept blocker"),
    NEXT_ACTION(
            "Next actions",
            "Preparation steps remain before play.",
            "Open preparation",
            "Accept blocker");

    private final String label;
    private final String summary;
    private final String repairActionLabel;
    private final String acceptanceActionLabel;

    ReadinessCategory(String label, String summary, String repairActionLabel,
                      String acceptanceActionLabel) {
        this.label = label;
        this.summary = summary;
        this.repairActionLabel = repairActionLabel;
        this.acceptanceActionLabel = acceptanceActionLabel;
    }

    public String label() {
        return label;
    }

    public String summary() {
        return summary;
    }

    public String repairActionLabel() {
        return repairActionLabel;
    }

    public String acceptanceActionLabel() {
        return acceptanceActionLabel;
    }
}
