package com.avimax.backend.dto;

public record ControlCountsResponse(
        long extractorsTotal,
        long extractorsOn,
        long criadorasTotal,
        long criadorasOn,
        long bombasTotal,
        long bombasOn
) {
}
