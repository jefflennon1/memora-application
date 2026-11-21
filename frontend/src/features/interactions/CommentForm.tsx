import { useId, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import type { Person } from '../../api/common';
import { COMMENT_TEXT_MAX, commentTextLength, type CommentVisibility, type WriteCommentRequest } from '../../api/interactions';
import { friendlyMessage } from '../../api/problems';
import { Button } from '../../components/Button';
import { CharCounter } from '../../components/Fields';
import type { Media } from '../../api/memories';
import { AudioField, discardAudio, type AudioDraft } from '../audio/AudioField';
import styles from './Interactions.module.css';

/** "@ana" logo antes do cursor: o trecho que está sendo digitado como menção. */
function mentionQuery(text: string, caret: number): { query: string; start: number } | null {
  const match = /(^|\s)@([a-z0-9._]{0,30})$/i.exec(text.slice(0, caret));
  if (!match) return null;
  return { query: match[2]!.toLowerCase(), start: caret - match[2]!.length - 1 };
}

/**
 * Escrever ou editar um comentário/resposta (até 200 caracteres, emoji conta 1).
 * Ao digitar "@", sugere participantes do evento: só quem participa vira menção na API.
 */
export function CommentForm({
  label,
  submitLabel,
  eventId,
  initialText = '',
  initialAudio,
  initialVisibility = 'PARTICIPANTS_ONLY',
  allowPublic,
  people = [],
  pending,
  error,
  autoFocus,
  onSubmit,
  onCancel,
}: {
  label: string;
  submitLabel: string;
  eventId: string;
  initialText?: string | null;
  initialAudio?: Media | null;
  initialVisibility?: CommentVisibility;
  /** Comentário público só em memória pública de evento público; resposta pública só sob comentário público. */
  allowPublic: boolean;
  people?: Person[];
  pending?: boolean;
  error?: unknown;
  autoFocus?: boolean;
  onSubmit: (body: WriteCommentRequest) => void;
  onCancel?: () => void;
}) {
  const id = useId();
  const area = useRef<HTMLTextAreaElement>(null);
  const [text, setText] = useState(initialText ?? '');
  const [audio, setAudio] = useState<AudioDraft | null>(initialAudio ? { media: initialAudio, fresh: false } : null);
  const [visibility, setVisibility] = useState<CommentVisibility>(allowPublic ? initialVisibility : 'PARTICIPANTS_ONLY');
  const [caret, setCaret] = useState((initialText ?? '').length);
  const [active, setActive] = useState(0);
  const length = commentTextLength(text);
  const tooLong = length > COMMENT_TEXT_MAX;

  const typing = mentionQuery(text, caret);
  const suggestions = typing
    ? people
        .filter((p) => !p.profileHidden && p.username)
        .filter(
          (p) =>
            p.username!.startsWith(typing.query) ||
            (p.displayName ?? '').toLowerCase().split(/\s+/).some((w) => w.startsWith(typing.query)),
        )
        .slice(0, 5)
    : [];

  function insert(person: Person) {
    if (!typing) return;
    const before = text.slice(0, typing.start);
    const after = text.slice(caret);
    const mention = `@${person.username} `;
    const next = `${before}${mention}${after}`;
    setText(next);
    const position = before.length + mention.length;
    setCaret(position);
    requestAnimationFrame(() => {
      area.current?.focus();
      area.current?.setSelectionRange(position, position);
    });
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (!suggestions.length) return;
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      const delta = event.key === 'ArrowDown' ? 1 : -1;
      setActive((i) => (i + delta + suggestions.length) % suggestions.length);
    } else if (event.key === 'Enter' || event.key === 'Tab') {
      event.preventDefault();
      insert(suggestions[Math.min(active, suggestions.length - 1)]!);
    } else if (event.key === 'Escape') {
      setCaret(-1);
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    const trimmed = text.trim();
    if ((!trimmed && !audio) || tooLong) return;
    const body: WriteCommentRequest = { text: trimmed || (initialText ? '' : undefined), visibility };
    if (audio?.fresh) body.audioId = audio.media.id;
    if (initialAudio && !audio) body.removeAudio = true;
    onSubmit(body);
  }

  const listId = `${id}-people`;
  return (
    <form className={styles.commentForm} onSubmit={submit} noValidate>
      <div className={styles.commentField}>
        <label htmlFor={id} className="visually-hidden">
          {label}
        </label>
        <textarea
          ref={area}
          id={id}
          className={styles.commentInput}
          value={text}
          placeholder={label}
          rows={2}
          autoFocus={autoFocus}
          onChange={(e) => {
            setText(e.target.value);
            setCaret(e.target.selectionStart ?? e.target.value.length);
            setActive(0);
          }}
          onSelect={(e) => setCaret(e.currentTarget.selectionStart ?? 0)}
          onKeyDown={onKeyDown}
          aria-invalid={tooLong || undefined}
          aria-autocomplete="list"
          aria-controls={suggestions.length ? listId : undefined}
          aria-activedescendant={suggestions.length ? `${listId}-${active}` : undefined}
        />
        {suggestions.length > 0 && (
          <ul id={listId} className={styles.suggestions} role="listbox" aria-label="Mencionar participante">
            {suggestions.map((person, i) => (
              <li
                key={person.id}
                id={`${listId}-${i}`}
                role="option"
                aria-selected={i === active}
                className={styles.suggestion}
                onMouseDown={(e) => {
                  e.preventDefault();
                  insert(person);
                }}
              >
                <span className={styles.suggestionName}>{person.displayName}</span>
                <span className={styles.suggestionHandle}>@{person.username}</span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <AudioField eventId={eventId} value={audio} onChange={setAudio} disabled={pending} />

      <div className={styles.commentFormBar}>
        <CharCounter used={length} max={COMMENT_TEXT_MAX} />
        {allowPublic && (
          <label className={styles.publicToggle}>
            <input
              type="checkbox"
              checked={visibility === 'PUBLIC'}
              onChange={(e) => setVisibility(e.target.checked ? 'PUBLIC' : 'PARTICIPANTS_ONLY')}
            />
            Público
          </label>
        )}
        <span className={styles.grow} />
        {onCancel && (
          <Button variant="quiet" onClick={() => { discardAudio(audio?.fresh ? audio : null); onCancel(); }} disabled={pending}>
            Cancelar
          </Button>
        )}
        <Button type="submit" loading={pending} disabled={(!text.trim() && !audio) || tooLong}>
          {submitLabel}
        </Button>
      </div>
      {tooLong && <p className={styles.error}>Use até {COMMENT_TEXT_MAX} caracteres.</p>}
      {!!error && (
        <p className={styles.error} role="alert">
          {friendlyMessage(error)}
        </p>
      )}
    </form>
  );
}
