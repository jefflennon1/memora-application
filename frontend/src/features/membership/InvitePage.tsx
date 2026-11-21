import { useState, type CSSProperties } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router';
import { isApiError } from '../../api/client';
import { apiAsset } from '../../api/config';
import { keys } from '../../api/keys';
import { invitesApi, type InvitePageContent, type InvitePreview } from '../../api/membership';
import { friendlyMessage, messageForCode } from '../../api/problems';
import { withNext } from '../../auth/redirect';
import { useMe } from '../../auth/session';
import { EmptyState } from '../../components/Bits';
import { Button, LinkButton } from '../../components/Button';
import { Notice } from '../../components/Notice';
import page from '../../components/Page.module.css';
import { Wordmark } from '../../components/Wordmark';
import { formatEventWhen, formatShortDate } from '../../lib/format';
import styles from './Membership.module.css';

const GONE = new Set(['INVITE_EXPIRED', 'INVITE_REVOKED', 'INVITE_ALREADY_USED']);

/** Página pública e personalizada em /invites/:token. A sessão só é exigida ao responder. */
export function InvitePage() {
  const { token = '' } = useParams();
  const preview = useQuery({
    queryKey: keys.invitePreview(token),
    queryFn: ({ signal }) => invitesApi.preview(token, signal),
    retry: false,
  });

  if (preview.isPending) return <div className={styles.inviteLoading}>Abrindo o convite…</div>;
  if (preview.isError) {
    const code = isApiError(preview.error) ? preview.error.code : '';
    const title = GONE.has(code)
      ? 'Este convite não vale mais'
      : code === 'INVITE_NOT_FOUND'
        ? 'Convite não encontrado'
        : 'Não deu para abrir o convite';
    return (
      <div className={styles.inviteErrorPage}>
        <EmptyState title={title} action={<LinkButton to="/" variant="outline">Ir para o início</LinkButton>}>
          {friendlyMessage(preview.error)}
          {code === 'INVITE_NOT_FOUND' && ' Confira se o endereço do convite está completo.'}
        </EmptyState>
      </div>
    );
  }
  return <PublicInvite invite={preview.data} token={token} />;
}

function fallbackContent(invite: InvitePreview): InvitePageContent {
  return {
    title: `Você está convidado para ${invite.event.name}`,
    body: 'Esperamos você para compartilhar este momento com a gente.',
    footer: null,
    location: invite.event.city ?? null,
    backgroundColor: '#163f47',
    backgroundGradientColor: '#256a75',
    backgroundImageLayout: 'COVER',
  };
}

function inviteBackground(content: InvitePageContent): CSSProperties {
  if (content.backgroundImageUrl) {
    return {
      backgroundColor: content.backgroundColor,
      backgroundImage: `url(${JSON.stringify(apiAsset(content.backgroundImageUrl))})`,
      backgroundPosition: content.backgroundImageLayout === 'COVER' ? 'center' : 'top left',
      backgroundSize: content.backgroundImageLayout === 'COVER' ? 'cover' : 'auto',
      backgroundRepeat: content.backgroundImageLayout === 'COVER' ? 'no-repeat' : 'repeat',
      backgroundAttachment: content.backgroundImageLayout === 'COVER' ? 'fixed' : undefined,
    };
  }
  return {
    backgroundColor: content.backgroundColor,
    backgroundImage: content.backgroundGradientColor
      ? `linear-gradient(145deg, ${content.backgroundColor}, ${content.backgroundGradientColor})`
      : undefined,
  };
}

export function googleMapsEmbedUrl(location: string): string {
  return `https://www.google.com/maps?q=${encodeURIComponent(location)}&output=embed`;
}

