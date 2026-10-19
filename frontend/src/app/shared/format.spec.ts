import { formatDate, formatSalary } from './format';

describe('formatSalary', () => {
  const base = { salaryMin: null, salaryMax: null, currency: null };

  it('devuelve null cuando el salario es desconocido', () => {
    expect(formatSalary(base)).toBeNull();
  });

  it('distingue 0 de desconocido', () => {
    expect(formatSalary({ ...base, salaryMin: 0, currency: 'EUR' })).toBe('Desde 0 EUR');
  });

  it('formatea rangos y límites abiertos', () => {
    expect(formatSalary({ salaryMin: 30000, salaryMax: 40000, currency: 'EUR' })).toContain('–');
    expect(formatSalary({ ...base, salaryMax: 50000, currency: 'EUR' })).toMatch(/^Hasta 50\.?000 EUR$/);
  });
});

describe('formatDate', () => {
  it('no desplaza el día por la zona horaria', () => {
    expect(formatDate('2026-03-01')).toContain('1');
    expect(formatDate('2026-03-01')).toContain('2026');
  });
  it('muestra un guion cuando no hay fecha', () => {
    expect(formatDate(null)).toBe('—');
  });
});
