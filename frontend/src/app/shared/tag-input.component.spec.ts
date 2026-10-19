import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TagInputComponent } from './tag-input.component';

@Component({
  imports: [ReactiveFormsModule, TagInputComponent],
  template: `<app-tag-input inputId="t" [formControl]="ctrl" />`,
})
class Host {
  ctrl = new FormControl<string[]>(['Java'], { nonNullable: true });
}

describe('TagInputComponent', () => {
  function setup() {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    const type = (text: string, key = 'Enter') => {
      input.value = text;
      input.dispatchEvent(new Event('input'));
      input.dispatchEvent(new KeyboardEvent('keydown', { key, cancelable: true }));
      fixture.detectChanges();
    };
    return { fixture, input, type, ctrl: fixture.componentInstance.ctrl };
  }

  it('muestra los valores iniciales como etiquetas', () => {
    const { fixture } = setup();
    expect(fixture.nativeElement.textContent).toContain('Java');
  });

  it('añade con Enter, admite varias separadas por coma y evita duplicados sin importar mayúsculas', () => {
    const { type, ctrl } = setup();
    type('Angular, SQL');
    expect(ctrl.value).toEqual(['Java', 'Angular', 'SQL']);
    type('java');
    expect(ctrl.value).toEqual(['Java', 'Angular', 'SQL']);
  });

  it('quita una etiqueta con su botón accesible', () => {
    const { fixture, ctrl } = setup();
    const btn = fixture.nativeElement.querySelector('button[aria-label="Quitar Java"]') as HTMLButtonElement;
    btn.click();
    expect(ctrl.value).toEqual([]);
  });

  it('Retroceso con el campo vacío borra la última', () => {
    const { type, ctrl } = setup();
    type('', 'Backspace');
    expect(ctrl.value).toEqual([]);
  });
});
