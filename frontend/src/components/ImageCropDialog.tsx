import { useCallback, useEffect, useRef, useState, type CSSProperties, type PointerEvent as ReactPointerEvent, type WheelEvent } from 'react';
import { VIDEO_MAX_DURATION_MS } from '../lib/video';
import { Button } from './Button';
import styles from './ImageCropDialog.module.css';

interface ImageCropDialogProps {
  file: File;
  title?: string;
  aspectRatio?: number;
  showAspectRatios?: boolean;
  onApply: (file: File) => void;
  onCancel: () => void;
}

interface CropRect { x: number; y: number; width: number; height: number }
interface SourceSize { width: number; height: number }
interface DragState { pointerId: number; x: number; y: number; horizontal: number; vertical: number }
type AspectChoice = 'original' | 'square' | 'portrait' | 'landscape';

const OUTPUT_MAX_SIDE = 3200;
const VIDEO_CLIP_MAX_SECONDS = VIDEO_MAX_DURATION_MS / 1000;
const ASPECTS: { value: AspectChoice; label: string; shortLabel: string; ratio?: number }[] = [
  { value: 'original', label: 'Proporção original', shortLabel: 'Original' },
  { value: 'square', label: 'Proporção quadrada 1 por 1', shortLabel: '1:1', ratio: 1 },
  { value: 'portrait', label: 'Proporção vertical 4 por 5', shortLabel: '4:5', ratio: 4 / 5 },
  { value: 'landscape', label: 'Proporção horizontal 16 por 9', shortLabel: '16:9', ratio: 16 / 9 },
];

export function ImageCropDialog(props: ImageCropDialogProps) {
  return <MediaCropDialog {...props} kind="image" />;
}

export function VideoCropDialog(props: Omit<ImageCropDialogProps, 'aspectRatio' | 'showAspectRatios'>) {
  return <MediaCropDialog {...props} kind="video" showAspectRatios />;
}

