export type OfferStatus =
  | 'SAVED'
  | 'PENDING_TO_APPLY'
  | 'APPLIED'
  | 'IN_PROCESS'
  | 'INTERVIEW'
  | 'OFFER_RECEIVED'
  | 'REJECTED'
  | 'WITHDRAWN';

export type WorkMode = 'REMOTE' | 'HYBRID' | 'ON_SITE';

export const STATUS_ORDER: OfferStatus[] = [
  'SAVED',
  'PENDING_TO_APPLY',
  'APPLIED',
  'IN_PROCESS',
  'INTERVIEW',
  'OFFER_RECEIVED',
  'REJECTED',
  'WITHDRAWN',
];

export const STATUS_LABELS: Record<OfferStatus, string> = {
  SAVED: 'Guardada',
  PENDING_TO_APPLY: 'Pendiente de solicitar',
  APPLIED: 'Candidatura enviada',
  IN_PROCESS: 'Proceso de selección',
  INTERVIEW: 'Entrevista',
  OFFER_RECEIVED: 'Oferta recibida',
  REJECTED: 'Rechazada',
  WITHDRAWN: 'Retirada',
};

export const WORK_MODES: WorkMode[] = ['REMOTE', 'HYBRID', 'ON_SITE'];

export const WORK_MODE_LABELS: Record<WorkMode, string> = {
  REMOTE: 'Remoto',
  HYBRID: 'Híbrido',
  ON_SITE: 'Presencial',
};

export interface User {
  id: string;
  email: string;
  displayName: string;
  /** true en la instancia pública de solo lectura (modo demo): sin login y sin escritura. */
  demo?: boolean;
}

export interface Offer {
  id: string;
  title: string;
  company: string;
  description: string | null;
  url: string | null;
  location: string | null;
  workMode: WorkMode | null;
  publishedDate: string | null;
  detectedDate: string;
  appliedDate: string | null;
  salaryMin: number | null;
  salaryMax: number | null;
  currency: string | null;
  contractType: string | null;
  workingHours: string | null;
  minYearsExperience: number | null;
  skills: string[];
  requirements: string | null;
  notes: string | null;
  status: OfferStatus;
  favorite: boolean;
  compatibilityScore: number | null;
  createdAt: string;
  updatedAt: string;
}

/** Cuerpo de creación/edición. Los campos desconocidos se envían como null. */
export interface OfferInput {
  title: string;
  company: string;
  description: string | null;
  url: string | null;
  location: string | null;
  workMode: WorkMode | null;
  publishedDate: string | null;
  detectedDate: string | null;
  appliedDate: string | null;
  salaryMin: number | null;
  salaryMax: number | null;
  currency: string | null;
  contractType: string | null;
  workingHours: string | null;
  minYearsExperience: number | null;
  skills: string[];
  requirements: string | null;
  notes: string | null;
  status?: OfferStatus | null;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface OfferFilters {
  q?: string;
  status?: OfferStatus | '';
  company?: string;
  location?: string;
  workMode?: WorkMode | '';
  favorite?: boolean;
  sort?: string;
  page?: number;
  size?: number;
}

export interface HistoryEntry {
  id: string;
  fromStatus: OfferStatus | null;
  toStatus: OfferStatus;
  changedAt: string;
  note: string | null;
}

export interface CompatibilityDimension {
  key: string;
  label: string;
  weight: number;
  available: boolean;
  earned: number;
  detail: string;
}

export interface Compatibility {
  score: number | null;
  sufficientInformation: boolean;
  dimensions: CompatibilityDimension[];
  positives: string[];
  missing: string[];
  unknown: string[];
  disclaimer: string;
}

export interface LanguageSkill {
  language: string;
  level: string;
}

export interface Profile {
  degree: string | null;
  experienceSummary: string | null;
  yearsExperience: number | null;
  skills: string[];
  languages: LanguageSkill[];
  location: string | null;
  preferredWorkMode: WorkMode | null;
  desiredRoles: string[];
  expectedSalary: number | null;
  salaryCurrency: string | null;
  preferences: string | null;
  updatedAt: string | null;
}

export type ProfileInput = Omit<Profile, 'updatedAt'>;

export interface Stats {
  totals: { saved: number; applied: number; active: number; interviews: number; offersReceived: number; favorites: number };
  byStatus: { status: OfferStatus; count: number }[];
  applicationsOverTime: { weekStart: string; count: number }[];
  responseRate: { value: number | null; responded: number; applied: number; sufficientData: boolean };
  topCompanies: { company: string; count: number }[];
  averageCompatibility: { value: number | null; offersScored: number };
  topCompatible: { id: string; title: string; company: string; score: number }[];
  recentActivity: {
    offerId: string;
    title: string;
    company: string;
    fromStatus: OfferStatus | null;
    toStatus: OfferStatus;
    note: string | null;
    changedAt: string;
  }[];
}

export interface ImportResult {
  dryRun: boolean;
  totalRows: number;
  validRows: number;
  imported: number;
  errors: { row: number; field: string; message: string }[];
  preview: { row: number; title: string; company: string; status: OfferStatus }[];
}

export interface ProblemDetail {
  status: number;
  title?: string;
  detail?: string;
  errors?: { field: string; message: string }[];
}