function PublicInvite({ invite, token }: { invite: InvitePreview; token: string }) {
  const content = invite.page ?? fallbackContent(invite);
  return (
    <div className={styles.publicInvitePage} style={inviteBackground(content)}>
      <div className={styles.inviteBackdrop} aria-hidden="true" />
      <header className={styles.publicInviteTop}>
        <Link to="/" aria-label="Memora"><Wordmark /></Link>
      </header>
      <main className={styles.publicInviteMain}>
        <article className={styles.publicInviteCard} aria-labelledby="invite-title">
          <p className={styles.inviteEyebrow}>Você recebeu um convite</p>
          <h1 id="invite-title" className={styles.publicInviteTitle}>{content.title}</h1>
          <p className={styles.publicInviteBody}>{content.body}</p>
          <div className={styles.inviteEventDetails}>
            <h2>{invite.event.name}</h2>
            <span>{formatEventWhen(invite.event)}</span>
          </div>
          <InviteActions invite={invite} token={token} />
          <p className={styles.inviteValidity}>Convite válido até {formatShortDate(invite.expiresAt)}.</p>
          {content.footer && <footer className={styles.publicInviteFooter}>{content.footer}</footer>}
        </article>

        {content.location && (
          <section className={styles.inviteMap} aria-labelledby="invite-location">
            <div className={styles.mapHeading}>
              <div>
                <span>Localização</span>
                <h2 id="invite-location">{content.location}</h2>
              </div>
              <a href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(content.location)}`} target="_blank" rel="noreferrer">
                Abrir no Google Maps
              </a>
            </div>
            <iframe
              title={`Mapa de ${content.location}`}
              src={googleMapsEmbedUrl(content.location)}
              loading="lazy"
              referrerPolicy="no-referrer-when-downgrade"
              allowFullScreen
            />
          </section>
        )}
      </main>
    </div>
  );
}

function InviteActions({ invite, token }: { invite: InvitePreview; token: string }) {
  const client = useQueryClient();
  const navigate = useNavigate();
  const meQuery = useMe();
  const me = meQuery.data;
  const [localStatus, setLocalStatus] = useState(invite.myStatus);
  const eventPath = `/events/${invite.event.id}`;
  const respond = useMutation({
    mutationFn: (answer: 'ACCEPT' | 'DECLINE') =>
      answer === 'ACCEPT'
        ? invitesApi.join(token, invite.event.modality === 'IN_PERSON' ? 'ATTENDING' : undefined)
        : invitesApi.decline(token),
    onSuccess: (result, answer) => {
      void client.invalidateQueries({ queryKey: keys.myEvents });
      void client.invalidateQueries({ queryKey: keys.receivedInvites });
      if (answer === 'DECLINE' || result.status === 'DECLINED') {
        setLocalStatus('DECLINED');
        return;
      }
      if (result.status === 'JOINED' || result.status === 'ALREADY_PARTICIPANT') {
        void client.invalidateQueries({ queryKey: keys.event(result.eventId) });
        navigate(`/events/${result.eventId}`);
        return;
      }
      setLocalStatus(result.status === 'REJECTED' ? 'REJECTED' : 'PENDING_APPROVAL');
    },
  });

  if (meQuery.isPending) {
    return <div className={styles.inviteActionLoading}>Preparando sua resposta…</div>;
  }

  if (!me) {
    return (
      <div className={styles.inviteActions}>
        <Notice title="Entre para responder">Você pode ver o convite sem uma conta, mas precisa entrar para aceitar ou recusar.</Notice>
        <LinkButton to={withNext('/login', `/invites/${token}`)} block>Entrar para responder</LinkButton>
      </div>
    );
  }

  if (!me.onboardingCompleted) {
    return (
      <div className={styles.inviteActions}>
        <Notice title="Falta pouco">Conclua seu cadastro para responder. Depois, você volta direto para este convite.</Notice>
        <LinkButton to={withNext('/onboarding', `/invites/${token}`)} block>Concluir cadastro</LinkButton>
      </div>
    );
  }

  const code = isApiError(respond.error) ? respond.error.code : '';
  const error = respond.error ? (
    <Notice tone="error" title="Não deu para responder">
      {GONE.has(code) ? messageForCode(code) : friendlyMessage(respond.error)}
    </Notice>
  ) : null;

  if (localStatus === 'CAN_JOIN' || localStatus === 'CAN_REQUEST' || localStatus === 'DECLINED') {
    return (
      <div className={styles.inviteActions}>
        {localStatus === 'DECLINED' && <Notice title="Você recusou este convite">Mudou de ideia? Você ainda pode aceitar enquanto o convite estiver válido.</Notice>}
        {error}
        <div className={styles.responseButtons}>
          <Button onClick={() => respond.mutate('ACCEPT')} loading={respond.isPending && respond.variables === 'ACCEPT'}>
            Aceitar convite
          </Button>
          {localStatus !== 'DECLINED' && (
            <Button variant="outline" onClick={() => respond.mutate('DECLINE')} loading={respond.isPending && respond.variables === 'DECLINE'}>
              Recusar convite
            </Button>
          )}
        </div>
        {localStatus === 'CAN_REQUEST' && <p className={page.muted}>A organização receberá seu pedido e decidirá se aprova.</p>}
      </div>
    );
  }

  if (localStatus === 'PENDING_APPROVAL') {
    return <Notice tone="success" title="Pedido enviado">A organização recebeu seu pedido e ainda vai responder.</Notice>;
  }
  if (localStatus === 'REJECTED') {
    return <Notice tone="error" title="Pedido recusado">A organização recusou o pedido feito por este link.</Notice>;
  }
  return (
    <div className={styles.inviteActions}>
      <Notice tone="success" title="Você já aceitou este convite" />
      <LinkButton to={eventPath} block>Abrir o evento</LinkButton>
    </div>
  );
}
