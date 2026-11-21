export const VIDEO_MAX_DURATION_MS = 60_000;
export const VIDEO_MAX_BYTES = 100 * 1024 * 1024;
export const ACCEPTED_VIDEO_TYPES = ['video/mp4', 'video/quicktime'];

export interface VideoMetadata {
  durationMs: number;
  width: number;
  height: number;
}

/** Validação antecipada para feedback rápido; o backend repete a validação pelo conteúdo. */
export function inspectVideo(file: File): Promise<VideoMetadata> {
  if (!ACCEPTED_VIDEO_TYPES.includes(file.type)) {
    return Promise.reject(new Error('VIDEO_TYPE_NOT_SUPPORTED'));
  }
  if (file.size > VIDEO_MAX_BYTES) {
    return Promise.reject(new Error('VIDEO_TOO_LARGE'));
  }
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const video = document.createElement('video');
    const finish = () => {
      URL.revokeObjectURL(url);
      video.onloadedmetadata = null;
      video.onerror = null;
      video.removeAttribute('src');
      video.load();
    };
    const timer = window.setTimeout(() => {
      finish();
      reject(new Error('INVALID_VIDEO'));
    }, 15_000);
    video.preload = 'metadata';
    video.onloadedmetadata = () => {
      window.clearTimeout(timer);
      const durationMs = Math.round(video.duration * 1000);
      const metadata = { durationMs, width: video.videoWidth, height: video.videoHeight };
      finish();
      if (!Number.isFinite(durationMs) || durationMs <= 0 || !metadata.width || !metadata.height) {
        reject(new Error('INVALID_VIDEO'));
      } else if (durationMs > VIDEO_MAX_DURATION_MS) {
        reject(new Error('VIDEO_TOO_LONG'));
      } else {
        resolve(metadata);
      }
    };
    video.onerror = () => {
      window.clearTimeout(timer);
      finish();
      reject(new Error('INVALID_VIDEO'));
    };
    video.src = url;
  });
}

export function videoErrorMessage(error: unknown): string {
  const code = error instanceof Error ? error.message : '';
  if (code === 'VIDEO_TOO_LONG') return 'O vídeo pode ter no máximo 60 segundos.';
  if (code === 'VIDEO_TOO_LARGE') return 'O vídeo pode ter no máximo 100 MB.';
  if (code === 'VIDEO_TYPE_NOT_SUPPORTED') return 'Use um vídeo MP4 ou MOV.';
  return 'Não foi possível abrir este vídeo. Use um arquivo MP4 ou MOV válido.';
}
