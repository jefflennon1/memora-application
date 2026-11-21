import { useEffect, useRef, useState } from 'react';
import { apiAsset } from '../../api/config';
import { mediaApi, type Media } from '../../api/memories';
import { friendlyMessage, messageForCode } from '../../api/problems';
import { Button } from '../../components/Button';
import { AUDIO_MAX_BYTES, AUDIO_MAX_DURATION_MS, startPcmRecorder, type PcmRecorder } from '../../lib/audio';
import styles from './AudioField.module.css';

export interface AudioDraft {
  media: Media;
  fresh: boolean;
}

export function AudioPlayer({ media, compact = false }: { media: Media; compact?: boolean }) {
  const audio = useRef<HTMLAudioElement>(null);
  const [playing, setPlaying] = useState(false);
  const [muted, setMuted] = useState(false);
  const [current, setCurrent] = useState(0);
  const [duration, setDuration] = useState(Math.max(0, (media.durationMs ?? 0) / 1000));
  const progress = duration > 0 ? Math.min(1, current / duration) : 0;
  const barCount = compact ? 20 : 30;

  async function togglePlayback() {
    const element = audio.current;
    if (!element) return;
    if (element.paused) {
      try {
        await element.play();
      } catch {
        setPlaying(false);
      }
    } else {
      element.pause();
    }
  }

  return (
    <div className={`${styles.player} ${compact ? styles.playerCompact : ''}`}>
      <audio
        ref={audio}
        className={styles.audioNative}
        preload="metadata"
        src={apiAsset(media.url)}
        muted={muted}
        onPlay={() => setPlaying(true)}
        onPause={() => setPlaying(false)}
        onEnded={() => {
          setPlaying(false);
          setCurrent(0);
        }}
        onTimeUpdate={(event) => setCurrent(event.currentTarget.currentTime)}
        onLoadedMetadata={(event) => {
          const loaded = event.currentTarget.duration;
          if (Number.isFinite(loaded)) setDuration(loaded);
        }}
      >
        Seu navegador não consegue reproduzir este áudio.
      </audio>
      <button
        type="button"
        className={styles.playButton}
        onClick={() => void togglePlayback()}
        aria-label={playing ? 'Pausar áudio' : 'Reproduzir áudio'}
      >
        {playing ? (
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7.5 5.5h3v13h-3zm6 0h3v13h-3z" /></svg>
        ) : (
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m8 5 11 7-11 7z" /></svg>
        )}
      </button>
      <div className={styles.playerBody}>
        <div className={styles.playerTitle}>
          <span className={styles.audioLabel}>Áudio · {formatDuration(media.durationMs ?? duration * 1000)}</span>
          <span className={styles.audioTime}>{formatClock(current)} / {formatClock(duration)}</span>
        </div>
        <div className={styles.waveSeek}>
          <div className={styles.savedWaveform} aria-hidden="true">
            {Array.from({ length: barCount }, (_, index) => (
              <span
                key={index}
                className={index / Math.max(1, barCount - 1) <= progress ? styles.wavePlayed : undefined}
              />
            ))}
          </div>
          <input
            type="range"
            className={styles.audioSeek}
            min="0"
            max={duration || 0}
            step="0.05"
            value={Math.min(current, duration || 0)}
            aria-label="Posição do áudio"
            onChange={(event) => {
              const next = Number(event.target.value);
              setCurrent(next);
              if (audio.current) audio.current.currentTime = next;
            }}
          />
        </div>
      </div>
      <button
        type="button"
        className={styles.soundButton}
        onClick={() => setMuted((value) => !value)}
        aria-label={muted ? 'Ativar som' : 'Silenciar áudio'}
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M5 10v4h3l4 3V7l-4 3zm10.2-.8a4 4 0 0 1 0 5.6M17.6 6.8a7.2 7.2 0 0 1 0 10.4" />
          {muted && <path d="m15 9 5 6m0-6-5 6" />}
        </svg>
      </button>
    </div>
  );
}

