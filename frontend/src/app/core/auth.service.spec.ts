import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { authGuard, guestGuard } from './auth.guard';
import { AuthService } from './auth.service';

const USER = { id: '1', email: 'a@b.com', displayName: 'Ana' };

describe('AuthService y guards', () => {
  let http: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });
  afterEach(() => http.verify());

  it('restore() deja el usuario si hay sesión', async () => {
    const p = auth.restore();
    http.expectOne('/api/v1/auth/me').flush(USER);
    await p;
    expect(auth.user()).toEqual(USER);
    expect(auth.isAuthenticated()).toBe(true);
  });

  it('restore() deja usuario nulo ante 401', async () => {
    const p = auth.restore();
    http.expectOne('/api/v1/auth/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    await p;
    expect(auth.user()).toBeNull();
  });

  it('login() guarda el usuario devuelto', () => {
    auth.login('a@b.com', 'x').subscribe();
    const req = http.expectOne('/api/v1/auth/login');
    expect(req.request.body).toEqual({ email: 'a@b.com', password: 'x' });
    req.flush(USER);
    expect(auth.user()).toEqual(USER);
  });

  it('logout() borra el usuario aunque falle la petición y va a /login', async () => {
    auth.login('a@b.com', 'x').subscribe();
    http.expectOne('/api/v1/auth/login').flush(USER);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const p = auth.logout();
    http.expectOne('/api/v1/auth/logout').flush({}, { status: 500, statusText: 'err' });
    await p.catch(() => undefined);
    expect(auth.user()).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('authGuard redirige a /login con returnUrl cuando no hay sesión', () => {
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, { url: '/ofertas?x=1' } as never)) as UrlTree;
    expect(result.toString()).toBe('/login?returnUrl=%2Fofertas%3Fx%3D1');
  });

  it('authGuard permite el paso con sesión y guestGuard lo impide', () => {
    auth.login('a@b.com', 'x').subscribe();
    http.expectOne('/api/v1/auth/login').flush(USER);
    expect(TestBed.runInInjectionContext(() => authGuard({} as never, { url: '/x' } as never))).toBe(true);
    const guest = TestBed.runInInjectionContext(() => guestGuard({} as never, {} as never)) as UrlTree;
    expect(guest.toString()).toBe('/dashboard');
  });
});
