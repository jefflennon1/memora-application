import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { isApiError } from '../../api/client';
import { friendlyMessage, messageForCode } from '../../api/problems';
import { usersApi, type Me, type ProfilePrivacy, type ProfileUpdateRequest } from '../../api/users';
import { ME_KEY, useLogout } from '../../auth/session';
import { useCurrentUser } from '../../auth/useCurrentUser';
import { Avatar } from '../../components/Avatar';
import { Button } from '../../components/Button';
import { ChoiceGroup } from '../../components/ChoiceGroup';
import { ImageCropDialog } from '../../components/ImageCropDialog';
import { Notice } from '../../components/Notice';
import { TextField } from '../../components/TextField';
import { UsernameField, useUsernameCheck } from '../../components/UsernameField';
import { prepareImage } from '../../lib/images';
import { PRIVACY_CHOICES, PRIVACY_LABEL } from './privacy';
import styles from './ProfilePage.module.css';

const USERNAME_CODES = new Set(['INVALID_USERNAME', 'USERNAME_RESERVED', 'USERNAME_TAKEN']);
const memberSince = new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric' });

export function ProfilePage() {
  const me = useCurrentUser();
  return (
    <div className={styles.page}>
      <header className={styles.identity}>
        <Avatar name={me.displayName} src={me.photoUrl} size={88} />
        <div className={styles.identityText}>
          <h1 className={styles.name}>{me.displayName}</h1>
          <p className={styles.handle}>@{me.username}</p>
          <p className={styles.meta}>
            Perfil {PRIVACY_LABEL[me.profilePrivacy].toLowerCase()}, na Memora desde{' '}
            {memberSince.format(new Date(me.createdAt))}
          </p>
        </div>
      </header>

      <ProfileForm me={me} />
      <PhotoSection me={me} />
      <AccountSection me={me} />
    </div>
  );
}

function ProfileForm({ me }: { me: Me }) {
  const client = useQueryClient();
  const [displayName, setDisplayName] = useState(me.displayName);
  const [username, setUsername] = useState(me.username ?? '');
  const [privacy, setPrivacy] = useState<ProfilePrivacy>(me.profilePrivacy);
  const [saved, setSaved] = useState(false);

  const check = useUsernameCheck(username, me.username);

  const changes: ProfileUpdateRequest = {};
  if (displayName.trim() !== me.displayName) changes.displayName = displayName.trim();
  if (check.normalized !== me.username) changes.username = check.normalized;
  if (privacy !== me.profilePrivacy) changes.profilePrivacy = privacy;
  const dirty = Object.keys(changes).length > 0;

  const update = useMutation({
    mutationFn: usersApi.updateProfile,
    onSuccess: (updated) => {
      client.setQueryData(ME_KEY, updated);
      // Alinha o formulário ao que a API gravou (ex.: @usuário normalizado em minúsculas).
      setDisplayName(updated.displayName);
      setUsername(updated.username ?? '');
      setPrivacy(updated.profilePrivacy);
      setSaved(true);
    },
  });

  const nameError = displayName.trim().length === 0 ? 'O nome não pode ficar vazio.' : null;
  const error = update.error;
  const usernameServerError =
    isApiError(error) && USERNAME_CODES.has(error.code) ? messageForCode(error.code) : null;
  const nameServerError = isApiError(error, 'INVALID_DISPLAY_NAME') ? messageForCode(error.code) : null;
  const generalError = error && !usernameServerError && !nameServerError ? friendlyMessage(error) : null;
  const blocked = !!nameError || check.state === 'invalid' || check.state === 'unavailable';

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!dirty || blocked) return;
    setSaved(false);
    update.mutate(changes);
  }

  return (
    <section className={styles.section} aria-labelledby="edit-profile">
      <h2 id="edit-profile" className={styles.sectionTitle}>
        Editar perfil
      </h2>
      <form className={styles.form} onSubmit={onSubmit} noValidate>
        <TextField
          label="Nome exibido"
          value={displayName}
          onChange={(e) => {
            setDisplayName(e.target.value);
            setSaved(false);
          }}
          maxLength={80}
          autoComplete="name"
          error={nameServerError ?? nameError}
          required
        />
        <UsernameField
          value={username}
          onChange={(value) => {
            setUsername(value);
            setSaved(false);
          }}
          check={check}
          serverError={usernameServerError}
        />
        <ChoiceGroup
          legend="Quem vê seu perfil"
          name="privacy"
          value={privacy}
          onChange={(value) => {
            setPrivacy(value);
            setSaved(false);
          }}
          choices={PRIVACY_CHOICES}
        />

        {generalError && <Notice tone="error" title="Não deu para salvar">{generalError}</Notice>}
        {saved && !dirty && <Notice tone="success" title="Perfil salvo" />}

        <div className={styles.actions}>
          <Button type="submit" loading={update.isPending} disabled={!dirty || blocked}>
            Salvar alterações
          </Button>
        </div>
      </form>
    </section>
  );
}

