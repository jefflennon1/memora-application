import { useMutation, useQueryClient } from '@tanstack/react-query';
import QRCode from 'qrcode';
import { useEffect, useState } from 'react';
import { isApiError } from '../../api/client';
import { isOrganization, type MemoraEvent } from '../../api/events';
import { keys } from '../../api/keys';
import { invitesApi, type Invite, type InvitePageInput, type InviteState, type InviteType } from '../../api/membership';
import { friendlyMessage, messageForCode } from '../../api/problems';
import { normalizeUsername, usernameFormatProblem, FORMAT_HINTS } from '../../auth/username';
import { ConfirmAction, EmptyState, LoadMore, PersonLine, SectionHeader, Tag } from '../../components/Bits';
import { Button } from '../../components/Button';
import { ChoiceGroup } from '../../components/ChoiceGroup';
import { SelectField } from '../../components/Fields';
import { Notice } from '../../components/Notice';
import page from '../../components/Page.module.css';
import { TextField } from '../../components/TextField';
import { formatShortDate } from '../../lib/format';
import { usePaged } from '../../lib/usePaged';
import { useEventContext } from '../events/EventLayout';
import styles from './Membership.module.css';
import { defaultInvitePage, InvitePageForm, pageValueFrom } from './InvitePageForm';

const TYPES = [
  {
    value: 'LINK' as InviteType,
    title: 'Link',
    description: 'Quem tiver o link pede para entrar, e a organização aprova.',
  },
  {
    value: 'QR_CODE' as InviteType,
    title: 'QR Code',
    description: 'Igual ao link, numa imagem para imprimir ou mostrar no dia.',
  },
  {
    value: 'NAMED' as InviteType,
    title: 'Para uma pessoa',
    description: 'Pelo @usuário. A pessoa entra direto, uma única vez.',
  },
];

export const TYPE_TITLE: Record<InviteType, string> = {
  LINK: 'Convite por link',
  QR_CODE: 'Convite por QR Code',
  NAMED: 'Convite nominal',
};

const STATE: Record<InviteState, { label: string; tone: 'primary' | 'neutral' | 'muted' | 'accent' }> = {
  ACTIVE: { label: 'Ativo', tone: 'primary' },
  USED: { label: 'Usado', tone: 'neutral' },
  EXPIRED: { label: 'Expirado', tone: 'muted' },
  REVOKED: { label: 'Cancelado', tone: 'muted' },
};

/** /events/:id/invites (organização). */
export function InvitesTab() {
  const { event } = useEventContext();
  if (!isOrganization(event)) {
    return <EmptyState title="Só a organização cria convites">Peça um convite para quem organiza o evento.</EmptyState>;
  }
  return <Invites event={event} />;
}

function Invites({ event }: { event: MemoraEvent }) {
  const client = useQueryClient();
  const [created, setCreated] = useState<Invite | null>(null);
  const invites = usePaged(keys.eventInvites(event.id), (p, signal) => invitesApi.list(event.id, { page: p, size: 20 }, signal));
  const revoke = useMutation({
    mutationFn: (inviteId: string) => invitesApi.revoke(event.id, inviteId),
    onSettled: () => void client.invalidateQueries({ queryKey: keys.eventInvites(event.id) }),
  });

  return (
    <div className={page.page}>
      <section className={page.section} aria-labelledby="new-invite">
        <SectionHeader id="new-invite" title="Novo convite" />
        <CreateInvite
          event={event}
          onCreated={(invite) => {
            setCreated(invite);
            void client.invalidateQueries({ queryKey: keys.eventInvites(event.id) });
          }}
        />
        {created && (
          <div aria-live="polite">
            <Notice tone="success" title="Convite criado. Agora é só compartilhar." />
            <InviteRow invite={created} event={event} highlight />
          </div>
        )}
      </section>

      <section className={page.section} aria-labelledby="invites">
        <SectionHeader id="invites" title="Convites do evento" />
        {invites.isError && <Notice tone="error" title="Não deu para carregar">{friendlyMessage(invites.error)}</Notice>}
        {revoke.isError && <Notice tone="error" title="Não deu para cancelar">{friendlyMessage(revoke.error)}</Notice>}
        {invites.isSuccess && invites.items.length === 0 && <p className={page.muted}>Nenhum convite criado ainda.</p>}
        <ul className={page.list}>
          {invites.items
            .filter((i) => i.id !== created?.id)
            .map((invite) => (
              <li key={invite.id}>
                <InviteRow
                  invite={invite}
                  event={event}
                  onRevoke={() => revoke.mutate(invite.id)}
                  revoking={revoke.isPending && revoke.variables === invite.id}
                />
              </li>
            ))}
        </ul>
        <LoadMore hasNext={!!invites.hasNextPage} loading={invites.isFetchingNextPage} onClick={() => invites.fetchNextPage()} />
        <p className={page.muted}>Cancelar um convite não tira ninguém do evento.</p>
      </section>
    </div>
  );
}