function MediaCropDialog({ file, kind, title = kind === 'video' ? 'Editar vídeo' : 'Recortar foto', aspectRatio, showAspectRatios = false, onApply, onCancel }: ImageCropDialogProps & { kind: 'image' | 'video' }) {
  const preview = useRef<HTMLCanvasElement>(null);
  const video = useRef<HTMLVideoElement>(null);
  const image = useRef<HTMLImageElement | null>(null);
  const drag = useRef<DragState | null>(null);
  const [objectUrl, setObjectUrl] = useState('');
  const [source, setSource] = useState<SourceSize | null>(null);
  const [duration, setDuration] = useState(0);
  const [clipStart, setClipStart] = useState(0);
  const [clipEnd, setClipEnd] = useState(0);
  const [aspectChoice, setAspectChoice] = useState<AspectChoice>('original');
  const [zoom, setZoom] = useState(1);
  const [horizontal, setHorizontal] = useState(50);
  const [vertical, setVertical] = useState(50);
  const [playing, setPlaying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [applying, setApplying] = useState(false);
  const [progress, setProgress] = useState(0);
  const originalAspect = source ? source.width / source.height : 1;
  const choiceRatio = ASPECTS.find((choice) => choice.value === aspectChoice)?.ratio;
  const targetAspect = aspectRatio ?? choiceRatio ?? originalAspect;

  useEffect(() => {
    const url = URL.createObjectURL(file);
    setObjectUrl(url);
    setSource(null);
    setDuration(0);
    setClipStart(0);
    setClipEnd(0);
    setZoom(1);
    setHorizontal(50);
    setVertical(50);
    setError(null);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  useEffect(() => {
    if (kind !== 'image' || !objectUrl) return;
    const loaded = new Image();
    loaded.onload = () => {
      image.current = loaded;
      setSource({ width: loaded.naturalWidth, height: loaded.naturalHeight });
    };
    loaded.onerror = () => setError('Não foi possível abrir esta foto para recorte.');
    loaded.src = objectUrl;
    return () => {
      image.current = null;
      loaded.onload = null;
      loaded.onerror = null;
    };
  }, [kind, objectUrl]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !applying) onCancel();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [applying, onCancel]);

  const renderFrame = useCallback(() => {
    const canvas = preview.current;
    const media = kind === 'image' ? image.current : video.current;
    if (!canvas || !media || !source) return;
    const longSide = 900;
    canvas.width = targetAspect >= 1 ? longSide : Math.max(1, Math.round(longSide * targetAspect));
    canvas.height = targetAspect >= 1 ? Math.max(1, Math.round(longSide / targetAspect)) : longSide;
    drawCrop(canvas, media, cropRect(source, targetAspect, zoom, horizontal, vertical));
  }, [horizontal, kind, source, targetAspect, vertical, zoom]);

  useEffect(() => { renderFrame(); }, [renderFrame]);

  useEffect(() => {
    if (kind !== 'video') return;
    const player = video.current;
    if (!player) return;
    let frame = 0;
    const paint = () => {
      renderFrame();
      if (!player.paused && !player.ended) frame = requestAnimationFrame(paint);
    };
    const onSeeked = () => renderFrame();
    const onPlay = () => { setPlaying(true); frame = requestAnimationFrame(paint); };
    const onPause = () => setPlaying(false);
    const onTimeUpdate = () => {
      if (clipEnd && player.currentTime >= clipEnd) {
        player.pause();
        player.currentTime = clipStart;
      }
    };
    player.addEventListener('seeked', onSeeked);
    player.addEventListener('loadeddata', onSeeked);
    player.addEventListener('play', onPlay);
    player.addEventListener('pause', onPause);
    player.addEventListener('timeupdate', onTimeUpdate);
    return () => {
      cancelAnimationFrame(frame);
      player.removeEventListener('seeked', onSeeked);
      player.removeEventListener('loadeddata', onSeeked);
      player.removeEventListener('play', onPlay);
      player.removeEventListener('pause', onPause);
      player.removeEventListener('timeupdate', onTimeUpdate);
    };
  }, [clipEnd, clipStart, kind, renderFrame]);

  function videoLoaded() {
    const player = video.current;
    if (!player) return;
    const nextDuration = Number.isFinite(player.duration) ? player.duration : 0;
    if (!player.videoWidth || !player.videoHeight || nextDuration <= 0) {
      setError('Não foi possível abrir este vídeo. Use um arquivo MP4 ou MOV válido.');
      return;
    }
    setSource({ width: player.videoWidth, height: player.videoHeight });
    setDuration(nextDuration);
    setClipStart(0);
    setClipEnd(Math.min(nextDuration, VIDEO_CLIP_MAX_SECONDS));
  }

  function chooseAspect(value: AspectChoice) {
    setAspectChoice(value);
    setZoom(1);
    setHorizontal(50);
    setVertical(50);
  }

  function changeZoom(value: number) { setZoom(clamp(value, 1, 3)); }

  function pointerDown(event: ReactPointerEvent<HTMLCanvasElement>) {
    if (!source) return;
    event.currentTarget.setPointerCapture(event.pointerId);
    drag.current = { pointerId: event.pointerId, x: event.clientX, y: event.clientY, horizontal, vertical };
  }

  function pointerMove(event: ReactPointerEvent<HTMLCanvasElement>) {
    const start = drag.current;
    const canvas = preview.current;
    if (!start || start.pointerId !== event.pointerId || !source || !canvas) return;
    const rect = cropRect(source, targetAspect, zoom, start.horizontal, start.vertical);
    const rangeX = source.width - rect.width;
    const rangeY = source.height - rect.height;
    const sourceDx = (event.clientX - start.x) * (rect.width / Math.max(1, canvas.clientWidth));
    const sourceDy = (event.clientY - start.y) * (rect.height / Math.max(1, canvas.clientHeight));
    if (rangeX > 0) setHorizontal(clamp(start.horizontal - (sourceDx / rangeX) * 100, 0, 100));
    if (rangeY > 0) setVertical(clamp(start.vertical - (sourceDy / rangeY) * 100, 0, 100));
  }

  function pointerUp(event: ReactPointerEvent<HTMLCanvasElement>) {
    if (drag.current?.pointerId === event.pointerId) drag.current = null;
  }

  function wheel(event: WheelEvent<HTMLCanvasElement>) {
    event.preventDefault();
    changeZoom(zoom - event.deltaY * 0.002);
  }

  function changeStart(value: number) {
    const nextStart = Math.min(value, Math.max(0, clipEnd - 1));
    const nextEnd = clipEnd - nextStart > VIDEO_CLIP_MAX_SECONDS ? nextStart + VIDEO_CLIP_MAX_SECONDS : clipEnd;
    setClipStart(nextStart);
    setClipEnd(Math.min(duration, nextEnd));
    seekVideo(nextStart);
  }

  function changeEnd(value: number) {
    const nextEnd = Math.max(value, Math.min(duration, clipStart + 1));
    const nextStart = nextEnd - clipStart > VIDEO_CLIP_MAX_SECONDS ? nextEnd - VIDEO_CLIP_MAX_SECONDS : clipStart;
    setClipStart(Math.max(0, nextStart));
    setClipEnd(nextEnd);
    seekVideo(nextStart);
  }

  function seekVideo(time: number) {
    const player = video.current;
    if (!player) return;
    player.pause();
    player.currentTime = time;
  }

  async function togglePreview() {
    const player = video.current;
    if (!player) return;
    if (!player.paused) { player.pause(); return; }
    if (player.currentTime < clipStart || player.currentTime >= clipEnd) player.currentTime = clipStart;
    await player.play().catch(() => setError('Toque novamente para reproduzir a prévia.'));
  }

  async function apply() {
    const media = kind === 'image' ? image.current : video.current;
    if (!source || !media) return;
    setApplying(true);
    setError(null);
    setProgress(0);
    try {
      const rect = cropRect(source, targetAspect, zoom, horizontal, vertical);
      if (kind === 'video') {
        video.current?.pause();
        const { editVideo } = await import('../lib/videoProcessing');
        const edited = await editVideo(file, { start: clipStart, end: clipEnd, crop: rect }, setProgress);
        onApply(edited);
      } else {
        const scale = Math.min(1, OUTPUT_MAX_SIDE / Math.max(rect.width, rect.height));
        const canvas = document.createElement('canvas');
        canvas.width = Math.max(1, Math.round(rect.width * scale));
        canvas.height = Math.max(1, Math.round(rect.height * scale));
        drawCrop(canvas, media, rect);
        const type = file.type === 'image/png' ? 'image/png' : file.type === 'image/webp' ? 'image/webp' : 'image/jpeg';
        const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, type, 0.9));
        if (!blob) throw new Error('crop failed');
        const base = (file.name || 'foto').replace(/\.[^.]+$/, '');
        const extension = type === 'image/png' ? 'png' : type === 'image/webp' ? 'webp' : 'jpg';
        onApply(new File([blob], `${base}-recorte.${extension}`, { type, lastModified: Date.now() }));
      }
    } catch {
      setError(kind === 'video'
        ? 'Não foi possível preparar o vídeo neste dispositivo. Tente um trecho menor ou outro arquivo MP4/MOV.'
        : 'Não foi possível aplicar o recorte. Você ainda pode usar a foto original.');
    } finally {
      setApplying(false);
    }
  }

  const selectionLeft = duration ? (clipStart / duration) * 100 : 0;
  const selectionWidth = duration ? ((clipEnd - clipStart) / duration) * 100 : 100;

  return (
    <div className={styles.backdrop} role="presentation">
      <section className={styles.dialog} role="dialog" aria-modal="true" aria-labelledby="crop-title">
        <header className={styles.header}>
          <div><span className={styles.eyebrow}>{kind === 'video' ? 'Enquadramento e duração' : 'Ajuste da imagem'}</span><h2 id="crop-title">{title}</h2></div>
          <button type="button" className={styles.close} onClick={onCancel} aria-label="Fechar editor" disabled={applying}>×</button>
        </header>

        {showAspectRatios && source && (
          <div className={styles.aspectChoices} role="group" aria-label="Proporção do recorte">
            {ASPECTS.map((choice) => (
              <button key={choice.value} type="button" className={aspectChoice === choice.value ? styles.aspectActive : undefined} aria-pressed={aspectChoice === choice.value} aria-label={choice.label} onClick={() => chooseAspect(choice.value)}>
                <span className={styles.aspectIcon} style={{ aspectRatio: String(choice.ratio ?? originalAspect) }} />{choice.shortLabel}
              </button>
            ))}
          </div>
        )}

        <div className={styles.stage} style={{ aspectRatio: String(targetAspect) }}>
          <canvas ref={preview} aria-label={kind === 'video' ? 'Prévia do recorte do vídeo' : 'Prévia do recorte'} onPointerDown={pointerDown} onPointerMove={pointerMove} onPointerUp={pointerUp} onPointerCancel={pointerUp} onWheel={wheel} />
          <span className={styles.grid} aria-hidden="true" />
          <i className={`${styles.corner} ${styles.topLeft}`} aria-hidden="true" /><i className={`${styles.corner} ${styles.topRight}`} aria-hidden="true" />
          <i className={`${styles.corner} ${styles.bottomLeft}`} aria-hidden="true" /><i className={`${styles.corner} ${styles.bottomRight}`} aria-hidden="true" />
          {!source && !error && <span className={styles.loading}>Preparando {kind === 'video' ? 'vídeo' : 'foto'}…</span>}
          {kind === 'video' && objectUrl && <video ref={video} src={objectUrl} playsInline preload="auto" onLoadedMetadata={videoLoaded} onLoadedData={renderFrame} onError={() => setError('Não foi possível abrir este vídeo. Use um arquivo MP4 ou MOV válido.')} />}
        </div>

        {source && (
          <div className={styles.positionControls}>
            <p>Arraste a {kind === 'video' ? 'imagem do vídeo' : 'foto'} para ajustar o enquadramento</p>
            <div className={styles.zoomControl}>
              <button type="button" onClick={() => changeZoom(zoom - 0.1)} disabled={zoom <= 1 || applying} aria-label="Diminuir zoom">−</button>
              <label><span className="visually-hidden">Zoom</span><input type="range" min="1" max="3" step="0.05" value={zoom} onChange={(event) => changeZoom(Number(event.target.value))} disabled={applying} /></label>
              <button type="button" onClick={() => changeZoom(zoom + 0.1)} disabled={zoom >= 3 || applying} aria-label="Aumentar zoom">+</button>
              <button type="button" className={styles.reset} onClick={() => { setZoom(1); setHorizontal(50); setVertical(50); }} disabled={applying}>Centralizar</button>
            </div>
          </div>
        )}

        {kind === 'video' && duration > 0 && (
          <div className={styles.timelineSection}>
            <div className={styles.timelineHeading}>
              <div><strong>Escolha o trecho</strong><span>Até 1 minuto</span></div>
              <button type="button" className={styles.previewButton} onClick={() => void togglePreview()} disabled={applying}>{playing ? 'Pausar' : 'Reproduzir trecho'}</button>
            </div>
            <div className={styles.timeline} style={{ '--selection-left': `${selectionLeft}%`, '--selection-width': `${selectionWidth}%` } as CSSProperties}>
              <span className={styles.timelineTrack} aria-hidden="true" /><span className={styles.timelineSelection} aria-hidden="true" />
              <input aria-label="Início do trecho" type="range" min="0" max={duration} step="0.1" value={clipStart} onChange={(event) => changeStart(Number(event.target.value))} disabled={applying} />
              <input aria-label="Fim do trecho" type="range" min="0" max={duration} step="0.1" value={clipEnd} onChange={(event) => changeEnd(Number(event.target.value))} disabled={applying} />
            </div>
            <div className={styles.timeLabels}>
              <span><small>Início</small>{formatTime(clipStart)}</span><span className={styles.clipDuration}>{formatTime(clipEnd - clipStart)} selecionados</span><span><small>Fim</small>{formatTime(clipEnd)}</span>
            </div>
          </div>
        )}

        {applying && kind === 'video' && <div className={styles.processing} role="status"><span>Preparando o trecho… {Math.round(progress * 100)}%</span><progress max="1" value={progress} /><small>Mantenha esta janela aberta. O vídeo é processado somente neste dispositivo.</small></div>}
        {error && <p className={styles.error} role="alert">{error}</p>}
        <footer className={styles.actions}>
          <Button variant="quiet" onClick={onCancel} disabled={applying}>Cancelar</Button>
          {kind === 'image' && <Button variant="outline" onClick={() => onApply(file)} disabled={applying}>Usar foto original</Button>}
          <Button onClick={() => void apply()} loading={applying} disabled={!source || (kind === 'video' && clipEnd <= clipStart)}>{kind === 'video' ? 'Usar este trecho' : 'Aplicar recorte'}</Button>
        </footer>
      </section>
    </div>
  );
}

function cropRect(source: SourceSize, aspect: number, zoom: number, horizontal: number, vertical: number): CropRect {
  const sourceAspect = source.width / source.height;
  let width = sourceAspect > aspect ? source.height * aspect : source.width;
  let height = sourceAspect > aspect ? source.height : source.width / aspect;
  width /= zoom;
  height /= zoom;
  return { x: (source.width - width) * (horizontal / 100), y: (source.height - height) * (vertical / 100), width, height };
}

function drawCrop(canvas: HTMLCanvasElement, media: CanvasImageSource, rect: CropRect) {
  if (media instanceof HTMLVideoElement && media.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) return;
  const context = canvas.getContext('2d');
  if (!context) throw new Error('canvas unavailable');
  context.clearRect(0, 0, canvas.width, canvas.height);
  context.drawImage(media, rect.x, rect.y, rect.width, rect.height, 0, 0, canvas.width, canvas.height);
}

function clamp(value: number, minimum: number, maximum: number) { return Math.min(maximum, Math.max(minimum, value)); }

function formatTime(value: number) {
  const seconds = Math.max(0, Math.round(value));
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}
