import { Link, Navigate, isRouteErrorResponse, useRouteError, type RouteObject } from 'react-router';
import { RequireOnboarded, RequireSession } from '../auth/session';
import { AppShell } from '../components/AppShell';
import { Wordmark } from '../components/Wordmark';
import { LoginPage } from '../features/login/LoginPage';
import { RegisterPage } from '../features/login/RegisterPage';
import { OnboardingPage } from '../features/onboarding/OnboardingPage';
import { ProfilePage } from '../features/profile/ProfilePage';
import styles from './router.module.css';

export const routes: RouteObject[] = [
  {
    errorElement: <RouteError />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
      {
        element: <RequireSession />,
        children: [{ path: '/onboarding', element: <OnboardingPage /> }],
      },
      {
        element: <RequireOnboarded />,
        children: [
          {
            element: <AppShell />,
            children: [
              { index: true, element: <Navigate to="/profile" replace /> },
              { path: '/profile', element: <ProfilePage /> },
              { path: '*', element: <NotFound /> },
            ],
          },
        ],
      },
    ],
  },
];

function NotFound() {
  return (
    <section className={styles.notFound}>
      <h1>Esta página não existe</h1>
      <p>O endereço pode ter mudado ou estar incompleto.</p>
      <Link to="/">Voltar ao início</Link>
    </section>
  );
}

function RouteError() {
  const error = useRouteError();
  const status = isRouteErrorResponse(error) ? error.status : null;
  if (import.meta.env.DEV) console.error(error);
  return (
    <div className={styles.crash} role="alert">
      <Wordmark size="lg" />
      <h1>{status === 404 ? 'Esta página não existe' : 'Algo saiu do lugar'}</h1>
      <p>Recarregue a página. Se continuar, volte ao início.</p>
      <a href="/">Voltar ao início</a>
    </div>
  );
}
