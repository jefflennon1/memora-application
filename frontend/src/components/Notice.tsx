import type { ReactNode } from 'react';
import styles from './Notice.module.css';

/** Aviso em linha. Erros usam role="alert" para serem lidos assim que aparecem. */
export function Notice({
  tone = 'info',
  title,
  children,
}: {
  tone?: 'info' | 'error' | 'success';
  title?: string;
  children?: ReactNode;
}) {
  return (
    <div className={`${styles.notice} ${styles[tone]}`} role={tone === 'error' ? 'alert' : 'status'}>
      {title && <p className={styles.title}>{title}</p>}
      {children && <div className={styles.body}>{children}</div>}
    </div>
  );
}
