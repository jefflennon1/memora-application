import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { apiAsset } from '../../api/config';
import type { MemoraEvent } from '../../api/events';
import { keys } from '../../api/keys';
import { memoriesApi, type Memory } from '../../api/memories';
import { friendlyMessage } from '../../api/problems';
import { ConfirmAction, PersonLine, Tag } from '../../components/Bits';
import { Button } from '../../components/Button';
import { ExpandableText } from '../../components/ExpandableText';
import { Notice } from '../../components/Notice';
import { useMe } from '../../auth/session';
import { formatDateTime, personName, timeAgo } from '../../lib/format';
import { Comments, CommentsToggle, type CommentEvent } from '../interactions/Comments';
import { Reactions } from '../interactions/Reactions';
import { Composer } from './Composer';
import { Lightbox } from './Lightbox';
import { AudioPlayer } from '../audio/AudioField';
import styles from './Memories.module.css';

/**
 * Uma memória no mural. O autor edita (com o mural aberto) e exclui (mesmo com o mural fechado).
 * `onDeleted`/`onUpdated` deixam a tela que mostra a memória reagir (ex.: voltar ao evento).
 */
export function MemoryCard({
  memory,
  event,
  onDeleted,
  onUpdated,
  commentsOpen = false,
}: {
  memory: Memory;
  event?: Pick<MemoraEvent, 'id' | 'privacy' | 'muralStatus' | 'myRole'>;
  onDeleted?: () => void;
  onUpdated?: (memory: Memory) => void;
  /** Abre os comentários de saída (na página da memória). */
  commentsOpen?: boolean;
}) {
  const client = useQueryClient();
  const { data: me } = useMe();
  const [editing, setEditing] = useState(false);
  const [showComments, setShowComments] = useState(commentsOpen);
  const [commentDelta, setCommentDelta] = useState(0);
  // A diferença local só vale até a API devolver a contagem atualizada (ex.: o mural recarregou).
  useEffect(() => setCommentDelta(0), [memory.commentCount]);
  const commentCount = Math.max(0, (memory.commentCount ?? 0) + commentDelta);
  const canInteract = !!event?.myRole && event.muralStatus === 'OPEN';
  const [viewing, setViewing] = useState<number | null>(null);
  const author = personName(memory.author);
  const muralOpen = event?.muralStatus === 'OPEN';
  const images = memory.media.filter((media) => media.kind === 'IMAGE');
  const videos = memory.media.filter((media) => media.kind === 'VIDEO');
  const audio = memory.media.find((media) => media.kind === 'AUDIO');

  const remove = useMutation({
    mutationFn: () => memoriesApi.remove(memory.id),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: keys.mural(memory.eventId) });
      client.removeQueries({ queryKey: keys.memory(memory.id) });
      onDeleted?.();
    },
  });

  if (editing && event) {
    return (
      <article className={styles.card} aria-label={`Editando memória de ${author}`}>
        <Composer
          eventId={event.id}
          eventIsPublic={event.privacy === 'PUBLIC'}
          initial={memory}
          onDone={(updated) => {
            setEditing(false);
            client.setQueryData(keys.memory(updated.id), updated);
            void client.invalidateQueries({ queryKey: keys.mural(memory.eventId) });
            onUpdated?.(updated);
          }}
          onCancel={() => setEditing(false)}
        />
      </article>
    );
  }

  return (
    <article className={styles.card} aria-label={`Memória de ${author}`}>
      <header className={styles.cardHead}>
        <PersonLine
          person={memory.author}
          size={40}
          extra={
            <span className={styles.meta}>
              <Link to={`/memories/${memory.id}`} title={formatDateTime(memory.createdAt)}>
                <time dateTime={memory.createdAt}>{timeAgo(memory.createdAt)}</time>
              </Link>
              {memory.editedAt && <span title={formatDateTime(memory.editedAt)}>, editada</span>}
            </span>
          }
        />
        <Tag tone={memory.visibility === 'PUBLIC' ? 'neutral' : 'muted'}>
          {memory.visibility === 'PUBLIC' ? 'Pública' : 'Participantes'}
        </Tag>
      </header>

      {memory.text && <ExpandableText text={memory.text} className={styles.text} />}

      {images.length > 0 && (
        <div className={styles.grid} data-count={Math.min(images.length, 5)}>
          {images.map((media, i) => (
            <button
              key={media.id}
              type="button"
              className={styles.thumb}
              onClick={() => setViewing(i)}
              aria-label={`Ver foto ${i + 1} de ${images.length} em tamanho grande`}
            >
              <img
                src={apiAsset(media.url)}
                alt=""
                loading="lazy"
                decoding="async"
                width={media.width ?? undefined}
                height={media.height ?? undefined}
                style={images.length === 1 && media.width && media.height ? { aspectRatio: `${media.width} / ${media.height}` } : undefined}
              />
            </button>
          ))}
        </div>
      )}

      {videos.map((video) => (
        <div key={video.id} className={styles.videoPlayer}>
          <video
            controls
            playsInline
            preload="metadata"
            src={apiAsset(video.url)}
            width={video.width ?? undefined}
            height={video.height ?? undefined}
          >
            Seu navegador não consegue reproduzir este vídeo.
          </video>
          <span>Vídeo · até 1 minuto</span>
        </div>
      ))}

      {audio && <AudioPlayer media={audio} />}

      {(event || memory.mine) && (
        <div className={styles.engagement}>
          {event && (
            <>
              <Reactions
                memoryId={memory.id}
                initial={memory.reactions}
                canReact={canInteract}
                signedIn={!!me}
                onChange={(reactions) =>
                  client.setQueryData<Memory>(keys.memory(memory.id), (old) => (old ? { ...old, reactions } : old))
                }
              />
              {(commentCount > 0 || canInteract) && (
                <CommentsToggle count={commentCount} open={showComments} onClick={() => setShowComments((v) => !v)} />
              )}
            </>
          )}
          {memory.mine && (
            <details className={styles.moreActions}>
              <summary aria-label="Mais ações da memória" title="Mais ações">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <circle cx="5" cy="12" r="1.5" />
                  <circle cx="12" cy="12" r="1.5" />
                  <circle cx="19" cy="12" r="1.5" />
                </svg>
              </summary>
              <div className={styles.actionsMenu}>
                {muralOpen && (
                  <Button variant="quiet" onClick={() => setEditing(true)}>
                    Editar memória
                  </Button>
                )}
                <ConfirmAction
                  label="Excluir memória"
                  variant="quiet"
                  question="Excluir esta memória? As mídias dela também são apagadas, e isso não pode ser desfeito."
                  onConfirm={() => remove.mutate()}
                  pending={remove.isPending}
                />
              </div>
            </details>
          )}
        </div>
      )}
      {event && showComments && (
        <Comments memory={memory} event={event as CommentEvent} onCountChange={(d) => setCommentDelta((n) => n + d)} />
      )}

      {remove.isError && <Notice tone="error" title="Não deu para excluir">{friendlyMessage(remove.error)}</Notice>}

      <Lightbox
        media={images}
        index={viewing}
        onIndex={setViewing}
        onClose={() => setViewing(null)}
        label={`Fotos da memória de ${author}`}
      />
    </article>
  );
}
