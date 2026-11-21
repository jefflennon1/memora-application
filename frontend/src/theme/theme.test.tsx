import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';
import { makeMe, json, mockApi, problem, renderApp } from '../test/utils';
import { THEME_STORAGE_KEY } from './theme';

afterEach(() => {
  localStorage.clear();
  delete document.documentElement.dataset.theme;
});

describe('theme toggle', () => {
  it('is visible on the entry screen and switches and remembers the theme', async () => {
    mockApi({ 'GET /api/v1/users/me': () => problem(401, 'UNAUTHENTICATED') });
    const user = userEvent.setup();
    renderApp('/login');

    // jsdom não tem matchMedia: sem escolha salva, o tema efetivo é o claro.
    await user.click(await screen.findByRole('button', { name: 'Usar tema escuro' }));

    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');

    await user.click(screen.getByRole('button', { name: 'Usar tema claro' }));
    expect(document.documentElement.dataset.theme).toBe('light');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('light');
  });

  it('keeps every toggle on the page in sync', async () => {
    mockApi({ 'GET /api/v1/users/me': () => json(makeMe()) });
    const user = userEvent.setup();
    renderApp('/');

    await screen.findByRole('heading', { name: 'Olá, Ana' });
    const [header] = screen.getAllByRole('button', { name: /tema escuro/i });
    await user.click(header!);

    expect(screen.queryAllByRole('button', { name: /tema escuro/i })).toHaveLength(0);
    expect(screen.getAllByRole('button', { name: /tema claro/i })).toHaveLength(2);
  });
});
