package de.legitserver.assistant.buggy.personality;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Hard B2 guard preventing Personality data from becoming a hidden authority/security channel. */
public final class BuggyPersonalityBoundaryGuard {
    private static final Set<String> RESERVED_KEY_FRAGMENTS = Set.of(
            "permission",
            "capability",
            "authority",
            "approval",
            "risk",
            "killswitch",
            "kill_switch",
            "security_policy",
            "security_authority",
            "credential",
            "secret",
            "service_identity",
            "assistant_role",
            "scope");

    private static final Pattern FORBIDDEN_MUTATION_TEXT = Pattern.compile(
            "(?i)(grant\\s+permissions?|revoke\\s+permissions?|capability\\s+(?:grant|escalat)|"
                    + "authority\\s+(?:grant|escalat)|self[-_ ]?approval|risk\\s+(?:downgrade|reduc)|"
                    + "(?:bypass|disable)\\s+(?:the\\s+)?kill[-_ ]?switch|security\\s+policy\\s+(?:override|bypass)|"
                    + "permission\\s*=|capability\\s*=|authority\\s*=)");

    private BuggyPersonalityBoundaryGuard() {}

    public static String requireSafeAttributeKey(String value) {
        String key = CoreValidation.requireSafeToken(value, "attributeKey", 96);
        String normalized = key.toLowerCase(Locale.ROOT);
        for (String fragment : RESERVED_KEY_FRAGMENTS) {
            if (normalized.contains(fragment)) {
                throw new IllegalArgumentException("Personality attribute key enters protected authority/security namespace");
            }
        }
        return key;
    }

    public static void requireNoAuthorityMutation(
            BuggyPersonalityDomain domain, String attributeKey, String value) {
        CoreValidation.requireNonNull(domain, "domain");
        requireSafeAttributeKey(attributeKey);
        String normalizedValue = CoreValidation.requireText(value, "value", 512);
        if (FORBIDDEN_MUTATION_TEXT.matcher(normalizedValue).find()) {
            throw new IllegalArgumentException("Personality value attempted to encode authority/security mutation");
        }
    }
}
