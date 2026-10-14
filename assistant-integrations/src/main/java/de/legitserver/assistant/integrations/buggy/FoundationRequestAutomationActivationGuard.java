package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.buggy.automation.BuggyAutomationActivationGuard;
import de.legitserver.assistant.buggy.automation.BuggyAutomationRevision;
import de.legitserver.assistant.requests.model.RequestState;
import de.legitserver.assistant.requests.repository.RequestRepository;
import java.time.Clock;
import java.util.Objects;

/** Validates privileged automation activation against the existing Foundation Request/Approval aggregate. */
public final class FoundationRequestAutomationActivationGuard implements BuggyAutomationActivationGuard {
    public static final String REQUEST_TYPE = "BUGGY_AUTOMATION_ACTIVATION";
    private final RequestRepository requests;
    private final Clock clock;

    public FoundationRequestAutomationActivationGuard(RequestRepository requests, Clock clock) {
        this.requests = Objects.requireNonNull(requests, "requests");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean approved(BuggyAutomationRevision automation) {
        Objects.requireNonNull(automation, "automation");
        if (automation.activationRequestId() == null) return false;
        return requests.findById(automation.activationRequestId())
                .filter(request -> REQUEST_TYPE.equals(request.requestType()))
                .filter(request -> request.state() == RequestState.APPROVED || request.state() == RequestState.EXECUTING
                        || request.state() == RequestState.EXECUTED)
                .filter(request -> !request.isExpiredAt(clock.instant()))
                .filter(request -> automation.riskLevel().coveredBy(request.riskLevel()))
                .isPresent();
    }
}
