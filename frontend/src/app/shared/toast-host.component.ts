import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService } from '../core/toast.service';
import { IconComponent } from './icon.component';

@Component({
  selector: 'app-toast-host',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toasts" role="status" aria-live="polite" aria-atomic="false">
      @for (t of toasts.toasts(); track t.id) {
        <div class="toast" [class]="'toast toast--' + t.kind">
          <app-icon [name]="t.kind === 'error' ? 'alert' : 'check'" />
          <span>{{ t.message }}</span>
          <button type="button" class="btn btn--ghost btn--sm btn--icon" aria-label="Cerrar notificación" (click)="toasts.dismiss(t.id)">
            <app-icon name="x" [size]="16" />
          </button>
        </div>
      }
    </div>
  `,
  styles: `
    .toasts { position: fixed; z-index: 100; right: var(--s4); bottom: var(--s4); display: flex; flex-direction: column; gap: var(--s2);
      width: min(24rem, calc(100vw - 2rem)); }
    .toast { display: flex; align-items: center; gap: var(--s3); padding: var(--s3) var(--s3) var(--s3) var(--s4); background: #172033; color: #fff;
      border-radius: var(--radius); box-shadow: var(--shadow-lg); font-size: var(--fs-sm); }
    .toast span { flex: 1; }
    .toast .btn { color: inherit; }
    .toast .btn:hover { background: rgba(255, 255, 255, 0.12); }
    .toast--error { background: #7a1a12; }
    .toast--success app-icon { color: #6ee7a8; }
  `,
})
export class ToastHostComponent {
  protected readonly toasts = inject(ToastService);
}
