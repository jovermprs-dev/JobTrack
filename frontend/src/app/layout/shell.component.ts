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
    inject(Router)
      .events.pipe(filter((e) => e instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => this.menuOpen.set(false));
  }

  protected logout(): void {
    void this.auth.logout();
  }
}
