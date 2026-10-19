import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Stats } from '../../core/models';
import { makeStats } from '../../testing/fixtures';
import { StatsComponent } from './stats.component';

const withApplications = (over: Partial<Stats>) =>
  makeStats({ totals: { saved: 6, applied: 5, active: 1, interviews: 2, offersReceived: 0, favorites: 0 }, ...over });

describe('StatsComponent', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<StatsComponent>;

  function render(stats: Stats) {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(StatsComponent);
    fixture.detectChanges();
    http.expectOne('/api/v1/stats').flush(stats);
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
  }

  it('sin ofertas muestra el estado vacío', () => {
    expect(render(makeStats())).toContain('Aún no hay datos que analizar');
  });

  it('sin candidaturas enviadas no inventa una tasa de respuesta', () => {
    const text = render(withApplications({ totals: { saved: 3, applied: 0, active: 0, interviews: 0, offersReceived: 0, favorites: 0 } }));
    expect(text).toContain('Todavía no has enviado ninguna candidatura');
    expect(text).not.toContain('%');
  });

  it('calcula y explica la tasa de respuesta con su denominador', () => {
    const text = render(withApplications({ responseRate: { value: 0.6, responded: 3, applied: 5, sufficientData: true } }));
    expect(text).toContain('60%');
    expect(text).toContain('3 de 5 candidaturas enviadas han recibido respuesta');
    expect(text).toContain('Cómo se calcula');
    expect(text).not.toContain('poco fiable');
  });

  it('avisa de que la tasa es poco fiable con pocos datos', () => {
    const text = render(withApplications({ responseRate: { value: 0.5, responded: 1, applied: 2, sufficientData: false } }));
    expect(text).toContain('50%');
    expect(text).toContain('poco fiable');
  });

  it('muestra la compatibilidad media con el número de ofertas puntuadas, o un guion', () => {
    expect(render(withApplications({ averageCompatibility: { value: 64.5, offersScored: 4 } }))).toContain('64.5/100');
    TestBed.resetTestingModule();
    const text = render(withApplications({}));
    const avg = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.nums > div')).find((d) => d.textContent!.includes('Compatibilidad media'))!;
    expect(avg.querySelector('dd')!.textContent).toBe('—');
    expect(text).toContain('0 de 6 ofertas con puntuación');
  });

  it('lista las empresas con más candidaturas', () => {
    const text = render(withApplications({ topCompanies: [{ company: 'Acme', count: 3 }, { company: 'Beta', count: 1 }] }));
    expect(text).toContain('Acme');
    expect(text).toContain('Beta');
  });
});
