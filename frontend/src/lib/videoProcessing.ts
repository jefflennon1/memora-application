import coreURL from '@ffmpeg/core?url';
import wasmURL from '@ffmpeg/core/wasm?url';
import { FFmpeg } from '@ffmpeg/ffmpeg';

export interface VideoCrop {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface VideoEdit {
  start: number;
  end: number;
  crop: VideoCrop;
}

const OUTPUT_MAX_SIDE = 1280;

/**
 * Recorta o enquadramento e o tempo no próprio navegador. Assim a API continua
 * recebendo um MP4 de no máximo 60 s, sem precisar guardar o arquivo original.
 */
export async function editVideo(
  file: File,
  edit: VideoEdit,
  onProgress?: (progress: number) => void,
): Promise<File> {
  const ffmpeg = new FFmpeg();
  const inputExtension = file.name.split('.').pop()?.toLowerCase() === 'mov' ? 'mov' : 'mp4';
  const inputName = `entrada.${inputExtension}`;
  const outputName = 'recorte.mp4';
  const crop = evenCrop(edit.crop);
  const output = outputSize(crop.width, crop.height);
  const progress = ({ progress: value }: { progress: number }) => onProgress?.(Math.min(0.98, Math.max(0, value)));

  ffmpeg.on('progress', progress);
  try {
    onProgress?.(0.01);
    await ffmpeg.load({ coreURL, wasmURL });
    onProgress?.(0.04);
    await ffmpeg.writeFile(inputName, new Uint8Array(await file.arrayBuffer()));

    const result = await ffmpeg.exec([
      '-ss', seconds(edit.start),
      '-i', inputName,
      '-t', seconds(edit.end - edit.start),
      '-map', '0:v:0',
      '-map', '0:a?',
      '-vf', `crop=${crop.width}:${crop.height}:${crop.x}:${crop.y},scale=${output.width}:${output.height}`,
      '-c:v', 'libx264',
      '-preset', 'ultrafast',
      '-crf', '23',
      '-pix_fmt', 'yuv420p',
      '-c:a', 'aac',
      '-b:a', '128k',
      '-movflags', '+faststart',
      outputName,
    ]);
    if (result !== 0) throw new Error('VIDEO_EDIT_FAILED');

    const data = await ffmpeg.readFile(outputName);
    if (typeof data === 'string') throw new Error('VIDEO_EDIT_FAILED');
    const base = (file.name || 'video').replace(/\.[^.]+$/, '');
    onProgress?.(1);
    return new File([new Uint8Array(data)], `${base}-recorte.mp4`, { type: 'video/mp4', lastModified: Date.now() });
  } finally {
    ffmpeg.off('progress', progress);
    ffmpeg.terminate();
  }
}

function evenCrop(crop: VideoCrop): VideoCrop {
  const width = Math.max(2, Math.floor(crop.width / 2) * 2);
  const height = Math.max(2, Math.floor(crop.height / 2) * 2);
  return {
    x: Math.max(0, Math.floor(crop.x / 2) * 2),
    y: Math.max(0, Math.floor(crop.y / 2) * 2),
    width,
    height,
  };
}

function outputSize(width: number, height: number) {
  const scale = Math.min(1, OUTPUT_MAX_SIDE / Math.max(width, height));
  return {
    width: Math.max(2, Math.floor((width * scale) / 2) * 2),
    height: Math.max(2, Math.floor((height * scale) / 2) * 2),
  };
}

function seconds(value: number) {
  return Math.max(0, value).toFixed(3);
}
