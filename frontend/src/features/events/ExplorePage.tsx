import { useInfiniteQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router';
import { eventsApi } from '../../api/events';
import { keys } from '../../api/keys';
import { friendlyMessage } from '../../api/problems';
import { EmptyState, LoadMore } from '../../components/Bits';
import { Button } from '../../components/Button';
import { SelectField } from '../../components/Fields';
import { Notice } from '../../components/Notice';
import page from '../../components/Page.module.css';
import { TextField } from '../../components/TextField';
import { EventCard } from './EventCard';
import { useCategories } from './EventForm';
import styles from './ExplorePage.module.css';

/**
 * /explore: busca de eventos públicos (não exige login). Os filtros ficam no endereço,
 * então a busca pode ser compartilhada e o "voltar" do navegador funciona.
 */
export function ExplorePage() {
  const [params, setParams] = useSearchParams();
  const search = {
    q: params.get('q') ?? '',
    city: params.get('city') ?? '',
    category: params.get('category') ?? '',
  };
  const [draft, setDraft] = useState(search);
  const categories = useCategories();

  const results = useInfiniteQuery({
    queryKey: keys.publicEvents(search),
    queryFn: ({ pageParam, signal }) =>
      eventsApi.searchPublic({ ...search, page: pageParam, size: 20 }, signal),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.hasNext ? last.page + 1 : undefined),
  });
  const items = results.data?.pages.flatMap((p) => p.items) ?? [];
  const total = results.data?.pages[0]?.totalItems ?? 0;
  const filtered = !!(search.q || search.city || search.category);

  function submit(event: FormEvent) {
    event.preventDefault();
    const next = new URLSearchParams();
    for (const [key, value] of Object.entries(draft)) if (value.trim()) next.set(key, value.trim());
    setParams(next);
  }

  function clear() {
    setDraft({ q: '', city: '', category: '' });
    setParams(new URLSearchParams());
  }

  return (
    <div className={page.page}>
      <header className={page.header}>
        <h1 className={page.title}>Explorar eventos</h1>
        <p className={page.lede}>Eventos públicos, abertos para quem quiser ver. Os privados nunca aparecem aqui.</p>
      </header>

      <form className={styles.filters} onSubmit={submit} role="search" aria-label="Buscar eventos públicos">
        <TextField
          label="Nome do evento"
          value={draft.q}
          onChange={(e) => setDraft({ ...draft, q: e.target.value })}
          placeholder="Ex.: chá de fraldas"
          type="search"
        />
        <TextField
          label="Cidade"
          value={draft.city}
          onChange={(e) => setDraft({ ...draft, city: e.target.value })}
          placeholder="Ex.: Belo Horizonte"
        />
        <SelectField
          label="Categoria"
          value={draft.category}
          onChange={(e) => setDraft({ ...draft, category: e.target.value })}
        >
          <option value="">Todas</option>
          {categories.data?.map((c) => (
            <option key={c.code} value={c.code}>
              {c.name}
            </option>
          ))}
        </SelectField>
        <div className={styles.filterActions}>
          <Button type="submit">Buscar</Button>
          {filtered && (
            <Button variant="quiet" onClick={clear}>
              Limpar filtros
            </Button>
          )}
        </div>
      </form>

      {results.isError && <Notice tone="error" title="Não deu para buscar">{friendlyMessage(results.error)}</Notice>}
      {results.isPending && <p className={page.muted}>Buscando eventos…</p>}

      {results.isSuccess && (
        <section className={page.section} aria-label="Resultados">
          <p className={page.muted} aria-live="polite">
            {total === 0
              ? filtered
                ? 'Nenhum evento público com esses filtros.'
                : 'Ainda não há eventos públicos.'
              : total === 1
                ? '1 evento encontrado'
                : `${total} eventos encontrados`}
          </p>
          {total === 0 && filtered && (
            <EmptyState title="Tente outra busca">
              A busca ignora acentos e maiúsculas: "cha de fraldas" encontra "Chá de Fraldas". Tente um trecho menor do
              nome ou tire a cidade.
            </EmptyState>
          )}
          <ul className={page.list}>
            {items.map((event) => (
              <li key={event.id}>
                <EventCard event={event} />
              </li>
            ))}
          </ul>
          <LoadMore
            hasNext={!!results.hasNextPage}
            loading={results.isFetchingNextPage}
            onClick={() => results.fetchNextPage()}
          />
        </section>
      )}
    </div>
  );
}
