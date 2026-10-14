package de.legitserver.assistant.buggy.tools;

/** Secret handling expectation at the Buggy catalog boundary. */
public enum BuggyToolSecretHandling {
    REJECT_SECRET_INPUT_AND_REDACT_OUTPUT,
    METADATA_ONLY_AND_REDACT_OUTPUT
}