function CreateInvite({ event, onCreated }: { event: MemoraEvent; onCreated: (invite: Invite) => void }) {
  const [type, setType] = useState<InviteType>('LINK');
  const [username, setUsername] = useState('');
  const [days, setDays] = useState('30');
  const [touched, setTouched] = useState(false);
  const create = useMutation({
    mutationFn: async ({ page, background }: { page: InvitePageInput; background: File | null }) => {
      const invite = await invitesApi.create(event.id, {
        type,
        username: type === 'NAMED' ? normalizeUsername(username) : undefined,
        expiresInDays: Number(days),
        page,
      });
      return background
        ? invitesApi.uploadBackground(event.id, invite.id, background, background.name)
        : invite;
    },
    onSuccess: (invite) => {
      setUsername('');
      setTouched(false);
      onCreated(invite);
    },
  });

  const normalized = normalizeUsername(username);
  const problem = type === 'NAMED' ? usernameFormatProblem(normalized) : null;
  const usernameError =
    type === 'NAMED' && touched && problem
      ? FORMAT_HINTS[problem]
      : isApiError(create.error, 'USER_NOT_FOUND')
        ? 'Não encontramos ninguém com esse @usuário.'
        : isApiError(create.error, 'ALREADY_PARTICIPANT') || isApiError(create.error, 'INVITEE_REQUIRED')
          ? messageForCode(create.error.code)
          : null;
  const general = create.error && !usernameError ? friendlyMessage(create.error) : null;

  function submit(pageContent: InvitePageInput, background: File | null) {
    setTouched(true);
    if (problem) return;
    create.mutate({ page: pageContent, background });
  }

  return (
    <div className={styles.inviteForm}>
      <ChoiceGroup legend="Tipo de convite" hideLegend name="invite-type" value={type} onChange={setType} choices={TYPES} />
      {type === 'NAMED' && (
        <TextField
          label="@usuário de quem você quer convidar"
          prefix="@"
          value={username}
          onChange={(e) => setUsername(e.target.value.replace(/^@+/, ''))}
          autoCapitalize="none"
          autoCorrect="off"
          spellCheck={false}
          maxLength={31}
          error={usernameError}
          hint="O link só funciona para essa pessoa."
        />
      )}
      <SelectField label="Validade" value={days} onChange={(e) => setDays(e.target.value)}>
        <option value="1">1 dia</option>
        <option value="7">7 dias</option>
        <option value="30">30 dias</option>
        <option value="90">90 dias</option>
      </SelectField>
      {general && <Notice tone="error" title="Não deu para criar o convite">{general}</Notice>}
      <InvitePageForm
        initial={defaultInvitePage(event.name, event.description)}
        submitLabel="Criar convite"
        pending={create.isPending}
        onSubmit={(pageContent, background) => submit(pageContent, background)}
      />
    </div>
  );
}

