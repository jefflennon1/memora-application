import { QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { vi } from 'vitest';
import { createQueryClient } from '../app/queryClient';
import { routes } from '../app/router';
import type { Me } from '../api/users';

export const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

export const problem = (status: number, code: string, extra: Record<string, unknown> = {}) =>
  new Response(JSON.stringify({ status, code, detail: `detail ${code}`, ...extra }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });

type Handler = (url: URL, init: RequestInit) => Response | Promise<Response>;

export interface Call {
  method: string;
  url: URL;
  init: RequestInit;
}

/** Substitui fetch por uma tabela "MÉTODO /caminho" → resposta. */
export function mockApi(handlers: Record<string, Handler>) {
  const calls: Call[] = [];
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = new URL(String(input));
    const method = init.method ?? 'GET';
    calls.push({ method, url, init });
    const handler = handlers[`${method} ${url.pathname}`];
    if (!handler) return problem(404, 'NOT_FOUND');
    return handler(url, init);
  });
  vi.stubGlobal('fetch', fetchMock);
  return calls;
}

export function makeMe(overrides: Partial<Me> = {}): Me {
  return {
    id: '7d0f8f8e-4a57-4f43-9a3e-0c1f7f0d2b11',
    username: 'ana.souza',
    displayName: 'Ana Souza',
    photoUrl: 'https://lh3.googleusercontent.com/a/foto',
    profilePrivacy: 'PUBLIC',
    status: 'ACTIVE',
    onboardingCompleted: true,
    createdAt: '2026-11-03T14:20:11Z',
    onboardingCompletedAt: '2026-11-03T14:25:00Z',
    passwordLogin: false,
    identities: [{ provider: 'GOOGLE', email: 'ana@gmail.com', emailVerified: true, linkedAt: '2026-11-03T14:20:11Z' }],
    ...overrides,
  };
}

export function renderApp(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const client = createQueryClient();
  const utils = render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return { ...utils, router, client };
}

export function currentUrl(router: ReturnType<typeof createMemoryRouter>) {
  const { pathname, search } = router.state.location;
  return `${pathname}${search}`;
}
