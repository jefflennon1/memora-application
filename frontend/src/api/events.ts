import { api } from './client';
import type { Page, PageParams, Person } from './common';

export type EventModality = 'IN_PERSON' | 'ONLINE' | 'NO_LOCATION';
export type EventPrivacy = 'PUBLIC' | 'PRIVATE';
export type MuralStatus = 'OPEN' | 'CLOSED';
export type ParticipantRole = 'ORGANIZER' | 'CO_ORGANIZER' | 'PARTICIPANT';

export interface EventCategory {
  code: string;
  name: string;
}

/** GET /events/{id} e listas. Campos vazios são omitidos pela API. */
export interface MemoraEvent {
  id: string;
  name: string;
  description?: string | null;
  coverUrl?: string | null;
  category?: EventCategory | null;
  startDate: string; // AAAA-MM-DD
  startTime?: string | null; // HH:MM
  endDate?: string | null;
  endTime?: string | null;
  modality: EventModality;
  city?: string | null;
  address?: string | null;
  privacy: EventPrivacy;
  muralStatus: MuralStatus;
  muralStatusChangedAt?: string | null;
  organizer: Person;
  /** Ausente quando você não participa. */
  myRole?: ParticipantRole | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateEventRequest {
  name: string;
  description?: string;
  categoryCode?: string;
  startDate: string;
  startTime?: string;
  endDate?: string;
  endTime?: string;
  modality: EventModality;
  city?: string;
  address?: string;
  privacy?: EventPrivacy;
}

/**
 * Edição parcial: envie só o que mudar. Nos campos opcionais, `null` apaga o valor;
 * nos obrigatórios (name, startDate, modality, privacy), `null` é ignorado.
 */
export type UpdateEventRequest = Partial<{
  name: string;
  description: string | null;
  categoryCode: string | null;
  startDate: string;
  startTime: string | null;
  endDate: string | null;
  endTime: string | null;
  modality: EventModality;
  city: string | null;
  address: string | null;
  privacy: EventPrivacy;
}>;

export interface PublicSearch extends PageParams {
  q?: string;
  city?: string;
  category?: string;
}

export const eventsApi = {
  categories: (signal?: AbortSignal) => api<EventCategory[]>('/event-categories', { signal }),

  create: (body: CreateEventRequest) => api<MemoraEvent>('/events', { method: 'POST', body }),

  get: (id: string, signal?: AbortSignal) => api<MemoraEvent>(`/events/${id}`, { signal }),

  mine: (params: PageParams, signal?: AbortSignal) =>
    api<Page<MemoraEvent>>('/events/mine', { query: { ...params }, signal }),

  searchPublic: (params: PublicSearch, signal?: AbortSignal) =>
    api<Page<MemoraEvent>>('/events/public', { query: { ...params }, signal }),

  update: (id: string, body: UpdateEventRequest) => api<MemoraEvent>(`/events/${id}`, { method: 'PATCH', body }),

  setMural: (id: string, status: MuralStatus) =>
    api<MemoraEvent>(`/events/${id}/mural`, { method: 'PATCH', body: { status } }),

  uploadCover: (id: string, blob: Blob, filename: string) => {
    const form = new FormData();
    form.append('file', blob, filename);
    return api<MemoraEvent>(`/events/${id}/cover`, { method: 'PUT', form, timeoutMs: 60_000 });
  },

  removeCover: (id: string) => api<MemoraEvent>(`/events/${id}/cover`, { method: 'DELETE' }),
};

/** Organizador ou coorganizador. */
export function isOrganization(event: Pick<MemoraEvent, 'myRole'>): boolean {
  return event.myRole === 'ORGANIZER' || event.myRole === 'CO_ORGANIZER';
}

export function isParticipant(event: Pick<MemoraEvent, 'myRole'>): boolean {
  return !!event.myRole;
}
