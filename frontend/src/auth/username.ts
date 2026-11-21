/**
 * Regras do @usuário, espelhando a API (profile/domain/Username.java):
 * 3 a 30 caracteres, letras sem acento, números, "." e "_", começando e terminando
 * com letra ou número, sem ".." seguidos. Maiúsculas e minúsculas são equivalentes.
 * A lista de nomes reservados fica só no servidor e é consultada pela verificação de disponibilidade.
 */
const FORMAT = /^[a-z0-9][a-z0-9._]{1,28}[a-z0-9]$/;

export const USERNAME_MAX_INPUT = 31; // 30 + "@" opcional

export function normalizeUsername(raw: string): string {
  let value = raw.trim();
  if (value.startsWith('@')) value = value.slice(1);
  return value.toLowerCase();
}

export type UsernameFormatProblem = 'EMPTY' | 'TOO_SHORT' | 'TOO_LONG' | 'BAD_CHARS' | 'BAD_EDGES' | 'DOUBLE_DOT';

/** Explica, em linguagem simples, o que ainda falta para o @usuário ser válido. */
export function usernameFormatProblem(normalized: string): UsernameFormatProblem | null {
  if (normalized.length === 0) return 'EMPTY';
  if (/[^a-z0-9._]/.test(normalized)) return 'BAD_CHARS';
  if (normalized.length < 3) return 'TOO_SHORT';
  if (normalized.length > 30) return 'TOO_LONG';
  if (normalized.includes('..')) return 'DOUBLE_DOT';
  if (!FORMAT.test(normalized)) return 'BAD_EDGES';
  return null;
}

export const FORMAT_HINTS: Record<UsernameFormatProblem, string> = {
  EMPTY: 'Escolha como as pessoas vão te encontrar.',
  TOO_SHORT: 'Use pelo menos 3 caracteres.',
  TOO_LONG: 'Use no máximo 30 caracteres.',
  BAD_CHARS: 'Use só letras sem acento, números, ponto ou sublinhado.',
  BAD_EDGES: 'Comece e termine com uma letra ou um número.',
  DOUBLE_DOT: 'Não use dois pontos seguidos.',
};
