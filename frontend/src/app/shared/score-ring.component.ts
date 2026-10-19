import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Anillo de compatibilidad (0–100). `null` = información insuficiente (no es un 0). */
@Component({
  selector: 'app-score-ring',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="ring" [style.width.px]="size()" [style.height.px]="size()" role="img" [attr.aria-label]="label()">
      <svg viewBox="0 0 36 36" aria-hidden="true">
        <circle class="track" cx="18" cy="18" r="15.9155" />
        @if (score() !== null && score()! > 0) {
          <circle class="value" cx="18" cy="18" r="15.9155" [style.stroke]="color()" [attr.stroke-dasharray]="score() + ' ' + (100 - score()!)" />
        }
      </svg>
      <span class="num" [style.font-size.px]="size() * 0.3">{{ score() === null ? '—' : score() }}</span>
    </div>
  `,
  styles: `
    .ring { position: relative; flex: none; }
    svg { width: 100%; height: 100%; transform: rotate(-90deg); }
    circle { fill: none; stroke-width: 3.4; }
    .track { stroke: var(--surface-2); }
    .value { stroke-linecap: round; }
    .num { position: absolute; inset: 0; display: grid; place-items: center; font-weight: 650; color: var(--text); }
  `,
})
export class ScoreRingComponent {
  readonly score = input<number | null>(null);
  readonly size = input(44);
  protected readonly label = computed(() =>
    this.score() === null ? 'Compatibilidad: información insuficiente' : `Compatibilidad: ${this.score()} sobre 100`,
  );
  protected readonly color = computed(() => {
    const s = this.score() ?? 0;
    return s >= 70 ? 'var(--st-offer_received)' : s >= 40 ? 'var(--st-pending_to_apply)' : 'var(--st-rejected)';
  });
}
