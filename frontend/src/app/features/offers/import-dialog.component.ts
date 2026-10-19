import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { toApiError } from '../../core/api-error';
import { ApiService } from '../../core/api.service';
import { ImportResult, STATUS_LABELS } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { DialogComponent } from '../../shared/dialog.component';

/** Importación CSV en dos pasos: validar (vista previa) y confirmar. Todo o nada. */
@Component({
  selector: 'app-import-dialog',
  imports: [DialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-dialog [open]="open()" labelledBy="import-title" (closed)="reset(); closed.emit()">
      <h2 id="import-title">Importar ofertas desde CSV</h2>
      <p class="muted small intro">
        Usa el mismo formato que la exportación. Columnas obligatorias: <code>title</code> y <code>company</code>. Máximo 1000 filas y 2 MB.
        Si alguna fila es inválida no se importa ninguna.
      </p>

      <div class="field">
        <label for="csv-file">Archivo CSV (UTF-8)</label>
        <input id="csv-file" class="input" type="file" accept=".csv,text/csv" (change)="onFile($event)" />
      </div>

      @if (busy()) {
        <div class="loading"><span class="spinner"></span> Procesando…</div>
      }
      @if (error()) {
        <div class="alert alert--error top" role="alert">{{ error() }}</div>
      }

      @if (result(); as r) {
        <div class="top" aria-live="polite">
          <p>
            <strong>{{ r.totalRows }}</strong> filas leídas · <strong>{{ r.validRows }}</strong> válidas ·
            <strong>{{ r.errors.length }}</strong> errores
          </p>
          @if (r.errors.length) {
            <div class="alert alert--error top">
              <p>Corrige estos errores y vuelve a subir el archivo:</p>
              <ul class="errs">
                @for (e of r.errors.slice(0, 20); track $index) {
                  <li>Fila {{ e.row }} · <code>{{ e.field }}</code>: {{ e.message }}</li>
                }
              </ul>
              @if (r.errors.length > 20) { <p class="small">…y {{ r.errors.length - 20 }} errores más.</p> }
            </div>
          } @else if (r.preview.length) {
            <div class="table-wrap top">
              <table class="table">
                <caption class="sr-only">Vista previa de las primeras filas</caption>
                <thead><tr><th scope="col">Fila</th><th scope="col">Puesto</th><th scope="col">Empresa</th><th scope="col">Estado</th></tr></thead>
                <tbody>
                  @for (p of r.preview; track p.row) {
                    <tr><td>{{ p.row }}</td><td>{{ p.title }}</td><td>{{ p.company }}</td><td>{{ statusLabels[p.status] }}</td></tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>
      }

      <div class="form-actions">
        <button type="button" class="btn" (click)="cancel()">Cancelar</button>
        <button type="button" class="btn btn--primary" [disabled]="!canImport()" (click)="confirmImport()">
          Importar{{ result() ? ' ' + result()!.validRows + ' ofertas' : '' }}
        </button>
      </div>
    </app-dialog>
  `,
  styles: `
    .intro { margin: var(--s2) 0 var(--s4); }
    .top { margin-top: var(--s3); }
    .errs { display: grid; gap: var(--s1); margin-top: var(--s2); max-height: 10rem; overflow: auto; }
    code { background: var(--surface-2); padding: 0 4px; border-radius: 4px; }
  `,
})
export class ImportDialogComponent {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);

  readonly open = input.required<boolean>();
  readonly closed = output<void>();
  readonly imported = output<void>();

  protected readonly statusLabels = STATUS_LABELS;
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly result = signal<ImportResult | null>(null);
  private file: File | null = null;

  protected canImport(): boolean {
    const r = this.result();
    return !this.busy() && !!r && r.errors.length === 0 && r.validRows > 0;
  }

  protected onFile(event: Event): void {
    this.file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.result.set(null);
    this.error.set(null);
    if (!this.file) return;
    this.busy.set(true);
    this.api.importCsv(this.file, true).subscribe({
      next: (r) => {
        this.busy.set(false);
        this.result.set(r);
      },
      error: (e) => this.fail(e),
    });
  }

  protected confirmImport(): void {
    if (!this.file || !this.canImport()) return;
    this.busy.set(true);
    this.api.importCsv(this.file, false).subscribe({
      next: (r) => {
        this.busy.set(false);
        this.toast.success(`${r.imported} ofertas importadas`);
        this.reset();
        this.imported.emit();
      },
      error: (e) => this.fail(e),
    });
  }

  protected cancel(): void {
    this.reset();
    this.closed.emit();
  }

  protected reset(): void {
    this.file = null;
    this.result.set(null);
    this.error.set(null);
    this.busy.set(false);
  }

  private fail(e: unknown): void {
    this.busy.set(false);
    if (e instanceof HttpErrorResponse && e.status === 422 && e.error?.errors) {
      this.result.set(e.error as ImportResult);
      return;
    }
    this.error.set(toApiError(e).message);
  }
}
