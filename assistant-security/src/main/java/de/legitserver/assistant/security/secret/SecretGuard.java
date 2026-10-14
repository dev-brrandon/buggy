package de.legitserver.assistant.security.secret;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Central bounded secret detector/redactor used for both input and output handling. */
public final class SecretGuard {
    private static final int MAX_INPUT_CHARS = 1_000_000;
    private static final String REDACTED = "[REDACTED]";
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "-----BEGIN (?:OPENSSH |RSA |EC |DSA )?PRIVATE KEY-----.*?-----END (?:OPENSSH |RSA |EC |DSA )?PRIVATE KEY-----",
            Pattern.DOTALL);
    private static final Pattern JWT = Pattern.compile("(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}(?![A-Za-z0-9_-])");
    private static final Pattern BEARER = Pattern.compile("(?i)(Authorization\\s*:\\s*Bearer\\s+)[A-Za-z0-9._~+/-]{12,}={0,2}");
    private static final Pattern URL_CREDENTIAL = Pattern.compile("([A-Za-z][A-Za-z0-9+.-]*://[^\\s:/@]+:)[^\\s@/]+(@)");
    private static final Pattern NAMED_SECRET = Pattern.compile(
            "(?i)(\\b(?:password|passwd|api[_-]?key|access[_-]?token|refresh[_-]?token|secret|totp[_-]?secret|session[_-]?cookie)\\b\\s*[:=]\\s*)([^\\s,;]{4,})");

    public SecretScanResult redact(String input) {
        if (input == null) throw new IllegalArgumentException("input must not be null");
        if (input.length() > MAX_INPUT_CHARS) {
            throw new IllegalArgumentException("input exceeds secret-guard limit");
        }
        String output = input;
        EnumSet<SecretKind> kinds = EnumSet.noneOf(SecretKind.class);
        Replacement privateKey = replaceAll(output, PRIVATE_KEY, REDACTED);
        output = privateKey.value();
        if (privateKey.changed()) kinds.add(SecretKind.PRIVATE_KEY);
        Replacement jwt = replaceAll(output, JWT, REDACTED);
        output = jwt.value();
        if (jwt.changed()) kinds.add(SecretKind.JWT);
        Replacement bearer = replaceGroup(output, BEARER, 1, 0);
        output = bearer.value();
        if (bearer.changed()) kinds.add(SecretKind.BEARER_TOKEN);
        Replacement url = replaceUrlCredential(output);
        output = url.value();
        if (url.changed()) kinds.add(SecretKind.URL_CREDENTIAL);
        Replacement named = replaceGroup(output, NAMED_SECRET, 1, 0);
        output = named.value();
        if (named.changed()) kinds.add(SecretKind.NAMED_SECRET);
        return new SecretScanResult(output, Set.copyOf(kinds));
    }

    private static Replacement replaceAll(String input, Pattern pattern, String replacement) {
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) return new Replacement(input, false);
        return new Replacement(matcher.replaceAll(Matcher.quoteReplacement(replacement)), true);
    }

    private static Replacement replaceGroup(String input, Pattern pattern, int prefixGroup, int ignored) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer buffer = new StringBuffer();
        boolean changed = false;
        while (matcher.find()) {
            changed = true;
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(prefixGroup) + REDACTED));
        }
        matcher.appendTail(buffer);
        return new Replacement(buffer.toString(), changed);
    }

    private static Replacement replaceUrlCredential(String input) {
        Matcher matcher = URL_CREDENTIAL.matcher(input);
        StringBuffer buffer = new StringBuffer();
        boolean changed = false;
        while (matcher.find()) {
            changed = true;
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1) + REDACTED + matcher.group(2)));
        }
        matcher.appendTail(buffer);
        return new Replacement(buffer.toString(), changed);
    }

    private record Replacement(String value, boolean changed) {}
}
