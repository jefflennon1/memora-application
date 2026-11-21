import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { MemoraEvent } from '../api/events';
import type { Comment } from '../api/interactions';
import type { Memory } from '../api/memories';
import { json, makeMe, mockApi, problem, renderApp } from '../test/utils';

const page = <T,>(items: T[]) => json({ items, page: 0, size: 20, totalItems: items.length, totalPages: 1, hasNext: false });
const ana = { id: 'u1', username: 'ana.souza', displayName: 'Ana Souza', profileHidden: false };
const bia = { id: 'u2', username: 'bia.lima', displayName: 'Bia Lima', profileHidden: false };

function makeEvent(overrides: Partial<MemoraEvent> = {}): MemoraEvent {
  return {
    id: 'e1',
    name: 'Chá de Fraldas do Liam',
    startDate: '2027-03-06',
    modality: 'NO_LOCATION',
    privacy: 'PRIVATE',
    muralStatus: 'OPEN',
    organizer: ana,
    myRole: 'PARTICIPANT',
    createdAt: '2026-11-09T18:00:00Z',
    updatedAt: '2026-11-09T18:00:00Z',
    ...overrides,
  };
}

function makeMemory(overrides: Partial<Memory> = {}): Memory {
  return {
    id: 'm1',
    eventId: 'e1',
    author: bia,
    text: 'Que tarde linda!',
    visibility: 'PARTICIPANTS_ONLY',
    media: [],
    createdAt: '2026-11-09T18:20:00Z',
    mine: false,
    commentCount: 0,
    reactions: { total: 0, items: [] },
    ...overrides,
  };
}

function makeComment(overrides: Partial<Comment> = {}): Comment {
  return {
    id: 'c1',
    memoryId: 'm1',
    author: bia,
    text: 'Que foto linda!',
    mentions: [],
    visibility: 'PARTICIPANTS_ONLY',
    replyCount: 0,
    createdAt: '2026-11-09T18:25:00Z',
    mine: false,
    ...overrides,
  };
}

const base = (event: MemoraEvent, memory: Memory, user = makeMe({ id: 'u1' })) => ({
  'GET /api/v1/users/me': () => (user ? json(user) : problem(401, 'UNAUTHENTICATED')),
  'GET /api/v1/events/e1': () => json(event),
  'GET /api/v1/events/e1/memories': () => page([memory]),
  'GET /api/v1/events/e1/participants': () =>
    page([
      { person: ana, role: 'ORGANIZER', joinedAt: '2026-11-01T10:00:00Z' },
      { person: bia, role: 'PARTICIPANT', joinedAt: '2026-11-02T10:00:00Z' },
    ]),
});

describe('reactions', () => {
  it('reacts, switches and removes the reaction, using the summary the API returns', async () => {
    document.cookie = 'XSRF-TOKEN=t';
    const calls = mockApi({
      ...base(makeEvent(), makeMemory({ reactions: { total: 2, items: [{ emoji: '😂', count: 2 }] } })),
      'PUT /api/v1/memories/m1/reactions': (_u, init) => {
        const { emoji } = JSON.parse(init.body as string);
        return json({ total: 3, items: [{ emoji: '😂', count: 2 }, { emoji, count: 1 }], mine: emoji });
      },
      'DELETE /api/v1/memories/m1/reactions': () => json({ total: 2, items: [{ emoji: '😂', count: 2 }] }),
    });
    const user = userEvent.setup();
    renderApp('/events/e1');

    await user.click(await screen.findByRole('button', { name: 'Reagir' }));
    await user.click(screen.getByRole('button', { name: 'Reagir com ❤️' }));

    const mine = await screen.findByRole('button', { name: /Trocar reação.*Sua reação: ❤️/ });
    expect(mine).toHaveAttribute('aria-pressed', 'true');
    expect(JSON.parse(calls.find((c) => c.method === 'PUT')!.init.body as string)).toEqual({ emoji: '❤️' });

    await user.click(mine);
    await user.click(screen.getByRole('button', { name: 'Reagir com ❤️' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'DELETE')).toBe(true));
    await waitFor(() => expect(screen.queryByRole('button', { name: /Sua reação:/ })).not.toBeInTheDocument());
  });

  it('with the mural closed, only allows removing your own reaction', async () => {
    mockApi(
      base(
        makeEvent({ muralStatus: 'CLOSED' }),
        makeMemory({ reactions: { total: 3, items: [{ emoji: '🎉', count: 2 }, { emoji: '❤️', count: 1 }], mine: '❤️' } }),
      ),
    );
    renderApp('/events/e1');

    expect(await screen.findByRole('button', { name: /Tirar a sua reação ❤️/ })).toBeEnabled();
    expect(screen.queryByRole('button', { name: 'Reagir' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /🎉/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /3 reações/ })).toBeInTheDocument();
  });

  it('shows visitors the counts, never who reacted, without buttons', async () => {
    mockApi(
      base(
        makeEvent({ privacy: 'PUBLIC', myRole: null }),
        makeMemory({ visibility: 'PUBLIC', reactions: { total: 4, items: [{ emoji: '❤️', count: 4 }] } }),
        null as never,
      ),
    );
    renderApp('/events/e1');

    expect(await screen.findByLabelText('4 reações: ❤️ 4')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /❤️/ })).not.toBeInTheDocument();
  });
});

