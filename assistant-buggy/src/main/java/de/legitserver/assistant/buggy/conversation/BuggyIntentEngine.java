package de.legitserver.assistant.buggy.conversation;

import java.util.Set;

/** Framework-free G1.6 intent classifier. It deliberately does not infer tool permissions or authorization. */
public final class BuggyIntentEngine {
    public BuggyIntentClassification classify(BuggyConversationContext context) {
        String text = BuggyConversationText.normalized(context.currentMessage());
        Set<String> words = BuggyConversationText.words(text);

        if (isContinuation(text)) return result(BuggyConversationIntent.CONTINUATION, 9_800, "continuation phrase");
        if (isGreeting(text)) return result(BuggyConversationIntent.GREETING, 9_800, "greeting phrase");
        if (BuggyConversationText.containsAny(text, "tschüss", "tschuss", "bis später", "bis spaeter", "goodbye", "bye")) {
            return result(BuggyConversationIntent.FAREWELL, 9_700, "farewell phrase");
        }
        if (BuggyConversationText.containsAny(text, "vielen dank", "danke", "thank you", "thanks")) {
            return result(BuggyConversationIntent.THANKS, 9_600, "thanks phrase");
        }
        if (BuggyConversationText.containsAny(text,
                "wer bist du", "was bist du", "wie heißt du", "wie heisst du", "dein name", "who are you", "what are you", "your name")) {
            return result(BuggyConversationIntent.IDENTITY_QUERY, 9_700, "self identity query");
        }
        if (BuggyConversationText.containsAny(text,
                "was kannst du", "was kannst du jetzt", "deine fähigkeiten", "deine faehigkeiten", "was beherrschst du",
                "what can you do", "your capabilities", "your abilities")) {
            return result(BuggyConversationIntent.CAPABILITY_QUERY, 9_600, "capability query");
        }
        if (BuggyConversationText.containsAny(text,
                "wie geht es dir", "wie gehts dir", "wie geht's dir", "wie fühlst du dich", "wie fuehlst du dich", "how are you")) {
            return result(BuggyConversationIntent.SOCIAL_STATUS_QUERY, 9_400, "social status query");
        }
        if (isActionRequest(text, words)) {
            return result(BuggyConversationIntent.ACTION_REQUEST, 9_000, "operational action phrase");
        }
        if (isQuestion(text, words)) {
            return result(BuggyConversationIntent.INFORMATION_QUERY, 8_200, "question form");
        }
        return result(BuggyConversationIntent.STATEMENT, 7_500, "declarative fallback");
    }

    private static boolean isContinuation(String text) {
        String stripped = text.replaceAll("[.!?]+$", "").strip();
        return Set.of(
                "weiter", "mach weiter", "okay weiter", "ok weiter", "dann weiter", "okay dann weiter", "ok dann weiter",
                "weiter gehts", "weiter geht's", "continue", "go on", "keep going").contains(stripped);
    }

    private static boolean isGreeting(String text) {
        String stripped = text.replaceAll("[.!?]+$", "").strip();
        return Set.of(
                "hallo", "hi", "hey", "moin", "servus", "guten morgen", "guten tag", "guten abend",
                "hello", "good morning", "good afternoon", "good evening").contains(stripped);
    }

    private static boolean isActionRequest(String text, Set<String> words) {
        if (BuggyConversationText.hasAnyWord(words,
                "starte", "start", "stoppe", "stop", "restart", "restarte", "deploy", "deploye", "installiere", "install",
                "lösche", "loesche", "delete", "ändere", "aendere", "change", "execute", "run", "sende", "send")) {
            return true;
        }
        return BuggyConversationText.containsAny(text,
                "kannst du starten", "kannst du stoppen", "kannst du neustarten", "can you start", "can you stop",
                "can you restart", "führ aus", "fuehr aus");
    }

    private static boolean isQuestion(String text, Set<String> words) {
        if (text.endsWith("?")) return true;
        return BuggyConversationText.hasAnyWord(words,
                "was", "wer", "wie", "wo", "wann", "warum", "wieso", "weshalb", "welche", "welcher", "welches",
                "what", "who", "how", "where", "when", "why", "which");
    }

    private static BuggyIntentClassification result(BuggyConversationIntent intent, int confidence, String evidence) {
        return new BuggyIntentClassification(intent, confidence, evidence);
    }
}
