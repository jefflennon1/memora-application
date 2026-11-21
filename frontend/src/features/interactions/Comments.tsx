import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import type { Person } from '../../api/common';
import type { MemoraEvent } from '../../api/events';
import { commentsApi, type Comment, type WriteCommentRequest } from '../../api/interactions';
import { keys } from '../../api/keys';
import { participantsApi } from '../../api/membership';
import type { Memory } from '../../api/memories';
import { friendlyMessage } from '../../api/problems';
import { ConfirmAction, LoadMore } from '../../components/Bits';
import { Avatar } from '../../components/Avatar';
import { formatDateTime, personName, timeAgo } from '../../lib/format';
import { usePaged } from '../../lib/usePaged';
import { CommentForm } from './CommentForm';
import { MentionText } from './MentionText';
import { AudioPlayer } from '../audio/AudioField';
import styles from './Interactions.module.css';

export type CommentEvent = Pick<MemoraEvent, 'id' | 'privacy' | 'muralStatus' | 'myRole'>;

/** Participantes do evento, para sugerir @menções (só participantes veem a lista). */
function usePeople(event: CommentEvent, enabled: boolean): Person[] {
  const people = usePaged(keys.participants(event.id), (p, signal) => participantsApi.list(event.id, { page: p, size: 30 }, signal), {
    enabled,
  });
  return people.items.map((p) => p.person);
}

/**
 * Comentários de uma memória (só os principais; as respostas abrem sob demanda).
 * `onCountChange` mantém o "N comentários" do cartão em dia sem recarregar o mural.
 */
export function Comments({
  memory,
  event,
  onCountChange,
}: {
  memory: Memory;
  event: CommentEvent;
  onCountChange: (delta: number) => void;
}) {
  const client = useQueryClient();
  const canWrite = !!event.myRole && event.muralStatus === 'OPEN';
  const people = usePeople(event, !!event.myRole);
  const list = usePaged(keys.comments(memory.id), (p, signal) => commentsApi.list(memory.id, { page: p, size: 20 }, signal));
  // Cada envio bem-sucedido limpa o formulário (nova chave); erros mantêm o texto digitado.
  const [sent, setSent] = useState(0);
  const create = useMutation({
    mutationFn: (body: WriteCommentRequest) => commentsApi.create(memory.id, body),
    onSuccess: () => {
      setSent((n) => n + 1);
      onCountChange(1);
      void client.invalidateQueries({ queryKey: keys.comments(memory.id) });
    },
  });
  // Comentário público só em memória pública de evento público (a API recusa nos demais casos).
  const allowPublic = memory.visibility === 'PUBLIC' && event.privacy === 'PUBLIC';

  return (
    <section className={styles.comments} aria-label="Comentários">
      {list.isError && <p className={styles.error}>{friendlyMessage(list.error)}</p>}
      {list.isPending && <p className={styles.muted}>Carregando comentários…</p>}
      {list.isSuccess && list.items.length === 0 && (
        <p className={styles.muted}>
          {canWrite
            ? 'Nenhum comentário ainda. Que tal ser a primeira pessoa?'
            : event.myRole
              ? 'Nenhum comentário.'
              : 'Nenhum comentário público.'}
        </p>
      )}
      <ul className={styles.commentList}>
        {list.items.map((comment) => (
          <CommentItem
            key={comment.id}
            comment={comment}
            memory={memory}
            event={event}
            canWrite={canWrite}
            people={people}
            onCountChange={onCountChange}
          />
        ))}
      </ul>
      <LoadMore hasNext={!!list.hasNextPage} loading={list.isFetchingNextPage} onClick={() => list.fetchNextPage()} />

      {canWrite && (
        <CommentForm
          key={sent}
          label="Escreva um comentário"
          submitLabel="Comentar"
          eventId={event.id}
          allowPublic={allowPublic}
          people={people}
          pending={create.isPending}
          error={create.error}
          onSubmit={(body) => create.mutate(body)}
        />
      )}
      {!canWrite && event.myRole && event.muralStatus === 'CLOSED' && (
        <p className={styles.muted}>O mural está fechado: não dá para comentar.</p>
      )}
      {!event.myRole && <p className={styles.muted}>Só quem participa do evento comenta.</p>}
    </section>
  );
}

