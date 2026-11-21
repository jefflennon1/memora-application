/** Endereço da API Memora. Em desenvolvimento: http://localhost:8080 */
export const API_URL = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080').replace(/\/+$/, '');

/** Prefixo das operações de negócio. */
export const API_V1 = `${API_URL}/api/v1`;

export const CSRF_COOKIE = 'XSRF-TOKEN';
export const CSRF_HEADER = 'X-XSRF-TOKEN';

/** Endereço completo de um caminho devolvido pela API (ex.: o `url` de uma imagem, "/api/v1/media/…"). */
export function apiAsset(path: string): string {
  return path.startsWith('http') ? path : `${API_URL}${path}`;
}
