import { useMutation } from '@tanstack/react-query';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { apiAsset } from '../../api/config';
import {
  MEMORY_MEDIA_MAX,
  MEMORY_TEXT_MAX,
  mediaApi,
  memoriesApi,
  memoryTextLength,
  type Media,
  type Memory,
  type MemoryVisibility,
  type UpdateMemoryRequest,
} from '../../api/memories';
import { friendlyMessage, messageForCode } from '../../api/problems';
import { Button } from '../../components/Button';
import { ChoiceGroup } from '../../components/ChoiceGroup';
import { CharCounter, TextAreaField } from '../../components/Fields';
import { ImageCropDialog, VideoCropDialog } from '../../components/ImageCropDialog';
import { Notice } from '../../components/Notice';
import { prepareImage } from '../../lib/images';
import { inspectVideo, videoErrorMessage } from '../../lib/video';
import { AudioField, discardAudio, type AudioDraft } from '../audio/AudioField';
import styles from './Memories.module.css';

interface Item {
  key: string;
  kind: 'IMAGE' | 'VIDEO';
  preview: string | null;
  status: 'uploading' | 'ready' | 'error';
  media?: Media;
  error?: string;
  /** Enviada nesta edição (ainda pendente na API): ao tirar da lista, é descartada. */
  fresh: boolean;
  objectUrl?: string;
}

const VISIBILITIES = [
  { value: 'PARTICIPANTS_ONLY' as MemoryVisibility, title: 'Só participantes', description: 'Quem participa do evento.' },
  { value: 'PUBLIC' as MemoryVisibility, title: 'Pública', description: 'Qualquer pessoa que veja o evento.' },
];

let counter = 0;
const nextKey = () => `item-${++counter}`;

function fromMedia(media: Media): Item {
  return { key: media.id, kind: media.kind as 'IMAGE' | 'VIDEO', preview: apiAsset(media.url), status: 'ready', media, fresh: false };
}

/**
 * Publica (ou edita) uma memória: texto de até 500 caracteres e até 10 mídias.
 * Fotos passam pelo recorte e pela remoção de metadados; vídeos são validados antes do envio.
 */
