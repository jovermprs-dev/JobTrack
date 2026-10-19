import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { OfferStatus, STATUS_LABELS } from '../core/models';

@Component({
  selector: 'app-status-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span [class]="cls()">{{ label() }}</span>`,
})
export class StatusBadgeComponent {
  readonly status = input.required<OfferStatus>();
  protected readonly label = computed(() => STATUS_LABELS[this.status()]);
  protected readonly cls = computed(() => `badge badge--${this.status().toLowerCase()}`);
}
