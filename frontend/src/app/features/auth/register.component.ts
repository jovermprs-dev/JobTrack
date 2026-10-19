import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { toApiError } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { IconComponent } from '../../shared/icon.component';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './auth-card.scss',
  template: `
    <section class="aside" aria-hidden="true">
      <div class="brand"><span class="logo">J</span> JobTrack</div>
      <h1>Empieza a ordenar tu búsqueda.</h1>
      <p>Crea tu cuenta gratis. Tus datos son privados: solo tú puedes verlos.</p>
      <ul>
        <li><app-icon name="check" /> Sin servicios externos ni rastreo</li>
        <li><app-icon name="check" /> Exporta tus ofertas a CSV cuando quieras</li>
      </ul>
    </section>
    <section class="panel">
      <form class="form stack" [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div>
          <h2>Crea tu cuenta</h2>
          <p class="lead">Solo necesitas un email y una contraseña.</p>
        </div>

        @if (error()) {
          <div class="alert alert--error" role="alert">{{ error() }}</div>
        }

        <div class="field">
          <label for="displayName">Nombre</label>
          <input id="displayName" class="input" formControlName="displayName" autocomplete="name" maxlength="100" required
            [attr.aria-invalid]="invalid('displayName')" aria-describedby="name-err" />
          @if (invalid('displayName')) {
            <span id="name-err" class="error">Indica tu nombre.</span>
          }
        </div>
        <div class="field">
          <label for="email">Email</label>
          <input id="email" class="input" type="email" formControlName="email" autocomplete="email" required
            [attr.aria-invalid]="invalid('email') || fieldError('email') ? true : null" aria-describedby="email-err" />
          @if (invalid('email')) {
            <span id="email-err" class="error">Introduce un email válido.</span>
          } @else if (fieldError('email')) {
            <span id="email-err" class="error">{{ fieldError('email') }}</span>
          }
        </div>
        <div class="field">
          <label for="password">Contraseña</label>
          <input id="password" class="input" type="password" formControlName="password" autocomplete="new-password" required
            [attr.aria-invalid]="invalid('password')" aria-describedby="pw-hint pw-err" />
          <span id="pw-hint" class="hint">Entre 10 y 72 caracteres.</span>
          @if (invalid('password')) {
            <span id="pw-err" class="error">La contraseña debe tener entre 10 y 72 caracteres.</span>
          }
        </div>
        <button type="submit" class="btn btn--primary" [disabled]="loading()">{{ loading() ? 'Creando cuenta…' : 'Crear cuenta' }}</button>
        <p class="swap small">¿Ya tienes cuenta? <a routerLink="/login">Inicia sesión</a></p>
      </form>
    </section>
  `,
})
export class RegisterComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly serverFields = signal<Record<string, string>>({});
  protected readonly form = this.fb.nonNullable.group({
    displayName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(72)]],
  });

  protected invalid(name: 'displayName' | 'email' | 'password'): boolean {
    const c = this.form.controls[name];
    return c.invalid && (c.touched || c.dirty);
  }

  protected fieldError(name: string): string | undefined {
    return this.serverFields()[name];
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.loading()) return;
    this.loading.set(true);
    this.error.set(null);
    this.serverFields.set({});
    const { email, password, displayName } = this.form.getRawValue();
    this.auth.register(email, password, displayName).subscribe({
      next: () => {
        this.toast.success('Cuenta creada. ¡Bienvenido a JobTrack!');
        void this.router.navigateByUrl('/dashboard');
      },
      error: (e) => {
        this.loading.set(false);
        const err = toApiError(e);
        this.serverFields.set(err.fieldErrors);
        this.error.set(err.status === 409 ? err.message : Object.keys(err.fieldErrors).length ? null : err.message);
      },
    });
  }
}
