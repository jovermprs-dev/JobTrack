import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { ConfirmService } from '../../core/confirm.service';
import { Compatibility } from '../../core/models';
import { makeOffer, polyfillDialog } from '../../testing/fixtures';
import { OfferDetailComponent } from './offer-detail.component';

const COMPAT: Compatibility = {
  score: 69,
  sufficientInformation: true,
  dimensions: [{ key: 'skills', label: 'Competencias', weight: 50, available: true, earned: 0.5, detail: '1 de 2 competencias requeridas figuran en tu perfil.' }],
  positives: ['Competencia requerida que figura en tu perfil: Java'],
  missing: ['Competencia requerida no registrada en tu perfil: Kafka'],
  unknown: [],
  disclaimer: 'Indicador orientativo; no es una probabilidad de conseguir el puesto.',
};
const HISTORY = [{ id: 'h1', fromStatus: null, toStatus: 'SAVED' as const, changedAt: '2026-03-01T10:00:00Z', note: null }];

describe('OfferDetailComponent', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<OfferDetailComponent>;

  beforeAll(polyfillDialog);

  function setup() {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OfferDetailComponent);
    fixture.componentRef.setInput('id', 'o1');
    fixture.detectChanges();
    http.expectOne('/api/v1/offers/o1').flush(makeOffer({ salaryMin: 0, minYearsExperience: 0 }));
    http.expectOne('/api/v1/offers/o1/history').flush(HISTORY);
    http.expectOne('/api/v1/offers/o1/compatibility').flush(COMPAT);
    fixture.detectChanges();
  }
  const el = () => fixture.nativeElement as HTMLElement;
  const button = (label: string) => (Array.from(el().querySelectorAll('button')) as HTMLButtonElement[]).find((b) => b.textContent?.includes(label))!;

  it('muestra la oferta, la compatibilidad desglosada y el historial', () => {
    setup();
    expect(el().textContent).toContain('Java Developer');
    expect(el().textContent).toContain('69/100');
    expect(el().textContent).toContain('Competencia requerida no registrada en tu perfil: Kafka');
    expect(el().textContent).toContain('no es una probabilidad');
    expect(el().textContent).toContain('Creada como');
  });

  it('distingue salario/experiencia 0 de «no indicado»', () => {
    setup();
    expect(el().textContent).toContain('Desde 0');
    expect(el().textContent).toContain('No se pide');
    expect(el().textContent).toContain('No indicado');
  });

  it('cambia el estado con una nota y recarga el historial', () => {
    setup();
    button('Cambiar estado').click();
    fixture.detectChanges();
    const select = el().querySelector('#new-status') as HTMLSelectElement;
    expect(Array.from(select.options).some((o) => o.value === 'SAVED')).toBe(false);
    select.value = 'APPLIED';
    select.dispatchEvent(new Event('change'));
    const note = el().querySelector('#status-note') as HTMLTextAreaElement;
    note.value = 'Enviado por LinkedIn';
    note.dispatchEvent(new Event('input'));
    (el().querySelector('app-dialog form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    const post = http.expectOne('/api/v1/offers/o1/status');
    expect(post.request.body).toEqual({ status: 'APPLIED', note: 'Enviado por LinkedIn' });
    post.flush(makeOffer({ status: 'APPLIED', appliedDate: '2026-03-02' }));
    http.expectOne('/api/v1/offers/o1/history').flush([{ id: 'h2', fromStatus: 'SAVED', toStatus: 'APPLIED', changedAt: '2026-03-02T09:00:00Z', note: 'Enviado por LinkedIn' }, ...HISTORY]);
    fixture.detectChanges();
    expect(el().textContent).toContain('Enviado por LinkedIn');
    expect(el().textContent).toContain('Enviada el');
  });

  it('pide confirmación antes de eliminar y no borra si se cancela', async () => {
    setup();
    const confirm = TestBed.inject(ConfirmService);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    button('Eliminar').click();
    expect(confirm.request()?.danger).toBe(true);
    confirm.answer(false);
    await fixture.whenStable();
    http.expectNone('/api/v1/offers/o1/delete');
    expect(navigate).not.toHaveBeenCalled();

    button('Eliminar').click();
    confirm.answer(true);
    await fixture.whenStable();
    const del = http.expectOne('/api/v1/offers/o1');
    expect(del.request.method).toBe('DELETE');
    del.flush(null, { status: 204, statusText: 'No Content' });
    expect(navigate).toHaveBeenCalledWith(['/ofertas']);
  });
});
