import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { UrlTree, provideRouter } from '@angular/router';
import { OfferDetailComponent } from '../features/offers/offer-detail.component';
import { OfferListComponent } from '../features/offers/offer-list.component';
import { ProfileComponent } from '../features/profile/profile.component';
import { ShellComponent } from '../layout/shell.component';
import { makeOffer, makePage, polyfillDialog } from '../testing/fixtures';
import { writableGuard } from './auth.guard';
import { AuthService } from './auth.service';
import { Profile } from './models';

const DEMO_USER = { id: 'd', email: 'demo@jobtrack.local', displayName: 'Visitante', demo: true };
const NORMAL_USER = { id: 'u', email: 'ana@example.com', displayName: 'Ana' };

/** Modo demo: instancia pública de solo lectura. El servidor ya rechaza toda escritura; aquí se comprueba que la interfaz no la ofrece. */
describe('Modo demo (solo lectura)', () => {
  let http: HttpTestingController;

  beforeAll(polyfillDialog);

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
  });

  async function signIn(user: object): Promise<void> {
    const restored = TestBed.inject(AuthService).restore();
    http.expectOne('/api/v1/auth/me').flush(user);
    await restored;
  }
  const buttons = (root: HTMLElement) => Array.from(root.querySelectorAll('button, a.btn')).map((b) => b.textContent?.trim() ?? '');

  it('isDemo solo es true para la cuenta marcada como demo', async () => {
    await signIn(NORMAL_USER);
    expect(TestBed.inject(AuthService).isDemo()).toBe(false);
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
    await signIn(DEMO_USER);
    expect(TestBed.inject(AuthService).isDemo()).toBe(true);
  });

  it('writableGuard lleva al panel en la demo y deja pasar a una cuenta normal', async () => {
    await signIn(DEMO_USER);
    const blocked = TestBed.runInInjectionContext(() => writableGuard({} as never, {} as never)) as UrlTree;
    expect(blocked.toString()).toBe('/dashboard');

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
    await signIn(NORMAL_USER);
    expect(TestBed.runInInjectionContext(() => writableGuard({} as never, {} as never))).toBe(true);
  });

  it('el shell muestra el aviso de demo y no ofrece cuenta ni cerrar sesión', async () => {
    await signIn(DEMO_USER);
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Modo demostración');
    expect(el.textContent).toContain('solo lectura');
    expect(buttons(el).some((t) => t.includes('Cerrar sesión'))).toBe(false);
    expect(el.querySelector('a[href="/cuenta"]')).toBeNull();
  });

  it('el shell de una cuenta normal conserva cuenta y cerrar sesión, sin aviso', async () => {
    await signIn(NORMAL_USER);
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).not.toContain('Modo demostración');
    expect(buttons(el).some((t) => t.includes('Cerrar sesión'))).toBe(true);
    expect(el.querySelector('a[href="/cuenta"]')).not.toBeNull();
  });

  it('el listado no ofrece crear, importar ni marcar favoritas, pero sí exportar y ver la estrella', async () => {
    await signIn(DEMO_USER);
    const fixture = TestBed.createComponent(OfferListComponent);
    fixture.componentRef.setInput('favoritesOnly', false);
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/offers').flush(makePage([makeOffer({ favorite: true })]));
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const labels = buttons(el);
    expect(labels.some((t) => t.includes('Nueva oferta'))).toBe(false);
    expect(labels.some((t) => t.includes('Importar CSV'))).toBe(false);
    expect(labels.some((t) => t.includes('Exportar CSV'))).toBe(true);
    expect(el.querySelector('button[aria-label^="Favorita"]')).toBeNull();
    expect(el.querySelector('span[role="img"][aria-label^="Favorita"]')).not.toBeNull();
  });

  it('el detalle no ofrece editar, eliminar, marcar favorita ni cambiar el estado', async () => {
    await signIn(DEMO_USER);
    const fixture = TestBed.createComponent(OfferDetailComponent);
    fixture.componentRef.setInput('id', 'o1');
    fixture.detectChanges();
    http.expectOne('/api/v1/offers/o1').flush(makeOffer({ favorite: true }));
    http.expectOne('/api/v1/offers/o1/history').flush([]);
    http.expectOne('/api/v1/offers/o1/compatibility').flush({
      score: 70, sufficientInformation: true, dimensions: [], positives: [], missing: [], unknown: [], disclaimer: 'Orientativo',
    });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const labels = buttons(el).join('|');
    for (const forbidden of ['Editar', 'Eliminar', 'Cambiar estado']) expect(labels).not.toContain(forbidden);
    expect(el.querySelector('button[aria-pressed]')).toBeNull();
    expect(el.textContent).toContain('Java Developer');
    expect(el.textContent).toContain('Favorita');
  });

  it('el perfil se muestra deshabilitado y sin botón de guardar', async () => {
    await signIn(DEMO_USER);
    const fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
    const profile: Profile = {
      degree: 'CFGS DAM', experienceSummary: 'Resumen', yearsExperience: 1.5, skills: ['Java'],
      languages: [{ language: 'Español', level: 'Nativo' }], location: 'Alicante', preferredWorkMode: 'HYBRID',
      desiredRoles: ['Backend'], expectedSalary: 28000, salaryCurrency: 'EUR', preferences: null, updatedAt: null,
    };
    http.expectOne('/api/v1/profile').flush(profile);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect((el.querySelector('#degree') as HTMLInputElement).disabled).toBe(true);
    expect((el.querySelector('#summary') as HTMLTextAreaElement).disabled).toBe(true);
    expect(buttons(el).some((t) => t.includes('Guardar perfil'))).toBe(false);
    expect(buttons(el).some((t) => t.includes('Añadir idioma'))).toBe(false);
  });
});
