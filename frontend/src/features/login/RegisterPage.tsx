import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router';
import { authApi } from '../../api/auth';
import { isApiError } from '../../api/client';
import { friendlyMessage, messageForCode } from '../../api/problems';
import { usersApi } from '../../api/users';
import { safeNext, withNext } from '../../auth/redirect';
import { ME_KEY, useMe } from '../../auth/session';
import { Button, LinkButton } from '../../components/Button';
import { Notice } from '../../components/Notice';
import { TextField } from '../../components/TextField';
import { UsernameField, useUsernameCheck } from '../../components/UsernameField';
import { Wordmark } from '../../components/Wordmark';
import { ThemeToggle } from '../../theme/ThemeToggle';
import { CategoriesPanel } from './LoginPage';
import styles from './LoginPage.module.css';

const USERNAME_CODES = new Set(['INVALID_USERNAME', 'USERNAME_RESERVED', 'USERNAME_TAKEN']);

export function RegisterPage() {
  const [params] = useSearchParams();
  const next = safeNext(params.get('next'), '');
  const navigate = useNavigate();
  const client = useQueryClient();
  const me = useMe();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [phone, setPhone] = useState('');
  const check = useUsernameCheck(username, null, authApi.usernameAvailability);

  const register = useMutation({
    mutationFn: async () => {
      await authApi.register({
        username: check.normalized,
        password,
        passwordConfirmation: confirmation,
        phone,
      });
      return usersApi.me();
    },
    onSuccess: (account) => {
      client.setQueryData(ME_KEY, account);
      navigate(next || '/', { replace: true });
    },
  });

  if (me.data) return <Navigate to={next || '/'} replace />;

  const phoneDigits = phone.replace(/\D/g, '');
  const passwordError = password.length > 0 && password.length < 8 ? 'Use pelo menos 8 caracteres.' : null;
  const confirmationError = confirmation.length > 0 && confirmation !== password ? 'As senhas não são iguais.' : null;
  const phoneError = phone.length > 0 && (phoneDigits.length < 10 || phoneDigits.length > 15)
    ? 'Informe um telefone válido, com DDD.'
    : null;
  const serverCode = isApiError(register.error) ? register.error.code : null;
  const usernameServerError = serverCode && USERNAME_CODES.has(serverCode) ? messageForCode(serverCode) : null;
  const fieldCode = new Set(['INVALID_PASSWORD', 'PASSWORDS_DO_NOT_MATCH', 'INVALID_PHONE']);
  const generalError = register.error && (!serverCode || (!USERNAME_CODES.has(serverCode) && !fieldCode.has(serverCode)))
    ? friendlyMessage(register.error)
    : null;
  const ready = check.state === 'available' && password.length >= 8 && confirmation === password &&
    phoneDigits.length >= 10 && phoneDigits.length <= 15;

  function submit(event: FormEvent) {
    event.preventDefault();
    if (ready) register.mutate();
  }

  return (
    <div className={styles.page}>
      <main className={styles.main}>
        <div className={styles.topRow}>
          <Wordmark size="lg" />
          <ThemeToggle />
        </div>

        <div className={styles.registerHeading}>
          <h1>Crie sua conta</h1>
          <p>Um cadastro simples para começar a guardar suas memórias.</p>
        </div>

        <form className={styles.localForm} onSubmit={submit} noValidate>
          <UsernameField
            value={username}
            onChange={(value) => {
              setUsername(value);
              register.reset();
            }}
            check={check}
            serverError={usernameServerError}
            autoFocus
          />
          <TextField
            label="Senha"
            type="password"
            value={password}
            onChange={(event) => {
              setPassword(event.target.value);
              register.reset();
            }}
            minLength={8}
            maxLength={72}
            autoComplete="new-password"
            hint="Use pelo menos 8 caracteres."
            error={serverCode === 'INVALID_PASSWORD' ? messageForCode(serverCode) : passwordError}
            required
          />
          <TextField
            label="Repita a senha"
            type="password"
            value={confirmation}
            onChange={(event) => {
              setConfirmation(event.target.value);
              register.reset();
            }}
            minLength={8}
            maxLength={72}
            autoComplete="new-password"
            error={serverCode === 'PASSWORDS_DO_NOT_MATCH' ? messageForCode(serverCode) : confirmationError}
            required
          />
          <TextField
            label="Telefone"
            type="tel"
            value={phone}
            onChange={(event) => {
              setPhone(event.target.value);
              register.reset();
            }}
            maxLength={30}
            autoComplete="tel"
            inputMode="tel"
            placeholder="(11) 99999-9999"
            hint="Seu telefone é privado e não aparece no perfil."
            error={serverCode === 'INVALID_PHONE' ? messageForCode(serverCode) : phoneError}
            required
          />

          {generalError && <Notice tone="error" title="Não foi possível criar a conta">{generalError}</Notice>}

          <Button type="submit" block loading={register.isPending} disabled={!ready}>
            Criar minha conta
          </Button>
        </form>

        <div className={styles.createAccount}>
          <span>Já tem uma conta?</span>
          <LinkButton to={withNext('/login', next)} variant="outline" block>
            Voltar para entrar
          </LinkButton>
        </div>
      </main>
      <CategoriesPanel />
    </div>
  );
}
