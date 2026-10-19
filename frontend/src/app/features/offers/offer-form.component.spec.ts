import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { makeOffer } from '../../testing/fixtures';
import { OfferFormComponent } from './offer-form.component';

describe('OfferFormComponent', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<OfferFormComponent>;
  let navigate: ReturnType<typeof vi.spyOn>;

  function setup(id?: string) {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(OfferFormComponent);
    if (id) fixture.componentRef.setInput('id', id);
    fixture.detectChanges();
  }
  const el = () => fixture.nativeElement as HTMLElement;
  const set = (id: string, value: string) => {
    const input = el().querySelector(`#${id}`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('change'));
  };
  const submit = () => {
    (el().querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  };

  it('exige puesto y empresa y no llama a la API si faltan', () => {
    setup();
    submit();
    http.expectNone('/api/v1/offers');
    expect(el().textContent).toContain('Este campo es obligatorio.');
    expect(el().querySelector('[role="alert"]')?.textContent).toContain('Revisa los campos');
    expect(el().querySelector('#title')?.getAttribute('aria-invalid')).toBe('true');
  });

  it('valida URL (solo http/https) y rango salarial', () => {
    setup();
    set('title', 'Dev');
    set('company', 'Acme');
    set('url', 'javascript:alert(1)');
    set('salaryMin', '50000');
    set('salaryMax', '10000');
    submit();
    http.expectNone('/api/v1/offers');
    expect(el().textContent).toContain('La URL debe empezar por http:// o https://');
    expect(el().textContent).toContain('El mínimo no puede superar al máximo.');
  });

  it('envía null para lo desconocido y conserva 0 como valor real', () => {
    setup();
    set('title', '  Backend Dev  ');
    set('company', 'Acme');
    set('salaryMin', '0');
    set('currency', 'eur');
    submit();
    const req = http.expectOne('/api/v1/offers');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toMatchObject({
      title: 'Backend Dev',
      company: 'Acme',
      description: null,
      url: null,
      workMode: null,
      salaryMin: 0,
      salaryMax: null,
      currency: 'EUR',
      minYearsExperience: null,
      skills: [],
      status: 'SAVED',
    });
    req.flush(makeOffer({ id: 'new1' }));
    expect(navigate).toHaveBeenCalledWith(['/ofertas', 'new1']);
  });

  it('muestra los errores de validación que devuelve el servidor junto a su campo', () => {
    setup();
    set('title', 'Dev');
    set('company', 'Acme');
    submit();
    http.expectOne('/api/v1/offers').flush(
      { detail: 'Los datos enviados no son válidos.', errors: [{ field: 'company', message: 'Máximo 200 caracteres' }] },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();
    expect(el().querySelector('#company-err')?.textContent).toContain('Máximo 200 caracteres');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('en edición carga la oferta, no envía el estado y guarda con PUT', () => {
    setup('o1');
    http.expectOne('/api/v1/offers/o1').flush(makeOffer({ title: 'Original', salaryMin: 0, status: 'APPLIED' }));
    fixture.detectChanges();
    expect((el().querySelector('#title') as HTMLInputElement).value).toBe('Original');
    expect(el().querySelector('#status')).toBeNull();
    set('title', 'Editada');
    submit();
    const req = http.expectOne('/api/v1/offers/o1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.title).toBe('Editada');
    expect(req.request.body.salaryMin).toBe(0);
    expect('status' in req.request.body && req.request.body.status !== undefined).toBe(false);
    req.flush(makeOffer({ title: 'Editada' }));
  });

  it('informa si la oferta a editar no existe', () => {
    setup('missing');
    http.expectOne('/api/v1/offers/missing').flush({ detail: 'Oferta no encontrada.' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(el().textContent).toContain('No se pudo cargar la oferta');
    expect(el().textContent).toContain('Oferta no encontrada.');
  });
});
