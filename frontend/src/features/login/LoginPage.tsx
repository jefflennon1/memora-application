import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router';
import { authApi } from '../../api/auth';
import { api } from '../../api/client';
import { friendlyMessage, messageForCode } from '../../api/problems';
import { usersApi } from '../../api/users';
import { googleLoginUrl, safeNext, withNext } from '../../auth/redirect';
import { ME_KEY, useMe } from '../../auth/session';
import { Button, ButtonLink, LinkButton } from '../../components/Button';
import { Notice } from '../../components/Notice';
import { TextField } from '../../components/TextField';
import { Wordmark } from '../../components/Wordmark';
import { ThemeToggle } from '../../theme/ThemeToggle';
import styles from './LoginPage.module.css';

interface EventCategory {
  code: string;
  name: string;
}

const LOGIN_ERRORS: Record<string, { title: string; body: string }> = {
  login_failed: {
    title: 'Não foi possível entrar',
    body: 'O login com o Google foi cancelado ou não terminou. Tente de novo.',
  },
  account_disabled: {
    title: 'Conta desativada',
    body: messageForCode('ACCOUNT_DISABLED'),
  },
};

export function LoginPage() {
  const [params] = useSearchParams();
  const next = safeNext(params.get('next'), '');
  const errorKey = params.get('error');
  const loginError = errorKey ? (LOGIN_ERRORS[errorKey] ?? LOGIN_ERRORS.login_failed!) : null;
  const me = useMe();
  const client = useQueryClient();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  const login = useMutation({
    mutationFn: async () => {
      await authApi.login({ username, password });
      return usersApi.me();
    },
    onSuccess: (account) => {
      client.setQueryData(ME_KEY, account);
      navigate(account.onboardingCompleted ? next || '/' : withNext('/onboarding', next), { replace: true });
    },
  });

  // Já tem sessão: segue para o destino (ou para o cadastro, se estiver pendente).
  if (me.data) {
    return me.data.onboardingCompleted ? (
      <Navigate to={next || '/'} replace />
    ) : (
      <Navigate to={withNext('/onboarding', next)} replace />
    );
  }

  const cameFromInvite = next.startsWith('/invites/');

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!username.trim() || !password) return;
    login.mutate();
  }

  return (
    <div className={styles.page}>
      <main className={styles.main}>
        <div className={styles.topRow}>
          <Wordmark size="lg" />
          <ThemeToggle />
        </div>

        <h1 className={styles.slogan}>
          <span className={styles.passing}>
            Momentos passam<span className={styles.period}>.</span>
          </span>
          <span className={styles.staying}>
            Memórias ficam<span className={styles.period}>.</span>
          </span>
        </h1>

        <p className={styles.lede}>
          Guarde num só lugar as fotos, os recados e as histórias de quem viveu o momento com você: do chá de
          fraldas aos 80 anos da vó.
        </p>

        {loginError && (
          <Notice tone="error" title={loginError.title}>
            {loginError.body}
          </Notice>
        )}
        {cameFromInvite && !loginError && (
          <Notice title="Você recebeu um convite">Entre para ver o evento e participar.</Notice>
        )}

        <form className={styles.localForm} onSubmit={submit} noValidate>
          <TextField
            label="Nome de usuário"
            prefix="@"
            value={username}
            onChange={(event) => {
              setUsername(event.target.value.replace(/^@+/, ''));
              login.reset();
            }}
            autoComplete="username"
            autoCapitalize="none"
            autoCorrect="off"
            spellCheck={false}
            required
          />
          <TextField
            label="Senha"
            type="password"
            value={password}
            onChange={(event) => {
              setPassword(event.target.value);
              login.reset();
            }}
            autoComplete="current-password"
            required
          />
          {login.isError && (
            <Notice tone="error" title="Não foi possível entrar">
              {friendlyMessage(login.error)}
            </Notice>
          )}
          <Button type="submit" block loading={login.isPending} disabled={!username.trim() || !password}>
            Entrar
          </Button>
        </form>

        <div className={styles.createAccount}>
          <span>Ainda não tem uma conta?</span>
          <LinkButton to={withNext('/register', next)} variant="outline" block>
            Criar conta
          </LinkButton>
        </div>

        <div className={styles.divider} aria-hidden="true"><span>ou</span></div>

        <div className={styles.actions}>
          <ButtonLink href={googleLoginUrl(next)} block icon={<GoogleMark />}>
            Entrar com Google
          </ButtonLink>
          <p className={styles.fine}>
            Na primeira vez, criamos sua conta. Do Google usamos só nome, foto e e-mail.
          </p>
        </div>
      </main>

      <CategoriesPanel />
    </div>
  );
}

/** Painel decorativo com as categorias reais da API (endpoint público). Some se a API não responder. */
export function CategoriesPanel() {
  const categories = useQuery({
    queryKey: ['event-categories'],
    queryFn: ({ signal }) => api<EventCategory[]>('/event-categories', { signal }),
    staleTime: Infinity,
    retry: false,
  });

  return (
    <aside className={styles.panel} aria-label="Tipos de evento">
      {categories.data && categories.data.length > 0 && (
        <>
          <p className={styles.panelTitle}>Para cada encontro que vale lembrar</p>
          <ul className={styles.categories}>
            {categories.data.map((c) => (
              <li key={c.code}>{c.name}</li>
            ))}
          </ul>
        </>
      )}
    </aside>
  );
}

function GoogleMark() {
  return (
    <svg width="20" height="20" viewBox="0 0 48 48" aria-hidden="true" className={styles.google}>
      <path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z" />
      <path fill="#FF3D00" d="m6.3 14.7 6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z" />
      <path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-8l-6.5 5C9.5 39.6 16.2 44 24 44z" />
      <path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.4-.4-3.5z" />
    </svg>
  );
}
