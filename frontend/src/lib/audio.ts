export const AUDIO_MAX_DURATION_MS = 30_000;
export const AUDIO_MAX_BYTES = 10 * 1024 * 1024;

export interface PcmRecorder {
  stop: () => Promise<Blob>;
  cancel: () => Promise<void>;
}

/** Grava PCM mono e gera WAV, formato que todos os navegadores modernos reproduzem e o backend valida. */
export async function startPcmRecorder(): Promise<PcmRecorder> {
  if (!navigator.mediaDevices?.getUserMedia) throw new Error('MICROPHONE_UNAVAILABLE');
  const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
  const context = new AudioContext();
  const source = context.createMediaStreamSource(stream);
  const processor = context.createScriptProcessor(4096, 1, 1);
  const silent = context.createGain();
  silent.gain.value = 0;
  const chunks: Float32Array[] = [];
  const maxSamples = Math.floor(context.sampleRate * (AUDIO_MAX_DURATION_MS / 1000));
  let recordedSamples = 0;
  processor.onaudioprocess = (event) => {
    const input = event.inputBuffer.getChannelData(0);
    const remaining = maxSamples - recordedSamples;
    if (remaining <= 0) return;
    const chunk = new Float32Array(input.slice(0, Math.min(input.length, remaining)));
    chunks.push(chunk);
    recordedSamples += chunk.length;
  };
  source.connect(processor);
  processor.connect(silent);
  silent.connect(context.destination);
  const finish = async () => {
    processor.disconnect();
    source.disconnect();
    silent.disconnect();
    stream.getTracks().forEach((track) => track.stop());
    await context.close();
  };
  return {
    stop: async () => {
      await finish();
      return encodeWav(chunks, context.sampleRate);
    },
    cancel: finish,
  };
}

function encodeWav(chunks: Float32Array[], sampleRate: number): Blob {
  const samples = chunks.reduce((total, chunk) => total + chunk.length, 0);
  const buffer = new ArrayBuffer(44 + samples * 2);
  const view = new DataView(buffer);
  writeAscii(view, 0, 'RIFF');
  view.setUint32(4, 36 + samples * 2, true);
  writeAscii(view, 8, 'WAVE');
  writeAscii(view, 12, 'fmt ');
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, 1, true);
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  writeAscii(view, 36, 'data');
  view.setUint32(40, samples * 2, true);
  let offset = 44;
  for (const chunk of chunks) {
    for (const value of chunk) {
      const sample = Math.max(-1, Math.min(1, value));
      view.setInt16(offset, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true);
      offset += 2;
    }
  }
  return new Blob([buffer], { type: 'audio/wav' });
}

function writeAscii(view: DataView, offset: number, value: string) {
  for (let i = 0; i < value.length; i++) view.setUint8(offset + i, value.charCodeAt(i));
}

