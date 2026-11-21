import { useId, type InputHTMLAttributes, type ReactNode } from 'react';
import styles from './TextField.module.css';

interface Props extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> {
  label: string;
  hint?: ReactNode;
  error?: string | null;
  prefix?: string;
  /** Conteúdo exibido abaixo do campo no lugar da dica (ex.: estado da verificação). */
  status?: ReactNode;
}

export function TextField({ label, hint, error, prefix, status, className, ...input }: Props) {
  const id = useId();
  const messageId = `${id}-message`;
  const message = error ?? status ?? hint;
  return (
    <div className={`${styles.field} ${className ?? ''}`}>
      <label htmlFor={id} className={styles.label}>
        {label}
      </label>
      <div className={`${styles.control} ${error ? styles.invalid : ''}`}>
        {prefix && (
          <span className={styles.prefix} aria-hidden="true">
            {prefix}
          </span>
        )}
        <input
          id={id}
          className={styles.input}
          aria-invalid={error ? true : undefined}
          aria-describedby={message ? messageId : undefined}
          {...input}
        />
      </div>
      {message && (
        <div id={messageId} className={error ? styles.error : styles.hint} aria-live="polite">
          {message}
        </div>
      )}
    </div>
  );
}
