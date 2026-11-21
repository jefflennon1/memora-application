import { useState, type ReactNode } from 'react';
import type { Person } from '../api/common';
import { personName } from '../lib/format';
import { Avatar } from './Avatar';
import { Button } from './Button';
import styles from './Bits.module.css';

/* Peças pequenas reaproveitadas pelas telas de eventos, convites e memórias. */

export function Tag({ tone = 'neutral', children }: { tone?: 'neutral' | 'primary' | 'accent' | 'muted'; children: ReactNode }) {
  return <span className={`${styles.tag} ${styles[tone]}`}>{children}</span>;
}

/** Pessoa com foto, nome e @usuário. Perfis ocultos aparecem como "Perfil reservado". */
export function PersonLine({ person, size = 40, extra }: { person: Person | null | undefined; size?: number; extra?: ReactNode }) {
  const name = personName(person);
  const visible = person && !person.profileHidden;
  return (
    <span className={styles.person}>
      <Avatar name={visible ? name : '·'} src={visible ? person.photoUrl : null} size={size} />
      <span className={styles.personText}>
        <span className={styles.personName}>{name}</span>
        {visible && person.username && <span className={styles.personHandle}>@{person.username}</span>}
        {extra}
      </span>
    </span>
  );
}

export function EmptyState({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className={styles.empty}>
      <p className={styles.emptyTitle}>{title}</p>
      {children && <div className={styles.emptyText}>{children}</div>}
      {action}
    </div>
  );
}

/** Botão que pede confirmação em linha antes de executar uma ação destrutiva. */
export function ConfirmAction({
  label,
  question,
  confirmLabel,
  onConfirm,
  pending,
  variant = 'danger',
}: {
  label: string;
  question: ReactNode;
  confirmLabel?: string;
  onConfirm: () => void;
  pending?: boolean;
  variant?: 'danger' | 'outline' | 'quiet';
}) {
  const [asking, setAsking] = useState(false);
  if (!asking) {
    return (
      <Button variant={variant === 'danger' ? 'outline' : variant} onClick={() => setAsking(true)}>
        {label}
      </Button>
    );
  }
  return (
    <div className={styles.confirm} role="group" aria-label={`Confirmar: ${label}`}>
      <p>{question}</p>
      <div className={styles.confirmActions}>
        <Button variant={variant === 'danger' ? 'danger' : 'primary'} onClick={onConfirm} loading={pending}>
          {confirmLabel ?? label}
        </Button>
        <Button variant="quiet" onClick={() => setAsking(false)} disabled={pending}>
          Cancelar
        </Button>
      </div>
    </div>
  );
}

export function LoadMore({ hasNext, loading, onClick }: { hasNext: boolean; loading: boolean; onClick: () => void }) {
  if (!hasNext) return null;
  return (
    <div className={styles.more}>
      <Button variant="outline" onClick={onClick} loading={loading}>
        Carregar mais
      </Button>
    </div>
  );
}

export function SectionHeader({ title, action, id }: { title: string; action?: ReactNode; id?: string }) {
  return (
    <div className={styles.sectionHeader}>
      <h2 id={id} className={styles.sectionTitle}>
        {title}
      </h2>
      {action}
    </div>
  );
}
