import { api } from './client';
import type { Page, PageParams, Person } from './common';
import type { ReactionSummary } from './interactions';

export type MemoryVisibility = 'PARTICIPANTS_ONLY' | 'PUBLIC';
export type MuralOrder = 'OLDEST' | 'NEWEST';
export type MediaKind = 'IMAGE' | 'AUDIO' | 'VIDEO';

export interface Media {
  id: string;
  /** Caminho na API ("/api/v1/media/…"); use apiAsset() para montar o endereço. */
  url: string;
  kind: MediaKind;
  contentType: string;
  width?: number | null;
  height?: number | null;
  durationMs?: number | null;
  sizeBytes: number;
}

export interface Memory {
  id: string;
  eventId: string;
  author: Person;
  text?: string | null;
  visibility: MemoryVisibility;
  media: Media[];
  /** Data de publicação: nunca muda. */
  createdAt: string;
  editedAt?: string | null;
  mine: boolean;
  /** Comentários e respostas que você pode ver (Entrega 6). */
  commentCount?: number;
  /** Resumo das reações, o mesmo de GET /memories/{id}/reactions/summary (Entrega 6). */
  reactions?: ReactionSummary;
}

export interface PublishMemoryRequest {
  text?: string;
  mediaIds?: string[];
  visibility?: MemoryVisibility;
}

/** Envie só o que mudar. `text: null` apaga o texto; `mediaIds` substitui a lista inteira. */
export type UpdateMemoryRequest = Partial<{
  text: string | null;
  mediaIds: string[];
  visibility: MemoryVisibility;
}>;

/** Regras do conteúdo (README da API, seção 5.8). */
export const MEMORY_TEXT_MAX = 500;
export const MEMORY_MEDIA_MAX = 10;
export const MEDIA_MAX_BYTES = 15 * 1024 * 1024;
export const AUDIO_MAX_BYTES = 10 * 1024 * 1024;
export const AUDIO_MAX_DURATION_MS = 30_000;
export const ACCEPTED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];

/** A API conta pontos de código depois de remover espaços nas pontas (um emoji simples conta 1). */
export function memoryTextLength(text: string): number {
  return [...text.trim()].length;
}

export const memoriesApi = {
  mural: (eventId: string, params: PageParams & { order?: MuralOrder }, signal?: AbortSignal) =>
    api<Page<Memory>>(`/events/${eventId}/memories`, { query: { ...params }, signal }),

  get: (id: string, signal?: AbortSignal) => api<Memory>(`/memories/${id}`, { signal }),

  publish: (eventId: string, body: PublishMemoryRequest) =>
    api<Memory>(`/events/${eventId}/memories`, { method: 'POST', body }),

  update: (id: string, body: UpdateMemoryRequest) => api<Memory>(`/memories/${id}`, { method: 'PATCH', body }),

  remove: (id: string) => api<void>(`/memories/${id}`, { method: 'DELETE' }),
};

export const mediaApi = {
  /** Envia uma imagem; ela fica pendente até ser usada numa memória (some em 24 h se não for). */
  upload: (eventId: string, file: Blob, fileName: string, signal?: AbortSignal) => {
    const form = new FormData();
    form.append('file', file, fileName);
    return api<Media>(`/events/${eventId}/media`, { method: 'POST', form, signal, timeoutMs: 120_000 });
  },

  /** Descarta uma imagem ainda não publicada. */
  discard: (id: string) => api<void>(`/media/${id}`, { method: 'DELETE' }),
};
