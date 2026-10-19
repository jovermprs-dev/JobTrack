import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { toApiError } from '../../core/api-error';
import { ApiService } from '../../core/api.service';
import { STATUS_LABELS, STATUS_ORDER, Stats } from '../../core/models';
import { ActivityListComponent } from '../../shared/activity-list.component';
import { BarChartComponent, HBarListComponent } from '../../shared/charts.component';
import { formatShortDate } from '../../shared/format';
import { IconComponent } from '../../shared/icon.component';

@Component({
  selector: 'app-stats',
  imports: [RouterLink, IconComponent, BarChartComponent, HBarListComponent, ActivityListComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './stats.component.html',
  styleUrl: './stats.component.scss',
})
export class StatsComponent implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly stats = signal<Stats | null>(null);

  protected readonly byStatus = computed(() =>
    STATUS_ORDER.map((s) => ({
      label: STATUS_LABELS[s],
      value: this.stats()?.byStatus.find((b) => b.status === s)?.count ?? 0,
      color: `var(--st-${s.toLowerCase()})`,
    })),
  );
  protected readonly weekly = computed(() =>
    (this.stats()?.applicationsOverTime ?? []).map((w) => ({ label: formatShortDate(w.weekStart), value: w.count })),
  );
  protected readonly totalWeekly = computed(() => this.weekly().reduce((a, w) => a + w.value, 0));
  protected readonly companies = computed(() => (this.stats()?.topCompanies ?? []).map((c) => ({ label: c.company, value: c.count })));

  protected readonly rate = computed(() => {
    const r = this.stats()?.responseRate;
    return r?.value == null ? null : Math.round(r.value * 100);
  });

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