export function Composer({
  eventId,
  eventIsPublic,
  initial,
  onDone,
  onCancel,
}: {
  eventId: string;
  eventIsPublic: boolean;
  initial?: Memory;
  onDone: (memory: Memory) => void;
  onCancel?: () => void;
}) {
  const [text, setText] = useState(initial?.text ?? '');
  const [items, setItems] = useState<Item[]>(() => initial?.media.filter((m) => m.kind !== 'AUDIO').map(fromMedia) ?? []);
  const [cropQueue, setCropQueue] = useState<File[]>([]);
  const [videoDraft, setVideoDraft] = useState<File | null>(null);
  const [audio, setAudio] = useState<AudioDraft | null>(() => {
    const existing = initial?.media.find((m) => m.kind === 'AUDIO');
    return existing ? { media: existing, fresh: false } : null;
  });
  const [visibility, setVisibility] = useState<MemoryVisibility>(initial?.visibility ?? 'PARTICIPANTS_ONLY');
  const [overflow, setOverflow] = useState(false);
  const [emptyTried, setEmptyTried] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const videoInputRef = useRef<HTMLInputElement>(null);
  const itemsRef = useRef(items);
  itemsRef.current = items;

  // Libera as prévias locais ao sair.
  useEffect(() => () => itemsRef.current.forEach((i) => i.objectUrl && URL.revokeObjectURL(i.objectUrl)), []);

  const length = memoryTextLength(text);
  const uploading = cropQueue.length > 0 || !!videoDraft || items.some((i) => i.status === 'uploading');
  const ready = items.filter((i) => i.status === 'ready' && i.media);
  const hasVideo = !!videoDraft || items.some((i) => i.kind === 'VIDEO');
  const mediaCount = items.length + cropQueue.length + (videoDraft ? 1 : 0) + (audio ? 1 : 0);
  const tooLong = length > MEMORY_TEXT_MAX;
  const isEmpty = length === 0 && ready.length === 0 && !audio;

  const update = (key: string, patch: Partial<Item>) =>
    setItems((list) => list.map((i) => (i.key === key ? { ...i, ...patch } : i)));

  async function uploadImage(key: string, file: File) {
    try {
      const prepared = await prepareImage(file);
      const media = await mediaApi.upload(eventId, prepared.blob, prepared.name);
      update(key, { status: 'ready', media });
    } catch (error) {
      update(key, { status: 'error', error: friendlyMessage(error) });
    }
  }

  function addPhotoFiles(files: FileList | null) {
    if (!files?.length) return;
    const room = MEMORY_MEDIA_MAX - mediaCount;
    const chosen = Array.from(files).slice(0, Math.max(0, room));
    setOverflow(files.length > room);
    setCropQueue((queue) => [...queue, ...chosen]);
    if (inputRef.current) inputRef.current.value = '';
  }

  function acceptPhoto(file: File) {
    const objectUrl = URL.createObjectURL(file);
    const item: Item = { key: nextKey(), kind: 'IMAGE', preview: objectUrl, objectUrl, status: 'uploading', fresh: true };
    setCropQueue((queue) => queue.slice(1));
    setItems((list) => [...list, item]);
    void uploadImage(item.key, file);
  }

  function skipPhoto() {
    setCropQueue((queue) => queue.slice(1));
  }

  async function uploadVideo(key: string, file: File) {
    try {
      await inspectVideo(file);
      const media = await mediaApi.upload(eventId, file, file.name || 'video.mp4');
      update(key, { status: 'ready', media });
    } catch (error) {
      const message = error instanceof Error && error.message.startsWith('VIDEO_')
        ? videoErrorMessage(error)
        : friendlyMessage(error);
      update(key, { status: 'error', error: message });
    }
  }

  function acceptVideo(file: File) {
    const objectUrl = URL.createObjectURL(file);
    const item: Item = { key: nextKey(), kind: 'VIDEO', preview: objectUrl, objectUrl, status: 'uploading', fresh: true };
    setVideoDraft(null);
    setItems((list) => [...list, item]);
    void uploadVideo(item.key, file);
  }

  function addVideo(file: File | undefined) {
    if (!file || hasVideo || mediaCount >= MEMORY_MEDIA_MAX) return;
    setVideoDraft(file);
    if (videoInputRef.current) videoInputRef.current.value = '';
  }

  function skipVideo() {
    setVideoDraft(null);
  }

  function removeItem(item: Item) {
    if (item.fresh && item.media) void mediaApi.discard(item.media.id).catch(() => undefined);
    if (item.objectUrl) URL.revokeObjectURL(item.objectUrl);
    setItems((list) => list.filter((i) => i.key !== item.key));
    setOverflow(false);
  }

  function reset() {
    items.forEach((i) => {
      if (i.fresh && i.media) void mediaApi.discard(i.media.id).catch(() => undefined);
      if (i.objectUrl) URL.revokeObjectURL(i.objectUrl);
    });
    discardAudio(audio);
    setText('');
    setItems([]);
    setCropQueue([]);
    setVideoDraft(null);
    setAudio(null);
    setVisibility('PARTICIPANTS_ONLY');
    setOverflow(false);
    setEmptyTried(false);
  }

  const save = useMutation({
    mutationFn: async () => {
      const mediaIds = [...ready.map((i) => i.media!.id), ...(audio ? [audio.media.id] : [])];
      const trimmed = text.trim();
      if (!initial) {
        return memoriesApi.publish(eventId, {
          text: trimmed || undefined,
          mediaIds: mediaIds.length ? mediaIds : undefined,
          visibility,
        });
      }
      const body: UpdateMemoryRequest = {};
      if (trimmed !== (initial.text ?? '')) body.text = trimmed || null;
      if (mediaIds.join() !== initial.media.map((m) => m.id).join()) body.mediaIds = mediaIds;
      if (visibility !== initial.visibility) body.visibility = visibility;
      if (Object.keys(body).length === 0) return initial;
      return memoriesApi.update(initial.id, body);
    },
    onSuccess: (memory) => {
      items.forEach((i) => i.objectUrl && URL.revokeObjectURL(i.objectUrl));
      if (!initial) {
        setText('');
        setItems([]);
        setCropQueue([]);
        setAudio(null);
        setVisibility('PARTICIPANTS_ONLY');
        setEmptyTried(false);
      }
      onDone(memory);
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    if (isEmpty) {
      setEmptyTried(true);
      return;
    }
    if (tooLong || uploading) return;
    save.mutate();
  }

  const dirty = text.length > 0 || items.length > 0 || cropQueue.length > 0 || !!videoDraft || !!audio;

  return (
    <form className={styles.composer} onSubmit={submit} noValidate aria-label={initial ? 'Editar memória' : 'Escrever uma memória'}>
      <TextAreaField
        label={initial ? 'Texto da memória' : 'Nova memória'}
        value={text}
        onChange={(e) => {
          setText(e.target.value);
          setEmptyTried(false);
        }}
        placeholder="Conte como foi este momento…"
        rows={3}
        counter={<CharCounter used={length} max={MEMORY_TEXT_MAX} />}
        error={tooLong ? `Use até ${MEMORY_TEXT_MAX} caracteres.` : emptyTried ? messageForCode('MEMORY_EMPTY') : null}
      />

      {items.length > 0 && (
        <ul className={styles.uploads} aria-label="Mídias da memória">
          {items.map((item, i) => (
            <li key={item.key} className={styles.upload} data-status={item.status}>
              {item.preview ? (
                item.kind === 'VIDEO'
                  ? <video src={item.preview} muted playsInline preload="metadata" aria-label="Prévia do vídeo" />
                  : <img src={item.preview} alt="" />
              ) : <span className={styles.uploadPlaceholder} aria-hidden="true" />}
              <span className={styles.uploadStatus} role={item.status === 'error' ? 'alert' : undefined}>
                {item.status === 'uploading'
                  ? item.kind === 'VIDEO' ? 'Validando e enviando vídeo…' : 'Enviando…'
                  : item.status === 'error' ? item.error : item.kind === 'VIDEO' ? 'Vídeo pronto' : `Foto ${i + 1}`}
              </span>
              <button
                type="button"
                className={styles.uploadRemove}
                onClick={() => removeItem(item)}
                aria-label={item.kind === 'VIDEO' ? 'Tirar vídeo' : `Tirar foto ${i + 1}`}
                disabled={item.status === 'uploading'}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
      {overflow && <p className={styles.hint}>Cada memória pode ter até {MEMORY_MEDIA_MAX} mídias. As demais ficaram de fora.</p>}

      <div className={styles.mediaTools} role="group" aria-label="Adicionar mídia">
        <label className={`${styles.mediaTool} ${mediaCount >= MEMORY_MEDIA_MAX ? styles.disabled : ''}`}>
          <input
            ref={inputRef}
            type="file"
            accept="image/jpeg,image/png,image/webp,image/gif,image/*"
            multiple
            className="visually-hidden"
            aria-label="Adicionar fotos"
            disabled={mediaCount >= MEMORY_MEDIA_MAX}
            onChange={(e) => addPhotoFiles(e.target.files)}
          />
          <PhotoIcon />
          <span>Fotos</span>
        </label>
        <label className={`${styles.mediaTool} ${hasVideo || mediaCount >= MEMORY_MEDIA_MAX ? styles.disabled : ''}`}>
          <input
            ref={videoInputRef}
            type="file"
            accept="video/mp4,video/quicktime,.mp4,.mov"
            className="visually-hidden"
            aria-label="Adicionar vídeo"
            disabled={hasVideo || mediaCount >= MEMORY_MEDIA_MAX}
            onChange={(e) => addVideo(e.target.files?.[0])}
          />
          <VideoIcon />
          <span>Vídeo</span>
        </label>
        <AudioField
          eventId={eventId}
          value={audio}
          onChange={setAudio}
          disabled={!audio && mediaCount >= MEMORY_MEDIA_MAX}
          compactTrigger
          triggerClassName={styles.mediaTool}
        />
      </div>

      {eventIsPublic ? (
        <ChoiceGroup legend="Quem vê esta memória" name={`visibility-${initial?.id ?? 'new'}`} value={visibility} onChange={setVisibility} choices={VISIBILITIES} />
      ) : (
        <p className={styles.hint}>Só quem participa do evento vê esta memória.</p>
      )}

      {save.isError && (
        <Notice tone="error" title={initial ? 'Não deu para salvar' : 'Não deu para publicar'}>
          {friendlyMessage(save.error)}
        </Notice>
      )}

      <div className={styles.composerActions}>
        <span className={styles.spacer} />
        {(onCancel || (!initial && dirty)) && (
          <Button
            variant="quiet"
            onClick={() => {
              if (initial) {
                items.forEach((i) => i.fresh && i.media && void mediaApi.discard(i.media.id).catch(() => undefined));
                discardAudio(audio?.fresh ? audio : null);
                onCancel?.();
              } else reset();
            }}
            disabled={save.isPending}
          >
            {initial ? 'Cancelar' : 'Descartar'}
          </Button>
        )}
        <Button type="submit" loading={save.isPending} disabled={uploading || tooLong}>
          {uploading ? 'Preparando mídias…' : initial ? 'Salvar' : 'Publicar'}
        </Button>
      </div>
      {cropQueue[0] && (
        <ImageCropDialog
          file={cropQueue[0]}
          showAspectRatios
          title={cropQueue.length > 1 ? `Recortar foto · ${cropQueue.length} restantes` : 'Recortar foto'}
          onCancel={skipPhoto}
          onApply={acceptPhoto}
        />
      )}
      {videoDraft && (
        <VideoCropDialog
          file={videoDraft}
          onCancel={skipVideo}
          onApply={acceptVideo}
        />
      )}
    </form>
  );
}

function PhotoIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <rect x="3" y="4" width="18" height="16" rx="3" />
      <circle cx="9" cy="9" r="1.8" />
      <path d="m5.5 17 4.2-4.2 3 2.8 2.2-2.2 3.6 3.6" />
    </svg>
  );
}

function VideoIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <rect x="3" y="5" width="13" height="14" rx="3" />
      <path d="m16 10 5-3v10l-5-3z" />
    </svg>
  );
}
