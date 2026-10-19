import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { IconComponent } from './icon.component';

@Component({
  selector: 'app-pagination',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (totalElements() > 0) {
      <nav class="pager" aria-label="Paginación">
        <p class="muted small" aria-live="polite">Mostrando {{ from() }}–{{ to() }} de {{ totalElements() }}</p>
        <div class="row">
          <button type="button" class="btn btn--sm" [disabled]="page() === 0" (click)="pageChange.emit(page() - 1)">
            <app-icon name="left" [size]="16" /> Anterior
          </button>
          <span class="small">Página {{ page() + 1 }} de {{ totalPages() }}</span>
          <button type="button" class="btn btn--sm" [disabled]="page() + 1 >= totalPages()" (click)="pageChange.emit(page() + 1)">
            Siguiente <app-icon name="right" [size]="16" />
          </button>
        </div>
      </nav>
    }
  `,
  styles: `.pager { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--s3); padding-top: var(--s4); }`,
})
export class PaginationComponent {
  readonly page = input.required<number>();
  readonly size = input.required<number>();
  readonly totalElements = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly pageChange = output<number>();

  protected readonly from = computed(() => this.page() * this.size() + 1);
  protected readonly to = computed(() => Math.min((this.page() + 1) * this.size(), this.totalElements()));
}
