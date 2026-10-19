import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Compatibility,
  HistoryEntry,
  ImportResult,
  Offer,
  OfferFilters,
  OfferInput,
  OfferStatus,
  Page,
  Profile,
  ProfileInput,
  Stats,
  User,
} from './models';

const BASE = '/api/v1';

/** Único punto de acceso a la API REST. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  // --- Auth ---
  me(): Observable<User> {
    return this.http.get<User>(`${BASE}/auth/me`);
  }
  login(email: string, password: string): Observable<User> {
    return this.http.post<User>(`${BASE}/auth/login`, { email, password });
  }
  register(email: string, password: string, displayName: string): Observable<User> {
    return this.http.post<User>(`${BASE}/auth/register`, { email, password, displayName });
  }
  logout(): Observable<void> {
    return this.http.post<void>(`${BASE}/auth/logout`, {});
  }
  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.put<void>(`${BASE}/auth/password`, { currentPassword, newPassword });
  }

  // --- Ofertas ---
  listOffers(filters: OfferFilters): Observable<Page<Offer>> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(filters)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<Page<Offer>>(`${BASE}/offers`, { params });
  }
  getOffer(id: string): Observable<Offer> {
    return this.http.get<Offer>(`${BASE}/offers/${id}`);
  }
  createOffer(input: OfferInput): Observable<Offer> {
    return this.http.post<Offer>(`${BASE}/offers`, input);
  }
  updateOffer(id: string, input: OfferInput): Observable<Offer> {
    return this.http.put<Offer>(`${BASE}/offers/${id}`, input);
  }
  deleteOffer(id: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/offers/${id}`);
  }
  setFavorite(id: string, favorite: boolean): Observable<Offer> {
    return this.http.patch<Offer>(`${BASE}/offers/${id}/favorite`, { favorite });
  }
  changeStatus(id: string, status: OfferStatus, note: string | null): Observable<Offer> {
    return this.http.post<Offer>(`${BASE}/offers/${id}/status`, { status, note });
  }
  history(id: string): Observable<HistoryEntry[]> {
    return this.http.get<HistoryEntry[]>(`${BASE}/offers/${id}/history`);
  }
  compatibility(id: string): Observable<Compatibility> {
    return this.http.get<Compatibility>(`${BASE}/offers/${id}/compatibility`);
  }
  exportCsv(): Observable<Blob> {
    return this.http.get(`${BASE}/offers/export`, { responseType: 'blob' });
  }
  importCsv(file: File, dryRun: boolean): Observable<ImportResult> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<ImportResult>(`${BASE}/offers/import`, body, { params: { dryRun } });
  }

  // --- Perfil y estadísticas ---
  getProfile(): Observable<Profile> {
    return this.http.get<Profile>(`${BASE}/profile`);
  }
  saveProfile(input: ProfileInput): Observable<Profile> {
    return this.http.put<Profile>(`${BASE}/profile`, input);
  }
  stats(): Observable<Stats> {
    return this.http.get<Stats>(`${BASE}/stats`);
  }
}
