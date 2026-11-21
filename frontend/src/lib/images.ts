/**
 * Prepara uma foto antes do envio.
 *
 * A API ainda não remove metadados EXIF (como a localização GPS) das fotos. Redesenhar a imagem num
 * canvas e exportar de novo gera um arquivo novo, sem esses metadados, já na orientação correta e
 * com o lado maior limitado a MAX_SIDE (fotos de celular ficam bem menores).
 *
 * - GIF segue como está (redesenhar perderia a animação; GIF não carrega EXIF).
 * - Se o navegador não conseguir abrir o arquivo (ex.: HEIC no Chrome), ele segue como está e
 *   a API responde com a mensagem adequada.
 */
export const MAX_SIDE = 3200;
const QUALITY = 0.9;

export type PreparedImage = { blob: Blob; name: string; reencoded: boolean };

function outputType(type: string): string {
  if (type === 'image/png') return 'image/png'; // preserva transparência
  if (type === 'image/webp') return 'image/webp';
  return 'image/jpeg';
}

function extension(type: string): string {
  return type === 'image/png' ? 'png' : type === 'image/webp' ? 'webp' : 'jpg';
}

export async function prepareImage(file: File): Promise<PreparedImage> {
  const original: PreparedImage = { blob: file, name: file.name || 'foto', reencoded: false };
  if (file.type === 'image/gif' || typeof createImageBitmap !== 'function') return original;

  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
  } catch {
    return original;
  }

  try {
    const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
    const width = Math.max(1, Math.round(bitmap.width * scale));
    const height = Math.max(1, Math.round(bitmap.height * scale));
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d');
    if (!context) return original;
    context.drawImage(bitmap, 0, 0, width, height);

    const type = outputType(file.type);
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, type, QUALITY));
    if (!blob) return original;
    const base = (file.name || 'foto').replace(/\.[^.]+$/, '');
    return { blob, name: `${base}.${extension(blob.type || type)}`, reencoded: true };
  } finally {
    bitmap.close();
  }
}
