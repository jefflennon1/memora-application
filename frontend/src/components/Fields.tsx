import { useId, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import styles from './TextField.module.css';
import own from './Fields.module.css';

/* Campos irmãos do TextField, com o mesmo visual e a mesma ligação rótulo/mensagem. */

interface Common {
  label: string;
  hint?: ReactNode;
  error?: string | null;
}

function Message({ id, error, hint }: { id: string; error?: string | null; hint?: ReactNode }) {
  const message = error ?? hint;
  if (!message) return null;
  return (
    <div id={id} className={error ? styles.error : styles.hint} aria-live="polite">
      {message}
    </div>
  );
}

export function SelectField({
  label,
  hint,
  error,
  children,
  className,
  ...select
}: Common & Omit<SelectHTMLAttributes<HTMLSelectElement>, 'id'>) {
  const id = useId();
  const messageId = `${id}-message`;
  return (
    <div className={`${styles.field} ${className ?? ''}`}>
      <label htmlFor={id} className={styles.label}>
        {label}
      </label>
      <div className={`${styles.control} ${error ? styles.invalid : ''}`}>
        <select
          id={id}
          className={`${styles.input} ${own.select}`}
          aria-invalid={error ? true : undefined}
          aria-describedby={error || hint ? messageId : undefined}
          {...select}
        >
          {children}
        </select>
      </div>
      <Message id={messageId} error={error} hint={hint} />
    </div>
  );
}

export function TextAreaField({
  label,
  hint,
  error,
  counter,
  className,
  ...area
}: Common & { counter?: ReactNode } & Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'id'>) {
  const id = useId();
  const messageId = `${id}-message`;
  return (
    <div className={`${styles.field} ${className ?? ''}`}>
      <div className={own.labelRow}>
        <label htmlFor={id} className={styles.label}>
          {label}
        </label>
        {counter}
      </div>
      <div className={`${styles.control} ${own.areaControl} ${error ? styles.invalid : ''}`}>
        <textarea
          id={id}
          className={`${styles.input} ${own.area}`}
          aria-invalid={error ? true : undefined}
          aria-describedby={error || hint ? messageId : undefined}
          {...area}
        />
      </div>
      <Message id={messageId} error={error} hint={hint} />
    </div>
  );
}

/** Contador "120/500"; fica no tom de erro quando passa do limite. */
export function CharCounter({ used, max }: { used: number; max: number }) {
  const over = used > max;
  return (
    <span className={`${own.counter} ${over ? own.over : ''}`} aria-live="polite">
      <span className="visually-hidden">{over ? 'Passou do limite: ' : 'Caracteres usados: '}</span>
      {used}/{max}
    </span>
  );
}
