import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { toApiError } from '../../core/api-error';
import { ApiService } from '../../core/api.service';
import { Profile, ProfileInput, WORK_MODES, WORK_MODE_LABELS, WorkMode } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { IconComponent } from '../../shared/icon.component';
import { TagInputComponent } from '../../shared/tag-input.component';

const LEVELS = ['Nativo', 'C2', 'C1', 'B2', 'B1', 'A2', 'A1'];

@Component({
  selector: 'app-profile',
  imports: [ReactiveFormsModule, TagInputComponent, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
})
export class ProfileComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);

  protected readonly workModes = WORK_MODES;
  protected readonly workModeLabels = WORK_MODE_LABELS;
  protected readonly levels = LEVELS;
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly updatedAt = signal<string | null>(null);

  protected readonly form = new FormGroup({
    degree: new FormControl('', { nonNullable: true, validators: Validators.maxLength(200) }),
    experienceSummary: new FormControl('', { nonNullable: true, validators: Validators.maxLength(5000) }),
    yearsExperience: new FormControl<number | null>(null, [Validators.min(0), Validators.max(60)]),
    skills: new FormControl<string[]>([], { nonNullable: true }),
    languages: new FormArray<FormGroup<{ language: FormControl<string>; level: FormControl<string> }>>([]),
    location: new FormControl('', { nonNullable: true, validators: Validators.maxLength(200) }),
    preferredWorkMode: new FormControl<WorkMode | ''>('', { nonNullable: true }),
    desiredRoles: new FormControl<string[]>([], { nonNullable: true }),
    expectedSalary: new FormControl<number | null>(null, [Validators.min(0)]),
    salaryCurrency: new FormControl('', { nonNullable: true, validators: Validators.pattern(/^[A-Za-z]{3}$/) }),
    preferences: new FormControl('', { nonNullable: true, validators: Validators.maxLength(5000) }),
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.api.getProfile().subscribe({
      next: (p) => {
        this.fill(p);
        this.loading.set(false);
      },
      error: (e) => {
        this.loading.set(false);
        this.loadError.set(toApiError(e).message);
      },
    });
  }

  private fill(p: Profile): void {
    this.form.patchValue({
      degree: p.degree ?? '',
      experienceSummary: p.experienceSummary ?? '',
      yearsExperience: p.yearsExperience,
      skills: p.skills,
      location: p.location ?? '',
      preferredWorkMode: p.preferredWorkMode ?? '',
      desiredRoles: p.desiredRoles,
      expectedSalary: p.expectedSalary,
      salaryCurrency: p.salaryCurrency ?? '',
      preferences: p.preferences ?? '',
    });
    this.form.controls.languages.clear();
    p.languages.forEach((l) => this.addLanguage(l.language, l.level));
    this.updatedAt.set(p.updatedAt);
  }

  protected addLanguage(language = '', level = 'B2'): void {
    this.form.controls.languages.push(
      new FormGroup({
        language: new FormControl(language, { nonNullable: true, validators: [Validators.required, Validators.maxLength(60)] }),
        level: new FormControl(level, { nonNullable: true, validators: Validators.required }),
      }),
    );
  }

  protected removeLanguage(index: number): void {
    this.form.controls.languages.removeAt(index);
  }

  protected bad(name: string): true | null {
    const c = this.form.get(name);
    return c && c.invalid && (c.touched || c.dirty) ? true : null;
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    this.formError.set(null);
    if (this.form.invalid || this.saving()) {
      this.formError.set('Revisa los campos marcados antes de guardar.');
      return;
    }
    const v = this.form.getRawValue();
    const nullable = (s: string) => (s.trim() === '' ? null : s.trim());
    const body: ProfileInput = {
      degree: nullable(v.degree),
      experienceSummary: nullable(v.experienceSummary),
      yearsExperience: v.yearsExperience,
      skills: v.skills,
      languages: v.languages.map((l) => ({ language: l.language.trim(), level: l.level })).filter((l) => l.language),
      location: nullable(v.location),
      preferredWorkMode: v.preferredWorkMode || null,
      desiredRoles: v.desiredRoles,
      expectedSalary: v.expectedSalary,
      salaryCurrency: nullable(v.salaryCurrency)?.toUpperCase() ?? null,
      preferences: nullable(v.preferences),
    };
    this.saving.set(true);
    this.api.saveProfile(body).subscribe({
      next: (p) => {
        this.saving.set(false);
        this.updatedAt.set(p.updatedAt);
        this.toast.success('Perfil guardado. Se ha recalculado la compatibilidad de tus ofertas.');
      },
      error: (e) => {
        this.saving.set(false);
        this.formError.set(toApiError(e).message);
      },
    });
  }
}
