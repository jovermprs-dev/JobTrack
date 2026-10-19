import { Offer } from '../core/models';

const dateFmt = new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', year: 'numeric' });
const dateTimeFmt = new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
const shortFmt = new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short' });
const numFmt = new Intl.NumberFormat('es-ES', { maximumFractionDigits: 0 });

/** `YYYY-MM-DD` → fecha local sin desfase de zona horaria. */
function parseDateOnly(value: string): Date {
  const [y, m, d] = value.split('-').map(Number);
  return new Date(y, m - 1, d);
}

export function formatDate(value: string | null | undefined): string {
  return value ? dateFmt.format(parseDateOnly(value)) : '—';
}

export function formatShortDate(value: string): string {
  return shortFmt.format(parseDateOnly(value));
}

export function formatDateTime(value: string | null | undefined): string {
  return value ? dateTimeFmt.format(new Date(value)) : '—';
}

/** Distingue «no indicado» de 0. */
export function formatSalary(offer: Pick<Offer, 'salaryMin' | 'salaryMax' | 'currency'>): string | null {
  const { salaryMin: min, salaryMax: max } = offer;
  const cur = offer.currency ?? '';
  if (min === null && max === null) return null;
  if (min !== null && max !== null) return `${numFmt.format(min)} – ${numFmt.format(max)} ${cur}`.trim();
  if (min !== null) return `Desde ${numFmt.format(min)} ${cur}`.trim();
  return `Hasta ${numFmt.format(max!)} ${cur}`.trim();
}

export function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
