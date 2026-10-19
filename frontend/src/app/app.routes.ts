import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    title: 'Iniciar sesión · JobTrack',
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'registro',
    canActivate: [guestGuard],
    title: 'Crear cuenta · JobTrack',
    loadComponent: () => import('./features/auth/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell.component').then((m) => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        title: 'Panel · JobTrack',
        loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        path: 'ofertas',
        title: 'Ofertas · JobTrack',
        loadComponent: () => import('./features/offers/offer-list.component').then((m) => m.OfferListComponent),
      },
      {
        path: 'favoritas',
        title: 'Favoritas · JobTrack',
        data: { favoritesOnly: true },
        loadComponent: () => import('./features/offers/offer-list.component').then((m) => m.OfferListComponent),
      },
      {
        path: 'ofertas/nueva',
        title: 'Nueva oferta · JobTrack',
        loadComponent: () => import('./features/offers/offer-form.component').then((m) => m.OfferFormComponent),
      },
      {
        path: 'ofertas/:id/editar',
        title: 'Editar oferta · JobTrack',
        loadComponent: () => import('./features/offers/offer-form.component').then((m) => m.OfferFormComponent),
      },
      {
        path: 'ofertas/:id',
        title: 'Detalle de la oferta · JobTrack',
        loadComponent: () => import('./features/offers/offer-detail.component').then((m) => m.OfferDetailComponent),
      },
      {
        path: 'estadisticas',
        title: 'Estadísticas · JobTrack',
        loadComponent: () => import('./features/stats/stats.component').then((m) => m.StatsComponent),
      },
      {
        path: 'perfil',
        title: 'Perfil profesional · JobTrack',
        loadComponent: () => import('./features/profile/profile.component').then((m) => m.ProfileComponent),
      },
      {
        path: 'cuenta',
        title: 'Cuenta · JobTrack',
        loadComponent: () => import('./features/account/account.component').then((m) => m.AccountComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
