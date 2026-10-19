import { HttpErrorResponse } from '@angular/common/http';
import { ProblemDetail } from './models';

export interface ApiError {
  status: number;
  message: string;
  fieldErrors: Record<string, string>;
}

/** Traduce cualquier error HTTP a un mensaje comprensible y errores por campo. */
export function toApiError(error: unknown): ApiError {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return { status: 0, message: 'No se pudo conectar con el servidor. Comprueba tu conexión e inténtalo de nuevo.', fieldErrors: {} };
    }
    const problem = (typeof error.error === 'object' && error.error ? error.error : {}) as Partial<ProblemDetail>;
    const fieldErrors: Record<string, string> = {};
    for (const e of problem.errors ?? []) {
      fieldErrors[e.field] ??= e.message;
    }
    const message =
      problem.detail ??
      (error.status === 401
        ? 'Tu sesión ha caducado. Inicia sesión de nuevo.'
        : error.status === 403
          ? 'No tienes permiso para realizar esta acción.'
          : error.status === 404
            ? 'No se encontró el recurso solicitado.'
            : error.status >= 500
              ? 'Se ha producido un error en el servidor. Inténtalo más tarde.'
              : 'No se pudo completar la operación.');
    return { status: error.status, message, fieldErrors };
  }
  return { status: -1, message: 'Se ha producido un error inesperado.', fieldErrors: {} };
}
