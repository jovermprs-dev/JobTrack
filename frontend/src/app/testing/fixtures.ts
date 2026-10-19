import { Offer, Page, Stats } from '../core/models';

export function makeOffer(overrides: Partial<Offer> = {}): Offer {
  return {
    id: 'o1',
    title: 'Java Developer',
    company: 'Acme',
    description: null,
    url: null,
    location: 'Madrid',
    workMode: 'HYBRID',
    publishedDate: null,
    detectedDate: '2026-03-01',
    appliedDate: null,
    salaryMin: null,
    salaryMax: null,
    currency: null,
    contractType: null,
    workingHours: null,
    minYearsExperience: null,
    skills: ['Java', 'SQL'],
    requirements: null,
    notes: null,
    status: 'SAVED',
    favorite: false,
    compatibilityScore: 72,
    createdAt: '2026-03-01T10:00:00Z',
    updatedAt: '2026-03-01T10:00:00Z',
    ...overrides,
  };
}

export function makePage(content: Offer[], overrides: Partial<Page<Offer>> = {}): Page<Offer> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0, ...overrides };
}

export function makeStats(overrides: Partial<Stats> = {}): Stats {
  return {
    totals: { saved: 0, applied: 0, active: 0, interviews: 0, offersReceived: 0, favorites: 0 },
    byStatus: [],
    applicationsOverTime: Array.from({ length: 12 }, (_, i) => ({ weekStart: `2026-01-${String(i + 1).padStart(2, '0')}`, count: 0 })),
    responseRate: { value: null, responded: 0, applied: 0, sufficientData: false },
    topCompanies: [],
    averageCompatibility: { value: null, offersScored: 0 },
    topCompatible: [],
    recentActivity: [],
    ...overrides,
  };
}

/** jsdom no implementa <dialog>.showModal(): se simula con el atributo `open`. */
export function polyfillDialog(): void {
  const proto = HTMLDialogElement.prototype as unknown as Record<string, unknown>;
  proto['showModal'] = function (this: HTMLDialogElement) {
    this.setAttribute('open', '');
  };
  proto['close'] = function (this: HTMLDialogElement) {
    this.removeAttribute('open');
    this.dispatchEvent(new Event('close'));
  };
}
