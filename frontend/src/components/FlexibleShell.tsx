import { Link, Navigate, Outlet, useLocation } from 'react-router';
import { isApiError } from '../api/client';
import type { Me } from '../api/users';
import { withNext } from '../auth/redirect';
import { useLogout, useMe } from '../auth/session';
import { ThemeToggle } from '../theme/ThemeToggle';
import { AppShell } from './AppShell';
import { Button } from './Button';
import { FullPageError, FullPageLoading } from './FullPageState';
import { Wordmark } from './Wordmark';
import styles from './FlexibleShell.module.css';

function currentPath(location: { pathname: string; search: string; hash: string }) {
  return `${location.pathname}${location.search}${location.hash}`;
}

/**
 * Estrutura de páginas que não exigem cadastro concluído:
 *  - conta ativa → o app completo (AppShell);
 *  - sem sessão → cabeçalho simples com "Entrar" (eventos e memórias públicos, busca);
 *    com `requireSession`, vai para o login e volta depois (convites);
 *  - cadastro pendente → cabeçalho simples com "Concluir cadastro" (ex.: abrir um convite antes do cadastro).
 */
export function FlexibleShell({ requireSession = false }: { requireSession?: boolean }) {
  const location = useLocation();
  const me = useMe();

  if (me.isPending) return <FullPageLoading />;
  if (me.isError) {
    if (isApiError(me.error, 'ACCOUNT_DISABLED')) return <Navigate to="/login?error=account_disabled" replace />;
    return <FullPageError error={me.error} onRetry={() => me.refetch()} />;
  }
  if (me.data === null && requireSession) {
    return <Navigate to={withNext('/login', currentPath(location))} replace />;
  }
  if (me.data?.onboardingCompleted) return <AppShell />;
  return <PublicShell me={me.data} />;
}

function PublicShell({ me }: { me: Me | null }) {
  const location = useLocation();
  const logout = useLogout();
  const here = currentPath(location);

  return (
    <div className={styles.shell}>
      <header className={styles.top}>
        <Link to={me ? withNext('/onboarding', here) : '/explore'} className={styles.brand} aria-label="Memora">
          <Wordmark />
        </Link>
        <div className={styles.actions}>
          <ThemeToggle />
          {me ? (
            <>
              {/* No celular, o cartão da página já oferece "Concluir cadastro"; o cabeçalho fica enxuto. */}
              <Link to={withNext('/onboarding', here)} className={`${styles.cta} ${styles.wideOnly}`}>
                Concluir cadastro
              </Link>
              <Button variant="quiet" onClick={() => logout.mutate()} loading={logout.isPending}>
                Sair
              </Button>
            </>
          ) : (
            <Link to={withNext('/login', here)} className={styles.cta}>
              Entrar
            </Link>
          )}
        </div>
      </header>
      <main id="conteudo" className={styles.main}>
        <Outlet />
      </main>
    </div>
  );
}
