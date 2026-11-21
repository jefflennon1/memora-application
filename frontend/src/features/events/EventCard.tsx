import { Link } from 'react-router';
import type { MemoraEvent } from '../../api/events';
import { Tag } from '../../components/Bits';
import { EVENT_PRIVACY_LABEL, MODALITY_LABEL, ROLE_LABEL, dateBlock, formatEventWhen } from '../../lib/format';
import styles from './EventCard.module.css';

/** Bloco de calendário: faixa rosa com o mês, o dia em destaque e o dia da semana. */
export function DateBlock({ date, size = 'md' }: { date: string; size?: 'md' | 'lg' }) {
  const d = dateBlock(date);
  return (
    <span className={`${styles.date} ${styles[size]}`} aria-hidden="true">
      <span className={styles.month}>{d.month}</span>
      <span className={styles.day}>{d.day}</span>
      <span className={styles.weekday}>{d.weekday}</span>
    </span>
  );
}

export function EventCard({ event }: { event: MemoraEvent }) {
  const place = event.modality === 'IN_PERSON' ? event.city || MODALITY_LABEL.IN_PERSON : MODALITY_LABEL[event.modality];
  return (
    <Link to={`/events/${event.id}`} className={styles.card}>
      <DateBlock date={event.startDate} />
      <span className={styles.body}>
        <span className={styles.name}>{event.name}</span>
        <span className={styles.when}>{formatEventWhen(event)}</span>
        <span className={styles.tags}>
          <Tag tone="muted">{place}</Tag>
          {event.category && <Tag>{event.category.name}</Tag>}
          <Tag tone={event.privacy === 'PUBLIC' ? 'neutral' : 'muted'}>{EVENT_PRIVACY_LABEL[event.privacy]}</Tag>
          {event.myRole && event.myRole !== 'PARTICIPANT' && <Tag tone="primary">{ROLE_LABEL[event.myRole]}</Tag>}
          {event.muralStatus === 'CLOSED' && <Tag tone="accent">Mural fechado</Tag>}
        </span>
      </span>
    </Link>
  );
}
