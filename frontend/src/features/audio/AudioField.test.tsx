import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { AudioField } from './AudioField';

describe('AudioField', () => {
  it('keeps audio compact until the person opens its options', async () => {
    const user = userEvent.setup();
    render(<AudioField eventId="e1" value={null} onChange={vi.fn()} compactTrigger />);

    expect(screen.queryByRole('button', { name: 'Gravar áudio' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Adicionar áudio' }));
    expect(screen.getByRole('button', { name: 'Gravar áudio' })).toBeInTheDocument();
    expect(screen.getByText('Escolher arquivo')).toBeInTheDocument();
  });

  it('shows an animated and explicit recording state while the microphone is active', async () => {
    const source = { connect: vi.fn(), disconnect: vi.fn() };
    const processor = { connect: vi.fn(), disconnect: vi.fn(), onaudioprocess: null };
    const gain = { connect: vi.fn(), disconnect: vi.fn(), gain: { value: 1 } };
    const track = { stop: vi.fn() };

    class FakeAudioContext {
      sampleRate = 48_000;
      destination = {};
      createMediaStreamSource = () => source;
      createScriptProcessor = () => processor;
      createGain = () => gain;
      close = vi.fn(async () => undefined);
    }

    vi.stubGlobal('AudioContext', FakeAudioContext);
    vi.stubGlobal('navigator', {
      mediaDevices: { getUserMedia: vi.fn(async () => ({ getTracks: () => [track] })) },
    });

    const user = userEvent.setup();
    const view = render(<AudioField eventId="e1" value={null} onChange={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: 'Gravar áudio' }));

    expect(await screen.findByText('Gravando áudio')).toBeInTheDocument();
    expect(screen.getByText('0:00 de 0:30')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Parar e usar áudio' })).toBeInTheDocument();
    expect(view.container.querySelectorAll('.waveform span')).toHaveLength(21);

    view.unmount();
    expect(track.stop).toHaveBeenCalled();
  });
});
