import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import { Subject, catchError, debounceTime, merge, of, switchMap, tap } from 'rxjs';
import { toApiError } from '../../core/api-error';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Offer, OfferFilters, Page, STATUS_LABELS, STATUS_ORDER, WORK_MODES, WORK_MODE_LABELS } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { formatDate, formatSalary } from '../../shared/format';
import { IconComponent } from '../../shared/icon.component';
import { PaginationComponent } from '../../shared/pagination.component';
import { ScoreRingComponent } from '../../shared/score-ring.component';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { ImportDialogComponent } from './import-dialog.component';

const PAGE_SIZE = 20;

const SORTS = [
  { value: 'detectedDate,desc', label: 'Más recientes' },
  { value: 'detectedDate,asc', label: 'Más antiguas' },
  { value: 'compatibilityScore,desc', label: 'Mayor compatibilidad' },
  { value: 'company,asc', label: 'Empresa (A–Z)' },
  { value: 'title,asc', label: 'Puesto (A–Z)' },
];

@Component({
  selector: 'app-offer-list',
  imports: [ReactiveFormsModule, RouterLink, IconComponent, PaginationComponent, ScoreRingComponent, StatusBadgeComponent, ImportDialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './offer-list.component.html',
  styleUrl: './offer-list.component.scss',
})
export class OfferListComponent implements OnInit {
  private readonly api = inject(ApiService);
  /** En la demo de solo lectura no se ofrecen importar, crear ni marcar favoritas. */
  protected readonly demo = inject(AuthService).isDemo;
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  /** Vienen de los datos de la ruta (/favoritas). */
  readonly favoritesOnly = input(false);

  protected readonly statuses = STATUS_ORDER;
  protected readonly statusLabels = STATUS_LABELS;
  protected readonly workModes = WORK_MODES;
  protected readonly workModeLabels = WORK_MODE_LABELS;
  protected readonly sorts = SORTS;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly result = signal<Page<Offer> | null>(null);
  protected readonly hasFilters = signal(false);
  protected readonly importOpen = signal(false);
  protected readonly exporting = signal(false);
  private readonly reload$ = new Subject<void>();

  protected readonly form = new FormGroup({
    q: new FormControl('', { nonNullable: true }),
    status: new FormControl('', { nonNullable: true }),
    workMode: new FormControl('', { nonNullable: true }),
    company: new FormControl('', { nonNullable: true }),
    location: new FormControl('', { nonNullable: true }),
    favorite: new FormControl(false, { nonNullable: true }),
    sort: new FormControl(SORTS[0].value, { nonNullable: true }),
  });

  ngOnInit(): void {
    // Se inicia aquí (no en el constructor) para que `favoritesOnly` ya esté enlazado desde los datos de la ruta.
    // La URL es la fuente de verdad: el formulario escribe en ella y los datos se cargan al cambiar.
    merge(this.route.queryParams, this.reload$.pipe(switchMap(() => of(this.route.snapshot.queryParams))))
      .pipe(
        tap((params) => {
          this.syncForm(params);
          this.loading.set(true);
          this.error.set(null);
        }),
        switchMap((params) =>
          this.api.listOffers(this.toFilters(params)).pipe(
            catchError((e) => {
              this.error.set(toApiError(e).message);
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((page) => {
        this.loading.set(false);
        if (page) {
          // Si la página pedida quedó vacía (p. ej. al quitar la última favorita de la última página), se retrocede.
          if (page.content.length === 0 && page.totalElements > 0 && page.page > 0) {
            this.goToPage(page.totalPages - 1);
            return;
          }
          this.result.set(page);
        }
      });

    this.form.valueChanges.pipe(debounceTime(300), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.pushFilters());
  }

  private toFilters(params: Params): OfferFilters {
    return {
      q: params['q'],
      status: params['status'],
      workMode: params['workMode'],
      company: params['company'],
      location: params['location'],
      favorite: this.favoritesOnly() ? true : params['favorite'] === 'true' ? true : undefined,
      sort: params['sort'] ?? SORTS[0].value,
      page: Number(params['page'] ?? 0) || 0,
      size: PAGE_SIZE,
    };
  }

  private syncForm(params: Params): void {
    // Los campos de texto se guardan recortados en la URL: no se sobrescribe lo que se está escribiendo (espacios finales).
    const keep = (control: FormControl<string>, value: string | undefined) => (control.value.trim() === (value ?? '') ? control.value : (value ?? ''));
    this.form.patchValue(
      {
        q: keep(this.form.controls.q, params['q']),
        status: params['status'] ?? '',
        workMode: params['workMode'] ?? '',
        company: keep(this.form.controls.company, params['company']),
        location: keep(this.form.controls.location, params['location']),
        favorite: params['favorite'] === 'true',
        sort: params['sort'] ?? SORTS[0].value,
      },
      { emitEvent: false },
    );
    this.hasFilters.set(['q', 'status', 'workMode', 'company', 'location', 'favorite'].some((k) => !!params[k]));
  }

  private pushFilters(): void {
    const v = this.form.getRawValue();
    const current = this.route.snapshot.queryParams;
    const next: Params = {
      q: v.q.trim() || null,
      status: v.status || null,
      workMode: v.workMode || null,
      company: v.company.trim() || null,
      location: v.location.trim() || null,
      favorite: v.favorite && !this.favoritesOnly() ? 'true' : null,
      sort: v.sort === SORTS[0].value ? null : v.sort,
      page: null,
    };
    const unchanged = Object.keys(next).every((k) => (next[k] ?? undefined) === (current[k] ?? undefined));
    if (!unchanged) {
      void this.router.navigate([], { relativeTo: this.route, queryParams: next, replaceUrl: true });
    }
  }

  protected goToPage(page: number): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: { page: page || null }, queryParamsHandling: 'merge' });
  }

  protected clearFilters(): void {
    this.form.reset({ sort: SORTS[0].value });
    void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }

  protected retry(): void {
    this.reload$.next();
  }

  protected toggleFavorite(offer: Offer): void {
    this.api.setFavorite(offer.id, !offer.favorite).subscribe({
      next: (updated) => {
        if (this.favoritesOnly() && !updated.favorite) {
          this.reload$.next(); // sale de la vista de favoritas
        } else {
          this.result.update((r) => (r ? { ...r, content: r.content.map((o) => (o.id === updated.id ? updated : o)) } : r));
        }
        this.toast.success(updated.favorite ? 'Añadida a favoritas' : 'Quitada de favoritas');
      },
      error: (e) => this.toast.error(toApiError(e).message),
    });
  }

  protected exportCsv(): void {
    this.exporting.set(true);
    this.api.exportCsv().subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'jobtrack-ofertas.csv';
        a.click();
        URL.revokeObjectURL(url);
        this.exporting.set(false);
      },
      error: (e) => {
        this.exporting.set(false);
        this.toast.error(toApiError(e).message);
      },
    });
  }

  protected onImported(): void {
    this.importOpen.set(false);
    this.reload$.next();
  }

  protected salary(o: Offer): string | null {
    return formatSalary(o);
  }
  protected date(value: string): string {
    return formatDate(value);
  }
}
