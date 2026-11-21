import { api } from './client';
import type { Page, PageParams, Person } from './common';
import type { Media } from './memories';

/* Entrega 6: comentários, respostas, @menções e reações (README da API, seções 5.9 e 5.10). */

export type CommentVisibility = 'PARTICIPANTS_ONLY' | 'PUBLIC';

/**
 * Trecho de `text` que é uma menção. Posições em unidades UTF-16 (as de String.prototype.slice).
 * O trecho é "@usuario", "Perfil privado" ou "Usuário excluído".
 */
export interface Mention {
  start: number;
  length: number;
  person: Person;
}

export interface Comment {
  id: string;
  memoryId: string;
  /** Presente numa resposta: o comentário principal. */
  parentId?: string | null;
  author: Person;
  text?: string | null;
  mentions: Mention[];
  audio?: Media | null;
  visibility: CommentVisibility;
  /** Respostas que você pode ver (só em comentário principal). */
  replyCount?: number | null;
  createdAt: string;
  editedAt?: string | null;
  mine: boolean;
}

export interface WriteCommentRequest {
  text?: string;
  visibility?: CommentVisibility;
  audioId?: string;
  removeAudio?: boolean;
}

export interface ReactionCount {
  emoji: string;
  count: number;
}

/** Quantas reações há de cada emoji (mais usados primeiro) e a sua. Nunca quem reagiu. */
export interface ReactionSummary {
  total: number;
  items: ReactionCount[];
  mine?: string | null;
}

export const COMMENT_TEXT_MAX = 200;

/** Emojis oferecidos de cara; qualquer outro emoji único também é aceito pela API. */
export const QUICK_REACTIONS = ['❤️', '😍', '😂', '🥹', '👏', '🎉', '🙏', '😮'];

export const commentsApi = {
  list: (memoryId: string, params: PageParams, signal?: AbortSignal) =>
    api<Page<Comment>>(`/memories/${memoryId}/comments`, { query: { ...params }, signal }),

  create: (memoryId: string, body: WriteCommentRequest) =>
    api<Comment>(`/memories/${memoryId}/comments`, { method: 'POST', body }),

  replies: (commentId: string, params: PageParams, signal?: AbortSignal) =>
    api<Page<Comment>>(`/comments/${commentId}/replies`, { query: { ...params }, signal }),

  reply: (commentId: string, body: WriteCommentRequest) =>
    api<Comment>(`/comments/${commentId}/replies`, { method: 'POST', body }),

  /** Envie só o que mudar. A data de publicação não muda. */
  update: (id: string, body: WriteCommentRequest) => api<Comment>(`/comments/${id}`, { method: 'PATCH', body }),

  /** Excluir um comentário principal apaga as respostas dele. */
  remove: (id: string) => api<void>(`/comments/${id}`, { method: 'DELETE' }),
};

/** O resumo também vem em cada memória (`reactions`), então o mural não pede GET .../reactions/summary. */
export const reactionsApi = {
  /** Reagir de novo troca a reação (no máximo uma por pessoa e memória). */
  react: (memoryId: string, emoji: string) =>
    api<ReactionSummary>(`/memories/${memoryId}/reactions`, { method: 'PUT', body: { emoji } }),

  remove: (memoryId: string) => api<ReactionSummary>(`/memories/${memoryId}/reactions`, { method: 'DELETE' }),

};

/** Mesma contagem da API: pontos de código depois de remover espaços nas pontas. */
export function commentTextLength(text: string): number {
  return [...text.trim()].length;
}

export type TextSegment = { kind: 'text'; text: string } | { kind: 'mention'; text: string; mention: Mention };

/** Divide o texto em trechos comuns e menções, usando as posições devolvidas pela API. */
export function splitMentions(text: string, mentions: Mention[] = []): TextSegment[] {
  const segments: TextSegment[] = [];
  let cursor = 0;
  for (const mention of [...mentions].sort((a, b) => a.start - b.start)) {
    const end = mention.start + mention.length;
    if (mention.start < cursor || end > text.length) continue; // posição inconsistente: mostra como texto
    if (mention.start > cursor) segments.push({ kind: 'text', text: text.slice(cursor, mention.start) });
    segments.push({ kind: 'mention', text: text.slice(mention.start, end), mention });
    cursor = end;
  }
  if (cursor < text.length) segments.push({ kind: 'text', text: text.slice(cursor) });
  return segments;
}