function InviteRow({
  invite,
  event,
  onRevoke,
  revoking,
  highlight,
}: {
  invite: Invite;
  event: MemoraEvent;
  onRevoke?: () => void;
  revoking?: boolean;
  highlight?: boolean;
}) {
  const client = useQueryClient();
  const [editing, setEditing] = useState(false);
  const update = useMutation({
    mutationFn: async ({
      page: nextPage,
      background,
      removeBackground,
    }: {
      page: InvitePageInput;
      background: File | null;
      removeBackground: boolean;
    }) => {
      let updated = await invitesApi.updatePage(event.id, invite.id, nextPage);
      if (background) updated = await invitesApi.uploadBackground(event.id, invite.id, background, background.name);
      else if (removeBackground && invite.page?.backgroundImageUrl) updated = await invitesApi.removeBackground(event.id, invite.id);
      return updated;
    },
    onSuccess: () => {
      setEditing(false);
      void client.invalidateQueries({ queryKey: keys.eventInvites(event.id) });
    },
  });
  const state = STATE[invite.state];
  const active = invite.state === 'ACTIVE';
  return (
    <div className={styles.inviteRow} style={highlight ? { borderColor: 'var(--color-selected-border)' } : undefined}>
      <div className={styles.inviteTop}>
        <span className={styles.inviteTitle}>{TYPE_TITLE[invite.type]}</span>
        <Tag tone={state.tone}>{state.label}</Tag>
      </div>
      {invite.type === 'NAMED' && invite.invitee && <PersonLine person={invite.invitee} size={32} />}
      <p className={styles.meta}>
        Criado em {formatShortDate(invite.createdAt)}.{' '}
        {active ? `Vale até ${formatShortDate(invite.expiresAt)}.` : invite.usedAt ? `Usado em ${formatShortDate(invite.usedAt)}.` : ''}
      </p>
      {active && (
        <>
          <code className={styles.link}>{invite.url}</code>
          <ShareActions url={invite.url} />
          {invite.type === 'QR_CODE' && <QrImage url={invite.url} />}
        </>
      )}
      {active && !editing && (
        <div>
          <Button variant="outline" onClick={() => setEditing(true)}>Editar página do convite</Button>
        </div>
      )}
      {editing && (
        <div className={styles.inlineEditor}>
          <h3>Editar página do convite</h3>
          {update.isError && <Notice tone="error" title="Não deu para salvar">{friendlyMessage(update.error)}</Notice>}
          <InvitePageForm
            initial={invite.page ? pageValueFrom(invite.page) : defaultInvitePage(event.name, event.description)}
            submitLabel="Salvar alterações"
            pending={update.isPending}
            onCancel={() => setEditing(false)}
            onSubmit={(nextPage, background, removeBackground) =>
              update.mutate({ page: nextPage, background, removeBackground })
            }
          />
        </div>
      )}
      {active && onRevoke && (
        <div>
          <ConfirmAction
            label="Cancelar convite"
            variant="quiet"
            question="Cancelar este convite? O link para de funcionar; quem já entrou continua no evento."
            confirmLabel="Cancelar convite"
            onConfirm={onRevoke}
            pending={revoking}
          />
        </div>
      )}
    </div>
  );
}

function ShareActions({ url }: { url: string }) {
  const [copied, setCopied] = useState(false);
  const canShare = typeof navigator !== 'undefined' && typeof navigator.share === 'function';
  async function copy() {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2500);
    } catch {
      setCopied(false);
    }
  }
  return (
    <div className={styles.rowActions}>
      <Button variant="outline" onClick={copy}>
        {copied ? 'Link copiado' : 'Copiar link'}
      </Button>
      {canShare && (
        <Button variant="quiet" onClick={() => navigator.share({ title: 'Convite da Memora', url }).catch(() => undefined)}>
          Compartilhar
        </Button>
      )}
    </div>
  );
}

/** QR Code gerado no navegador a partir do link (a API não gera a imagem). */
export function QrImage({ url }: { url: string }) {
  const [src, setSrc] = useState<string | null>(null);
  useEffect(() => {
    let alive = true;
    QRCode.toDataURL(url, { margin: 1, width: 512, errorCorrectionLevel: 'M' })
      .then((data) => alive && setSrc(data))
      .catch(() => alive && setSrc(null));
    return () => {
      alive = false;
    };
  }, [url]);
  if (!src) return null;
  return (
    <div className={styles.qr}>
      <img src={src} alt="QR Code do convite" />
      <a href={src} download="convite-memora.png">
        Baixar QR Code
      </a>
    </div>
  );
}
