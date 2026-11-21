import type { ReactNode } from 'react';
import { Link } from 'react-router';
import styles from './ComingSoon.module.css';

/**
 * Tela honesta para funcionalidades que a API ainda não oferece.
 * Não mostra dados simulados: diz o que virá e em qual entrega do backend.
 */
export function ComingSoon({
  title,
  delivery,
  children,
  endpoints,
}: {
  title: string;
  /** Ex.: "Chega na Entrega 3" */
  delivery: string;
  children: ReactNode;
  /** Endpoints da API que esta tela vai consumir (para quem está testando). */
  endpoints?: string[];
}) {
  return (
    <section className={styles.wrap} aria-labelledby="coming-soon-title">
      <p className={styles.badge}>{delivery}</p>
      <h1 id="coming-soon-title" className={styles.title}>
        {title}
      </h1>
      <div className={styles.body}>{children}</div>
      {endpoints && endpoints.length > 0 && (
        <details className={styles.details}>
          <summary>O que esta tela vai usar da API</summary>
          <ul>
            {endpoints.map((e) => (
              <li key={e}>
                <code>{e}</code>
              </li>
            ))}
          </ul>
        </details>
      )}
      <Link to="/" className={styles.back}>
        Voltar ao início
      </Link>
    </section>
  );
}
