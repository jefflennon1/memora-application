import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { isApiError } from '../../api/client';
import { keys } from '../../api/keys';
import { memoriesApi, type Memory, type MuralOrder } from '../../api/memories';
import { friendlyMessage } from '../../api/problems';
import { withNext } from '../../auth/redirect';
import { useMe } from '../../auth/session';
import { EmptyState, LoadMore } from '../../components/Bits';
import { Button, LinkButton } from '../../components/Button';
import { Notice } from '../../components/Notice';
import page from '../../components/Page.module.css';
import { usePaged } from '../../lib/usePaged';
import { watchMemoryCreated } from '../../lib/eventStream';
import { useEvent, useEventContext } from '../events/EventLayout';
import { Composer } from './Composer';
import { MemoryCard } from './MemoryCard';
import styles from './Memories.module.css';

/** /events/:id: o mural do evento. */
export function MuralTab() {
  const { event } = useEventContext();
  const { data: me } = useMe();
  const location = useLocation();
  const [order, setOrder] = useState<MuralOrder>('NEWEST');
  const [justPublished, setJustPublished] = useState<Memory[]>([]);
  const [newMemoryIds, setNewMemoryIds] = useState<Set<string>>(() => new Set());
  const [refreshing, setRefreshing] = useState(false);
  const mural = usePaged(keys.mural(event.id, order), (p, signal) =>
    memoriesApi.mural(event.id, { order, page: p, size: 20 }, signal),
  );

  const participant = !!event.myRole;
  const open = event.muralStatus === 'OPEN';
  const fresh = new Set(justPublished.map((m) => m.id));
  const items = mural.items.filter((m) => !fresh.has(m.id));

  useEffect(() => {
    if (!participant) return;
    return watchMemoryCreated(event.id, (notice) => {
      setNewMemoryIds((current) => {
        if (current.has(notice.memoryId)) return current;
        const next = new Set(current);
        next.add(notice.memoryId);
        return next;
      });
    });
  }, [event.id, participant]);

  const loadNewMemories = async () => {
    setRefreshing(true);
    const result = await mural.refetch();
    if (!result.isError) setNewMemoryIds(new Set());
    setRefreshing(false);
  };

  return (
    <div className={styles.mural}>
      {participant && open && (
        <Composer
          eventId={event.id}
          eventIsPublic={event.privacy === 'PUBLIC'}
          onDone={(memory) => setJustPublished((list) => [memory, ...list])}
        />
      )}
      {participant && !open && (
        <Notice title="Mural fechado">
          A organização fechou o mural: as memórias continuam aqui, mas não dá para publicar nem editar.
        </Notice>
      )}
      {!participant && (
        <Notice title={me ? 'Você vê só as memórias públicas' : 'Você está vendo como visitante'}>
          {me ? (
            'Para ver tudo e publicar, peça um convite para quem organiza o evento.'
          ) : (
            <>
              Aqui aparecem só as memórias públicas. Se você participa,{' '}
              <Link to={withNext('/login', location.pathname)}>entre na sua conta</Link>.
            </>
          )}
        </Notice>
      )}

      {justPublished.length > 0 && (
        <section className={styles.list} aria-label="Publicadas agora">
          {justPublished.map((memory) => (
            <div key={memory.id} className={styles.fresh}>
              <span className={styles.freshLabel}>Publicada agora</span>
              <MemoryCard
                memory={memory}
                event={event}
                onDeleted={() => setJustPublished((list) => list.filter((m) => m.id !== memory.id))}
                onUpdated={(updated) => setJustPublished((list) => list.map((m) => (m.id === updated.id ? updated : m)))}
              />
            </div>
          ))}
        </section>
      )}

      <div className={styles.muralBar}>
        <h2 className={styles.muralTitle}>Memórias</h2>
        <div className={styles.segmented} role="group" aria-label="Ordem do mural">
          <button type="button" aria-pressed={order === 'OLDEST'} onClick={() => setOrder('OLDEST')}>
            Mais antigas
          </button>
          <button type="button" aria-pressed={order === 'NEWEST'} onClick={() => setOrder('NEWEST')}>
            Mais recentes
          </button>
        </div>
      </div>

      {newMemoryIds.size > 0 && (
        <Notice title={newMemoryIds.size === 1 ? 'Há uma nova memória' : `Há ${newMemoryIds.size} novas memórias`}>
          <div className={styles.newMemoriesAction}>
            <span>Veja quando quiser, sem perder sua posição no mural.</span>
            <Button variant="outline" loading={refreshing} onClick={() => void loadNewMemories()}>
              Atualizar mural
            </Button>
          </div>
        </Notice>
      )}

      {mural.isError && (
        <Notice tone="error" title="Não deu para carregar o mural">
          {friendlyMessage(mural.error)}
        </Notice>
      )}
      {mural.isPending && <p className={page.muted}>Carregando memórias…</p>}
      {mural.isSuccess && items.length === 0 && justPublished.length === 0 && (
        <EmptyState title={participant ? 'O mural está esperando a primeira memória' : 'Nenhuma memória pública ainda'}>
          {participant && open
            ? 'Escreva como foi, ou adicione as fotos daquele dia. Quem participa vê tudo por aqui.'
            : participant
              ? 'O mural foi fechado antes de alguém publicar.'
              : 'Quando alguém publicar uma memória pública, ela aparece aqui.'}
        </EmptyState>
      )}

      <div className={styles.list}>
        {items.map((memory) => (
          <MemoryCard key={memory.id} memory={memory} event={event} />
        ))}
      </div>
      <LoadMore hasNext={!!mural.hasNextPage} loading={mural.isFetchingNextPage} onClick={() => mural.fetchNextPage()} />
    </div>
  );
}

