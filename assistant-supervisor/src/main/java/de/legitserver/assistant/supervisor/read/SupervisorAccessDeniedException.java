package de.legitserver.assistant.supervisor.read;

/** Safe fail-closed Supervisor read denial. */
public final class SupervisorAccessDeniedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SupervisorAccessDeniedException() {
        super("SUPERVISOR_ACCESS_DENIED");
    }
}
