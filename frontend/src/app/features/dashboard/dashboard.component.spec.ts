import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { makeStats } from '../../testing/fixtures';
import { DashboardComponent } from './dashboard.component';

describe('DashboardComponent', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<DashboardComponent>;

  function setup() {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
  }
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  it('muestra carga y luego un estado vacío de bienvenida si no hay ofertas', () => {
    setup();
    expect(text()).toContain('Cargando tu panel');
    http.expectOne('/api/v1/stats').flush(makeStats());
    fixture.detectChanges();
    expect(text()).toContain('Empieza guardando tu primera oferta');
    expect(fixture.nativeElement.querySelector('app-donut-chart')).toBeNull();
  });

  it('refleja los datos reales: métricas, estados, compatibilidad y actividad', () => {
    setup();
    http.expectOne('/api/v1/stats').flush(
      makeStats({
        totals: { saved: 7, applied: 4, active: 2, interviews: 1, offersReceived: 1, favorites: 3 },
        byStatus: [
          { status: 'SAVED', count: 3 },
          { status: 'APPLIED', count: 2 },
          { status: 'INTERVIEW', count: 1 },
          { status: 'OFFER_RECEIVED', count: 1 },
        ],
        applicationsOverTime: Array.from({ length: 12 }, (_, i) => ({ weekStart: `2026-01-${String(i + 1).padStart(2, '0')}`, count: i === 11 ? 4 : 0 })),
        topCompatible: [{ id: 'o1', title: 'Java Developer', company: 'Acme', score: 91 }],
        recentActivity: [{ offerId: 'o1', title: 'Java Developer', company: 'Acme', fromStatus: 'SAVED', toStatus: 'APPLIED', note: null, changedAt: '2026-03-02T09:00:00Z' }],
      }),
    );
    fixture.detectChanges();
    const kpis = Array.from(fixture.nativeElement.querySelectorAll('.kpi') as NodeListOf<HTMLElement>).map(
      (k) => `${k.querySelector('.kpi__label')!.textContent}=${k.querySelector('.kpi__value')!.textContent}`,
    );
    expect(kpis).toEqual([
      'Ofertas guardadas=7',
      'Candidaturas enviadas=4',
      'Procesos activos=2',
      'Entrevistas=1',
      'Ofertas recibidas=1',
    ]);
    expect(fixture.nativeElement.querySelector('app-donut-chart .center strong').textContent).toBe('7');
    expect(fixture.nativeElement.querySelector('app-bar-chart')).not.toBeNull();
    expect(text()).toContain('91');
    expect(text()).toContain('Guardada');
  });

  it('muestra mensajes de ausencia de datos en los gráficos sin información', () => {
    setup();
    http.expectOne('/api/v1/stats').flush(makeStats({ totals: { saved: 2, applied: 0, active: 0, interviews: 0, offersReceived: 0, favorites: 0 }, byStatus: [{ status: 'SAVED', count: 2 }] }));
    fixture.detectChanges();
    expect(text()).toContain('Todavía no has enviado candidaturas');
    expect(text()).toContain('Aún no hay compatibilidades calculadas');
    expect(text()).toContain('Aún no hay actividad');
    expect(fixture.nativeElement.querySelector('app-bar-chart')).toBeNull();
  });

  it('muestra el error y reintenta', () => {
    setup();
    http.expectOne('/api/v1/stats').flush({}, { status: 500, statusText: 'err' });
    fixture.detectChanges();
    expect(text()).toContain('No se pudo cargar el panel');
    (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[]).find((b) => b.textContent?.includes('Reintentar'))!.click();
    http.expectOne('/api/v1/stats').flush(makeStats());
  });
});
