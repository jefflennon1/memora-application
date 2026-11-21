/** Formatos compartilhados pela API. */

/** Página de uma lista (README da API, seção 4, Paginação). */
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  hasNext: boolean;
}

/**
 * Pessoa como aparece em eventos, convites e memórias. Com `profileHidden: true`
 * (perfil reservado sem evento em comum, ou conta excluída), só o `id` vem preenchido.
 */
export interface Person {
  id: string;
  username?: string | null;
  displayName?: string | null;
  photoUrl?: string | null;
  profileHidden: boolean;
}

export interface PageParams {
  page?: number;
  size?: number;
}
