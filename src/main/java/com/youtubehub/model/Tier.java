package com.youtubehub.model;

// How much you care about a channel. Replaces the old 1–10 priority number.
//
// Behind the scenes each tier is a level (1–4), saved in the channels table's "priority" column.
// The Subscriptions ranking treats each level as "this many hours newer" (the Settings tab's
// "hours per tier step"), so higher tiers float up, but a brand-new Desperate video can still
// beat a weeks-old Favorite one.
public enum Tier {
    DESPERATE(1, "Desperate"),
    GOOD_FILLER(2, "Good filler"),
    HIGH(3, "High"),
    FAVORITE(4, "Favorite");

    private final int level;
    private final String label;

    Tier(int level, String label) {
        this.level = level;
        this.label = label;
    }

    public int level() {
        return level;
    }

    // The CSS class for this tier's coloured badge, e.g. "tier-good-filler"
    public String styleClass() {
        return "tier-" + name().toLowerCase().replace('_', '-');
    }

    // Shown in dropdowns and badges ("Good filler", not "GOOD_FILLER")
    @Override
    public String toString() {
        return label;
    }

    // Level number -> tier. Anything above 4 counts as Favorite, anything below 1 as Desperate
    // (so old 1–10 priorities still land somewhere sensible).
    public static Tier fromLevel(int level) {
        if (level >= FAVORITE.level) {
            return FAVORITE;
        }
        if (level <= DESPERATE.level) {
            return DESPERATE;
        }
        for (Tier tier : values()) {
            if (tier.level == level) {
                return tier;
            }
        }
        return DESPERATE; // can't happen, but Java wants a return
    }

    // Highest first, for dropdowns
    public static Tier[] highestFirst() {
        return new Tier[] {FAVORITE, HIGH, GOOD_FILLER, DESPERATE};
    }
}
