import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { tap } from 'rxjs/operators';
import { ApiService } from './api.service';
import { User } from './models';

/** Estado de sesión. La sesión real vive en una cookie HttpOnly; aquí solo se guarda el usuario en memoria. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly _user = signal<User | null>(null);

  readonly user = this._user.asReadonly();
  readonly isAuthenticated = computed(() => this._user() !== null);
  /** Instancia pública de solo lectura: el servidor ya rechaza toda escritura; la interfaz solo evita ofrecerla. */
  readonly isDemo = computed(() => this._user()?.demo === true);

  /** Consulta la sesión al arrancar (también obtiene la cookie XSRF-TOKEN). */
  async restore(): Promise<void> {
    try {
      this._user.set(await firstValueFrom(this.api.me()));
    } catch {
      this._user.set(null);
    }
  }

  login(email: string, password: string) {
    return this.api.login(email, password).pipe(tap((u) => this._user.set(u)));
  }

  register(email: string, password: string, displayName: string) {
    return this.api.register(email, password, displayName).pipe(tap((u) => this._user.set(u)));
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.api.logout());
    } finally {
      this._user.set(null);
      await this.router.navigate(['/login']);
    }
  }

  /** Se invoca cuando la API responde 401 a una petición privada. */
  expireSession(): void {
    this._user.set(null);
  }
}
