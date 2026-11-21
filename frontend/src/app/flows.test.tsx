import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { currentUrl, json, makeMe, mockApi, problem, renderApp } from '../test/utils';

describe('session guards', () => {
  it('shows the public invite to visitors and asks for login only when responding', async () => {
    mockApi({
      'GET /api/v1/users/me': () => problem(401, 'UNAUTHENTICATED'),
      'GET /api/v1/invites/AbC123': () => json({
        token: 'AbC123',
        type: 'LINK',
        expiresAt: '2026-12-09T00:00:00Z',
        myStatus: 'CAN_REQUEST',
        event: { id: 'e1', name: 'Bodas de Ouro', startDate: '2027-01-10', modality: 'NO_LOCATION', privacy: 'PRIVATE', organizer: {} },
        page: {
          title: 'Venha celebrar conosco',
          body: 'Uma noite muito especial.',
          footer: 'Com carinho, Ana e Rui',
          location: 'Praça da Liberdade, Belo Horizonte',
          backgroundColor: '#102f35',
          backgroundGradientColor: '#804060',
          backgroundImageLayout: 'COVER',
        },
      }),
    });

    const { router } = renderApp('/invites/AbC123');

    expect(await screen.findByRole('heading', { name: 'Venha celebrar conosco' })).toBeInTheDocument();
    expect(screen.getByText('Uma noite muito especial.')).toBeInTheDocument();
    expect(screen.getByTitle('Mapa de Praça da Liberdade, Belo Horizonte')).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: 'Entrar para responder' })).toHaveAttribute('href', '/login?next=%2Finvites%2FAbC123');
    expect(currentUrl(router)).toBe('/invites/AbC123');
  });

  it('sends accounts with pending onboarding to the onboarding screen', async () => {
    mockApi({
      'GET /api/v1/users/me': () =>
        json(makeMe({ username: null, status: 'PENDING_ONBOARDING', onboardingCompleted: false })),
    });

    const { router } = renderApp('/profile');

    expect(await screen.findByRole('heading', { name: 'Boas-vindas, Ana' })).toBeInTheDocument();
    expect(currentUrl(router)).toBe('/onboarding?next=%2Fprofile');
  });

  it('opens the home screen for active accounts', async () => {
    mockApi({ 'GET /api/v1/users/me': () => json(makeMe()) });

    renderApp('/');

    expect(await screen.findByRole('heading', { name: 'Olá, Ana' })).toBeInTheDocument();
    expect(screen.queryByText('O que já dá para fazer')).not.toBeInTheDocument();
    expect(screen.queryByText('Em breve')).not.toBeInTheDocument();
  });

  it('minimizes the desktop menu and remembers the preference', async () => {
    localStorage.removeItem('memora.sidebar.collapsed');
    mockApi({ 'GET /api/v1/users/me': () => json(makeMe()) });
    const user = userEvent.setup();

    renderApp('/');

    const minimize = await screen.findByRole('button', { name: 'Minimizar menu lateral' });
    await user.click(minimize);
    expect(screen.getByRole('button', { name: 'Expandir menu lateral' })).toBeInTheDocument();
    expect(localStorage.getItem('memora.sidebar.collapsed')).toBe('true');

    await user.click(screen.getByRole('button', { name: 'Expandir menu lateral' }));
    expect(screen.getByRole('button', { name: 'Minimizar menu lateral' })).toBeInTheDocument();
    expect(localStorage.getItem('memora.sidebar.collapsed')).toBe('false');
    localStorage.removeItem('memora.sidebar.collapsed');
  });

  it('redirects disabled accounts to the entry screen with the right message', async () => {
    mockApi({ 'GET /api/v1/users/me': () => problem(403, 'ACCOUNT_DISABLED') });

    const { router } = renderApp('/');

    expect(await screen.findByText('Conta desativada')).toBeInTheDocument();
    expect(currentUrl(router)).toBe('/login?error=account_disabled');
  });

  it('explains when the API is unreachable', async () => {
    mockApi({ 'GET /api/v1/users/me': () => Promise.reject(new TypeError('Failed to fetch')) });

    renderApp('/');

    expect(await screen.findByRole('heading', { name: 'Sem conexão com a Memora' }, { timeout: 4000 })).toBeInTheDocument();
  });
});