describe('comments', () => {
  it('renders a video in the memory but never offers video in comments', async () => {
    const video = {
      id: 'v1',
      url: '/api/v1/media/v1',
      kind: 'VIDEO' as const,
      contentType: 'video/mp4',
      durationMs: 60_000,
      width: 1280,
      height: 720,
      sizeBytes: 1000,
    };
    mockApi({
      ...base(makeEvent(), makeMemory({ media: [video] })),
      'GET /api/v1/memories/m1/comments': () => page([]),
    });
    const user = userEvent.setup();
    renderApp('/events/e1');

    const player = await screen.findByText('Vídeo · até 1 minuto');
    expect(player.closest('div')?.querySelector('video')).toHaveAttribute('src', 'http://localhost:8080/api/v1/media/v1');
    await user.click(screen.getByRole('button', { name: 'Comentários' }));
    const comments = await screen.findByRole('region', { name: 'Comentários' });
    expect(within(comments).queryByText('Adicionar vídeo')).not.toBeInTheDocument();
  });

  it('renders thirty-second audio in memories and comments', async () => {
    const audio = {
      id: 'a1',
      url: '/api/v1/media/a1',
      kind: 'AUDIO' as const,
      contentType: 'audio/wav',
      durationMs: 30_000,
      sizeBytes: 1000,
    };
    mockApi({
      ...base(makeEvent(), makeMemory({ media: [audio], commentCount: 1 })),
      'GET /api/v1/memories/m1/comments': () => page([
        makeComment({ text: null, audio: { ...audio, id: 'a2', url: '/api/v1/comments/c1/audio' } }),
      ]),
    });
    const user = userEvent.setup();
    renderApp('/events/e1');

    expect((await screen.findAllByText('Áudio · 0:30')).length).toBeGreaterThanOrEqual(1);
    await user.click(screen.getByRole('button', { name: '1 comentário' }));
    expect((await screen.findAllByText('Áudio · 0:30')).length).toBe(2);
    expect(document.querySelectorAll('audio')).toHaveLength(2);
  });

  it('comments with a suggested @mention and updates the count', async () => {
    document.cookie = 'XSRF-TOKEN=t';
    const created = makeComment({
      id: 'c9',
      author: ana,
      mine: true,
      text: 'Linda, @bia.lima ',
      mentions: [{ start: 7, length: 9, person: bia }],
    });
    let comments: Comment[] = [];
    const calls = mockApi({
      ...base(makeEvent(), makeMemory()),
      'GET /api/v1/memories/m1/comments': () => page(comments),
      'POST /api/v1/memories/m1/comments': () => {
        comments = [created];
        return json(created, 201);
      },
    });
    const user = userEvent.setup();
    renderApp('/events/e1');

    await user.click(await screen.findByRole('button', { name: 'Comentários' }));
    const box = await screen.findByLabelText('Escreva um comentário');
    await user.type(box, 'Linda, @bi');
    expect(await screen.findByRole('option', { name: /Bia Lima/ })).toBeInTheDocument();
    await user.keyboard('{Enter}');
    expect(box).toHaveValue('Linda, @bia.lima ');

    await user.click(screen.getByRole('button', { name: 'Comentar' }));

    expect(await screen.findByRole('button', { name: '1 comentário' })).toBeInTheDocument();
    const section = screen.getByRole('region', { name: 'Comentários' });
    const mention = await within(section).findByText('@bia.lima');
    expect(mention).toHaveAttribute('title', 'Bia Lima');
    expect(JSON.parse(calls.find((c) => c.method === 'POST')!.init.body as string)).toEqual({
      text: 'Linda, @bia.lima',
      visibility: 'PARTICIPANTS_ONLY',
    });
  });

  it('offers public comments only on a public memory of a public event', async () => {
    mockApi({
      ...base(makeEvent({ privacy: 'PUBLIC' }), makeMemory({ visibility: 'PUBLIC' })),
      'GET /api/v1/memories/m1/comments': () => page([]),
    });
    const user = userEvent.setup();
    renderApp('/events/e1');
    await user.click(await screen.findByRole('button', { name: 'Comentários' }));
    expect(await screen.findByRole('checkbox', { name: 'Público' })).toBeInTheDocument();
  });

  it('hides the public option on a participants-only memory', async () => {
    mockApi({
      ...base(makeEvent({ privacy: 'PUBLIC' }), makeMemory({ visibility: 'PARTICIPANTS_ONLY' })),
      'GET /api/v1/memories/m1/comments': () => page([]),
    });
    const user = userEvent.setup();
    renderApp('/events/e1');
    await user.click(await screen.findByRole('button', { name: 'Comentários' }));
    await screen.findByLabelText('Escreva um comentário');
    expect(screen.queryByRole('checkbox', { name: 'Público' })).not.toBeInTheDocument();
  });

  it('replies to a comment, but never to a reply, and warns that deleting removes replies', async () => {
    document.cookie = 'XSRF-TOKEN=t';
    const top = makeComment({ id: 'c1', mine: true, author: ana, replyCount: 1 });
    const answer = makeComment({ id: 'r1', parentId: 'c1', author: bia, text: 'Obrigada!', replyCount: null });
    mockApi({
      ...base(makeEvent(), makeMemory({ commentCount: 2 })),
      'GET /api/v1/memories/m1/comments': () => page([top]),
      'GET /api/v1/comments/c1/replies': () => page([answer]),
    });
    const user = userEvent.setup();
    renderApp('/events/e1');

    await user.click(await screen.findByRole('button', { name: '2 comentários' }));
    await user.click(await screen.findByRole('button', { name: 'Ver 1 resposta' }));
    const reply = (await screen.findByText('Obrigada!')).closest('li')!;
    expect(within(reply).queryByRole('button', { name: 'Responder' })).not.toBeInTheDocument();

    const comment = screen.getByText('Que foto linda!').closest('li')!;
    expect(within(comment).getAllByRole('button', { name: 'Responder' })).toHaveLength(1);
    await user.click(within(comment).getAllByRole('button', { name: 'Excluir' })[0]!);
    expect(screen.getByText('Excluir este comentário? A resposta dele também será apagada.')).toBeInTheDocument();
  });

  it('shows visitors only that participants comment', async () => {
    mockApi({
      ...base(makeEvent({ privacy: 'PUBLIC', myRole: null }), makeMemory({ visibility: 'PUBLIC', commentCount: 1 }), null as never),
      'GET /api/v1/memories/m1/comments': () => page([makeComment({ visibility: 'PUBLIC', text: 'Parabéns!' })]),
    });
    const user = userEvent.setup();
    renderApp('/events/e1');

    await user.click(await screen.findByRole('button', { name: '1 comentário' }));
    expect(await screen.findByText('Parabéns!')).toBeInTheDocument();
    expect(screen.getByText('Só quem participa do evento comenta.')).toBeInTheDocument();
    expect(screen.queryByLabelText('Escreva um comentário')).not.toBeInTheDocument();
  });
});
