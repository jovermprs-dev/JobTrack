import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { makeOffer, makePage, polyfillDialog } from '../../testing/fixtures';
import { OfferListComponent } from './offer-list.component';

describe('OfferListComponent', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<OfferListComponent>;

  beforeAll(polyfillDialog);

  function setup(favoritesOnly = false) {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OfferListComponent);
    fixture.componentRef.setInput('favoritesOnly', favoritesOnly);
    fixture.detectChanges();
  }
  const listRequest = (): TestRequest => http.expectOne((r) => r.url === '/api/v1/offers');
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const flush = async (req: TestRequest, body: object) => {
    req.flush(body);
    await fixture.whenStable();
    fixture.detectChanges();
  };

  it('muestra estado de carga y luego las ofertas con estado, modalidad y compatibilidad', async () => {
    setup();
    expect(text()).toContain('Cargando ofertas');
    await flush(listRequest(), makePage([makeOffer({ status: 'INTERVIEW', skills: ['Java', 'SQL', 'A', 'B', 'C', 'D', 'E'] })]));
    expect(text()).toContain('Java Developer');
    expect(text()).toContain('Entrevista');
    expect(text()).toContain('Híbrido');
    expect(text()).toContain('+2');
    expect(fixture.nativeElement.querySelector('[aria-label="Compatibilidad: 72 sobre 100"]')).not.toBeNull();
  });

  it('pide la primera página ordenada por fecha con 20 elementos', async () => {
    setup();
    const req = listRequest();
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.get('sort')).toBe('detectedDate,desc');
    expect(req.request.params.has('favorite')).toBe(false);
    await flush(req, makePage([]));
  });

  it('muestra el estado vacío con llamada a la acción cuando no hay ofertas', async () => {
    setup();
    await flush(listRequest(), makePage([]));
    expect(text()).toContain('Todavía no has guardado ninguna oferta');
    expect(fixture.nativeElement.querySelector('a[href="/ofertas/nueva"]')).not.toBeNull();
  });

  it('muestra el error y permite reintentar', async () => {
    setup();
    listRequest().flush({ detail: 'Fallo temporal' }, { status: 500, statusText: 'err' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(text()).toContain('No se pudieron cargar las ofertas');
    (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[]).find((b) => b.textContent?.includes('Reintentar'))!.click();
    await flush(listRequest(), makePage([makeOffer()]));
    expect(text()).toContain('Java Developer');
  });

  it('en /favoritas siempre filtra por favorito y oculta el filtro de estado', async () => {
    setup(true);
    const req = listRequest();
    expect(req.request.params.get('favorite')).toBe('true');
    await flush(req, makePage([]));
    expect(text()).toContain('Aún no tienes favoritas');
    expect(fixture.nativeElement.querySelector('#f-status')).toBeNull();
  });

  it('un filtro se aplica en el backend tras el debounce y se refleja en la URL', async () => {
    setup();
    await flush(listRequest(), makePage([makeOffer()]));
    const select = fixture.nativeElement.querySelector('#f-status') as HTMLSelectElement;
    select.value = 'APPLIED';
    select.dispatchEvent(new Event('change'));
    await new Promise((r) => setTimeout(r, 400));
    const req = listRequest();
    expect(req.request.params.get('status')).toBe('APPLIED');
    await flush(req, makePage([]));
    expect(text()).toContain('Sin resultados');
    expect(text()).toContain('Limpiar filtros');
  });

  it('marcar favorita llama al backend y actualiza la fila', async () => {
    setup();
    await flush(listRequest(), makePage([makeOffer()]));
    const star = fixture.nativeElement.querySelector('button[aria-pressed]') as HTMLButtonElement;
    expect(star.getAttribute('aria-pressed')).toBe('false');
    star.click();
    const patch = http.expectOne('/api/v1/offers/o1/favorite');
    expect(patch.request.method).toBe('PATCH');
    expect(patch.request.body).toEqual({ favorite: true });
    patch.flush(makeOffer({ favorite: true }));
    fixture.detectChanges();
    expect((fixture.nativeElement.querySelector('button[aria-pressed]') as HTMLElement).getAttribute('aria-pressed')).toBe('true');
  });

  it('la paginación pide la página siguiente', async () => {
    setup();
    await flush(listRequest(), makePage([makeOffer()], { totalElements: 45, totalPages: 3 }));
    expect(text()).toContain('Mostrando 1–20 de 45');
    (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[]).find((b) => b.textContent?.includes('Siguiente'))!.click();
    await fixture.whenStable();
    const req = listRequest();
    expect(req.request.params.get('page')).toBe('1');
    await flush(req, makePage([makeOffer({ id: 'o2' })], { page: 1, totalElements: 45, totalPages: 3 }));
  });
});
