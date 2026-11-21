import type { RouteObject } from 'react-router';
import { Wordmark } from '../components/Wordmark';
import { ThemeToggle } from '../theme/ThemeToggle';

function ShowcaseLanding() {
  return (
    <main>
      <header>
        <Wordmark />
        <ThemeToggle />
      </header>
      <section>
        <h1>Momentos passam. Memórias ficam.</h1>
        <p>
          Esta interface pública é executada somente com uma API local. Os fluxos do produto são adicionados de
          forma incremental nas próximas camadas do showcase.
        </p>
      </section>
    </main>
  );
}

export const routes: RouteObject[] = [{ path: '*', element: <ShowcaseLanding /> }];
