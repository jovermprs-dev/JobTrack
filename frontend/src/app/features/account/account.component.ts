import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { toApiError } from '../../core/api-error';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';

function matches(group: AbstractControl): ValidationErrors | null {
  return group.get('newPassword')?.value === group.get('confirm')?.value ? null : { mismatch: true };
}

@Component({
  selector: 'app-account',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page narrow">
      <header class="page-header"><div><h1>Mi cuenta</h1><p>Datos de acceso y seguridad.</p></div></header>

      <section class="card stack">
        <h2>Datos</h2>
        <dl class="facts">
          <div><dt>Nombre</dt><dd>{{ auth.user()?.displayName }}</dd></div>
          <div><dt>Email</dt><dd>{{ auth.user()?.email }}</dd></div>
        </dl>
      </section>

      <form class="card stack top" [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <h2>Cambiar contraseña</h2>
        @if (error()) { <div class="alert alert--error" role="alert">{{ error() }}</div> }
        <div class="field">
          <label for="current">Contraseña actual</label>
          <input id="current" class="input" type="password" formControlName="currentPassword" autocomplete="current-password"
            [attr.aria-invalid]="bad('currentPassword')" />
          @if (bad('currentPassword')) { <span class="error">Introduce tu contraseña actual.</span> }
        </div>
        <div class="field">
          <label for="new">Nueva contraseña</label>
          <input id="new" class="input" type="password" formControlName="newPassword" autocomplete="new-password"
            [attr.aria-invalid]="bad('newPassword')" aria-describedby="new-hint" />
          <span id="new-hint" class="hint">Entre 10 y 72 caracteres.</span>
          @if (bad('newPassword')) { <span class="error">La contraseña debe tener entre 10 y 72 caracteres.</span> }
        </div>
        <div class="field">
          <label for="confirm">Repite la nueva contraseña</label>
          <input id="confirm" class="input" type="password" formControlName="confirm" autocomplete="new-password"
            [attr.aria-invalid]="mismatch() ? true : null" />
          @if (mismatch()) { <span class="error">Las contraseñas no coinciden.</span> }
        </div>
        <div class="form-actions"><button type="submit" class="btn btn--primary" [disabled]="saving()">{{ saving() ? 'Guardando…' : 'Cambiar contraseña' }}</button></div>
      </form>
    </div>
  `,
  styles: `
    .narrow { max-width: 40rem; }
    .top { margin-top: var(--s4); }
    .facts { display: grid; gap: var(--s3); margin: 0; }
    .facts dt { font-size: var(--fs-xs); text-transform: uppercase; letter-spacing: 0.04em; color: var(--text-muted); font-weight: 600; }
    .facts dd { margin: 0; }
  `,
})
export class AccountComponent {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = new FormGroup(
    {
      currentPassword: new FormControl('', { nonNullable: true, validators: Validators.required }),
      newPassword: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(10), Validators.maxLength(72)] }),
      confirm: new FormControl('', { nonNullable: true, validators: Validators.required }),
    },
    { validators: matches },
  );

  protected bad(name: 'currentPassword' | 'newPassword'): true | null {
    const c = this.form.controls[name];
    return c.invalid && (c.touched || c.dirty) ? true : null;
  }

  protected mismatch(): boolean {
    return !!this.form.errors?.['mismatch'] && this.form.controls.confirm.touched;
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    const { currentPassword, newPassword } = this.form.getRawValue();
    this.api.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.saving.set(false);
        this.form.reset();
        this.toast.success('Contraseña actualizada');
      },
      error: (e) => {
        this.saving.set(false);
        this.error.set(toApiError(e).message);
      },
    });
  }
}
