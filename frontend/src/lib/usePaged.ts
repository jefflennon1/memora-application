import { useInfiniteQuery, type QueryKey } from '@tanstack/react-query';
import type { Page } from '../api/common';

/** Lista paginada com "Carregar mais": junta as páginas e expõe os itens já carregados. */
export function usePaged<T>(
  queryKey: QueryKey,
  fetchPage: (page: number, signal: AbortSignal) => Promise<Page<T>>,
  options: { enabled?: boolean } = {},
) {
  const query = useInfiniteQuery({
    queryKey,
    queryFn: ({ pageParam, signal }) => fetchPage(pageParam, signal),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.hasNext ? last.page + 1 : undefined),
    enabled: options.enabled,
  });
  const items = query.data?.pages.flatMap((p) => p.items) ?? [];
  const total = query.data?.pages[0]?.totalItems ?? 0;
  return { ...query, items, total };
}
