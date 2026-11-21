import { ApiError } from '../../api/client';
import type {
  CreateEventRequest,
  EventModality,
  EventPrivacy,
  MemoraEvent,
  UpdateEventRequest,
} from '../../api/events';
import { messageForCode } from '../../api/problems';
import { todayISO } from '../../lib/format';

/** Valores do formulário como o usuário digita (texto); a conversão para a API fica aqui. */
export interface EventFormValues {
  name: string;
  description: string;
  categoryCode: string;
  startDate: string;
  startTime: string;
  endDate: string;
  endTime: string;
  modality: EventModality;
  city: string;
  address: string;
  privacy: EventPrivacy;
}

export type EventFormErrors = Partial<Record<keyof EventFormValues, string>>;

export const NAME_MAX = 120;
export const DESCRIPTION_MAX = 2000;
export const CITY_MAX = 120;
export const ADDRESS_MAX = 300;

export function emptyValues(): EventFormValues {
  return {
    name: '',
    description: '',
    categoryCode: '',
    startDate: '',
    startTime: '',
    endDate: '',
    endTime: '',
    modality: 'IN_PERSON',
    city: '',
    address: '',
    privacy: 'PRIVATE',
  };
}

export function valuesFrom(event: MemoraEvent): EventFormValues {
  return {
    name: event.name,
    description: event.description ?? '',
    categoryCode: event.category?.code ?? '',
    startDate: event.startDate,
    startTime: event.startTime ?? '',
    endDate: event.endDate ?? '',
    endTime: event.endTime ?? '',
    modality: event.modality,
    city: event.city ?? '',
    address: event.address ?? '',
    privacy: event.privacy,
  };
}

/**
 * Mesmas regras da API (README, seção 5.6). `original` existe na edição: a regra "início não pode
 * estar no passado" só vale quando a data de início muda.
 */
export function validate(v: EventFormValues, original?: MemoraEvent, today = todayISO()): EventFormErrors {
  const errors: EventFormErrors = {};
  const name = v.name.trim();
  if (!name) errors.name = 'Dê um nome ao evento.';
  else if (name.length > NAME_MAX) errors.name = `Use até ${NAME_MAX} caracteres.`;
  if (v.description.length > DESCRIPTION_MAX) errors.description = `Use até ${DESCRIPTION_MAX} caracteres.`;

  if (!v.startDate) errors.startDate = 'Informe a data de início.';
  else if (v.startDate < today && (!original || original.startDate !== v.startDate)) {
    errors.startDate = 'A data de início não pode estar no passado.';
  }
  if (v.endDate && v.startDate && v.endDate < v.startDate) {
    errors.endDate = 'O término não pode ser antes do início.';
  }
  const sameDay = !v.endDate || v.endDate === v.startDate;
  if (sameDay && v.startTime && v.endTime && v.endTime < v.startTime) {
    errors.endTime = 'O horário de término não pode ser antes do início.';
  }
  if (v.modality === 'IN_PERSON') {
    if (v.city.length > CITY_MAX) errors.city = `Use até ${CITY_MAX} caracteres.`;
    if (v.address.length > ADDRESS_MAX) errors.address = `Use até ${ADDRESS_MAX} caracteres.`;
  }
  return errors;
}

const opt = (s: string) => {
  const t = s.trim();
  return t.length ? t : undefined;
};

export function toCreateRequest(v: EventFormValues): CreateEventRequest {
  const inPerson = v.modality === 'IN_PERSON';
  return {
    name: v.name.trim(),
    description: opt(v.description),
    categoryCode: opt(v.categoryCode),
    startDate: v.startDate,
    startTime: opt(v.startTime),
    endDate: opt(v.endDate),
    endTime: opt(v.endTime),
    modality: v.modality,
    city: inPerson ? opt(v.city) : undefined,
    address: inPerson ? opt(v.address) : undefined,
    privacy: v.privacy,
  };
}

/**
 * Só o que mudou. Opcionais esvaziados vão como `null` (a API apaga); obrigatórios só quando mudam.
 * A privacidade só entra se `canChangePrivacy` (exclusivo do organizador).
 */
export function toUpdateRequest(v: EventFormValues, original: MemoraEvent, canChangePrivacy: boolean): UpdateEventRequest {
  const before = valuesFrom(original);
  const out: UpdateEventRequest = {};
  const name = v.name.trim();
  if (name !== before.name) out.name = name;
  if (v.startDate !== before.startDate) out.startDate = v.startDate;
  if (v.modality !== before.modality) out.modality = v.modality;
  if (canChangePrivacy && v.privacy !== before.privacy) out.privacy = v.privacy;

  const optional = ['description', 'categoryCode', 'startTime', 'endDate', 'endTime', 'city', 'address'] as const;
  for (const key of optional) {
    if ((key === 'city' || key === 'address') && v.modality !== 'IN_PERSON') continue;
    const now = v[key].trim();
    if (now !== before[key].trim()) out[key] = now.length ? now : null;
  }
  return out;
}

/** Leva o erro da API para o campo certo; o que não for de campo volta em `general`. */
export function errorsFromApi(error: unknown): { fields: EventFormErrors; general: string | null } {
  if (!(error instanceof ApiError)) return { fields: {}, general: messageForCode('UNKNOWN_ERROR') };
  const byCode: Record<string, keyof EventFormValues> = {
    INVALID_EVENT_NAME: 'name',
    INVALID_EVENT_DESCRIPTION: 'description',
    INVALID_EVENT_CATEGORY: 'categoryCode',
    EVENT_START_REQUIRED: 'startDate',
    EVENT_START_IN_PAST: 'startDate',
    EVENT_END_BEFORE_START: 'endDate',
    EVENT_MODALITY_REQUIRED: 'modality',
    INVALID_EVENT_LOCATION: 'city',
  };
  const field = byCode[error.code];
  if (field) return { fields: { [field]: messageForCode(error.code, error.problem?.detail) }, general: null };
  if (error.code === 'VALIDATION_FAILED') {
    const fields: EventFormErrors = {};
    for (const [name, message] of Object.entries(error.fieldErrors())) {
      if (name in emptyValues()) fields[name as keyof EventFormValues] = message;
    }
    if (Object.keys(fields).length) return { fields, general: null };
  }
  return { fields: {}, general: messageForCode(error.code, error.problem?.detail) };
}
