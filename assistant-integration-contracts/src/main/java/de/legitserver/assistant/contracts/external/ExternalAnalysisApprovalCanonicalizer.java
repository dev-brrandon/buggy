package de.legitserver.assistant.contracts.external;

import de.legitserver.assistant.contracts.common.ContractValidation;

/** Canonical Phase-9 payload for one exact outbound analysis manifest. */
public final class ExternalAnalysisApprovalCanonicalizer {
    public static final String CONTENT_TYPE = "application/vnd.legit.external-analysis+text;v=1";

    private ExternalAnalysisApprovalCanonicalizer() {}

    public static String canonicalPayload(String analysisType, PayloadManifest manifest) {
        return "LEGIT_EXTERNAL_ANALYSIS_V1\n"
                + "analysisType=" + analysisType + "\n"
                + "manifestSchema=" + manifest.schemaVersion() + "\n"
                + "manifestSha256=" + manifest.aggregateSha256() + "\n"
                + "totalBytes=" + manifest.totalBytes() + "\n";
    }

    public static String payloadSha256(String analysisType, PayloadManifest manifest) {
        return ContractValidation.sha256Of(canonicalPayload(analysisType, manifest));
    }
}
