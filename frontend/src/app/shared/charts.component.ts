import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export interface DonutSlice {
  label: string;
  value: number;
  color: string;
}

/** Gráfico de anillo con leyenda numérica (la información no depende solo del color). */
@Component({
  selector: 'app-donut-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="wrap">
      <div class="chart">
        <svg viewBox="0 0 36 36" role="img" [attr.aria-label]="summary()">
          <circle class="track" cx="18" cy="18" r="15.9155" />
          @for (s of arcs(); track s.label) {
            <circle class="arc" cx="18" cy="18" r="15.9155" [style.stroke]="s.color"
              [attr.stroke-dasharray]="s.pct + ' ' + (100 - s.pct)" [attr.stroke-dashoffset]="-s.offset" />
          }
        </svg>
        <div class="center"><strong>{{ total() }}</strong><span class="muted small">{{ centerLabel() }}</span></div>
      </div>
      <ul class="legend">
        @for (s of slices(); track s.label) {
          <li><span class="dot" [style.background]="s.color"></span><span class="lbl">{{ s.label }}</span><strong>{{ s.value }}</strong></li>
        }
      </ul>
    </div>
  `,
  styles: `
    .wrap { display: flex; flex-wrap: wrap; align-items: center; gap: var(--s5); }
    .chart { position: relative; width: 9.5rem; height: 9.5rem; flex: none; }
    svg { width: 100%; height: 100%; transform: rotate(-90deg); }
    circle { fill: none; stroke-width: 4; }
    .track { stroke: var(--surface-2); }
    .center { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; line-height: 1.2; }
    .center strong { font-size: var(--fs-2xl); }
    .legend { flex: 1; min-width: 11rem; display: grid; gap: var(--s2); }
    .legend li { display: flex; align-items: center; gap: var(--s2); font-size: var(--fs-sm); }
    .lbl { flex: 1; }
    .dot { width: 0.65rem; height: 0.65rem; border-radius: 50%; flex: none; }
  `,
})
export class DonutChartComponent {
  readonly slices = input.required<DonutSlice[]>();
  readonly centerLabel = input('total');
  protected readonly total = computed(() => this.slices().reduce((sum, s) => sum + s.value, 0));
  protected readonly arcs = computed(() => {
    const total = this.total();
    let offset = 0;
    return this.slices()
      .filter((s) => s.value > 0)
      .map((s) => {
        const pct = (s.value / total) * 100;
        const arc = { label: s.label, color: s.color, pct, offset };
        offset += pct;
        return arc;
      });
  });
  protected readonly summary = computed(() =>
    this.slices().filter((s) => s.value > 0).map((s) => `${s.label}: ${s.value}`).join('; ') || 'Sin datos',
  );
}

export interface BarDatum {
  label: string;
  value: number;
}

/** Gráfico de barras verticales simple (CSS). */
@Component({
  selector: 'app-bar-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="sr-only" [attr.aria-label]="ariaLabel()">
      @for (d of data(); track $index) { <li>{{ d.label }}: {{ d.value }}</li> }
    </ul>
    <div class="bars" aria-hidden="true" [style.grid-template-columns]="'repeat(' + data().length + ', minmax(0, 1fr))'">
      @for (d of data(); track $index) {
        <div class="col" [title]="d.label + ': ' + d.value">
          <span class="val">{{ d.value || '' }}</span>
          <div class="bar" [style.height.%]="d.value ? (d.value / max()) * 100 : 0" [class.zero]="!d.value"></div>
          <span class="lab" [class.hide]="(data().length - 1 - $index) % step() !== 0">{{ d.label }}</span>
        </div>
      }
    </div>
  `,
  styles: `
    .bars { display: grid; gap: 4px; height: 11rem; align-items: stretch; }
    .col { display: grid; grid-template-rows: 1.1rem 1fr 1.4rem; justify-items: center; min-width: 0; }
    .val { font-size: var(--fs-xs); color: var(--text-muted); }
    .bar { align-self: end; width: 100%; max-width: 2rem; min-height: 2px; background: var(--primary); border-radius: 4px 4px 0 0; }
    .bar.zero { background: var(--border); height: 2px !important; }
    .lab { font-size: 0.6875rem; color: var(--text-muted); white-space: nowrap; }
    .lab.hide { visibility: hidden; }
  `,
})
export class BarChartComponent {
  readonly data = input.required<BarDatum[]>();
  readonly ariaLabel = input('Gráfico de barras');
  protected readonly max = computed(() => Math.max(1, ...this.data().map((d) => d.value)));
  protected readonly step = computed(() => (this.data().length > 8 ? 2 : 1));
}

export interface HBarDatum {
  label: string;
  value: number;
  color?: string;
}

/** Lista de barras horizontales: etiqueta, barra proporcional y valor. */
@Component({
  selector: 'app-hbar-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="list">
      @for (d of data(); track d.label) {
        <li>
          <span class="lbl truncate" [title]="d.label">{{ d.label }}</span>
          <span class="track" aria-hidden="true"><span class="fill" [style.width.%]="(d.value / max()) * 100" [style.background]="d.color ?? 'var(--primary)'"></span></span>
          <strong class="num">{{ d.value }}</strong>
        </li>
      }
    </ul>
  `,
  styles: `
    .list { display: grid; gap: var(--s3); }
    li { display: grid; grid-template-columns: minmax(6rem, 38%) 1fr 2rem; align-items: center; gap: var(--s3); font-size: var(--fs-sm); }
    .track { height: 0.6rem; background: var(--surface-2); border-radius: 999px; overflow: hidden; }
    .fill { display: block; height: 100%; border-radius: 999px; min-width: 2px; }
    .num { text-align: right; }
  `,
})
export class HBarListComponent {
  readonly data = input.required<HBarDatum[]>();
  protected readonly max = computed(() => Math.max(1, ...this.data().map((d) => d.value)));
}