describe('entry screen', () => {
  it('shows a friendly message for ?error=login_failed', async () => {
    mockApi({ 'GET /api/v1/users/me': () => problem(401, 'UNAUTHENTICATED') });

    renderApp('/login?error=login_failed');

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível entrar');
  });

  it('skips the entry screen when the person is already signed in', async () => {
    mockApi({ 'GET /api/v1/users/me': () => json(makeMe()) });

    const { router } = renderApp('/login?next=%2Fprofile');

    await waitFor(() => expect(currentUrl(router)).toBe('/profile'));
  });

  it('logs in with username and password and keeps the requested destination', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    let authenticated = false;
    const calls = mockApi({
      'GET /api/v1/users/me': () => authenticated ? json(makeMe({ passwordLogin: true })) : problem(401, 'UNAUTHENTICATED'),
      'POST /api/v1/auth/login': () => {
        authenticated = true;
        return new Response(null, { status: 204 });
      },
    });
    const user = userEvent.setup();
    const { router } = renderApp('/login?next=%2Fprofile');

    await user.type(await screen.findByLabelText('Nome de usuário'), 'ana.souza');
    await user.type(screen.getByLabelText('Senha'), 'senha-segura');
    await user.click(screen.getByRole('button', { name: 'Entrar' }));

    await waitFor(() => expect(currentUrl(router)).toBe('/profile'));
    const request = calls.find((call) => call.method === 'POST')!;
    expect(JSON.parse(request.init.body as string)).toEqual({ username: 'ana.souza', password: 'senha-segura' });
  });

  it('checks availability and creates a simple local account', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    let authenticated = false;
    const calls = mockApi({
      'GET /api/v1/users/me': () => authenticated ? json(makeMe({ passwordLogin: true, phone: '11999991234' })) : problem(401, 'UNAUTHENTICATED'),
      'GET /api/v1/auth/username-availability': (url) =>
        json({ username: url.searchParams.get('username'), available: true, reason: null }),
      'POST /api/v1/auth/register': () => {
        authenticated = true;
        return new Response(null, { status: 201 });
      },
    });
    const user = userEvent.setup();
    const { router } = renderApp('/register');

    await user.type(await screen.findByLabelText('@usuário'), 'nova.pessoa');
    expect(await screen.findByText('@nova.pessoa está livre')).toBeInTheDocument();
    await user.type(screen.getByLabelText('Senha'), 'senha-segura');
    await user.type(screen.getByLabelText('Repita a senha'), 'senha-segura');
    await user.type(screen.getByLabelText('Telefone'), '(11) 99999-1234');
    await user.click(screen.getByRole('button', { name: 'Criar minha conta' }));

    await waitFor(() => expect(currentUrl(router)).toBe('/'));
    const request = calls.find((call) => call.url.pathname.endsWith('/auth/register'))!;
    expect(JSON.parse(request.init.body as string)).toEqual({
      username: 'nova.pessoa',
      password: 'senha-segura',
      passwordConfirmation: 'senha-segura',
      phone: '(11) 99999-1234',
    });
  });
});

describe('onboarding', () => {
  const pending = makeMe({ username: null, status: 'PENDING_ONBOARDING', onboardingCompleted: false });

  it('checks the @username while typing and completes the onboarding', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    const calls = mockApi({
      'GET /api/v1/users/me': () => json(pending),
      'GET /api/v1/users/username-availability': (url) => {
        const username = url.searchParams.get('username')!;
        return json({ username, available: username !== 'ana', reason: username === 'ana' ? 'USERNAME_TAKEN' : null });
      },
      'PATCH /api/v1/users/me/onboarding': () => json(makeMe()),
    });
    const user = userEvent.setup();
    const { router } = renderApp('/onboarding?next=%2Finvites%2FAbC123');

    const field = await screen.findByLabelText('@usuário');
    await user.type(field, 'Ana');
    expect(await screen.findByText(/já está em uso/)).toBeInTheDocument();

    await user.type(field, '.Souza');
    expect(await screen.findByText('@ana.souza está livre')).toBeInTheDocument();

    await user.click(screen.getByRole('radio', { name: /Ficar sem foto/ }));
    await user.click(screen.getByRole('radio', { name: /Reservado/ }));
    await user.click(screen.getByRole('button', { name: 'Concluir cadastro' }));

    await waitFor(() => expect(currentUrl(router)).toBe('/invites/AbC123'));
    const patch = calls.find((c) => c.method === 'PATCH')!;
    expect(JSON.parse(patch.init.body as string)).toEqual({
      username: 'ana.souza',
      displayName: 'Ana Souza',
      keepSuggestedPhoto: false,
      profilePrivacy: 'PRIVATE',
    });
    expect((patch.init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('tok');
  });

  it('does not call the API for usernames with an invalid format', async () => {
    const calls = mockApi({ 'GET /api/v1/users/me': () => json(pending) });
    const user = userEvent.setup();
    renderApp('/onboarding');

    await user.type(await screen.findByLabelText('@usuário'), 'joão');

    expect(await screen.findByText(/letras sem acento, números, ponto ou sublinhado/i)).toBeInTheDocument();
    expect(calls.some((c) => c.url.pathname.endsWith('username-availability'))).toBe(false);
  });

  it('shows the server error on the @username field when it was taken meanwhile', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    mockApi({
      'GET /api/v1/users/me': () => json(pending),
      'GET /api/v1/users/username-availability': (url) =>
        json({ username: url.searchParams.get('username'), available: true }),
      'PATCH /api/v1/users/me/onboarding': () => problem(409, 'USERNAME_TAKEN'),
    });
    const user = userEvent.setup();
    renderApp('/onboarding');

    await user.type(await screen.findByLabelText('@usuário'), 'ana.souza');
    await screen.findByText('@ana.souza está livre');
    await user.click(screen.getByRole('button', { name: 'Concluir cadastro' }));

    expect(await screen.findByText(/já está em uso/)).toBeInTheDocument();
  });
});

