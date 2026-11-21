import { describe, expect, it } from 'vitest';
import { dateBlock, formatEventWhen, personName, timeAgo, todayISO } from './format';

describe('formatEventWhen', () => {
  it('describes a single day with start and end time', () => {
    expect(formatEventWhen({ startDate: '2026-12-05', startTime: '15:00', endTime: '18:00' })).toBe(
      'Sábado, 5 de dezembro de 2026, das 15:00 às 18:00',
    );
  });

  it('describes a single day without time', () => {
    expect(formatEventWhen({ startDate: '2026-12-05' })).toBe('Sábado, 5 de dezembro de 2026');
  });

  it('describes multi-day events', () => {
    expect(formatEventWhen({ startDate: '2026-12-05', endDate: '2026-12-07' })).toBe(
      'De 5 de dezembro a 7 de dezembro de 2026',
    );
    expect(formatEventWhen({ startDate: '2026-12-30', endDate: '2027-01-02' })).toBe(
      'De 30 de dezembro de 2026 a 2 de janeiro de 2027',
    );
  });

  it('never shifts the day by time zone', () => {
    expect(dateBlock('2026-01-01')).toMatchObject({ day: '1', year: 2026 });
  });
});

describe('helpers', () => {
  it('todayISO uses the local date', () => {
    expect(todayISO(new Date(2026, 10, 9, 23, 30))).toBe('2026-11-09');
  });

  it('timeAgo is relative for recent moments', () => {
    const now = Date.parse('2026-11-09T12:00:00Z');
    expect(timeAgo('2026-11-09T11:59:50Z', now)).toBe('agora');
    expect(timeAgo('2026-11-09T11:55:00Z', now)).toBe('há 5 minutos');
    expect(timeAgo('2026-11-09T09:00:00Z', now)).toBe('há 3 horas');
  });

  it('hides names of hidden profiles', () => {
    expect(personName({ id: '1', profileHidden: true, displayName: 'Ana' })).toBe('Perfil reservado');
    expect(personName({ id: '1', profileHidden: false, displayName: 'Ana' })).toBe('Ana');
  });
});
