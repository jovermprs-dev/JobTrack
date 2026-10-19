import { ChangeDetectionStrategy, Component, ElementRef, effect, input, output, viewChild } from '@angular/core';

/** Modal accesible basado en <dialog> nativo (foco atrapado, Esc para cerrar). */
@Component({
  selector: 'app-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog #dlg [attr.aria-labelledby]="labelledBy()" (close)="closed.emit()" (click)="onBackdrop($event)">
      <div class="body">
        <ng-content />
      </div>
    </dialog>
  `,
  styles: `
    dialog { border: 0; padding: 0; border-radius: var(--radius-lg); box-shadow: var(--shadow-lg); width: min(32rem, calc(100vw - 2rem));
      color: var(--text); background: var(--surface); }
    dialog::backdrop { background: rgba(15, 23, 42, 0.5); }
    .body { padding: var(--s5); }
  `,
})
export class DialogComponent {
  readonly open = input.required<boolean>();
  readonly labelledBy = input<string | null>(null);
  readonly closed = output<void>();
  private readonly dlg = viewChild.required<ElementRef<HTMLDialogElement>>('dlg');

  constructor() {
    effect(() => {
      const el = this.dlg().nativeElement;
      if (this.open() && !el.open) {
        el.showModal();
      } else if (!this.open() && el.open) {
        el.close();
      }
    });
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === this.dlg().nativeElement) {
      this.dlg().nativeElement.close();
    }
  }
}
