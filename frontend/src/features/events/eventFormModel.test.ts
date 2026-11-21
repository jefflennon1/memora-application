import { describe, expect, it } from 'vitest';
import type { MemoraEvent } from '../../api/events';
import { emptyValues, toCreateRequest, toUpdateRequest, validate, valuesFrom } from './eventFormModel';

const event: MemoraEvent = {
  id: 'e1',
  name: 'Chá de Fraldas do Liam',
  description: 'Uma tarde para celebrar.',
  category: { code: 'BABY_SHOWER', name: 'Chá de bebê / Chá de fraldas' },
  startDate: '2026-12-05',
  startTime: '15:00',
  endTime: '18:00',
  modality: 'IN_PERSON',
  city: 'Belo Horizonte',
  address: 'Rua das Flores, 10',
  privacy: 'PRIVATE',
  muralStatus: 'OPEN',
  organizer: { id: 'u1', profileHidden: false, displayName: 'Ana' },
  myRole: 'ORGANIZER',
  createdAt: '2026-11-09T18:00:00Z',
  updatedAt: '2026-11-09T18:00:00Z',
};

describe('event form rules (mirror the API)', () => {
  it('requires name and start date, and refuses a start in the past', () => {
    expect(validate(emptyValues(), undefined, '2026-11-09')).toMatchObject({
      name: expect.any(String),
      startDate: expect.any(String),
    });
    const past = { ...emptyValues(), name: 'X', startDate: '2026-11-08' };
    expect(validate(past, undefined, '2026-11-09').startDate).toMatch(/passado/);
  });

  it('accepts a past start date when editing without changing it', () => {
    const old = { ...event, startDate: '2026-01-10' };
    expect(validate(valuesFrom(old), old, '2026-11-09').startDate).toBeUndefined();
  });

  it('refuses an end before the start (date, or time on the same day)', () => {
    const base = { ...emptyValues(), name: 'X', startDate: '2026-12-05' };
    expect(validate({ ...base, endDate: '2026-12-04' }, undefined, '2026-11-09').endDate).toBeDefined();
    expect(validate({ ...base, startTime: '15:00', endTime: '14:00' }, undefined, '2026-11-09').endTime).toBeDefined();
    expect(
      validate({ ...base, endDate: '2026-12-06', startTime: '15:00', endTime: '14:00' }, undefined, '2026-11-09').endTime,
    ).toBeUndefined();
  });

  it('creates without empty optionals and drops the place for online events', () => {
    const body = toCreateRequest({
      ...emptyValues(),
      name: '  Bodas de Ouro ',
      startDate: '2026-12-20',
      modality: 'ONLINE',
      city: 'Recife',
    });
    expect(body).toEqual({ name: 'Bodas de Ouro', startDate: '2026-12-20', modality: 'ONLINE', privacy: 'PRIVATE' });
  });

  it('edits only what changed, clearing optionals with null', () => {
    const values = { ...valuesFrom(event), description: '', endTime: '19:00', name: event.name };
    expect(toUpdateRequest(values, event, true)).toEqual({ description: null, endTime: '19:00' });
  });

  it('never sends privacy for co-organizers', () => {
    const values = { ...valuesFrom(event), privacy: 'PUBLIC' as const };
    expect(toUpdateRequest(values, event, false)).toEqual({});
    expect(toUpdateRequest(values, event, true)).toEqual({ privacy: 'PUBLIC' });
  });
});
