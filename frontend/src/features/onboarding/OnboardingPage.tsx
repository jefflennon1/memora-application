import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router';
import { isApiError } from '../../api/client';
import { friendlyMessage, messageForCode } from '../../api/problems';
import { usersApi, type Me, type ProfilePrivacy } from '../../api/users';
import { safeNext } from '../../auth/redirect';
import { ME_KEY, useLogout, useMe } from '../../auth/session';
import { Avatar } from '../../components/Avatar';
import { Button } from '../../components/Button';
import { ChoiceGroup } from '../../components/ChoiceGroup';
import { Notice } from '../../components/Notice';
import { TextField } from '../../components/TextField';
import { UsernameField, useUsernameCheck } from '../../components/UsernameField';
import { Wordmark } from '../../components/Wordmark';
import { ThemeToggle } from '../../theme/ThemeToggle';
import { PRIVACY_CHOICES } from '../profile/privacy';
import styles from './OnboardingPage.module.css';

const USERNAME_CODES = new Set(['INVALID_USERNAME', 'USERNAME_RESERVED', 'USERNAME_TAKEN']);

export function OnboardingPage() {
  const { data: me } = useMe();
  const [params] = useSearchParams();
  const next = safeNext(params.get('next'));

  if (!me) return null; // o guard RequireSession já trata a ausência de sessão
  if (me.onboardingCompleted) return <Navigate to={next} replace />;
  return <OnboardingForm me={me} next={next} />;
}

function OnboardingForm({ me, next }: { me: Me; next: string }) {
  const client = useQueryClient();
  const navigate = useNavigate();
  const logout = useLogout();

  const [displayName, setDisplayName] = useState(me.displayName ?? '');
  const [keepPhoto, setKeepPhoto] = useState<'KEEP' | 'REMOVE'>('KEEP');
  const [username, setUsername] = useState('');
  const [privacy, setPrivacy] = useState<ProfilePrivacy>(me.profilePrivacy ?? 'PUBLIC');
  const [submitted, setSubmitted] = useState(false);

  const check = useUsernameCheck(username);
  const nameError = displayName.trim().length === 0 ? 'Diga como você quer ser chamado.' : null;

  const complete = useMutation({
    mutationFn: usersApi.completeOnboarding,
    onSuccess: (updated) => {
      client.setQueryData(ME_KEY, updated);
      navigate(next, { replace: true });
    },
    onError: (error) => {
      if (isApiError(error, 'ONBOARDING_ALREADY_COMPLETED')) {
        void client.invalidateQueries({ queryKey: ME_KEY });
      }
    },
  });

  const error = complete.error;
  const usernameServerError =
    isApiError(error) && USERNAME_CODES.has(error.code) ? messageForCode(error.code) : null;
  const nameServerError = isApiError(error, 'INVALID_DISPLAY_NAME')
    ? messageForCode(error.code)
    : isApiError(error, 'VALIDATION_FAILED')
      ? (error.fieldErrors().displayName ?? null)
      : null;
  const generalError = error && !usernameServerError && !nameServerError ? friendlyMessage(error) : null;

  const hasPhoto = !!me.photoUrl;
  const canSubmit = !nameError && (check.state === 'available' || check.state === 'checking');

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitted(true);
    if (nameError || check.state === 'invalid' || check.state === 'unavailable' || check.state === 'idle') return;
    complete.mutate({
      username: check.normalized,
      displayName: displayName.trim(),
      keepSuggestedPhoto: hasPhoto ? keepPhoto === 'KEEP' : false,
      profilePrivacy: privacy,
    });
  }

  const firstName = me.displayName?.trim().split(/\s+/)[0];
  let step = 0;

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <Wordmark />
        <div className={styles.headerActions}>
          <ThemeToggle />
          <Button variant="quiet" onClick={() => logout.mutate()} loading={logout.isPending}>
            Sair
          </Button>
        </div>
      </header>

      <main className={styles.main}>
        <div className={styles.intro}>
          <h1 className={styles.title}>{firstName ? `Boas-vindas, ${firstName}` : 'Boas-vindas à Memora'}</h1>
          <p className={styles.lede}>
            Antes do primeiro evento, confirme como as pessoas vão ver você. Dá para mudar tudo depois no seu perfil.
          </p>
        </div>

        <form className={styles.form} onSubmit={onSubmit} noValidate>
          <section className={styles.step} aria-labelledby="step-name">
            <StepHeading id="step-name" number={++step} title="Seu nome" />
            <TextField
              label="Nome exibido"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              maxLength={80}
              autoComplete="name"
              hint="É assim que você aparece nas memórias e nos comentários."
              error={nameServerError ?? (submitted ? nameError : null)}
              required
            />
          </section>

          <section className={styles.step} aria-labelledby="step-photo">
            <StepHeading id="step-photo" number={++step} title="Sua foto" />
            {hasPhoto ? (
              <ChoiceGroup
                legend="Foto do perfil"
                hideLegend
                name="photo"
                value={keepPhoto}
                onChange={setKeepPhoto}
                choices={[
                  {
                    value: 'KEEP',
                    title: 'Usar a foto do Google',
                    description: 'A mesma da sua conta Google.',
                    visual: <Avatar name={displayName || me.displayName} src={me.photoUrl} size={44} />,
                  },
                  {
                    value: 'REMOVE',
                    title: 'Ficar sem foto',
                    description: 'Mostramos suas iniciais no lugar.',
                    visual: <Avatar name={displayName || me.displayName} size={44} />,
                  },
                ]}
              />
            ) : (
              <p className={styles.muted}>
                Sua conta Google não tem foto, então mostramos suas iniciais. O envio de foto própria chega em breve.
              </p>
            )}
          </section>

          <section className={styles.step} aria-labelledby="step-username">
            <StepHeading id="step-username" number={++step} title="Seu @usuário" />
            <UsernameField value={username} onChange={setUsername} check={check} serverError={usernameServerError} />
          </section>

          <section className={styles.step} aria-labelledby="step-privacy">
            <StepHeading id="step-privacy" number={++step} title="Quem vê seu perfil" />
            <ChoiceGroup
              legend="Privacidade do perfil"
              hideLegend
              name="privacy"
              value={privacy}
              onChange={setPrivacy}
              choices={PRIVACY_CHOICES}
            />
          </section>

          {generalError && <Notice tone="error" title="Não deu para concluir">{generalError}</Notice>}

          <div className={styles.submit}>
            <Button type="submit" block loading={complete.isPending} disabled={!canSubmit && submitted}>
              Concluir cadastro
            </Button>
          </div>
        </form>
      </main>
    </div>
  );
}

function StepHeading({ id, number, title }: { id: string; number: number; title: string }) {
  return (
    <h2 id={id} className={styles.stepHeading}>
      <span className={styles.stepNumber} aria-hidden="true">
        {number}
      </span>
      {title}
    </h2>
  );
}
