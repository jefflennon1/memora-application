import { isApiError } from '../api/client';
import { API_URL } from '../api/config';
import { friendlyMessage } from '../api/problems';
import { Button } from './Button';
import { Wordmark } from './Wordmark';
import styles from './FullPageState.module.css';

export function FullPageLoading() {
  return (
    <div className={styles.page} role="status" aria-live="polite">
      <Wordmark size="lg" />
      <p className={styles.quiet}>Abrindo suas memórias…</p>
    </div>
  );
}

export function FullPageError({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const offline = isApiError(error, 'NETWORK_ERROR') || isApiError(error, 'TIMEOUT');
  return (
    <div className={styles.page}>
      <Wordmark size="lg" />
      <div className={styles.text} role="alert">
        <h1 className={styles.title}>{offline ? 'Sem conexão com a Memora' : 'Não deu para abrir agora'}</h1>
        <p>{friendlyMessage(error)}</p>
        {offline && import.meta.env.DEV && (
          <p className={styles.quiet}>
            Em desenvolvimento: confira se a API está rodando em <code>{API_URL}</code>.
          </p>
        )}
      </div>
      {onRetry && <Button onClick={onRetry}>Tentar de novo</Button>}
    </div>
  );
}
