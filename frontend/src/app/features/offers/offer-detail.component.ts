import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { toApiError } from '../../core/api-error';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ConfirmService } from '../../core/confirm.service';
import { Compatibility, HistoryEntry, Offer, OfferStatus, STATUS_LABELS, STATUS_ORDER, WORK_MODE_LABELS } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { DialogComponent } from '../../shared/dialog.component';
import { formatDate, formatDateTime, formatSalary } from '../../shared/format';
import { IconComponent } from '../../shared/icon.component';
import { ScoreRingComponent } from '../../shared/score-ring.component';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

@Component({
  selector: 'app-offer-detail',
  imports: [RouterLink, ReactiveFormsModule, IconComponent, StatusBadgeComponent, ScoreRingComponent, DialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './offer-detail.component.html',
  styleUrl: './offer-detail.component.scss',
})
export class OfferDetailComponent implements OnInit {
  private readonly api = inject(ApiService);
  /** En la demo de solo lectura no se ofrecen acciones de escritura. */
  protected readonly demo = inject(AuthService).isDemo;
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);

  readonly id = input.required<string>();

  protected readonly workModeLabels = WORK_MODE_LABELS;
  protected readonly statusLabels = STATUS_LABELS;
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly offer = signal<Offer | null>(null);
  protected readonly history = signal<HistoryEntry[]>([]);
  protected readonly compat = signal<Compatibility | null>(null);
  protected readonly statusOpen = signal(false);
  protected readonly savingStatus = signal(false);
  protected readonly statusError = signal<string | null>(null);

  protected readonly statusForm = new FormGroup({
    status: new FormControl<OfferStatus>('SAVED', { nonNullable: true, validators: Validators.required }),
    note: new FormControl('', { nonNullable: true, validators: Validators.maxLength(1000) }),
  });

  protected readonly selectableStatuses = computed(() => STATUS_ORDER.filter((s) => s !== this.offer()?.status));
  protected readonly salary = computed(() => (this.offer() ? formatSalary(this.offer()!) : null));

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    // La oferta es imprescindible; el historial y la compatibilidad son secundarios y no deben ocultarla si fallan.
    forkJoin({
      offer: this.api.getOffer(this.id()),
      history: this.api.history(this.id()).pipe(catchError(() => of([] as HistoryEntry[]))),
      compat: this.api.compatibility(this.id()).pipe(catchError(() => of(null))),
    }).subscribe({
      next: ({ offer, history, compat }) => {
        this.offer.set(offer);
        this.history.set(history);
        this.compat.set(compat);
        this.loading.set(false);
      },
      error: (e) => {
        this.loading.set(false);
        this.error.set(toApiError(e).message);
      },
    });
  }

  protected openStatus(): void {
    const first = this.selectableStatuses()[0];
    this.statusForm.reset({ status: first, note: '' });
    this.statusError.set(null);
    this.statusOpen.set(true);
  }

  protected saveStatus(): void {
    if (this.statusForm.invalid || this.savingStatus()) return;
    const { status, note } = this.statusForm.getRawValue();
    this.savingStatus.set(true);
    this.api.changeStatus(this.id(), status, note.trim() || null).subscribe({
      next: (offer) => {
        this.savingStatus.set(false);
        this.statusOpen.set(false);
        this.offer.set(offer);
        this.toast.success(`Estado actualizado: ${STATUS_LABELS[status]}`);
        this.api.history(this.id()).subscribe({ next: (h) => this.history.set(h), error: () => this.toast.error('No se pudo recargar el historial.') });
      },
      error: (e) => {
        this.savingStatus.set(false);
        this.statusError.set(toApiError(e).message);
      },
    });
  }

  protected toggleFavorite(): void {
    const o = this.offer();
    if (!o) return;
    this.api.setFavorite(o.id, !o.favorite).subscribe({
      next: (updated) => {
        this.offer.set(updated);
        this.toast.success(updated.favorite ? 'Añadida a favoritas' : 'Quitada de favoritas');
      },
      error: (e) => this.toast.error(toApiError(e).message),
    });
  }

  protected async remove(): Promise<void> {
    const o = this.offer();
    if (!o) return;
    const ok = await this.confirm.ask({
      title: 'Eliminar oferta',
      message: `Se eliminará «${o.title}» de ${o.company} junto con todo su historial. Esta acción no se puede deshacer.`,
      confirmLabel: 'Eliminar',
      danger: true,
    });
    if (!ok) return;
    this.api.deleteOffer(o.id).subscribe({
      next: () => {
        this.toast.success('Oferta eliminada');
        void this.router.navigate(['/ofertas']);
      },
      error: (e) => this.toast.error(toApiError(e).message),
    });
  }

  protected date(v: string | null): string {
    return formatDate(v);
  }
  protected dateTime(v: string): string {
    return formatDateTime(v);
  }
  protected pct(v: number): number {
    return Math.round(v * 100);
  }
}
