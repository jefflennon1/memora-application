import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { MemoraEvent } from '../api/events';
import type { Memory } from '../api/memories';
import { currentUrl, json, makeMe, mockApi, problem, renderApp } from '../test/utils';

const page = <T,>(items: T[], extra: Partial<{ hasNext: boolean }> = {}) =>
  json({ items, page: 0, size: 20, totalItems: items.length, totalPages: 1, hasNext: false, ...extra });

const organizer = { id: 'u1', username: 'ana.souza', displayName: 'Ana Souza', profileHidden: false };

function makeEvent(overrides: Partial<MemoraEvent> = {}): MemoraEvent {
  return {
    id: 'e1',
    name: 'Chá de Fraldas do Liam',
    startDate: '2027-03-06',
    startTime: '15:00',
    modality: 'IN_PERSON',
    city: 'Belo Horizonte',
    privacy: 'PUBLIC',
    muralStatus: 'OPEN',
    organizer,
    createdAt: '2026-11-09T18:00:00Z',
    updatedAt: '2026-11-09T18:00:00Z',
    ...overrides,
  };
}

function makeMemory(overrides: Partial<Memory> = {}): Memory {
  return {
    id: 'm1',
    eventId: 'e1',
    author: organizer,
    text: 'Que tarde linda!',
    visibility: 'PUBLIC',
    media: [],
    createdAt: '2026-11-09T18:20:00Z',
    mine: false,
    ...overrides,
  };
}

