import { API_V1 } from '../api/config';

export interface MemoryCreatedNotice {
  eventId: string;
  memoryId: string;
  createdAt: string;
}

/**
 * Mantem o stream do mural aberto. O EventSource reconecta sozinho; o servidor define
 * o intervalo de reconexao e envia apenas IDs, nunca o conteudo da memoria.
 */
export function watchMemoryCreated(eventId: string, onNotice: (notice: MemoryCreatedNotice) => void): () => void {
  if (typeof EventSource === 'undefined') return () => undefined;

  const source = new EventSource(`${API_V1}/events/${eventId}/stream`, { withCredentials: true });
  const receive = (raw: Event) => {
    try {
      const notice = JSON.parse((raw as MessageEvent<string>).data) as MemoryCreatedNotice;
      if (notice.eventId === eventId && typeof notice.memoryId === 'string' && typeof notice.createdAt === 'string') {
        onNotice(notice);
      }
    } catch {
      // Um evento malformado e ignorado; o stream continua conectado.
    }
  };
  source.addEventListener('memory-created', receive);
  return () => {
    source.removeEventListener('memory-created', receive);
    source.close();
  };
}
