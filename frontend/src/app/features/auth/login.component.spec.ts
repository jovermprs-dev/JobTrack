import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let http: HttpTestingController;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  function setup() {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
    navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const fill = (email: string, password: string) => {
      for (const [id, value] of [['email', email], ['password', password]]) {
        const input = el.querySelector(`#${id}`) as HTMLInputElement;
        input.value = value;
        input.dispatchEvent(new Event('input'));
      }
    };
    const submit = () => {
      (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
      fixture.detectChanges();
    };
    return { fixture, el, fill, submit };
  }

  it('no envía nada y muestra errores si el formulario es inválido', () => {
    const { el, submit } = setup();
    submit();
    http.expectNone('/api/v1/auth/login');
    expect(el.textContent).toContain('Introduce un email válido.');
    expect(el.textContent).toContain('Introduce tu contraseña.');
    expect(el.querySelector('#email')?.getAttribute('aria-invalid')).toBe('true');
  });

  it('inicia sesión y navega al panel', () => {
    const { fill, submit } = setup();
    fill('ana@example.com', 'secret-password');
    submit();
    http.expectOne('/api/v1/auth/login').flush({ id: '1', email: 'ana@example.com', displayName: 'Ana' });
    expect(navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('muestra el error del servidor y no navega', () => {
    const { fixture, el, fill, submit } = setup();
    fill('ana@example.com', 'wrong-password');
    submit();
    http.expectOne('/api/v1/auth/login').flush({ detail: 'Email o contraseña incorrectos.' }, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Email o contraseña incorrectos.');
    expect(navigateByUrl).not.toHaveBeenCalled();
  });
});
