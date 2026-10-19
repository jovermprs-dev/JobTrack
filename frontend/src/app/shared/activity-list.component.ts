import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Stats } from '../core/models';
import { formatDateTime } from './format';
import { StatusBadgeComponent } from './status-badge.component';

@Component({
  selector: 'app-activity-list',
  imports: [RouterLink, StatusBadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (items().length === 0) {
      <p class="muted">Aún no hay actividad. Los cambios de estado de tus candidaturas aparecerán aquí.</p>
    } @else {
      <ol class="feed">
        @for (a of items(); track $index) {
          <li>
            <p>
              <a [routerLink]="['/ofertas', a.offerId]"><strong>{{ a.title }}</strong></a>
              <span class="muted"> · {{ a.company }}</span>
            </p>
            <p class="row change">
              @if (a.fromStatus) { <app-status-badge [status]="a.fromStatus" /> <span class="sr-only">pasa a</span><span aria-hidden="true">→</span> }
              @else { <span class="muted small">Guardada como</span> }
              <app-status-badge [status]="a.toStatus" />
            </p>
            @if (a.note) { <p class="note small">{{ a.note }}</p> }
            <p class="muted small">{{ when(a.changedAt) }}</p>
          </li>
        }
      </ol>
    }
  `,
  styles: `
    .feed { display: grid; gap: var(--s4); }
    .feed li { display: grid; gap: 2px; padding-bottom: var(--s4); border-bottom: 1px solid var(--border); }
    .feed li:last-child { border-bottom: 0; padding-bottom: 0; }
    .change { gap: var(--s2); margin-block: 2px; }
    .note { background: var(--surface-2); padding: var(--s1) var(--s2); border-radius: var(--radius-sm); overflow-wrap: anywhere; }
  `,
})
export class ActivityListComponent {
  readonly items = input.required<Stats['recentActivity']>();
  protected when(v: string): string {
    return formatDateTime(v);
  }
}
