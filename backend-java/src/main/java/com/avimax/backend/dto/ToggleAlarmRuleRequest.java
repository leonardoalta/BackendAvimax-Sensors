package com.avimax.backend.dto;

import jakarta.validation.constraints.NotNull;

public record ToggleAlarmRuleRequest(
        @NotNull Boolean activa
) {
}
