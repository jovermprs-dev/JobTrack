import { Injectable, signal } from '@angular/core';

export interface ConfirmRequest {
  title: string;
  message: string;
  confirmLabel: string;
  danger: boolean;
  resolve: (value: boolean) => void;
}

/** Diálogo de confirmación imperativo: `await confirm.ask({...})`. Lo pinta ConfirmHost. */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  readonly request = signal<ConfirmRequest | null>(null);

  ask(options: { title: string; message: string; confirmLabel?: string; danger?: boolean }): Promise<boolean> {
    return new Promise((resolve) => {
      this.request.set({
        title: options.title,
        message: options.message,
        confirmLabel: options.confirmLabel ?? 'Confirmar',
        danger: options.danger ?? false,
        resolve,
      });
    });
  }

  answer(value: boolean): void {
    const current = this.request();
    this.request.set(null);
    current?.resolve(value);
  }
}
