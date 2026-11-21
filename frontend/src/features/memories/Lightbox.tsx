import { useEffect, useRef } from 'react';
import { apiAsset } from '../../api/config';
import type { Media } from '../../api/memories';
import styles from './Memories.module.css';

/** Visualizador de fotos em <dialog> nativo (foco preso, Esc fecha). Setas trocam de foto. */
export function Lightbox({
  media,
  index,
  onIndex,
  onClose,
  label,
}: {
  media: Media[];
  index: number | null;
  onIndex: (index: number) => void;
  onClose: () => void;
  label: string;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const open = index !== null;

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal?.();
    if (!open && dialog.open) dialog.close?.();
  }, [open]);

  if (index === null) return <dialog ref={ref} className={styles.lightbox} onClose={onClose} />;
  const current = media[index]!;
  const many = media.length > 1;
  const go = (delta: number) => onIndex((index + delta + media.length) % media.length);

  return (
    <dialog
      ref={ref}
      className={styles.lightbox}
      aria-label={label}
      onClose={onClose}
      onKeyDown={(e) => {
        if (!many) return;
        if (e.key === 'ArrowRight') go(1);
        if (e.key === 'ArrowLeft') go(-1);
      }}
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <img
        src={apiAsset(current.url)}
        alt={`Foto ${index + 1} de ${media.length}`}
        width={current.width ?? undefined}
        height={current.height ?? undefined}
        className={styles.lightboxImage}
      />
      <div className={styles.lightboxBar}>
        {many && (
          <button type="button" className={styles.lightboxButton} onClick={() => go(-1)} aria-label="Foto anterior">
            ‹
          </button>
        )}
        {many && (
          <span className={styles.lightboxCount}>
            {index + 1} de {media.length}
          </span>
        )}
        {many && (
          <button type="button" className={styles.lightboxButton} onClick={() => go(1)} aria-label="Próxima foto">
            ›
          </button>
        )}
        <button type="button" className={styles.lightboxClose} onClick={onClose}>
          Fechar
        </button>
      </div>
    </dialog>
  );
}
