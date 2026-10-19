import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { toApiError } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';
import { IconComponent } from '../../shared/icon.component';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './auth-card.scss',
  template: `
    <section class="aside" aria-hidden="true">
      <div class="brand"><span class="logo">J</span> JobTrack</div>
      <p class="tagline">Tu búsqueda de empleo, bajo control.</p>
      <p>Organiza ofertas, sigue cada candidatura y entiende cómo avanza tu búsqueda con datos reales.</p>
      <ul>
        <li><app-icon name="check" /> Estados e historial de cada candidatura</li>
        <li><app-icon name="check" /> Compatibilidad entre tu perfil y cada oferta</li>
        <li><app-icon name="check" /> Estadísticas de tu búsqueda</li>
      </ul>
    </section>
    <main class="panel">
      <form class="form stack" [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div>
          <h1>Inicia sesión</h1>
          <p class="lead">Accede a tu panel de JobTrack.</p>
        </div>

        @if (expired) {
          <div class="alert alert--warning" role="status">Tu sesión ha caducado. Inicia sesión de nuevo.</div>
        }
        @if (error()) {
          <div class="alert alert--error" role="alert">{{ error() }}</div>
        }

        <div class="field">
          <label for="email">Email</label>
          <input id="email" class="input" type="email" formControlName="email" autocomplete="email" required
            [attr.aria-invalid]="invalid('email')" aria-describedby="email-err" />
          @if (invalid('email')) {
            <span id="email-err" class="error">Introduce un email válido.</span>
          }
        </div>
        <div class="field">
          <label for="password">Contraseña</label>
          <input id="password" class="input" type="password" formControlName="password" autocomplete="current-password" required
            [attr.aria-invalid]="invalid('password')" aria-describedby="pw-err" />
          @if (invalid('password')) {
            <span id="pw-err" class="error">Introduce tu contraseña.</span>
          }
        </div>
        <button type="submit" class="btn btn--primary" [disabled]="loading()">{{ loading() ? 'Entrando…' : 'Iniciar sesión' }}</button>
        <p class="swap small">¿No tienes cuenta? <a routerLink="/registro">Crear cuenta</a></p>
      </form>
    </main>
  `,
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly expired = this.route.snapshot.queryParamMap.has('expired');
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected invalid(name: 'email' | 'password'): boolean {
    const c = this.form.controls[name];
    return c.invalid && (c.touched || c.dirty);
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.loading()) return;
    this.loading.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: () => {
        const target = this.route.snapshot.queryParamMap.get('returnUrl');
        // Solo rutas internas: evita redirecciones abiertas.
        void this.router.navigateByUrl(target && target.startsWith('/') && !target.startsWith('//') ? target : '/dashboard');
      },
      error: (e) => {
        this.loading.set(false);
        this.error.set(toApiError(e).message);
      },
    });
  }
}
