import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router';
import { eventsApi } from '../../api/events';
import { keys } from '../../api/keys';
import { friendlyMessage } from '../../api/problems';
import { EmptyState, LoadMore } from '../../components/Bits';
import { LinkButton } from '../../components/Button';
import { Notice } from '../../components/Notice';
import page from '../../components/Page.module.css';
import { EventCard } from './EventCard';
import { EventForm } from './EventForm';
import { errorsFromApi, toCreateRequest } from './eventFormModel';

export function useMyEvents() {
  return useInfiniteQuery({
    queryKey: keys.myEvents,
    queryFn: ({ pageParam, signal }) => eventsApi.mine({ page: pageParam, size: 20 }, signal),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.hasNext ? last.page + 1 : undefined),
  });
}

/** /events: eventos dos quais você participa, com qualquer papel. */
export function EventsPage() {
  const events = useMyEvents();
  const items = events.data?.pages.flatMap((p) => p.items) ?? [];

  return (
    <div className={page.page}>
      <header className={page.headerRow}>
        <div className={page.header}>
          <h1 className={page.title}>Seus eventos</h1>
          <p className={page.lede}>Os eventos que você organiza e aqueles de que participa.</p>
        </div>
        <LinkButton to="/events/new">Criar evento</LinkButton>
      </header>

      {events.isError && <Notice tone="error" title="Não deu para carregar seus eventos">{friendlyMessage(events.error)}</Notice>}
      {events.isPending && <p className={page.muted}>Carregando eventos…</p>}

      {events.isSuccess && items.length === 0 && (
        <EmptyState
          title="Nenhum evento por aqui ainda"
          action={
            <LinkButton to="/events/new">Criar o primeiro evento</LinkButton>
          }
        >
          Crie um evento para guardar as memórias de um encontro, ou entre por um convite que alguém te mandou. Também
          dá para <Link to="/explore">explorar eventos públicos</Link>.
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
      <LoadMore hasNext={!!events.hasNextPage} loading={events.isFetchingNextPage} onClick={() => events.fetchNextPage()} />
    </div>
  );
}

/** /events/new */
export function NewEventPage() {
  const client = useQueryClient();
  const navigate = useNavigate();
  const create = useMutation({
    mutationFn: eventsApi.create,
    onSuccess: (event) => {
      client.setQueryData(keys.event(event.id), event);
      void client.invalidateQueries({ queryKey: keys.myEvents });
      navigate(`/events/${event.id}`, { replace: true });
    },
  });
  const apiErrors = create.error ? errorsFromApi(create.error) : null;

  return (
    <div className={page.page}>
      <header className={page.header}>
        <Link to="/events" className={page.back}>
          Voltar aos seus eventos
        </Link>
        <h1 className={page.title}>Criar evento</h1>
        <p className={page.lede}>
          Você vira a organização do evento: convida pessoas, aprova quem pedir para entrar e decide quando o mural
          abre e fecha.
        </p>
      </header>
      <EventForm
        submitLabel="Criar evento"
        pending={create.isPending}
        serverErrors={apiErrors?.fields}
        generalError={apiErrors?.general}
        onSubmit={(values) => create.mutate(toCreateRequest(values))}
      />
    </div>
  );
}
