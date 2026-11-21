import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { keys } from '../../api/keys';
import { invitesApi } from '../../api/membership';
import { useCurrentUser } from '../../auth/useCurrentUser';
import { EmptyState, SectionHeader } from '../../components/Bits';
import { LinkButton } from '../../components/Button';
import page from '../../components/Page.module.css';
import { formatEventWhen, personName } from '../../lib/format';
import { DateBlock, EventCard } from '../events/EventCard';
import { useMyEvents } from '../events/EventsPage';
import styles from './HomePage.module.css';

export function HomePage() {
  const me = useCurrentUser();
  const firstName = me.displayName.trim().split(/\s+/)[0] ?? me.displayName;

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>Olá, {firstName}</h1>
        <p className={styles.lede}>
          Os eventos que você organiza e aqueles de que participa, cada um com o seu mural de memórias.
        </p>
      </header>

      <ReceivedInvites />
      <UpcomingEvents />
    </div>
  );
}

/** Convites nominais para você, de eventos dos quais ainda não participa. */
function ReceivedInvites() {
  const invites = useQuery({ queryKey: keys.receivedInvites, queryFn: ({ signal }) => invitesApi.received(signal) });
  if (!invites.data?.length) return null;
  return (
    <section className={page.section} aria-labelledby="received">
      <SectionHeader id="received" title="Convites para você" />
      <ul className={page.list}>
        {invites.data.map((invite) => (
          <li key={invite.token}>
            <Link to={`/invites/${invite.token}`} className={styles.invite}>
              <DateBlock date={invite.event.startDate} />
              <span className={styles.inviteText}>
                <span className={styles.inviteName}>{invite.event.name}</span>
                <span className={styles.inviteMeta}>{formatEventWhen(invite.event)}</span>
                <span className={styles.inviteMeta}>Convite de {personName(invite.event.organizer)}</span>
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}

/** Os primeiros eventos de "Seus eventos", com atalho para criar um. */
function UpcomingEvents() {
  const events = useMyEvents();
  const items = events.data?.pages[0]?.items.slice(0, 3) ?? [];
  const total = events.data?.pages[0]?.totalItems ?? 0;
  return (
    <section className={page.section} aria-labelledby="my-events">
      <SectionHeader
        id="my-events"
        title="Seus eventos"
        action={<LinkButton to="/events/new" variant="outline">Criar evento</LinkButton>}
      />
      {events.isSuccess && items.length === 0 && (
        <EmptyState title="Comece pelo primeiro evento">
          Crie um evento para o próximo encontro da família, ou abra o link de um convite que você recebeu.
        </EmptyState>
      )}
      {items.length > 0 && (
        <ul className={page.list}>
          {items.map((event) => (
            <li key={event.id}>
              <EventCard event={event} />
            </li>
          ))}
        </ul>
      )}
      {total > items.length && (
        <Link to="/events" className={page.back}>
          Ver todos os {total} eventos
        </Link>
      )}
    </section>
  );
}
