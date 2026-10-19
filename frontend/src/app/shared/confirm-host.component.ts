import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ConfirmService } from '../core/confirm.service';
import { DialogComponent } from './dialog.component';

@Component({
  selector: 'app-confirm-host',
  imports: [DialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-dialog [open]="confirm.request() !== null" labelledBy="confirm-title" (closed)="confirm.request() && confirm.answer(false)">
      @if (confirm.request(); as req) {
        <h2 id="confirm-title">{{ req.title }}</h2>
        <p class="muted msg">{{ req.message }}</p>
        <div class="form-actions">
          <button type="button" class="btn" (click)="confirm.answer(false)">Cancelar</button>
          <button type="button" class="btn" [class.btn--danger]="req.danger" [class.btn--primary]="!req.danger" (click)="confirm.answer(true)">
            {{ req.confirmLabel }}
          </button>
        </div>
      }
    </app-dialog>
  `,
  styles: `.msg { margin-top: var(--s2); }`,
})
export class ConfirmHostComponent {
  protected readonly confirm = inject(ConfirmService);
}
