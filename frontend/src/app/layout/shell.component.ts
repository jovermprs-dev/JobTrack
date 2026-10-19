import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../core/auth.service';
import { IconComponent } from '../shared/icon.component';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown.escape)': 'menuOpen.set(false)' },
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  protected readonly auth = inject(AuthService);
  protected readonly menuOpen = signal(false);

  protected readonly nav = [
    { path: '/dashboard', label: 'Panel', icon: 'dashboard' },
    { path: '/ofertas', label: 'Ofertas', icon: 'briefcase' },
    { path: '/favoritas', label: 'Favoritas', icon: 'star' },
    { path: '/estadisticas', label: 'Estadísticas', icon: 'chart' },
    { path: '/perfil', label: 'Perfil profesional', icon: 'user' },
  ];

  constructor() {
    let first = true;
    inject(Router)
      .events.pipe(filter((e) => e instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => {
        this.menuOpen.set(false);
        // Tras cada navegación el foco pasa al contenido (accesibilidad con teclado y lectores de pantalla).
        if (!first) document.getElementById('main')?.focus({ preventScroll: true });
        first = false;
      });
  }

  /** El ancla `#main` chocaría con `<base href="/">`: se gestiona el foco a mano. */
  protected skip(event: Event): void {
    event.preventDefault();
    document.getElementById('main')?.focus();
  }

  protected logout(): void {
    void this.auth.logout();
  }
}
