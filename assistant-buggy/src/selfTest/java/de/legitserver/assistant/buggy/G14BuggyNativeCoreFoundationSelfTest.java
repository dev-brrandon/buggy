package de.legitserver.assistant.buggy;

import de.legitserver.assistant.buggy.nativecore.BuggyCoreProtocol;
import de.legitserver.assistant.buggy.nativecore.BuggyMaturityDimension;
import de.legitserver.assistant.buggy.nativecore.BuggyMaturityStage;
import de.legitserver.assistant.buggy.nativecore.BuggyNativeCoreState;
import de.legitserver.assistant.buggy.nativecore.BuggyPersonalityAxis;
import de.legitserver.assistant.buggy.nativecore.BuggySelfCapabilityStatus;
import de.legitserver.assistant.buggy.repository.BuggyNativeCoreRepository;
import de.legitserver.assistant.buggy.service.BuggyNativeCoreService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/** Executable G1.4 contract test: birth, protocols, personality, maturity, self-model and idempotence. */
public final class G14BuggyNativeCoreFoundationSelfTest {
    private static final Instant BIRTH = Instant.parse("2026-10-06T12:00:00Z");

    private G14BuggyNativeCoreFoundationSelfTest() {}

    public static void main(String[] args) {
        InMemoryRepository repository = new InMemoryRepository();
        BuggyNativeCoreService service = new BuggyNativeCoreService(repository);

        BuggyNativeCoreState state = service.initializeFirstActivation(BIRTH);
        require(state.birth().conceptDate().equals(LocalDate.of(2026, 10, 6)), "protected concept date");
        require(state.birth().bornAt().equals(BIRTH), "birth is first Native-Core activation");
        require(state.maturityBaseline().stage() == BuggyMaturityStage.BOOTSTRAP, "born as BOOTSTRAP");
        require(state.protocols().protocols().size() == BuggyCoreProtocol.values().length, "all Core Protocols present");
        require(state.protocols().protocols().contains(BuggyCoreProtocol.AUTHORITY), "Authority Protocol present");
        require(state.protocols().protocols().contains(BuggyCoreProtocol.SELF_IMPROVEMENT), "Self-Improvement Protocol present");

        require(personality(state, BuggyPersonalityAxis.CURIOSITY) == 8_500, "curiosity starts at 85");
        require(personality(state, BuggyPersonalityAxis.CONFIDENCE) == 3_000, "confidence starts at 30");
        require(personality(state, BuggyPersonalityAxis.CAUTION) == 8_500, "caution starts at 85");
        require(personalityMinimum(state, BuggyPersonalityAxis.CAUTION) == 6_000, "caution cannot evolve below 60");
        require(personalityMinimum(state, BuggyPersonalityAxis.TECHNICAL_PRECISION) == 7_000,
                "technical precision cannot evolve below 70");

        require(maturity(state, BuggyMaturityDimension.SECURITY_RELIABILITY) == 10_000,
                "security reliability starts protected/high");
        require(maturity(state, BuggyMaturityDimension.SOCIAL) == 500,
                "Baby-Buggy starts socially inexperienced");

        require(capability(state, "identity.self") == BuggySelfCapabilityStatus.AVAILABLE,
                "self identity available");
        require(capability(state, "conversation.native") == BuggySelfCapabilityStatus.LIMITED,
                "native conversation intentionally limited until G1.6");
        require(capability(state, "model.external") == BuggySelfCapabilityStatus.UNAVAILABLE,
                "external model intentionally unavailable in G1.4");
        require(capability(state, "source.analysis") == BuggySelfCapabilityStatus.UNAVAILABLE,
                "advanced source analysis intentionally unavailable");

        BuggyNativeCoreState second = service.initializeFirstActivation(BIRTH);
        require(second.sha256().equals(state.sha256()), "same first activation is idempotent");
        expectFailure(() -> service.initializeFirstActivation(BIRTH.plusSeconds(1)));
        require(service.loadRequired().sha256().equals(state.sha256()), "different birth cannot rewrite baseline");

        System.out.println("G1.4 BUGGY NATIVE CORE FOUNDATION: PASS");
    }

    private static int personality(BuggyNativeCoreState state, BuggyPersonalityAxis axis) {
        return state.personalityBaseline().axes().stream()
                .filter(entry -> entry.axis() == axis)
                .findFirst()
                .orElseThrow()
                .startBps();
    }

    private static int personalityMinimum(BuggyNativeCoreState state, BuggyPersonalityAxis axis) {
        return state.personalityBaseline().axes().stream()
                .filter(entry -> entry.axis() == axis)
                .findFirst()
                .orElseThrow()
                .minimumBps();
    }

    private static int maturity(BuggyNativeCoreState state, BuggyMaturityDimension dimension) {
        return state.maturityBaseline().entries().stream()
                .filter(entry -> entry.dimension() == dimension)
                .findFirst()
                .orElseThrow()
                .scoreBps();
    }

    private static BuggySelfCapabilityStatus capability(BuggyNativeCoreState state, String capabilityId) {
        return state.capabilities().stream()
                .filter(entry -> entry.capabilityId().equals(capabilityId))
                .findFirst()
                .orElseThrow()
                .status();
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new IllegalStateException("Expected failure");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Assertion failed: " + message);
    }

    private static final class InMemoryRepository implements BuggyNativeCoreRepository {
        private BuggyNativeCoreState state;

        @Override
        public Optional<BuggyNativeCoreState> find() {
            return Optional.ofNullable(state);
        }

        @Override
        public BuggyNativeCoreState initialize(BuggyNativeCoreState requested) {
            if (state == null) {
                state = requested;
                return state;
            }
            if (!state.sha256().equals(requested.sha256())) {
                throw new IllegalStateException("Native-Core baseline is immutable");
            }
            return state;
        }
    }
}
