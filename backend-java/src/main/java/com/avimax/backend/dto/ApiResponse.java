package com.avimax.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Envuelto genérico para todas las respuestas REST.
 * Garantiza compatibilidad con clientes que esperan {"data": [...]}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        T data,
        String error,
        int status
) {
    /**
     * Respuesta exitosa con datos
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, null, 200);
    }

    /**
     * Respuesta sin contenido
     */
    public static <T> ApiResponse<T> noContent() {
        return new ApiResponse<>(null, null, 204);
    }

    /**
     * Respuesta de error
     */
    public static <T> ApiResponse<T> error(String message, int status) {
        return new ApiResponse<>(null, message, status);
    }
}
