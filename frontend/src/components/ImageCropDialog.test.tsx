import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ImageCropDialog, VideoCropDialog } from './ImageCropDialog';

describe('ImageCropDialog', () => {
  it('offers crop, original image and cancellation', async () => {
    const apply = vi.fn();
    const cancel = vi.fn();
    const file = new File(['photo'], 'familia.jpg', { type: 'image/jpeg' });
    const user = userEvent.setup();
    render(<ImageCropDialog file={file} onApply={apply} onCancel={cancel} />);

    expect(screen.getByRole('dialog', { name: 'Recortar foto' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Aplicar recorte' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Usar foto original' }));
    expect(apply).toHaveBeenCalledWith(file);
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(cancel).toHaveBeenCalledOnce();
  });

  it('lets a long video choose its framing and a clip of at most one minute', () => {
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => undefined);
    const file = new File(['video'], 'festa.mp4', { type: 'video/mp4' });
    const { container } = render(<VideoCropDialog file={file} onApply={vi.fn()} onCancel={vi.fn()} />);
    const player = container.querySelector('video')!;
    Object.defineProperties(player, {
      duration: { configurable: true, value: 150 },
      videoWidth: { configurable: true, value: 1920 },
      videoHeight: { configurable: true, value: 1080 },
    });

    fireEvent.loadedMetadata(player);

    expect(screen.getByRole('group', { name: 'Proporção do recorte' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Proporção quadrada 1 por 1' })).toBeInTheDocument();
    expect(screen.getByText('Até 1 minuto')).toBeInTheDocument();
    const start = screen.getByLabelText('Início do trecho');
    const end = screen.getByLabelText('Fim do trecho');
    expect(start).toHaveValue('0');
    expect(end).toHaveValue('60');

    fireEvent.change(end, { target: { value: '90' } });
    expect(start).toHaveValue('30');
    expect(end).toHaveValue('90');
  });
});