function PhotoSection({ me }: { me: Me }) {
  const client = useQueryClient();
  const input = useRef<HTMLInputElement>(null);
  const [confirming, setConfirming] = useState(false);
  const [cropFile, setCropFile] = useState<File | null>(null);
  const upload = useMutation({
    mutationFn: async (file: File) => {
      const prepared = await prepareImage(file);
      return usersApi.uploadPhoto(prepared.blob, prepared.name);
    },
    onSuccess: (updated) => {
      client.setQueryData(ME_KEY, updated);
      setConfirming(false);
    },
  });
  const remove = useMutation({
    mutationFn: usersApi.removePhoto,
    onSuccess: (updated) => {
      client.setQueryData(ME_KEY, updated);
      setConfirming(false);
    },
  });

  function choose(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (file) setCropFile(file);
  }

  return (
    <section className={styles.section} aria-labelledby="photo">
      <h2 id="photo" className={styles.sectionTitle}>
        Foto
      </h2>
      <input
        ref={input}
        className={styles.fileInput}
        type="file"
        accept="image/jpeg,image/png,image/webp,image/gif,.heic,.heif"
        onChange={choose}
        tabIndex={-1}
      />
      <div className={styles.photoCard}>
        <div className={styles.photoPreview}>
          <Avatar name={me.displayName} src={me.photoUrl} size={96} />
          <button
            type="button"
            className={styles.photoCamera}
            onClick={() => input.current?.click()}
            aria-label={me.photoUrl ? 'Alterar foto de perfil' : 'Adicionar foto de perfil'}
            disabled={upload.isPending}
          >
            <CameraIcon />
          </button>
        </div>
        <div className={styles.photoControls}>
          <div>
            <h3>{me.photoUrl ? 'Sua foto atual' : 'Adicione uma foto'}</h3>
            <p className={styles.muted}>JPEG, PNG, WebP ou GIF. A imagem será ajustada antes do envio.</p>
          </div>
          <div className={styles.photoActions}>
            <Button variant="outline" onClick={() => input.current?.click()} loading={upload.isPending}>
              {me.photoUrl ? 'Alterar foto' : 'Escolher foto'}
            </Button>
            {me.photoUrl && !confirming && (
              <Button variant="quiet" onClick={() => setConfirming(true)} disabled={upload.isPending}>
                Remover
              </Button>
            )}
          </div>
          {confirming ? (
            <div className={styles.confirm} role="group" aria-label="Confirmar remoção da foto">
              <p>Remover sua foto atual? Suas iniciais aparecerão no lugar.</p>
              <div className={styles.confirmActions}>
                <Button variant="danger" onClick={() => remove.mutate()} loading={remove.isPending}>
                  Remover foto
                </Button>
                <Button variant="quiet" onClick={() => setConfirming(false)} disabled={remove.isPending}>
                  Manter foto
                </Button>
              </div>
            </div>
          ) : null}
        </div>
      </div>
      {upload.isError && <Notice tone="error" title="Não deu para enviar a foto">{friendlyMessage(upload.error)}</Notice>}
      {remove.isError && <Notice tone="error" title="Não deu para remover a foto">{friendlyMessage(remove.error)}</Notice>}
      {cropFile && (
        <ImageCropDialog
          file={cropFile}
          title="Recortar foto de perfil"
          aspectRatio={1}
          onCancel={() => setCropFile(null)}
          onApply={(file) => {
            setCropFile(null);
            upload.mutate(file);
          }}
        />
      )}
    </section>
  );
}

function CameraIcon() {
  return (
    <svg width="19" height="19" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M4 7.5h3l1.5-2h7l1.5 2h3a2 2 0 0 1 2 2v8.5a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9.5a2 2 0 0 1 2-2Z" fill="none" stroke="currentColor" strokeWidth="1.8" />
      <circle cx="12" cy="13.5" r="3.5" fill="none" stroke="currentColor" strokeWidth="1.8" />
    </svg>
  );
}

function AccountSection({ me }: { me: Me }) {
  const logout = useLogout();
  const navigate = useNavigate();
  const google = me.identities.find((i) => i.provider === 'GOOGLE');

  return (
    <section className={styles.section} aria-labelledby="account">
      <h2 id="account" className={styles.sectionTitle}>
        Conta
      </h2>
      {google && (
        <p className={styles.muted}>
          Você entra com a conta Google <strong className={styles.email}>{google.email ?? 'vinculada'}</strong>.
        </p>
      )}
      {me.passwordLogin && (
        <p className={styles.muted}>
          Você entra com <strong className={styles.email}>@{me.username}</strong> e senha
          {me.phone ? <>. Telefone cadastrado: <strong className={styles.email}>{formatPhone(me.phone)}</strong></> : '.'}
        </p>
      )}
      {logout.isError && <Notice tone="error" title="Não deu para sair">{friendlyMessage(logout.error)}</Notice>}
      <div>
        <Button
          variant="outline"
          loading={logout.isPending}
          onClick={() => logout.mutate(undefined, { onSuccess: () => navigate('/login', { replace: true }) })}
        >
          Sair da Memora
        </Button>
      </div>
    </section>
  );
}

function formatPhone(raw: string) {
  const digits = raw.replace(/\D/g, '');
  if (digits.length === 11) return `(${digits.slice(0, 2)}) ${digits.slice(2, 7)}-${digits.slice(7)}`;
  if (digits.length === 10) return `(${digits.slice(0, 2)}) ${digits.slice(2, 6)}-${digits.slice(6)}`;
  return raw;
}
