import { API_V1, CSRF_COOKIE, CSRF_HEADER } from './config';

/** Problem Details (RFC 9457) como a API Memora devolve. */
export interface ProblemDetails {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  /** Código estável usado para decidir a mensagem exibida. */
  code?: string;
  /** Presente em VALIDATION_FAILED. */
  errors?: { field: string; message: string }[];
}

/** Erro de chamada à API. `code` vem do Problem Details ou é NETWORK_ERROR / UNKNOWN_ERROR. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly problem: ProblemDetails | null;

  constructor(status: number, code: string, problem: ProblemDetails | null, message?: string) {
    super(message ?? problem?.detail ?? code);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.problem = problem;
  }

  fieldErrors(): Record<string, string> {
    const out: Record<string, string> = {};
    for (const e of this.problem?.errors ?? []) out[e.field] ??= e.message;
    return out;
  }
}

export function isApiError(error: unknown, code?: string): error is ApiError {
  return error instanceof ApiError && (code === undefined || error.code === code);
}

const UNSAFE_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

export function readCookie(name: string): string | null {
  const prefix = `${name}=`;
  for (const part of document.cookie.split(';')) {
    const cookie = part.trim();
    if (cookie.startsWith(prefix)) {
      const value = decodeURIComponent(cookie.slice(prefix.length));
      return value.length > 0 ? value : null;
    }
  }
  return null;
}

/**
 * Garante que o cookie XSRF-TOKEN exista. A API emite o cookie em qualquer resposta,
 * então, se ele ainda não chegou (ou foi apagado no logout), uma leitura leve o recria.
 */
async function csrfToken(): Promise<string | null> {
  const existing = readCookie(CSRF_COOKIE);
  if (existing) return existing;
  try {
    await fetch(`${API_V1}/users/me`, { credentials: 'include', headers: { Accept: 'application/json' } });
  } catch {
    // Sem rede: a chamada principal vai reportar o erro.
  }
  return readCookie(CSRF_COOKIE);
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  /** Envio multipart (ex.: imagens). O navegador define o Content-Type com o boundary. */
  form?: FormData;
  signal?: AbortSignal;
  query?: Record<string, string | number | boolean | undefined | null>;
  /** Tempo máximo de espera pela resposta (padrão 15 s). */
  timeoutMs?: number;
}

const REQUEST_TIMEOUT_MS = 15_000;

function buildUrl(path: string, query?: RequestOptions['query']): string {
  const url = new URL(path.startsWith('http') ? path : `${API_V1}${path}`);
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value));
  }
  return url.toString();
}

async function parseProblem(response: Response): Promise<ProblemDetails | null> {
  const type = response.headers.get('Content-Type') ?? '';
  if (!type.includes('json')) return null;
  try {
    return (await response.json()) as ProblemDetails;
  } catch {
    return null;
  }
}

/**
 * Chamada à API Memora.
 * - Sempre com credenciais (cookie de sessão MEMORA_SESSION).
 * - POST/PUT/PATCH/DELETE levam o cabeçalho X-XSRF-TOKEN com o valor do cookie XSRF-TOKEN.
 * - Erros viram ApiError com o `code` do Problem Details.
 */
export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const headers: Record<string, string> = { Accept: 'application/json, application/problem+json' };

  if (options.body !== undefined && !options.form) headers['Content-Type'] = 'application/json';
  if (UNSAFE_METHODS.has(method)) {
    const token = await csrfToken();
    if (token) headers[CSRF_HEADER] = token;
  }

  // Uma API travada não pode deixar a tela carregando para sempre.
  const timeout = AbortSignal.timeout(options.timeoutMs ?? REQUEST_TIMEOUT_MS);
  const signal = options.signal ? AbortSignal.any([options.signal, timeout]) : timeout;

  let response: Response;
  try {
    response = await fetch(buildUrl(path, options.query), {
      method,
      credentials: 'include',
      headers,
      body: options.form ?? (options.body === undefined ? undefined : JSON.stringify(options.body)),
      signal,
    });
  } catch (cause) {
    if (timeout.aborted && !options.signal?.aborted) {
      throw new ApiError(0, 'TIMEOUT', null, 'A API demorou demais para responder.');
    }
    if (options.signal?.aborted) throw cause;
    throw new ApiError(0, 'NETWORK_ERROR', null, 'Não foi possível conectar à API.');
  }

  if (!response.ok) {
    const problem = await parseProblem(response);
    const code = problem?.code ?? (response.status === 401 ? 'UNAUTHENTICATED' : response.status === 403 ? 'FORBIDDEN' : 'UNKNOWN_ERROR');
    throw new ApiError(response.status, code, problem ?? { status: response.status });
  }

  if (response.status === 204 || response.headers.get('Content-Length') === '0') {
    return undefined as T;
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}
