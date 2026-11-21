import { API_URL } from '../api/config';

/**
 * Aceita só caminhos internos do frontend ("/algo"). Rejeita URLs absolutas, "//host" e "/\host",
 * espelhando a regra do `redirect_to` da API, para que um link não leve a pessoa a outro site.
 */
export function safeNext(value: string | null | undefined, fallback = '/'): string {
  if (!value) return fallback;
  if (!value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) return fallback;
  if (/[\u0000-\u001f]/.test(value)) return fallback;
  // Evita laços: depois do login nunca voltamos para a própria tela de entrada.
  if (
    value === '/login' ||
    value.startsWith('/login?') ||
    value === '/register' ||
    value.startsWith('/register?') ||
    value.startsWith('/onboarding')
  ) return fallback;
  return value;
}

/** Endereço que inicia o login com Google (navegação do navegador, nunca fetch). */
export function googleLoginUrl(next?: string | null): string {
  const url = new URL(`${API_URL}/oauth2/authorization/google`);
  const target = safeNext(next, '');
  if (target) url.searchParams.set('redirect_to', target);
  return url.toString();
}

/** Monta "/rota?next=/destino" quando houver um destino diferente do início. */
export function withNext(path: string, next: string | null | undefined): string {
  const target = safeNext(next, '/');
  return target === '/' ? path : `${path}?next=${encodeURIComponent(target)}`;
}
