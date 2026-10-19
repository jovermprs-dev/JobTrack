import { ChangeDetectionStrategy, Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { IconComponent } from './icon.component';

/** Campo de etiquetas (competencias, puestos…). Enter o coma añaden; Retroceso borra la última. */
@Component({
  selector: 'app-tag-input',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TagInputComponent), multi: true }],
  template: `
    <div class="box" [class.disabled]="disabled()">
      @for (tag of tags(); track tag) {
        <span class="chip">
          {{ tag }}
          <button type="button" class="x" [attr.aria-label]="'Quitar ' + tag" [disabled]="disabled()" (click)="remove(tag)">
            <app-icon name="x" [size]="12" />
          </button>
        </span>
      }
      <input
        class="inp"
        [id]="inputId()"
        [placeholder]="tags().length ? '' : placeholder()"
        [disabled]="disabled()"
        [value]="draft()"
        [attr.aria-describedby]="describedBy()"
        (input)="draft.set($any($event.target).value)"
        (keydown)="onKey($event)"
        (blur)="commit(); touched()"
      />
    </div>
  `,
  styles: `
    .box { display: flex; flex-wrap: wrap; gap: var(--s1); align-items: center; min-height: 2.5rem; padding: 0.25rem var(--s2);
      border: 1px solid var(--border-strong); border-radius: var(--radius-sm); background: var(--surface); }
    .box:focus-within { outline: 2px solid var(--focus); border-color: var(--focus); }
    .box.disabled { background: var(--surface-2); }
    .x { display: inline-flex; border: 0; background: transparent; padding: 2px; cursor: pointer; color: var(--text-muted); border-radius: 4px; }
    .x:hover { color: var(--danger); background: var(--danger-soft); }
    .inp { flex: 1; min-width: 8rem; border: 0; outline: 0; background: transparent; padding: 0.35rem 0.25rem; }
  `,
})
export class TagInputComponent implements ControlValueAccessor {
  readonly inputId = input<string>('');
  readonly placeholder = input('Escribe y pulsa Enter');
  readonly describedBy = input<string | null>(null);
  readonly maxItems = input(60);

  protected readonly tags = signal<string[]>([]);
  protected readonly draft = signal('');
  protected readonly disabled = signal(false);

  private onChange: (value: string[]) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  writeValue(value: string[] | null): void {
    this.tags.set(value ?? []);
  }
  registerOnChange(fn: (value: string[]) => void): void {
    this.onChange = fn;
  }
  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  protected touched(): void {
    this.onTouched();
  }

  protected onKey(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      this.commit();
    } else if (event.key === 'Backspace' && this.draft() === '' && this.tags().length) {
      this.update(this.tags().slice(0, -1));
    }
  }

  protected commit(): void {
    const parts = this.draft().split(',').map((p) => p.trim()).filter(Boolean);
    if (!parts.length) {
      this.draft.set('');
      return;
    }
    const next = [...this.tags()];
    for (const part of parts) {
      if (next.length < this.maxItems() && !next.some((t) => t.toLowerCase() === part.toLowerCase())) {
        next.push(part.slice(0, 80));
      }
    }
    this.draft.set('');
    this.update(next);
  }

  protected remove(tag: string): void {
    this.update(this.tags().filter((t) => t !== tag));
  }

  private update(next: string[]): void {
    this.tags.set(next);
    this.onChange(next);
  }
}
