import styles from './Wordmark.module.css';

/** "memora." — o ponto rosa ecoa os pontos finais do slogan. */
export function Wordmark({ size = 'md' }: { size?: 'md' | 'lg' }) {
  return (
    <span className={`${styles.wordmark} ${styles[size]}`} aria-label="Memora">
      <span aria-hidden="true">memora</span>
      <span aria-hidden="true" className={styles.dot}>.</span>
    </span>
  );
}