function CommentItem({
  comment,
  memory,
  event,
  canWrite,
  people,
  onCountChange,
  parent,
}: {
  comment: Comment;
  memory: Memory;
  event: CommentEvent;
  canWrite: boolean;
  people: Person[];
  onCountChange: (delta: number) => void;
  /** Presente numa resposta. */
  parent?: Comment;
}) {
  const client = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [replying, setReplying] = useState(false);
  const [showReplies, setShowReplies] = useState(false);
  const isReply = !!parent;
  const author = personName(comment.author);
  const visible = !comment.author.profileHidden;

  const refresh = () => {
    void client.invalidateQueries({ queryKey: keys.comments(memory.id) });
    if (parent) void client.invalidateQueries({ queryKey: keys.replies(parent.id) });
  };

  const update = useMutation({
    mutationFn: (body: WriteCommentRequest) => commentsApi.update(comment.id, body),
    onSuccess: () => {
      setEditing(false);
      refresh();
    },
  });
  const remove = useMutation({
    mutationFn: () => commentsApi.remove(comment.id),
    onSuccess: () => {
      // Excluir um comentário principal apaga as respostas dele.
      onCountChange(-(1 + (isReply ? 0 : (comment.replyCount ?? 0))));
      client.removeQueries({ queryKey: keys.replies(comment.id) });
      refresh();
    },
  });
  const reply = useMutation({
    mutationFn: (body: WriteCommentRequest) => commentsApi.reply(comment.id, body),
    onSuccess: () => {
      onCountChange(1);
      setReplying(false);
      setShowReplies(true);
      void client.invalidateQueries({ queryKey: keys.replies(comment.id) });
      void client.invalidateQueries({ queryKey: keys.comments(memory.id) });
    },
  });

  const replyCount = comment.replyCount ?? 0;
  const replyPrefix = !comment.mine && visible && comment.author.username ? `@${comment.author.username} ` : '';
  // Resposta pública só sob comentário público.
  const allowPublicReply = comment.visibility === 'PUBLIC' && memory.visibility === 'PUBLIC' && event.privacy === 'PUBLIC';
  const allowPublicEdit = isReply
    ? parent!.visibility === 'PUBLIC'
    : memory.visibility === 'PUBLIC' && event.privacy === 'PUBLIC';

  return (
    <li className={isReply ? styles.reply : styles.comment}>
      <Avatar name={visible ? author : '·'} src={visible ? comment.author.photoUrl : null} size={isReply ? 28 : 32} />
      <div className={styles.commentBody}>
        <div className={styles.commentHead}>
          <span className={styles.commentAuthor}>{author}</span>
          <time dateTime={comment.createdAt} title={formatDateTime(comment.createdAt)} className={styles.muted}>
            {timeAgo(comment.createdAt)}
          </time>
          {comment.editedAt && <span className={styles.muted}>editado</span>}
          {comment.visibility === 'PUBLIC' && <span className={styles.publicTag}>Público</span>}
        </div>

        {editing ? (
          <CommentForm
            label={isReply ? 'Editar resposta' : 'Editar comentário'}
            submitLabel="Salvar"
            eventId={event.id}
            initialText={comment.text}
            initialAudio={comment.audio}
            initialVisibility={comment.visibility}
            allowPublic={allowPublicEdit}
            people={people}
            pending={update.isPending}
            error={update.error}
            autoFocus
            onCancel={() => setEditing(false)}
            onSubmit={(body) => {
              const changes: WriteCommentRequest = { ...body };
              if ((body.text ?? '') === (comment.text ?? '')) delete changes.text;
              if (allowPublicEdit && body.visibility !== comment.visibility) changes.visibility = body.visibility;
              else delete changes.visibility;
              if (Object.keys(changes).length === 0) setEditing(false);
              else update.mutate(changes);
            }}
          />
        ) : (
          <>
            {comment.text && <MentionText text={comment.text} mentions={comment.mentions} />}
            {comment.audio && <AudioPlayer media={comment.audio} compact />}
          </>
        )}

        {!editing && (
          <div className={styles.commentActions}>
            {!isReply && canWrite && (
              <button type="button" className={styles.linkButton} onClick={() => setReplying((v) => !v)}>
                Responder
              </button>
            )}
            {comment.mine && canWrite && (
              <button type="button" className={styles.linkButton} onClick={() => setEditing(true)}>
                Editar
              </button>
            )}
            {comment.mine && (
              <ConfirmAction
                label="Excluir"
                variant="quiet"
                question={
                  !isReply && replyCount > 0
                    ? replyCount === 1
                      ? 'Excluir este comentário? A resposta dele também será apagada.'
                      : `Excluir este comentário? As ${replyCount} respostas dele também serão apagadas.`
                    : isReply
                      ? 'Excluir esta resposta?'
                      : 'Excluir este comentário?'
                }
                onConfirm={() => remove.mutate()}
                pending={remove.isPending}
              />
            )}
          </div>
        )}
        {remove.isError && <p className={styles.error}>{friendlyMessage(remove.error)}</p>}

        {!isReply && replyCount > 0 && (
          <button
            type="button"
            className={styles.repliesToggle}
            aria-expanded={showReplies}
            onClick={() => setShowReplies((v) => !v)}
          >
            {showReplies ? 'Ocultar respostas' : replyCount === 1 ? 'Ver 1 resposta' : `Ver ${replyCount} respostas`}
          </button>
        )}
        {!isReply && showReplies && (
          <Replies comment={comment} memory={memory} event={event} canWrite={canWrite} people={people} onCountChange={onCountChange} />
        )}
        {replying && (
          <CommentForm
            label={`Responder a ${author}`}
            submitLabel="Responder"
            eventId={event.id}
            initialText={replyPrefix}
            allowPublic={allowPublicReply}
            people={people}
            pending={reply.isPending}
            error={reply.error}
            autoFocus
            onCancel={() => setReplying(false)}
            onSubmit={(body) => reply.mutate(body)}
          />
        )}
      </div>
    </li>
  );
}

