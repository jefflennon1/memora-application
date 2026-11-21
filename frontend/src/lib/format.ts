import type { Person } from '../api/common';
import type { EventModality, EventPrivacy, ParticipantRole } from '../api/events';

/*
 * Datas do evento não têm fuso (são o dia e a hora do encontro): "AAAA-MM-DD" e "HH:MM".
 * Por isso são lidas como data local, nunca convertidas de UTC.
 */

function localDate(date: string): Date {
  const [y, m, d] = date.split('-').map(Number);
  return new Date(y!, (m ?? 1) - 1, d ?? 1);
}

const fullDate = new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
const dayMonth = new Intl.DateTimeFormat('pt-BR', { day: 'numeric', month: 'long' });
const dayMonthYear = new Intl.DateTimeFormat('pt-BR', { day: 'numeric', month: 'long', year: 'numeric' });
const monthShort = new Intl.DateTimeFormat('pt-BR', { month: 'short' });
const weekdayShort = new Intl.DateTimeFormat('pt-BR', { weekday: 'short' });
const dateTime = new Intl.DateTimeFormat('pt-BR', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
const shortDate = new Intl.DateTimeFormat('pt-BR', { day: 'numeric', month: 'short', year: 'numeric' });

const capitalize = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);
const clean = (s: string) => s.replace(/\.$/, '');

export interface EventWhen {
  startDate: string;
  startTime?: string | null;
  endDate?: string | null;
  endTime?: string | null;
}

/** "Sábado, 5 de dezembro de 2026, das 15:00 às 18:00" ou "De 5 a 7 de dezembro de 2026". */
export function formatEventWhen(e: EventWhen): string {
  const start = localDate(e.startDate);
  const multiDay = e.endDate && e.endDate !== e.startDate;

  if (multiDay) {
    const end = localDate(e.endDate!);
    const sameYear = start.getFullYear() === end.getFullYear();
    const from = sameYear ? dayMonth.format(start) : dayMonthYear.format(start);
    const fromTime = e.startTime ? `, ${e.startTime}` : '';
    const toTime = e.endTime ? `, ${e.endTime}` : '';
    return `De ${from}${fromTime} a ${dayMonthYear.format(end)}${toTime}`;
  }

  const day = capitalize(fullDate.format(start));
  if (e.startTime && e.endTime) return `${day}, das ${e.startTime} às ${e.endTime}`;
  if (e.startTime) return `${day}, às ${e.startTime}`;
  if (e.endTime) return `${day}, até ${e.endTime}`;
  return day;
}

/** Partes para o "bloco de calendário" dos cartões de evento. */
export function dateBlock(date: string): { day: string; month: string; weekday: string; year: number } {
  const d = localDate(date);
  return {
    day: String(d.getDate()),
    month: clean(monthShort.format(d)),
    weekday: clean(weekdayShort.format(d)),
    year: d.getFullYear(),
  };
}

/** Data de hoje no fuso de quem usa, em "AAAA-MM-DD" (mínimo do campo de início). */
export function todayISO(now = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

const relative = new Intl.RelativeTimeFormat('pt-BR', { numeric: 'auto' });

/** "agora", "há 5 minutos", "há 3 horas", "ontem"; depois de uma semana, a data. */
export function timeAgo(iso: string, now = Date.now()): string {
  const seconds = Math.round((new Date(iso).getTime() - now) / 1000);
  const abs = Math.abs(seconds);
  if (abs < 45) return 'agora';
  if (abs < 3600) return relative.format(Math.round(seconds / 60), 'minute');
  if (abs < 86_400) return relative.format(Math.round(seconds / 3600), 'hour');
  if (abs < 7 * 86_400) return relative.format(Math.round(seconds / 86_400), 'day');
  return shortDate.format(new Date(iso));
}

export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso));
}

export function formatShortDate(iso: string): string {
  return shortDate.format(new Date(iso));
}

export const MODALITY_LABEL: Record<EventModality, string> = {
  IN_PERSON: 'Presencial',
  ONLINE: 'Online',
  NO_LOCATION: 'Sem local',
};

export const EVENT_PRIVACY_LABEL: Record<EventPrivacy, string> = {
  PUBLIC: 'Público',
  PRIVATE: 'Privado',
};

export const ROLE_LABEL: Record<ParticipantRole, string> = {
  ORGANIZER: 'Organização',
  CO_ORGANIZER: 'Coorganização',
  PARTICIPANT: 'Participante',
};

/** Nome para exibir. Perfis ocultos (reservados ou contas excluídas) não revelam nada. */
export function personName(person: Person | null | undefined): string {
  if (!person || person.profileHidden) return 'Perfil reservado';
  return person.displayName || (person.username ? `@${person.username}` : 'Alguém');
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`;
}
