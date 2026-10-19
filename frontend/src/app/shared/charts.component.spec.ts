import { TestBed } from '@angular/core/testing';
import { BarChartComponent, DonutChartComponent } from './charts.component';

describe('gráficos', () => {
  it('el anillo suma el total, omite porciones a 0 y describe los datos para lectores de pantalla', () => {
    const fixture = TestBed.createComponent(DonutChartComponent);
    fixture.componentRef.setInput('slices', [
      { label: 'Enviadas', value: 3, color: 'red' },
      { label: 'Rechazadas', value: 1, color: 'blue' },
      { label: 'Retiradas', value: 0, color: 'grey' },
    ]);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.center strong')?.textContent).toBe('4');
    expect(el.querySelectorAll('circle.arc').length).toBe(2);
    expect(el.querySelector('svg')?.getAttribute('aria-label')).toBe('Enviadas: 3; Rechazadas: 1');
    expect(el.querySelectorAll('.legend li').length).toBe(3);
  });

  it('el anillo sin datos no rompe y lo indica', () => {
    const fixture = TestBed.createComponent(DonutChartComponent);
    fixture.componentRef.setInput('slices', [{ label: 'A', value: 0, color: 'red' }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('svg').getAttribute('aria-label')).toBe('Sin datos');
  });

  it('las barras escalan respecto al máximo y marcan los ceros', () => {
    const fixture = TestBed.createComponent(BarChartComponent);
    fixture.componentRef.setInput('data', [
      { label: 'a', value: 4 },
      { label: 'b', value: 2 },
      { label: 'c', value: 0 },
    ]);
    fixture.detectChanges();
    const bars = Array.from(fixture.nativeElement.querySelectorAll('.bar')) as HTMLElement[];
    expect(bars.map((b) => b.style.height)).toEqual(['100%', '50%', '0%']);
    expect(bars[2].classList.contains('zero')).toBe(true);
  });
});
