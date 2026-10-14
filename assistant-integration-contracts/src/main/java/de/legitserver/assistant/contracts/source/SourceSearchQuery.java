package de.legitserver.assistant.contracts.source;

import de.legitserver.assistant.contracts.common.ContractValidation;

public record SourceSearchQuery(String query, SourcePath within, int limit) {
    public SourceSearchQuery {
        query = ContractValidation.text(query, "query", 500);
        within = within == null ? SourcePath.root() : within;
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be 1..200");
    }
}
