package com.jobtrack.common;

/** Recurso inexistente o que no pertenece al usuario autenticado (ambos casos se responden igual). */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
