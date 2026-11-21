import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Navigate, Outlet, useLocation } from 'react-router';
import { isApiError } from '../api/client';
import { usersApi, type Me } from '../api/users';
import { FullPageError, FullPageLoading } from '../components/FullPageState';
import { withNext } from './redirect';

export const ME_KEY = ['me'] as const;

/**
 * Conta autenticada. `null` significa "sem sessão" (401): não é um erro, é um estado.
 * Outros problemas (API fora do ar, conta desativada) chegam como erro.
 */
export function useMe() {
  return useQuery<Me | null>({
    queryKey: ME_KEY,
    queryFn: async ({ signal }) => {
      try {
        return await usersApi.me(signal);
      } catch (error) {
        if (isApiError(error, 'UNAUTHENTICATED')) return null;
        throw error;
      }
    },
    staleTime: 5 * 60_000,
    retry: (count, error) => isApiError(error, 'NETWORK_ERROR') && count < 1,
  });
}

export function useLogout() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: usersApi.logout,
    onSettled: () => {
      client.clear();
      client.setQueryData(ME_KEY, null);
    },
  });
}

function currentPath(location: { pathname: string; search: string; hash: string }) {
  return `${location.pathname}${location.search}${location.hash}`;
}

/**
 * Exige sessão. Sem sessão → /login?next=…; cadastro pendente → /onboarding?next=…;
 * conta desativada → /login?error=account_disabled.
 */
export function RequireOnboarded() {
  const location = useLocation();
  const me = useMe();

  if (me.isPending) return <FullPageLoading />;
  if (me.isError) {
    if (isApiError(me.error, 'ACCOUNT_DISABLED')) return <Navigate to="/login?error=account_disabled" replace />;
    return <FullPageError error={me.error} onRetry={() => me.refetch()} />;
  }
  if (me.data === null) return <Navigate to={withNext('/login', currentPath(location))} replace />;
  if (!me.data.onboardingCompleted) {
    return <Navigate to={withNext('/onboarding', currentPath(location))} replace />;
  }
  return <Outlet context={me.data} />;
}

/** Exige sessão, mas aceita cadastro pendente (usada pelo onboarding). */
export function RequireSession() {
  const location = useLocation();
  const me = useMe();

  if (me.isPending) return <FullPageLoading />;
  if (me.isError) {
    if (isApiError(me.error, 'ACCOUNT_DISABLED')) return <Navigate to="/login?error=account_disabled" replace />;
    return <FullPageError error={me.error} onRetry={() => me.refetch()} />;
  }
  if (me.data === null) {
    const next = new URLSearchParams(location.search).get('next');
    return <Navigate to={withNext('/login', next)} replace />;
  }
  return <Outlet context={me.data} />;
}
