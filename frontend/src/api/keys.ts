/** Chaves do cache (TanStack Query) num só lugar, para invalidar sem erro de digitação. */
export const keys = {
  categories: ['event-categories'] as const,
  myEvents: ['events', 'mine'] as const,
  publicEvents: (search: Record<string, string>) => ['events', 'public', search] as const,
  event: (id: string) => ['event', id] as const,
  receivedInvites: ['invites', 'received'] as const,
  invitePreview: (token: string) => ['invite', token] as const,
  eventInvites: (eventId: string) => ['event', eventId, 'invites'] as const,
  joinRequests: (eventId: string) => ['event', eventId, 'join-requests'] as const,
  participants: (eventId: string) => ['event', eventId, 'participants'] as const,
  attendanceSummary: (eventId: string) => ['event', eventId, 'attendance-summary'] as const,
  mural: (eventId: string, order?: string) =>
    (order ? ['event', eventId, 'memories', order] : ['event', eventId, 'memories']) as readonly string[],
  memory: (id: string) => ['memory', id] as const,
  comments: (memoryId: string) => ['memory', memoryId, 'comments'] as const,
  replies: (commentId: string) => ['comment', commentId, 'replies'] as const,
};
