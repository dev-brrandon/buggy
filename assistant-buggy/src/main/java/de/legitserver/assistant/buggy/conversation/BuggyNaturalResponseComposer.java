package de.legitserver.assistant.buggy.conversation;

import de.legitserver.assistant.buggy.identity.BuggyIdentityCore;
import de.legitserver.assistant.buggy.nativecore.BuggyNativePersonalityBaseline;
import de.legitserver.assistant.buggy.nativecore.BuggyPersonalityAxis;

/** Deterministic G1.6 voice. It uses the immutable native personality baseline and no model provider. */
public final class BuggyNaturalResponseComposer {
    private final boolean friendly;
    private final boolean direct;

    public BuggyNaturalResponseComposer(BuggyNativePersonalityBaseline personality) {
        if (personality == null) throw new IllegalArgumentException("personality required");
        this.friendly = score(personality, BuggyPersonalityAxis.FRIENDLINESS) >= 6_000;
        this.direct = score(personality, BuggyPersonalityAxis.DIRECTNESS) >= 6_000;
    }

    public static BuggyNaturalResponseComposer canonical() {
        return new BuggyNaturalResponseComposer(BuggyNativePersonalityBaseline.canonical());
    }

    public String compose(BuggyConversationContext context, BuggyResponsePlan plan) {
        boolean german = context.language() == BuggyConversationLanguage.GERMAN;
        BuggyResponseAct act = plan.acts().getFirst();
        return german ? german(act) : english(act);
    }

    private String german(BuggyResponseAct act) {
        return switch (act) {
            case GREET -> friendly ? "Moin. Ich bin Buggy. Was liegt an?" : "Hallo. Was liegt an?";
            case IDENTIFY_SELF -> "Ich bin " + BuggyIdentityCore.DISPLAY_NAME
                    + ", der persistente Owner- und Network-Assistant von Legit-Server.de. "
                    + "Meine Identität bleibt stabil; Sicherheitsautorität bleibt bei NetworkCore.";
            case DESCRIBE_CAPABILITIES -> "Ich kann Gespräche nativ einordnen, begrenzten Gesprächskontext verfolgen, "
                    + "Referenzen auflösen und Antworten planen und formulieren. Memory & Development sind vorhanden. "
                    + "Live-Netzwerk-/Serverwissen und lesende Tools kommen mit G1.7; externe Modelle erst mit G1.10.";
            case ANSWER_SOCIAL_STATUS -> friendly
                    ? "Läuft. Mein nativer Gesprächskern ist aktiv, und ich bleibe bei Dingen, die ich belastbar weiß."
                    : "Mein nativer Gesprächskern ist aktiv.";
            case ACKNOWLEDGE_CONTINUATION -> direct
                    ? "Alles klar — ich bleibe beim aktuellen Thema und Kontext."
                    : "Alles klar. Wir bleiben beim aktuellen Thema.";
            case ACKNOWLEDGE_THANKS -> friendly ? "Gerne." : "Verstanden.";
            case SAY_GOODBYE -> friendly ? "Bis später." : "Bis dann.";
            case EXPLAIN_G17_KNOWLEDGE_LIMIT -> "Dazu brauche ich belastbares Live-Netzwerk-, Server- oder Servicewissen. "
                    + "Diese native Read-only-Schicht kommt erst mit G1.7; ich erfinde den aktuellen Zustand nicht.";
            case EXPLAIN_G17_ACTION_LIMIT -> "Diese Anfrage würde eine operative Aktion benötigen. "
                    + "G1.6 führt keine Server-, Service- oder Write-Aktionen aus; dafür ziehe ich keine späteren Tool-Blöcke vor.";
            case ASK_CLARIFICATION -> "Dazu fehlt mir in G1.6 noch ein belastbarer Bezug. "
                    + "Nenne bitte konkret, worauf sich die Frage bezieht.";
            case ACKNOWLEDGE_STATEMENT -> direct ? "Verstanden. Ich halte den Gesprächsbezug fest." : "Okay, verstanden.";
        };
    }

    private String english(BuggyResponseAct act) {
        return switch (act) {
            case GREET -> friendly ? "Hi. I'm Buggy. What do you need?" : "Hello. What do you need?";
            case IDENTIFY_SELF -> "I'm " + BuggyIdentityCore.DISPLAY_NAME
                    + ", Legit-Server.de's persistent owner and network assistant. "
                    + "My identity stays stable; security authority remains with NetworkCore.";
            case DESCRIBE_CAPABILITIES -> "I can natively classify conversations, follow bounded conversation context, "
                    + "resolve references, and plan and compose responses. Memory & Development are available. "
                    + "Live network/server knowledge and read-only tools arrive with G1.7; external models with G1.10.";
            case ANSWER_SOCIAL_STATUS -> friendly
                    ? "Running well. My native conversation core is active, and I stick to what I can support."
                    : "My native conversation core is active.";
            case ACKNOWLEDGE_CONTINUATION -> "Understood — I'll stay with the current topic and context.";
            case ACKNOWLEDGE_THANKS -> friendly ? "You're welcome." : "Understood.";
            case SAY_GOODBYE -> friendly ? "See you later." : "See you.";
            case EXPLAIN_G17_KNOWLEDGE_LIMIT -> "That requires reliable live network, server, or service knowledge. "
                    + "The native read-only layer arrives with G1.7, so I won't invent the current state.";
            case EXPLAIN_G17_ACTION_LIMIT -> "That request requires an operational action. "
                    + "G1.6 performs no server, service, or write actions and does not pull later tool blocks forward.";
            case ASK_CLARIFICATION -> "G1.6 does not yet have a reliable referent for that. Please name what the question refers to.";
            case ACKNOWLEDGE_STATEMENT -> "Understood. I'll keep the conversation context.";
        };
    }

    private static int score(BuggyNativePersonalityBaseline personality, BuggyPersonalityAxis axis) {
        return personality.axes().stream()
                .filter(entry -> entry.axis() == axis)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("missing personality axis " + axis))
                .startBps();
    }
}
