import { api } from './client';
import type { Page, PageParams, Person } from './common';
import type { EventModality, EventPrivacy, ParticipantRole } from './events';

export type InviteType = 'NAMED' | 'LINK' | 'QR_CODE';
export type InviteState = 'ACTIVE' | 'EXPIRED' | 'REVOKED' | 'USED';
export type MyInviteStatus = 'CAN_JOIN' | 'CAN_REQUEST' | 'PENDING_APPROVAL' | 'REJECTED' | 'DECLINED' | 'PARTICIPANT';
export type JoinOutcome = 'JOINED' | 'ALREADY_PARTICIPANT' | 'PENDING_APPROVAL' | 'REJECTED' | 'DECLINED';
export type JoinRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type AttendanceStatus = 'ATTENDING' | 'NOT_ATTENDING';
export type InviteBackgroundLayout = 'COVER' | 'REPEAT';

/** Conteúdo e aparência da página pública de um convite. */
export interface InvitePageContent {
  title: string;
  body: string;
  footer?: string | null;
  location?: string | null;
  backgroundColor: string;
  /** Ausente para cor sólida; presente para um gradiente simples de duas cores. */
  backgroundGradientColor?: string | null;
  backgroundImageUrl?: string | null;
  backgroundImageLayout: InviteBackgroundLayout;
}

export type InvitePageInput = Omit<InvitePageContent, 'backgroundImageUrl'>;

export interface Invite {
  id: string;
  type: InviteType;
  token: string;
  /** Link pronto para compartilhar: {FRONTEND_ORIGIN}/invites/{token} */
  url: string;
  state: InviteState;
  invitee?: Person | null;
  createdBy?: Person | null;
  createdAt: string;
  expiresAt: string;
  revokedAt?: string | null;
  usedAt?: string | null;
  page?: InvitePageContent | null;
}

export interface CreateInviteRequest {
  type: InviteType;
  /** Só para NAMED. */
  username?: string;
  /** 1 a 90 (padrão 30). */
  expiresInDays?: number;
  page: InvitePageInput;
}

/** O mínimo sobre o evento, sem descrição nem endereço. */
export interface InviteEventPreview {
  id: string;
  name: string;
  categoryCode?: string | null;
  categoryName?: string | null;
  startDate: string;
  startTime?: string | null;
  endDate?: string | null;
  modality: EventModality;
  city?: string | null;
  privacy: EventPrivacy;
  organizer: Person;
}

export interface InvitePreview {
  token: string;
  type: InviteType;
  expiresAt: string;
  event: InviteEventPreview;
  myStatus: MyInviteStatus;
  page?: InvitePageContent | null;
}

export interface JoinResult {
  status: JoinOutcome;
  eventId: string;
  requestId?: string | null;
}

export interface JoinRequest {
  id: string;
  person: Person;
  status: JoinRequestStatus;
  requestedAt: string;
  decidedAt?: string | null;
  attendanceStatus?: AttendanceStatus | null;
  attendanceUpdatedAt?: string | null;
}

export interface Participant {
  person: Person;
  role: ParticipantRole;
  joinedAt: string;
  attendanceStatus?: AttendanceStatus | null;
  attendanceUpdatedAt?: string | null;
}

export interface AttendanceSummary {
  attending: number;
  notAttending: number;
  unanswered: number;
  total: number;
  myStatus?: AttendanceStatus | null;
  myUpdatedAt?: string | null;
}

export const invitesApi = {
  create: (eventId: string, body: CreateInviteRequest) =>
    api<Invite>(`/events/${eventId}/invites`, { method: 'POST', body }),

  list: (eventId: string, params: PageParams, signal?: AbortSignal) =>
    api<Page<Invite>>(`/events/${eventId}/invites`, { query: { ...params }, signal }),

  revoke: (eventId: string, inviteId: string) =>
    api<void>(`/events/${eventId}/invites/${inviteId}`, { method: 'DELETE' }),

  updatePage: (eventId: string, inviteId: string, page: InvitePageInput) =>
    api<Invite>(`/events/${eventId}/invites/${inviteId}`, { method: 'PATCH', body: { page } }),

  uploadBackground: (eventId: string, inviteId: string, file: Blob, filename: string) => {
    const form = new FormData();
    form.append('file', file, filename);
    return api<Invite>(`/events/${eventId}/invites/${inviteId}/background`, {
      method: 'PUT',
      form,
      timeoutMs: 60_000,
    });
  },

  removeBackground: (eventId: string, inviteId: string) =>
    api<Invite>(`/events/${eventId}/invites/${inviteId}/background`, { method: 'DELETE' }),

  /** Convites nominais válidos para você, de eventos dos quais ainda não participa. */
  received: (signal?: AbortSignal) => api<InvitePreview[]>('/invites/received', { signal }),

  preview: (token: string, signal?: AbortSignal) =>
    api<InvitePreview>(`/invites/${encodeURIComponent(token)}`, { signal }),

  join: (token: string, attendanceStatus?: AttendanceStatus) =>
    api<JoinResult>(`/invites/${encodeURIComponent(token)}/join`, {
      method: 'POST',
      body: attendanceStatus ? { attendanceStatus } : undefined,
    }),

  decline: (token: string) =>
    api<JoinResult>(`/invites/${encodeURIComponent(token)}/decline`, { method: 'POST' }),
};

export const joinRequestsApi = {
  list: (eventId: string, params: PageParams & { status?: JoinRequestStatus }, signal?: AbortSignal) =>
    api<Page<JoinRequest>>(`/events/${eventId}/join-requests`, { query: { ...params }, signal }),

  approve: (eventId: string, requestId: string) =>
    api<JoinRequest>(`/events/${eventId}/join-requests/${requestId}/approve`, { method: 'POST' }),

  reject: (eventId: string, requestId: string) =>
    api<JoinRequest>(`/events/${eventId}/join-requests/${requestId}/reject`, { method: 'POST' }),
};

export const participantsApi = {
  list: (eventId: string, params: PageParams, signal?: AbortSignal) =>
    api<Page<Participant>>(`/events/${eventId}/participants`, { query: { ...params }, signal }),

  changeRole: (eventId: string, userId: string, role: Exclude<ParticipantRole, 'ORGANIZER'>) =>
    api<Participant>(`/events/${eventId}/participants/${userId}`, { method: 'PATCH', body: { role } }),

  attendanceSummary: (eventId: string, signal?: AbortSignal) =>
    api<AttendanceSummary>(`/events/${eventId}/participants/attendance-summary`, { signal }),

  updateAttendance: (eventId: string, attendanceStatus: AttendanceStatus) =>
    api<Participant>(`/events/${eventId}/participants/me/attendance`, {
      method: 'PATCH',
      body: { attendanceStatus },
    }),

  /** Com o próprio id, a pessoa sai do evento. */
  remove: (eventId: string, userId: string) =>
    api<void>(`/events/${eventId}/participants/${userId}`, { method: 'DELETE' }),
};
