import { ToastService } from './toast.service';

describe('ToastService', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('los avisos de éxito desaparecen solos', () => {
    const toasts = new ToastService();
    toasts.success('ok');
    expect(toasts.toasts().length).toBe(1);
    vi.advanceTimersByTime(5000);
    expect(toasts.toasts().length).toBe(0);
  });

  it('los errores permanecen hasta que se cierran', () => {
    const toasts = new ToastService();
    toasts.error('fallo');
    vi.advanceTimersByTime(60000);
    expect(toasts.toasts().length).toBe(1);
    toasts.dismiss(toasts.toasts()[0].id);
    expect(toasts.toasts().length).toBe(0);
  });
});