function Replies({
  comment,
  memory,
  event,
  canWrite,
  people,
  onCountChange,
}: {
  comment: Comment;
  memory: Memory;
  event: CommentEvent;
  canWrite: boolean;
  people: Person[];
  onCountChange: (delta: number) => void;
}) {
  const replies = usePaged(keys.replies(comment.id), (p, signal) => commentsApi.replies(comment.id, { page: p, size: 20 }, signal));
  return (
    <div className={styles.replies}>
      {replies.isError && <p className={styles.error}>{friendlyMessage(replies.error)}</p>}
      <ul className={styles.commentList}>
        {replies.items.map((r) => (
          <CommentItem
            key={r.id}
            comment={r}
            memory={memory}
            event={event}
            canWrite={canWrite}
            people={people}
            onCountChange={onCountChange}
            parent={comment}
          />
        ))}
      </ul>
      <LoadMore hasNext={!!replies.hasNextPage} loading={replies.isFetchingNextPage} onClick={() => replies.fetchNextPage()} />
    </div>
  );
}

/** Botão que abre/fecha os comentários, com a contagem que a pessoa pode ver. */
export function CommentsToggle({ count, open, onClick }: { count: number; open: boolean; onClick: () => void }) {
  const label = count === 0 ? 'Comentários' : count === 1 ? '1 comentário' : `${count} comentários`;
  return (
    <button
      type="button"
      className={styles.commentToggle}
      aria-label={label}
      aria-expanded={open}
      aria-pressed={open}
      onClick={onClick}
      title={open ? 'Ocultar comentários' : 'Ver comentários'}
    >
      <svg className={styles.actionIcon} viewBox="0 0 24 24" aria-hidden="true">
        <path d="M21 11.5a8.4 8.4 0 0 1-9 8.5 9.4 9.4 0 0 1-3.8-.8L3 21l1.7-4.4A8.5 8.5 0 1 1 21 11.5Z" fill="none" />
      </svg>
      {count > 0 && <span className={styles.actionCount}>{count}</span>}
    </button>
  );
}