describe('profile', () => {
  it('sends only the changed fields and confirms the save', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    let me = makeMe();
    const calls = mockApi({
      'GET /api/v1/users/me': () => json(me),
      'PATCH /api/v1/users/me': (_url, init) => {
        me = { ...me, ...JSON.parse(init.body as string) };
        return json(me);
      },
    });
    const user = userEvent.setup();
    renderApp('/profile');

    const name = await screen.findByLabelText('Nome exibido');
    await user.clear(name);
    await user.type(name, 'Ana S.');
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }));

    expect(await screen.findByText('Perfil salvo')).toBeInTheDocument();
    const patch = calls.find((c) => c.method === 'PATCH')!;
    expect(JSON.parse(patch.init.body as string)).toEqual({ displayName: 'Ana S.' });
    expect(screen.getByRole('heading', { level: 1, name: 'Ana S.' })).toBeInTheDocument();
  });

  it('asks for confirmation before removing the photo', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    const calls = mockApi({
      'GET /api/v1/users/me': () => json(makeMe()),
      'DELETE /api/v1/users/me/photo': () => json(makeMe({ photoUrl: null })),
    });
    const user = userEvent.setup();
    renderApp('/profile');

    await user.click(await screen.findByRole('button', { name: 'Remover' }));
    const confirm = screen.getByRole('group', { name: 'Confirmar remoção da foto' });
    await user.click(within(confirm).getByRole('button', { name: 'Remover foto' }));

    expect(await screen.findByRole('button', { name: 'Escolher foto' })).toBeInTheDocument();
    expect(calls.some((c) => c.method === 'DELETE' && c.url.pathname === '/api/v1/users/me/photo')).toBe(true);
  });

  it('uploads a new profile photo', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    const calls = mockApi({
      'GET /api/v1/users/me': () => json(makeMe({ photoUrl: null })),
      'PUT /api/v1/users/me/photo': () => json(makeMe({ photoUrl: '/api/v1/users/u1/photo?v=1' })),
    });
    const user = userEvent.setup();
    const { container } = renderApp('/profile');

    await screen.findByRole('button', { name: 'Escolher foto' });
    const input = container.querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(input, new File(['photo'], 'familia.jpg', { type: 'image/jpeg' }));
    await user.click(screen.getByRole('button', { name: 'Usar foto original' }));

    await waitFor(() => expect(calls.some((c) => c.method === 'PUT' && c.url.pathname === '/api/v1/users/me/photo')).toBe(true));
    const request = calls.find((c) => c.method === 'PUT')!;
    expect(request.init.body).toBeInstanceOf(FormData);
    expect((request.init.body as FormData).get('file')).toBeInstanceOf(File);
    expect(await screen.findByRole('button', { name: 'Alterar foto' })).toBeInTheDocument();
  });

  it('signs out and returns to the entry screen', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    const calls = mockApi({
      'GET /api/v1/users/me': () => json(makeMe()),
      'POST /api/v1/auth/logout': () => new Response(null, { status: 204 }),
    });
    const user = userEvent.setup();
    const { router } = renderApp('/profile');

    await user.click(await screen.findByRole('button', { name: 'Sair da Memora' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    expect(calls.some((c) => c.method === 'POST' && c.url.pathname === '/api/v1/auth/logout')).toBe(true);
  });
});