/** /memories/:memoryId: uma memória sozinha, com link para o evento. */
export function MemoryPage() {
  const { memoryId = '' } = useParams();
  const location = useLocation();
  const { data: me } = useMe();
  const memory = useQuery({
    queryKey: keys.memory(memoryId),
    queryFn: ({ signal }) => memoriesApi.get(memoryId, signal),
    retry: false,
  });
  const event = useEvent(memory.data?.eventId ?? '');
  const [deleted, setDeleted] = useState(false);

  if (deleted) {
    return (
      <div className={page.page}>
        <Notice tone="success" title="Memória excluída" />
        {memory.data && <LinkButton to={`/events/${memory.data.eventId}`}>Voltar ao evento</LinkButton>}
      </div>
    );
  }
  if (memory.isPending) return <p className={page.muted}>Abrindo a memória…</p>;
  if (memory.isError) {
    const notFound = isApiError(memory.error, 'MEMORY_NOT_FOUND');
    return (
      <div className={page.page}>
        <EmptyState
          title={notFound ? 'Memória não encontrada' : 'Não deu para abrir a memória'}
          action={
            !me && notFound ? (
              <LinkButton to={withNext('/login', location.pathname)}>Entrar</LinkButton>
            ) : (
              <Button variant="outline" onClick={() => memory.refetch()}>
                Tentar de novo
              </Button>
            )
          }
        >
          {notFound
            ? me
              ? 'Ela pode ter sido excluída, ou é só para participantes de um evento do qual você não faz parte.'
              : 'Ela pode ser só para participantes. Se você participa do evento, entre para ver.'
            : friendlyMessage(memory.error)}
        </EmptyState>
      </div>
    );
  }

  return (
    <div className={page.page}>
      <Link to={`/events/${memory.data.eventId}`} className={page.back}>
        {event.data ? `Voltar para ${event.data.name}` : 'Voltar ao evento'}
      </Link>
      <MemoryCard memory={memory.data} event={event.data} onDeleted={() => setDeleted(true)} commentsOpen />
    </div>
  );
}
