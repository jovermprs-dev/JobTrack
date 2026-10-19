import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RegisterComponent } from './register.component';

describe('RegisterComponent', () => {
  function setup() {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    const http = TestBed.inject(HttpTestingController);
    const navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(RegisterComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const fill = (name: string, email: string, password: string) => {
      for (const [id, value] of [['displayName', name], ['email', email], ['password', password]]) {
        const input = el.querySelector(`#${id}`) as HTMLInputElement;
        input.value = value;
        input.dispatchEvent(new Event('input'));
      }
    };
    const submit = () => {
      (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
      fixture.detectChanges();
    };
    return { fixture, el, http, navigateByUrl, fill, submit };
  }

  it('rechaza contraseñas de menos de 10 caracteres sin llamar a la API', () => {
    const { el, http, fill, submit } = setup();
    fill('Ana', 'ana@example.com', 'corta');
    submit();
    http.expectNone('/api/v1/auth/register');
    expect(el.textContent).toContain('entre 10 y 72 caracteres');
  });

  it('crea la cuenta y entra al panel', () => {
    const { http, navigateByUrl, fill, submit } = setup();
    fill('Ana', 'ana@example.com', 'una-contraseña-larga');
    submit();
    const req = http.expectOne('/api/v1/auth/register');
    expect(req.request.body).toEqual({ email: 'ana@example.com', password: 'una-contraseña-larga', displayName: 'Ana' });
    req.flush({ id: '1', email: 'ana@example.com', displayName: 'Ana' });
    expect(navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('informa de que el email ya existe (409)', () => {
    const { fixture, el, http, fill, submit } = setup();
    fill('Ana', 'ana@example.com', 'una-contraseña-larga');
    submit();
    http.expectOne('/api/v1/auth/register').flush({ detail: 'Ya existe una cuenta con ese email.' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Ya existe una cuenta');
  });
});
