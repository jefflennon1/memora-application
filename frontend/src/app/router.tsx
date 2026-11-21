import { Link, isRouteErrorResponse, useRouteError, type RouteObject } from 'react-router';
import { RequireOnboarded, RequireSession } from '../auth/session';
import { AppShell } from '../components/AppShell';
import { ComingSoon } from '../components/ComingSoon';
import { FlexibleShell } from '../components/FlexibleShell';
import { Wordmark } from '../components/Wordmark';
import { EditEventTab } from '../features/events/EditEventTab';
import { EventLayout } from '../features/events/EventLayout';
import { EventsPage, NewEventPage } from '../features/events/EventsPage';
import { ExplorePage } from '../features/events/ExplorePage';
import { HomePage } from '../features/home/HomePage';
import { LoginPage } from '../features/login/LoginPage';
import { RegisterPage } from '../features/login/RegisterPage';
import { InvitePage } from '../features/membership/InvitePage';
import { InvitesTab } from '../features/membership/InvitesTab';
import { PeopleTab } from '../features/membership/PeopleTab';
import { OnboardingPage } from '../features/onboarding/OnboardingPage';
import { ProfilePage } from '../features/profile/ProfilePage';
import styles from './router.module.css';

/**
 * Mapa de rotas.
 *  /login                       entrada (pública); trata ?error= e ?next=
 *  /onboarding                  cadastro inicial (exige sessão)
 *  /explore e /events/:id/…     abrem sem login; com conta ativa, dentro do app
 *  /invites/:token              exige sessão, mas abre antes do cadastro
 *  demais                       exigem sessão + cadastro concluído, dentro do AppShell
 */
export const routes: RouteObject[] = [
  {
    errorElement: <RouteError />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
      { path: '/invites/:token', element: <InvitePage /> },
      {
        element: <RequireSession />,
        children: [{ path: '/onboarding', element: <OnboardingPage /> }],
      },
      {
        element: <FlexibleShell />,
        children: [
          { path: '/explore', element: <ExplorePage /> },
          {
            path: '/events/:eventId',
            element: <EventLayout />,
            children: [
              {
                index: true,
                element: (
                  <ComingSoon title="Memórias" delivery="Próxima camada do showcase">
                    <p>O mural de memórias será incorporado no próximo commit temático.</p>
                  </ComingSoon>
                ),
              },
              { path: 'people', element: <PeopleTab /> },
              { path: 'invites', element: <InvitesTab /> },
              { path: 'edit', element: <EditEventTab /> },
            ],
          },
        ],
      },
      {
        element: <RequireOnboarded />,
        children: [
          {
            element: <AppShell />,
            children: [
              { path: '/', element: <HomePage /> },
              { path: '/profile', element: <ProfilePage /> },
              { path: '/events', element: <EventsPage /> },
              { path: '/events/new', element: <NewEventPage /> },
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
