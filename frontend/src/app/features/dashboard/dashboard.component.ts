import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { toApiError } from '../../core/api-error';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { STATUS_LABELS, Stats } from '../../core/models';
import { ActivityListComponent } from '../../shared/activity-list.component';
import { BarChartComponent, DonutChartComponent } from '../../shared/charts.component';
import { formatShortDate } from '../../shared/format';
import { IconComponent } from '../../shared/icon.component';
import { ScoreRingComponent } from '../../shared/score-ring.component';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, IconComponent, DonutChartComponent, BarChartComponent, ScoreRingComponent, ActivityListComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly stats = signal<Stats | null>(null);

  protected readonly kpis = computed(() => {
    const t = this.stats()?.totals;
    return t
      ? [
          { label: 'Ofertas guardadas', value: t.saved, hint: 'Total en tu cuenta' },
          { label: 'Candidaturas enviadas', value: t.applied, hint: 'Con fecha de solicitud' },
          { label: 'Procesos activos', value: t.active, hint: 'Enviadas, en proceso o entrevista' },
          { label: 'Entrevistas', value: t.interviews, hint: 'Ofertas que llegaron a entrevista' },
          { label: 'Ofertas recibidas', value: t.offersReceived, hint: 'Propuestas de empresas' },
        ]
      : [];
  });

  protected readonly slices = computed(() =>
    (this.stats()?.byStatus ?? []).map((s) => ({ label: STATUS_LABELS[s.status], value: s.count, color: `var(--st-${s.status.toLowerCase()})` })),
  );
  protected readonly weekly = computed(() =>
    (this.stats()?.applicationsOverTime ?? []).map((w) => ({ label: formatShortDate(w.weekStart), value: w.count })),
  );
  protected readonly totalApplications12w = computed(() => this.weekly().reduce((s, w) => s + w.value, 0));

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.stats().subscribe({
      next: (s) => {
        this.stats.set(s);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(toApiError(e).message);
        this.loading.set(false);
      },
    });
  }
}
