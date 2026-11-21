import { describe, expect, it } from 'vitest';
import { json, mockApi, problem } from '../test/utils';
import { ApiError, api } from './client';
import { friendlyMessage } from './problems';

const headersOf = (init: RequestInit) => init.headers as Record<string, string>;

describe('api client', () => {
  it('sends credentials and no CSRF header on GET', async () => {
    document.cookie = 'XSRF-TOKEN=tok-123';
    const calls = mockApi({ 'GET /api/v1/users/me': () => json({ id: '1' }) });

    await api('/users/me');

    expect(calls[0]!.init.credentials).toBe('include');
    expect(headersOf(calls[0]!.init)['X-XSRF-TOKEN']).toBeUndefined();
  });

  it.each(['POST', 'PUT', 'PATCH', 'DELETE'] as const)('sends X-XSRF-TOKEN from the cookie on %s', async (method) => {
    document.cookie = 'XSRF-TOKEN=tok-123';
    const calls = mockApi({ [`${method} /api/v1/things`]: () => new Response(null, { status: 204 }) });

    await api('/things', { method });

    expect(headersOf(calls[0]!.init)['X-XSRF-TOKEN']).toBe('tok-123');
    expect(calls[0]!.init.credentials).toBe('include');
  });

  it('primes the CSRF cookie with a GET when it is missing', async () => {
    const calls = mockApi({
      'GET /api/v1/users/me': () => {
        document.cookie = 'XSRF-TOKEN=fresh';
        return problem(401, 'UNAUTHENTICATED');
      },
      'POST /api/v1/auth/logout': () => new Response(null, { status: 204 }),
    });

    await api('/auth/logout', { method: 'POST' });

    expect(calls.map((c) => c.method)).toEqual(['GET', 'POST']);
    expect(headersOf(calls[1]!.init)['X-XSRF-TOKEN']).toBe('fresh');
  });

  it('turns Problem Details into ApiError with code and field errors', async () => {
    document.cookie = 'XSRF-TOKEN=t';
    mockApi({
      'PATCH /api/v1/users/me': () =>
        problem(400, 'VALIDATION_FAILED', { errors: [{ field: 'displayName', message: 'não deve estar em branco' }] }),
    });

    const error = await api('/users/me', { method: 'PATCH', body: {} }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(400);
    expect(apiError.code).toBe('VALIDATION_FAILED');
    expect(apiError.fieldErrors()).toEqual({ displayName: 'não deve estar em branco' });
  });

  it('reports network failures as NETWORK_ERROR with a friendly message', async () => {
    mockApi({ 'GET /api/v1/users/me': () => Promise.reject(new TypeError('Failed to fetch')) });

    const error = (await api('/users/me').catch((e: unknown) => e)) as ApiError;

    expect(error.code).toBe('NETWORK_ERROR');
    expect(friendlyMessage(error)).toMatch(/Não conseguimos falar com a Memora/);
  });

  it('gives up on a hanging API with TIMEOUT instead of loading forever', async () => {
    mockApi({
      'GET /api/v1/users/me': (_url, init) =>
        new Promise<Response>((_, reject) =>
          init.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError'))),
        ),
    });

    const error = (await api('/users/me', { timeoutMs: 50 }).catch((e: unknown) => e)) as ApiError;

    expect(error.code).toBe('TIMEOUT');
    expect(friendlyMessage(error)).toMatch(/demorou demais/);
  });

  it('keeps caller cancellations as aborts, not errors to show', async () => {
    mockApi({
      'GET /api/v1/users/me': (_url, init) =>
        new Promise<Response>((_, reject) =>
          init.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError'))),
        ),
    });
    const controller = new AbortController();
    const pending = api('/users/me', { signal: controller.signal }).catch((e: unknown) => e);
    controller.abort();

    expect(await pending).not.toBeInstanceOf(ApiError);
  });

  it('uses the friendly message for known codes and the API detail for unknown ones', () => {
    expect(friendlyMessage(new ApiError(409, 'USERNAME_TAKEN', { status: 409 }))).toMatch(/já está em uso/);
    expect(friendlyMessage(new ApiError(422, 'SOMETHING_NEW', { status: 422, detail: 'Mural fechado.' }))).toBe(
      'Mural fechado.',
    );
  });

  it('encodes query parameters', async () => {
    const calls = mockApi({
      'GET /api/v1/users/username-availability': () => json({ username: 'ana', available: true }),
    });
    await api('/users/username-availability', { query: { username: '@Ana Souza' } });
    expect(calls[0]!.url.searchParams.get('username')).toBe('@Ana Souza');
  });
});
