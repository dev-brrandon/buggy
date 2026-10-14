package de.legitserver.assistant.buggy.exclusive;

import java.util.EnumSet;
import java.util.Set;

/** Canonical B10 inventory. Self-Development and Decision Memory were implemented earlier in B7/B4. */
public final class BuggyExclusiveFeatureCatalog {
    public static final String CATALOG_ID = "buggy-exclusive-features-v1";
    private static final Set<BuggyExclusiveFeature> FEATURES = Set.copyOf(EnumSet.allOf(BuggyExclusiveFeature.class));

    private BuggyExclusiveFeatureCatalog() {}

    public static Set<BuggyExclusiveFeature> features() {
        return FEATURES;
    }
}