describe('public pages without login', () => {
  it('shows a public event and its public memories to visitors, without the composer', async () => {
    mockApi({
      'GET /api/v1/users/me': () => problem(401, 'UNAUTHENTICATED'),
      'GET /api/v1/events/e1': () => json(makeEvent({ description: 'Uma mensagem especial para quem participa.' })),
      'GET /api/v1/events/e1/memories': () => page([makeMemory()]),
    });

    renderApp('/events/e1');

    expect(await screen.findByRole('heading', { level: 1, name: 'Chá de Fraldas do Liam' })).toBeInTheDocument();
    expect(await screen.findByText('Que tarde linda!')).toBeInTheDocument();
    const description = screen.getByRole('complementary', { name: 'Sobre este evento' });
    expect(within(description).getByText('Uma mensagem especial para quem participa.')).toBeInTheDocument();
    expect(screen.getByText('Você está vendo como visitante')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Entrar' })).toHaveAttribute('href', '/login?next=%2Fevents%2Fe1');
    expect(screen.queryByRole('form', { name: 'Escrever uma memória' })).not.toBeInTheDocument();
  });

  it('treats a private event as not found and invites the visitor to sign in', async () => {
    mockApi({
      'GET /api/v1/users/me': () => problem(401, 'UNAUTHENTICATED'),
      'GET /api/v1/events/e9': () => problem(404, 'EVENT_NOT_FOUND'),
    });

    renderApp('/events/e9');

    expect(await screen.findByText('Evento não encontrado')).toBeInTheDocument();
    expect(screen.getByText(/Se você participa, entre/)).toBeInTheDocument();
  });

  it('searches public events with filters kept in the address', async () => {
    const calls = mockApi({
      'GET /api/v1/users/me': () => problem(401, 'UNAUTHENTICATED'),
      'GET /api/v1/event-categories': () => json([{ code: 'WEDDING', name: 'Casamento' }]),
      'GET /api/v1/events/public': (url) => page(url.searchParams.get('q') ? [makeEvent({ name: 'Casamento da Bia' })] : []),
    });
    const user = userEvent.setup();
    const { router } = renderApp('/explore');

    await user.type(await screen.findByLabelText('Nome do evento'), 'casamento');
    await user.click(screen.getByRole('button', { name: 'Buscar' }));

    expect(await screen.findByText('Casamento da Bia')).toBeInTheDocument();
    expect(currentUrl(router)).toBe('/explore?q=casamento');
    expect(calls.at(-1)!.url.searchParams.get('q')).toBe('casamento');
  });
});

describe('invites', () => {
  const preview = (myStatus: string, modality: 'IN_PERSON' | 'NO_LOCATION' = 'NO_LOCATION') =>
    json({
      token: 'tok',
      type: 'LINK',
      expiresAt: '2026-12-09T00:00:00Z',
      myStatus,
      event: { id: 'e1', name: 'Bodas de Ouro', startDate: '2027-01-10', modality, privacy: 'PRIVATE', organizer },
    });

  it('opens before the onboarding and sends the person to finish it, coming back afterwards', async () => {
    mockApi({
      'GET /api/v1/users/me': () => json(makeMe({ username: null, status: 'PENDING_ONBOARDING', onboardingCompleted: false })),
      'GET /api/v1/invites/tok': () => preview('CAN_REQUEST'),
    });

    renderApp('/invites/tok');

    expect(await screen.findByRole('heading', { name: 'Bodas de Ouro' })).toBeInTheDocument();
    // A resposta exige concluir o cadastro e volta para o convite depois.
    for (const link of await screen.findAllByRole('link', { name: 'Concluir cadastro' })) {
      expect(link).toHaveAttribute('href', '/onboarding?next=%2Finvites%2Ftok');
    }
  });

  it('requests to join through a generic link and shows the pending state', async () => {
    document.cookie = 'XSRF-TOKEN=t';
    const calls = mockApi({
      'GET /api/v1/users/me': () => json(makeMe()),
      'GET /api/v1/invites/tok': () => preview('CAN_REQUEST'),
      'POST /api/v1/invites/tok/join': () => json({ status: 'PENDING_APPROVAL', eventId: 'e1', requestId: 'r1' }),
    });
    const user = userEvent.setup();
    renderApp('/invites/tok');

    await user.click(await screen.findByRole('button', { name: 'Aceitar convite' }));

    expect(await screen.findByText('Pedido enviado')).toBeInTheDocument();
    expect(calls.some((c) => c.method === 'POST' && c.url.pathname.endsWith('/join'))).toBe(true);
  });

  it('joins directly with a named invite and opens the event', async () => {
    document.cookie = 'XSRF-TOKEN=t';
    mockApi({
      'GET /api/v1/users/me': () => json(makeMe()),
      'GET /api/v1/invites/tok': () => preview('CAN_JOIN'),
      'POST /api/v1/invites/tok/join': () => json({ status: 'JOINED', eventId: 'e1' }),
      'GET /api/v1/events/e1': () => json(makeEvent({ myRole: 'PARTICIPANT' })),
      'GET /api/v1/events/e1/memories': () => page([]),
    });
    const user = userEvent.setup();
    const { router } = renderApp('/invites/tok');

    await user.click(await screen.findByRole('button', { name: 'Aceitar convite' }));

    await waitFor(() => expect(currentUrl(router)).toBe('/events/e1'));
  });

  it('records attendance when accepting an in-person invite', async () => {
    document.cookie = 'XSRF-TOKEN=t';
    const calls = mockApi({
      'GET /api/v1/users/me': () => json(makeMe()),
      'GET /api/v1/invites/tok': () => preview('CAN_REQUEST', 'IN_PERSON'),
      'POST /api/v1/invites/tok/join': () => json({ status: 'PENDING_APPROVAL', eventId: 'e1', requestId: 'r1' }),
    });
    const user = userEvent.setup();
    renderApp('/invites/tok');

    await user.click(await screen.findByRole('button', { name: 'Aceitar convite' }));

    await screen.findByText('Pedido enviado');
    const body = JSON.parse(calls.find((call) => call.method === 'POST')!.init.body as string);
    expect(body).toEqual({ attendanceStatus: 'ATTENDING' });
  });

  it('lets the guest decline and change their mind later', async () => {
    document.cookie = 'XSRF-TOKEN=t';
    const calls = mockApi({
      'GET /api/v1/users/me': () => json(makeMe()),
      'GET /api/v1/invites/tok': () => preview('CAN_JOIN'),
      'POST /api/v1/invites/tok/decline': () => json({ status: 'DECLINED', eventId: 'e1' }),
    });
    const user = userEvent.setup();
    renderApp('/invites/tok');

    await user.click(await screen.findByRole('button', { name: 'Recusar convite' }));

    expect(await screen.findByText('Você recusou este convite')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Aceitar convite' })).toBeInTheDocument();
    expect(calls.some((call) => call.url.pathname.endsWith('/decline'))).toBe(true);
  });

  it('explains an expired invite', async () => {
    mockApi({
      'GET /api/v1/users/me': () => json(makeMe()),
      'GET /api/v1/invites/old': () => problem(410, 'INVITE_EXPIRED'),
    });

    renderApp('/invites/old');

    expect(await screen.findByText('Este convite não vale mais')).toBeInTheDocument();
    expect(screen.getByText(/expirou/)).toBeInTheDocument();
  });
});

describe('events', () => {
  it('lets the organization add and remove the event cover', async () => {
    document.cookie = 'XSRF-TOKEN=t';
    let event = makeEvent({ myRole: 'ORGANIZER', coverUrl: null });
    const calls = mockApi({
      'GET /api/v1/users/me': () => json(makeMe({ id: 'u1' })),
      'GET /api/v1/events/e1': () => json(event),
      'GET /api/v1/events/e1/memories': () => page([]),
      'PUT /api/v1/events/e1/cover': () => {
        event = { ...event, coverUrl: '/api/v1/events/e1/cover?v=1' };
        return json(event);
      },
      'DELETE /api/v1/events/e1/cover': () => {
        event = { ...event, coverUrl: null };
        return json(event);
      },
      'GET /api/v1/events/mine': () => page([]),
    });
    const user = userEvent.setup();
    const { container } = renderApp('/events/e1');

    await screen.findByRole('button', { name: 'Adicionar capa' });
    const input = container.querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(input, new File(['cover'], 'encontro.jpg', { type: 'image/jpeg' }));
    await user.click(screen.getByRole('button', { name: 'Usar foto original' }));

    expect(await screen.findByRole('img', { name: 'Capa de Chá de Fraldas do Liam' })).toBeInTheDocument();
    expect(calls.some((c) => c.method === 'PUT' && c.url.pathname === '/api/v1/events/e1/cover')).toBe(true);

    await user.click(screen.getByRole('button', { name: 'Remover' }));
    const confirm = screen.getByRole('group', { name: 'Confirmar remoção da capa' });
    await user.click(within(confirm).getByRole('button', { name: 'Remover capa' }));

    await waitFor(() => expect(screen.queryByRole('img', { name: 'Capa de Chá de Fraldas do Liam' })).not.toBeInTheDocument());
    expect(calls.some((c) => c.method === 'DELETE' && c.url.pathname === '/api/v1/events/e1/cover')).toBe(true);
  });

  it('creates an event and opens it', async () => {
    document.cookie = 'XSRF-TOKEN=t';
    const calls = mockApi({
      'GET /api/v1/users/me': () => json(makeMe()),
      'GET /api/v1/event-categories': () => json([{ code: 'BABY_SHOWER', name: 'Chá de bebê / Chá de fraldas' }]),
      'POST /api/v1/events': () => json(makeEvent({ id: 'new1', myRole: 'ORGANIZER', privacy: 'PRIVATE' }), 201),
      'GET /api/v1/events/new1/memories': () => page([]),
      'GET /api/v1/events/mine': () => page([]),
    });
    const user = userEvent.setup();
    const { router } = renderApp('/events/new');

    await user.type(await screen.findByLabelText('Nome do evento'), 'Chá de Fraldas do Liam');
    await user.selectOptions(screen.getByLabelText('Categoria (opcional)'), 'BABY_SHOWER');
    await user.type(screen.getByLabelText('Data de início'), '2027-03-06');
    await user.type(screen.getByLabelText('Cidade (opcional)'), 'Belo Horizonte');
    await user.click(screen.getByRole('button', { name: 'Criar evento' }));

    await waitFor(() => expect(currentUrl(router)).toBe('/events/new1'));
    const body = JSON.parse(calls.find((c) => c.method === 'POST')!.init.body as string);
    expect(body).toEqual({
      name: 'Chá de Fraldas do Liam',
      categoryCode: 'BABY_SHOWER',
      startDate: '2027-03-06',
      modality: 'IN_PERSON',
      city: 'Belo Horizonte',
      privacy: 'PRIVATE',
    });
  });

  it('shows the API error next to the right field', async () => {
    document.cookie = 'XSRF-TOKEN=t';
    mockApi({
      'GET /api/v1/users/me': () => json(makeMe()),
      'GET /api/v1/event-categories': () => json([]),
      'POST /api/v1/events': () => problem(422, 'EVENT_START_IN_PAST'),
    });
    const user = userEvent.setup();
    renderApp('/events/new');

    await user.type(await screen.findByLabelText('Nome do evento'), 'Festa');
    await user.type(screen.getByLabelText('Data de início'), '2099-01-01');
    await user.click(screen.getByRole('button', { name: 'Criar evento' }));

    expect(await screen.findByText('A data de início não pode estar no passado.')).toBeInTheDocument();
  });

  it('lets the organization approve join requests', async () => {
    document.cookie = 'XSRF-TOKEN=t';
    const calls = mockApi({
      'GET /api/v1/users/me': () => json(makeMe({ id: 'u1' })),
      'GET /api/v1/events/e1': () => json(makeEvent({ myRole: 'ORGANIZER' })),
      'GET /api/v1/events/e1/join-requests': () =>
        page([{ id: 'r1', status: 'PENDING', requestedAt: '2026-11-09T10:00:00Z', attendanceStatus: 'ATTENDING', person: { id: 'u2', displayName: 'Bia Lima', username: 'bia.lima', profileHidden: false } }]),
      'GET /api/v1/events/e1/participants': () => page([{ person: organizer, role: 'ORGANIZER', joinedAt: '2026-11-01T10:00:00Z', attendanceStatus: 'ATTENDING' }]),
      'GET /api/v1/events/e1/participants/attendance-summary': () =>
        json({ attending: 1, notAttending: 0, unanswered: 0, total: 1, myStatus: 'ATTENDING' }),
      'POST /api/v1/events/e1/join-requests/r1/approve': () => json({ id: 'r1', status: 'APPROVED' }),
    });
    const user = userEvent.setup();
    renderApp('/events/e1/people');

    const row = (await screen.findByText('Bia Lima')).closest('li')!;
    expect(within(row).getByText(/vai comparecer/)).toBeInTheDocument();
    expect(await screen.findByText('Presença confirmada')).toBeInTheDocument();
    await user.click(within(row).getByRole('button', { name: 'Aprovar' }));

    await waitFor(() => expect(calls.some((c) => c.url.pathname.endsWith('/r1/approve'))).toBe(true));
  });
});

describe('mural', () => {
  it('warns about new memories in real time and only reloads when asked', async () => {
    class FakeEventSource {
      static latest: FakeEventSource;
      readonly listeners = new Map<string, Set<(event: Event) => void>>();
      readonly url: string;
      closed = false;

      constructor(url: string) {
        this.url = url;
        FakeEventSource.latest = this;
      }

      addEventListener(type: string, listener: (event: Event) => void) {
        const listeners = this.listeners.get(type) ?? new Set();
        listeners.add(listener);
        this.listeners.set(type, listeners);
      }

      removeEventListener(type: string, listener: (event: Event) => void) {
        this.listeners.get(type)?.delete(listener);
      }

      close() {
        this.closed = true;
      }

      emit(type: string, data: unknown) {
        const event = new MessageEvent(type, { data: JSON.stringify(data) });
        this.listeners.get(type)?.forEach((listener) => listener(event));
      }
    }
    vi.stubGlobal('EventSource', FakeEventSource);
    let muralReads = 0;
    const second = makeMemory({ id: 'm2', text: 'Cheguei agora' });
    mockApi({
      'GET /api/v1/users/me': () => json(makeMe()),
      'GET /api/v1/events/e1': () => json(makeEvent({ myRole: 'PARTICIPANT', privacy: 'PRIVATE' })),
      'GET /api/v1/events/e1/memories': () => page(++muralReads === 1 ? [makeMemory()] : [second, makeMemory()]),
    });
    const user = userEvent.setup();
    renderApp('/events/e1');

    expect(await screen.findByText('Que tarde linda!')).toBeInTheDocument();
    expect(FakeEventSource.latest.url).toContain('/api/v1/events/e1/stream');
    FakeEventSource.latest.emit('memory-created', {
      eventId: 'e1',
      memoryId: 'm2',
      createdAt: '2026-11-09T18:30:00Z',
    });

    expect(await screen.findByText('Há uma nova memória')).toBeInTheDocument();
    expect(screen.queryByText('Cheguei agora')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Atualizar mural' }));
    expect(await screen.findByText('Cheguei agora')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText('Há uma nova memória')).not.toBeInTheDocument());
  });

  it('publishes a memory with a photo uploaded first', async () => {
    document.cookie = 'XSRF-TOKEN=t';
    const media = { id: 'img1', url: '/api/v1/media/img1', kind: 'IMAGE' as const, contentType: 'image/png', width: 10, height: 10, sizeBytes: 100 };
    const calls = mockApi({
      'GET /api/v1/users/me': () => json(makeMe()),
      'GET /api/v1/events/e1': () => json(makeEvent({ myRole: 'PARTICIPANT', privacy: 'PRIVATE' })),
      'GET /api/v1/events/e1/memories': () => page([]),
      'POST /api/v1/events/e1/media': () => json(media, 201),
      'POST /api/v1/events/e1/memories': (_url, init) =>
        json(makeMemory({ id: 'm9', mine: true, visibility: 'PARTICIPANTS_ONLY', media: [media], ...JSON.parse(init.body as string) }), 201),
    });
    const user = userEvent.setup();
    renderApp('/events/e1');

    const composer = await screen.findByRole('form', { name: 'Escrever uma memória' });
    await user.type(within(composer).getByLabelText('Nova memória'), 'Que dia! 💛');
    const file = new File(['png'], 'foto.png', { type: 'image/png' });
    await user.upload(within(composer).getByLabelText('Adicionar fotos'), file);
    await user.click(screen.getByRole('button', { name: 'Usar foto original' }));
    expect(await within(composer).findByText('Foto 1')).toBeInTheDocument();
    await user.click(within(composer).getByRole('button', { name: 'Publicar' }));

    expect(await screen.findByText('Publicada agora')).toBeInTheDocument();
    const upload = calls.find((c) => c.url.pathname.endsWith('/media'))!;
    expect(upload.init.body).toBeInstanceOf(FormData);
    expect((upload.init.headers as Record<string, string>)['Content-Type']).toBeUndefined();
    const publish = calls.find((c) => c.method === 'POST' && c.url.pathname.endsWith('/memories'))!;
    expect(JSON.parse(publish.init.body as string)).toEqual({
      text: 'Que dia! 💛',
      mediaIds: ['img1'],
      visibility: 'PARTICIPANTS_ONLY',
    });
  });

  it('counts an emoji as one character and blocks texts over 500', async () => {
    mockApi({
      'GET /api/v1/users/me': () => json(makeMe()),
      'GET /api/v1/events/e1': () => json(makeEvent({ myRole: 'PARTICIPANT' })),
      'GET /api/v1/events/e1/memories': () => page([]),
    });
    const user = userEvent.setup();
    renderApp('/events/e1');

    const area = await screen.findByLabelText('Nova memória');
    await user.click(area);
    await user.paste('💛'.repeat(500));
    expect(screen.getByText('500/500')).toBeInTheDocument();
    await user.paste('x');
    expect(screen.getByText('501/500')).toBeInTheDocument();
    expect(screen.getByText('Use até 500 caracteres.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Publicar' })).toBeDisabled();
  });

  it('hides the composer when the mural is closed and still lets authors delete', async () => {
    document.cookie = 'XSRF-TOKEN=t';
    const calls = mockApi({
      'GET /api/v1/users/me': () => json(makeMe()),
      'GET /api/v1/events/e1': () => json(makeEvent({ myRole: 'PARTICIPANT', muralStatus: 'CLOSED' })),
      'GET /api/v1/events/e1/memories': () => page([makeMemory({ mine: true })]),
      'DELETE /api/v1/memories/m1': () => new Response(null, { status: 204 }),
    });
    const user = userEvent.setup();
    renderApp('/events/e1');

    expect(await screen.findByText('Mural fechado', { selector: 'p' })).toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Escrever uma memória' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Editar memória' })).not.toBeInTheDocument();

    await user.click(await screen.findByLabelText('Mais ações da memória'));
    await user.click(screen.getByRole('button', { name: 'Excluir memória' }));
    await user.click(within(screen.getByRole('group', { name: 'Confirmar: Excluir memória' })).getByRole('button', { name: 'Excluir memória' }));

    await waitFor(() => expect(calls.some((c) => c.method === 'DELETE')).toBe(true));
  });
});
