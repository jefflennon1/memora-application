import { useMutation } from '@tanstack/react-query';
import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { QUICK_REACTIONS, reactionsApi, type ReactionSummary } from '../../api/interactions';
import { friendlyMessage } from '../../api/problems';
import styles from './Interactions.module.css';

const EMPTY: ReactionSummary = { total: 0, items: [] };

/** "❤" e "❤️" são o mesmo emoji para a API; compara sem o seletor de variação. */
const same = (a?: string | null, b?: string | null) =>
  !!a && !!b && a.replace(/️/g, '') === b.replace(/️/g, '');

/**
 * Reações de uma memória: contagem por emoji (mais usados primeiro) e a sua, nunca quem reagiu.
 * - `canReact`: participante com o mural aberto (reagir ou trocar).
 * - Remover a própria reação vale sempre, mesmo com o mural fechado.
 */
export function Reactions({
  memoryId,
  initial,
  canReact,
  signedIn,
  onChange,
}: {
  memoryId: string;
  initial?: ReactionSummary;
  canReact: boolean;
  signedIn: boolean;
  onChange?: (summary: ReactionSummary) => void;
}) {
  const [summary, setSummary] = useState<ReactionSummary>(initial ?? EMPTY);
  const [picking, setPicking] = useState(false);
  const holdTimer = useRef<number | null>(null);
  const heldOpen = useRef(false);
  useEffect(() => setSummary(initial ?? EMPTY), [initial]);
  useEffect(() => () => {
    if (holdTimer.current) window.clearTimeout(holdTimer.current);
  }, []);

  const apply = (next: ReactionSummary) => {
    setSummary(next);
    onChange?.(next);
  };
  const react = useMutation({ mutationFn: (emoji: string) => reactionsApi.react(memoryId, emoji), onSuccess: apply });
  const remove = useMutation({ mutationFn: () => reactionsApi.remove(memoryId), onSuccess: apply });
  const busy = react.isPending || remove.isPending;
  const error = react.error ?? remove.error;

  function choose(emoji: string) {
    setPicking(false);
    if (same(emoji, summary.mine)) remove.mutate();
    else react.mutate(emoji);
  }

  function stopHold() {
    if (holdTimer.current) window.clearTimeout(holdTimer.current);
    holdTimer.current = null;
  }

  function startHold() {
    if (!canReact || busy) return;
    stopHold();
    heldOpen.current = false;
    holdTimer.current = window.setTimeout(() => {
      heldOpen.current = true;
      setPicking(true);
    }, 420);
  }

  function activate() {
    if (heldOpen.current) {
      heldOpen.current = false;
      return;
    }
    if (canReact) setPicking((value) => !value);
    else if (summary.mine) remove.mutate();
  }

  if (summary.total === 0 && !canReact) return null;

  return (
    <div className={styles.reactions}>
      {canReact || summary.mine ? (
        <button
          type="button"
          className={styles.reactionAction}
          aria-expanded={canReact ? picking : undefined}
          aria-pressed={!!summary.mine}
          aria-label={
            canReact
              ? summary.mine
                ? `Trocar reação. ${summary.total} ${summary.total === 1 ? 'reação' : 'reações'}. Sua reação: ${summary.mine}`
                : 'Reagir'
              : `${summary.total} ${summary.total === 1 ? 'reação' : 'reações'}. Tirar a sua reação ${summary.mine}`
          }
          onPointerDown={startHold}
          onPointerUp={stopHold}
          onPointerCancel={stopHold}
          onPointerLeave={stopHold}
          onClick={activate}
          disabled={busy}
          title={canReact ? 'Clique ou segure para escolher uma reação' : 'Tirar sua reação'}
        >
          <HeartIcon filled={!!summary.mine} />
          {summary.total > 0 && <span className={styles.actionCount}>{summary.total}</span>}
          {summary.mine && <span className={styles.myReaction} aria-hidden="true">{summary.mine}</span>}
        </button>
      ) : (
        <span
          className={styles.reactionAction}
          aria-label={`${summary.total} ${summary.total === 1 ? 'reação' : 'reações'}: ${summary.items.map((item) => `${item.emoji} ${item.count}`).join(', ')}`}
        >
          <HeartIcon filled={false} />
          <span className={styles.actionCount}>{summary.total}</span>
        </span>
      )}
      {picking && <EmojiPicker current={summary.mine} onPick={choose} onClose={() => setPicking(false)} />}
      {!canReact && !signedIn && summary.total > 0 && <span className="visually-hidden">Entre para reagir.</span>}
      {error && (
        <p className={styles.error} role="alert">
          {friendlyMessage(error)}
        </p>
      )}
    </div>
  );
}

function HeartIcon({ filled }: { filled: boolean }) {
  return (
    <svg className={styles.actionIcon} viewBox="0 0 24 24" aria-hidden="true">
      <path
        d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8l1.1 1.1L12 21l7.8-7.5 1.1-1.1a5.5 5.5 0 0 0-.1-7.8Z"
        fill={filled ? 'currentColor' : 'none'}
      />
    </svg>
  );
}

function EmojiPicker({
  current,
  onPick,
  onClose,
}: {
  current?: string | null;
  onPick: (emoji: string) => void;
  onClose: () => void;
}) {
  const [other, setOther] = useState('');
  const id = useId();
  const first = useRef<HTMLButtonElement>(null);
  useEffect(() => first.current?.focus(), []);

  function submit(event: FormEvent) {
    event.preventDefault();
    const value = other.trim();
    if (value) onPick(value);
  }

  return (
    <div className={styles.picker} role="group" aria-label="Escolha uma reação" onKeyDown={(e) => e.key === 'Escape' && onClose()}>
      <div className={styles.pickerRow}>
        {QUICK_REACTIONS.map((emoji, i) => (
          <button
            key={emoji}
            ref={i === 0 ? first : undefined}
            type="button"
            className={styles.pickerEmoji}
            aria-pressed={same(emoji, current)}
            aria-label={`Reagir com ${emoji}`}
            onClick={() => onPick(emoji)}
          >
            {emoji}
          </button>
        ))}
      </div>
      <form className={styles.pickerOther} onSubmit={submit}>
        <label htmlFor={id} className={styles.pickerLabel}>
          Outro emoji
        </label>
        <input
          id={id}
          value={other}
          onChange={(e) => setOther(e.target.value)}
          className={styles.pickerInput}
          maxLength={16}
          autoComplete="off"
          inputMode="text"
          placeholder="🎂"
        />
        <button type="submit" className={styles.pickerSend} disabled={!other.trim()}>
          Reagir
        </button>
      </form>
    </div>
  );
}
