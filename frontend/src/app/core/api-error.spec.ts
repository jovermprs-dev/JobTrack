import { HttpErrorResponse } from '@angular/common/http';
import { toApiError } from './api-error';

describe('toApiError', () => {
  it('usa el detalle del servidor y recoge errores por campo', () => {
    const err = new HttpErrorResponse({
      status: 400,
      error: { detail: 'Los datos enviados no son válidos.', errors: [{ field: 'title', message: 'obligatorio' }, { field: 'title', message: 'otro' }] },
    });
    const result = toApiError(err);
    expect(result.message).toBe('Los datos enviados no son válidos.');
    expect(result.fieldErrors).toEqual({ title: 'obligatorio' });
  });

  it('explica la falta de conexión (status 0)', () => {
    expect(toApiError(new HttpErrorResponse({ status: 0 })).message).toContain('No se pudo conectar');
  });

  it('da un mensaje genérico para errores 5xx sin cuerpo', () => {
    expect(toApiError(new HttpErrorResponse({ status: 503 })).message).toContain('error en el servidor');
  });

  it('no falla con errores desconocidos', () => {
    expect(toApiError(new Error('x')).message).toContain('inesperado');
  });
});