export function AudioField({
  eventId,
  value,
  onChange,
  disabled,
  compactTrigger = false,
  triggerClassName,
}: {
  eventId: string;
  value: AudioDraft | null;
  onChange: (value: AudioDraft | null) => void;
  disabled?: boolean;
  compactTrigger?: boolean;
  triggerClassName?: string;
}) {
  const fileRef = useRef<HTMLInputElement>(null);
  const recorder = useRef<PcmRecorder | null>(null);
  const stopTimer = useRef<number | null>(null);
  const tickTimer = useRef<number | null>(null);
  const [recording, setRecording] = useState(false);
  const [seconds, setSeconds] = useState(0);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState(false);

  useEffect(() => () => {
    if (stopTimer.current) window.clearTimeout(stopTimer.current);
    if (tickTimer.current) window.clearInterval(tickTimer.current);
    void recorder.current?.cancel();
  }, []);

  async function upload(blob: Blob, name: string) {
    if (blob.size > AUDIO_MAX_BYTES) {
      setError(messageForCode('MEDIA_TOO_LARGE'));
      return;
    }
    setUploading(true);
    setError(null);
    try {
      const uploaded = await mediaApi.upload(eventId, blob, name);
      if (uploaded.kind !== 'AUDIO') throw new Error('INVALID_AUDIO');
      if (value?.fresh) void mediaApi.discard(value.media.id).catch(() => undefined);
      onChange({ media: uploaded, fresh: true });
    } catch (cause) {
      setError(friendlyMessage(cause));
    } finally {
      setUploading(false);
    }
  }

  async function start() {
    setError(null);
    try {
      recorder.current = await startPcmRecorder();
      setRecording(true);
      setSeconds(0);
      const started = performance.now();
      tickTimer.current = window.setInterval(
        () => setSeconds(Math.min(30, Math.floor((performance.now() - started) / 1000))),
        250,
      );
      stopTimer.current = window.setTimeout(() => void stop(), AUDIO_MAX_DURATION_MS);
    } catch {
      setError('Não foi possível usar o microfone. Confira a permissão do navegador.');
    }
  }

  async function stop() {
    const active = recorder.current;
    if (!active) return;
    recorder.current = null;
    if (stopTimer.current) window.clearTimeout(stopTimer.current);
    if (tickTimer.current) window.clearInterval(tickTimer.current);
    stopTimer.current = null;
    tickTimer.current = null;
    setRecording(false);
    try {
      const wav = await active.stop();
      await upload(wav, `gravacao-${Date.now()}.wav`);
    } catch {
      setError('Não foi possível concluir a gravação. Tente novamente.');
    }
  }

  function remove() {
    if (value?.fresh) void mediaApi.discard(value.media.id).catch(() => undefined);
    onChange(null);
    setError(null);
    setExpanded(false);
  }

  if (compactTrigger && !expanded && !recording && !value) {
    return (
      <div className={`${styles.field} ${styles.fieldInToolbar}`}>
        <button
          type="button"
          className={triggerClassName ?? styles.compactTrigger}
          onClick={() => {
            setError(null);
            setExpanded(true);
          }}
          disabled={disabled || uploading}
          aria-label="Adicionar áudio"
        >
          <MicrophoneIcon />
          <span>Áudio</span>
        </button>
        {error && <p className={styles.error} role="alert">{error}</p>}
      </div>
    );
  }

  return (
    <div className={`${styles.field} ${compactTrigger ? `${styles.fieldInToolbar} ${styles.fieldInToolbarOpen}` : ''}`}>
      {recording ? (
        <div className={styles.recordingPanel}>
          <div className={styles.recordingVisual} aria-hidden="true">
            <span className={styles.recordingDot} />
            <div className={styles.waveform}>
              {Array.from({ length: 21 }, (_, index) => (
                <span key={index} style={{ animationDelay: `${-(index % 7) * 0.11}s` }} />
              ))}
            </div>
          </div>
          <div className={styles.recordingText} role="status">
            <strong>Gravando áudio</strong>
            <span>{formatDuration(seconds * 1000)} de 0:30</span>
          </div>
          <Button variant="outline" onClick={() => void stop()} disabled={disabled}>
            Parar e usar áudio
          </Button>
        </div>
      ) : value ? (
        <div className={styles.preview}>
          <AudioPlayer media={value.media} compact />
          <Button variant="quiet" onClick={remove} disabled={disabled || uploading}>Remover áudio</Button>
        </div>
      ) : (
        <div className={`${styles.idlePanel} ${compactTrigger ? styles.idlePanelCompact : ''}`}>
          <div className={styles.idleIntro}>
            <span className={styles.micIcon} aria-hidden="true">
              <MicrophoneIcon />
            </span>
            <span>
              <strong>Adicione sua voz</strong>
              <small>Grave agora ou escolha um arquivo de até 30 segundos.</small>
            </span>
          </div>
          <div className={styles.actions}>
            <Button variant="quiet" onClick={() => void start()} disabled={disabled || uploading}>
              Gravar áudio
            </Button>
            <label className={styles.fileButton} aria-disabled={disabled || uploading}>
              <input
                ref={fileRef}
                type="file"
                accept=".mp3,.m4a,.ogg,.flac,.wav,audio/mpeg,audio/mp4,audio/ogg,audio/flac,audio/wav"
                disabled={disabled || uploading}
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) void upload(file, file.name);
                  event.currentTarget.value = '';
                }}
              />
              {uploading ? 'Enviando áudio…' : 'Escolher arquivo'}
            </label>
          </div>
          {compactTrigger && (
            <button type="button" className={styles.closeOptions} onClick={() => { setError(null); setExpanded(false); }} disabled={uploading} aria-label="Fechar opções de áudio">×</button>
          )}
        </div>
      )}
      {error && <p className={styles.error} role="alert">{error}</p>}
    </div>
  );
}

function MicrophoneIcon() {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
      <rect x="8" y="3" width="8" height="12" rx="4" fill="none" stroke="currentColor" strokeWidth="1.8" />
      <path d="M5.5 11.5a6.5 6.5 0 0 0 13 0M12 18v3M9 21h6" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  );
}

export function discardAudio(draft: AudioDraft | null) {
  if (draft?.fresh) void mediaApi.discard(draft.media.id).catch(() => undefined);
}

function formatDuration(milliseconds: number) {
  const seconds = Math.max(0, Math.ceil(milliseconds / 1000));
  return formatClock(seconds);
}

function formatClock(rawSeconds: number) {
  const seconds = Math.max(0, Math.floor(rawSeconds || 0));
  const minutes = Math.floor(seconds / 60);
  return `${minutes}:${String(seconds % 60).padStart(2, '0')}`;
}

