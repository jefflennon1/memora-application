import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query';
import { isApiError } from '../api/client';
import { ME_KEY } from '../auth/session';

/**
 * Reação global a erros de sessão em qualquer chamada:
 *  - 401 (sessão expirou)          → marca "sem sessão"; os guards levam ao login.
 *  - ONBOARDING_REQUIRED / ACCOUNT_DISABLED → recarrega a conta; os guards decidem o destino.
 */
export function createQueryClient() {
  const client: QueryClient = new QueryClient({
    queryCache: new QueryCache({ onError: (error, query) => handleSessionError(error, query.queryKey) }),
    mutationCache: new MutationCache({ onError: (error) => handleSessionError(error) }),
    defaultOptions: {
      queries: {
        retry: (count, error) => isApiError(error, 'NETWORK_ERROR') && count < 1,
        refetchOnWindowFocus: true,
      },
      mutations: { retry: false },
    },
  });

  function handleSessionError(error: unknown, queryKey?: readonly unknown[]) {
    if (queryKey && queryKey[0] === ME_KEY[0]) return;
    if (isApiError(error, 'UNAUTHENTICATED')) {
      client.setQueryData(ME_KEY, null);
    } else if (isApiError(error, 'ONBOARDING_REQUIRED') || isApiError(error, 'ACCOUNT_DISABLED')) {
      void client.invalidateQueries({ queryKey: ME_KEY });
    }
  }

  return client;
}
