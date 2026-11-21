import { describe, expect, it } from 'vitest';
import { googleLoginUrl, safeNext, withNext } from './redirect';
import { normalizeUsername, usernameFormatProblem } from './username';

describe('safeNext', () => {
  it.each([
    ['/invites/AbC123', '/invites/AbC123'],
    ['/events/1?tab=mural', '/events/1?tab=mural'],
    ['https://evil.example', '/'],
    ['//evil.example', '/'],
    ['/\\evil.example', '/'],
    ['javascript:alert(1)', '/'],
    ['/login', '/'],
    ['/onboarding?next=/x', '/'],
    [null, '/'],
  ])('%s -> %s', (input, expected) => {
    expect(safeNext(input)).toBe(expected);
  });
});

describe('googleLoginUrl', () => {
  it('passes redirect_to only for safe paths', () => {
    expect(googleLoginUrl('/invites/AbC123')).toBe(
      'http://localhost:8080/oauth2/authorization/google?redirect_to=%2Finvites%2FAbC123',
    );
    expect(googleLoginUrl('https://evil.example')).toBe('http://localhost:8080/oauth2/authorization/google');
    expect(googleLoginUrl(null)).toBe('http://localhost:8080/oauth2/authorization/google');
  });

  it('withNext omits the home destination', () => {
    expect(withNext('/login', '/')).toBe('/login');
    expect(withNext('/login', '/invites/x')).toBe('/login?next=%2Finvites%2Fx');
  });
});

describe('username rules (mirror the API)', () => {
  it('normalizes "@" and case', () => {
    expect(normalizeUsername('  @Ana.Souza ')).toBe('ana.souza');
  });

  it.each([
    ['ana.souza', null],
    ['a_1', null],
    ['', 'EMPTY'],
    ['ab', 'TOO_SHORT'],
    ['a'.repeat(31), 'TOO_LONG'],
    ['joão', 'BAD_CHARS'],
    ['ana souza', 'BAD_CHARS'],
    ['.ana', 'BAD_EDGES'],
    ['ana_', 'BAD_EDGES'],
    ['ana..souza', 'DOUBLE_DOT'],
  ])('%s -> %s', (value, expected) => {
    expect(usernameFormatProblem(value)).toBe(expected);
  });
});
