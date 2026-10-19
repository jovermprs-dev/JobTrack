import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, input, signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { toApiError } from '../../core/api-error';
import { ApiService } from '../../core/api.service';
import { Offer, OfferInput, OfferStatus, STATUS_LABELS, STATUS_ORDER, WORK_MODES, WORK_MODE_LABELS, WorkMode } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { todayIso } from '../../shared/format';
import { IconComponent } from '../../shared/icon.component';
import { TagInputComponent } from '../../shared/tag-input.component';

function salaryRange(group: AbstractControl): ValidationErrors | null {
  const min = group.get('salaryMin')?.value as number | null;
  const max = group.get('salaryMax')?.value as number | null;
  return min !== null && max !== null && min > max ? { salaryRange: true } : null;
}

const text = (max: number) => new FormControl('', { nonNullable: true, validators: [Validators.maxLength(max)] });

@Component({
  selector: 'app-offer-form',
  imports: [ReactiveFormsModule, RouterLink, TagInputComponent, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './offer-form.component.html',
  styleUrl: './offer-form.component.scss',
})
export class OfferFormComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  /** Id de la ruta `/ofertas/:id/editar`; ausente al crear. */
  readonly id = input<string>();

  protected readonly workModes = WORK_MODES;
  protected readonly workModeLabels = WORK_MODE_LABELS;
  protected readonly statuses = STATUS_ORDER;
  protected readonly statusLabels = STATUS_LABELS;

  protected readonly loading = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly editing = signal<Offer | null>(null);
  private readonly serverErrors = signal<Record<string, string>>({});

  protected readonly form = new FormGroup(
    {
      title: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(200)] }),
      company: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(200)] }),
      url: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(2000), Validators.pattern(/^https?:\/\/\S+$/i)] }),
      location: text(200),
      workMode: new FormControl<WorkMode | ''>('', { nonNullable: true }),
      contractType: text(80),
      workingHours: text(80),
      publishedDate: new FormControl('', { nonNullable: true }),
      detectedDate: new FormControl(todayIso(), { nonNullable: true }),
      appliedDate: new FormControl('', { nonNullable: true }),
      salaryMin: new FormControl<number | null>(null, [Validators.min(0), Validators.max(100000000)]),
      salaryMax: new FormControl<number | null>(null, [Validators.min(0), Validators.max(100000000)]),
      currency: new FormControl('', { nonNullable: true, validators: [Validators.pattern(/^[A-Za-z]{3}$/)] }),
      minYearsExperience: new FormControl<number | null>(null, [Validators.min(0), Validators.max(60)]),
      skills: new FormControl<string[]>([], { nonNullable: true }),
      description: text(20000),
      requirements: text(20000),
      notes: text(20000),
      status: new FormControl<OfferStatus>('SAVED', { nonNullable: true }),
    },
    { validators: salaryRange },
  );

  ngOnInit(): void {
    const id = this.id();
    if (!id) return;
    this.loading.set(true);
    this.api.getOffer(id).subscribe({
      next: (offer) => {
        this.editing.set(offer);
        this.form.patchValue({
          title: offer.title,
          company: offer.company,
          url: offer.url ?? '',
          location: offer.location ?? '',
          workMode: offer.workMode ?? '',
          contractType: offer.contractType ?? '',
          workingHours: offer.workingHours ?? '',
          publishedDate: offer.publishedDate ?? '',
          detectedDate: offer.detectedDate,
          appliedDate: offer.appliedDate ?? '',
          salaryMin: offer.salaryMin,
          salaryMax: offer.salaryMax,
          currency: offer.currency ?? '',
          minYearsExperience: offer.minYearsExperience,
          skills: offer.skills,
          description: offer.description ?? '',
          requirements: offer.requirements ?? '',
          notes: offer.notes ?? '',
          status: offer.status,
        });
        this.loading.set(false);
      },
      error: (e) => {
        this.loading.set(false);
        this.loadError.set(toApiError(e).message);
      },
    });
  }

  /** Mensaje de error de un campo (cliente o servidor), solo cuando ya se ha tocado. */
  protected err(name: string): string | null {
    const server = this.serverErrors()[name];
    if (server) return server;
    const c = this.form.get(name);
    if (!c || !c.errors || !(c.touched || c.dirty)) return null;
    if (c.errors['required']) return 'Este campo es obligatorio.';
    if (c.errors['maxlength']) return `Máximo ${c.errors['maxlength'].requiredLength} caracteres.`;
    if (c.errors['pattern']) return name === 'url' ? 'La URL debe empezar por http:// o https://' : 'Usa un código de 3 letras (EUR, USD…).';
    if (c.errors['min']) return 'No puede ser negativo.';
    if (c.errors['max']) return 'Valor demasiado alto.';
    return 'Valor no válido.';
  }

  protected invalidAttr(name: string): true | null {
    return this.err(name) ? true : null;
  }

  protected get salaryRangeError(): boolean {
    return !!this.form.errors?.['salaryRange'] && (this.form.controls.salaryMax.touched || this.form.controls.salaryMin.touched);
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    this.serverErrors.set({});
    this.formError.set(null);
    if (this.form.invalid || this.saving()) {
      this.formError.set('Revisa los campos marcados antes de guardar.');
      return;
    }
    const v = this.form.getRawValue();
    const nullable = (s: string) => (s.trim() === '' ? null : s.trim());
    const body: OfferInput = {
      title: v.title.trim(),
      company: v.company.trim(),
      description: nullable(v.description),
      url: nullable(v.url),
      location: nullable(v.location),
      workMode: v.workMode || null,
      publishedDate: nullable(v.publishedDate),
      detectedDate: nullable(v.detectedDate),
      appliedDate: nullable(v.appliedDate),
      salaryMin: v.salaryMin,
      salaryMax: v.salaryMax,
      currency: nullable(v.currency)?.toUpperCase() ?? null,
      contractType: nullable(v.contractType),
      workingHours: nullable(v.workingHours),
      minYearsExperience: v.minYearsExperience,
      skills: v.skills,
      requirements: nullable(v.requirements),
      notes: nullable(v.notes),
      status: this.editing() ? undefined : v.status,
    };
    this.saving.set(true);
    const editing = this.editing();
    const request$ = editing ? this.api.updateOffer(editing.id, body) : this.api.createOffer(body);
    request$.subscribe({
      next: (saved) => {
        this.toast.success(editing ? 'Oferta actualizada' : 'Oferta creada');
        void this.router.navigate(['/ofertas', saved.id]);
      },
      error: (e) => {
        this.saving.set(false);
        const apiError = toApiError(e);
        this.serverErrors.set(apiError.fieldErrors);
        this.formError.set(Object.keys(apiError.fieldErrors).length ? 'El servidor ha rechazado algunos campos.' : apiError.message);
      },
    });
  }
}
