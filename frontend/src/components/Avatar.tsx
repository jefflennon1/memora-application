import { useEffect, useState } from 'react';
import { apiAsset } from '../api/config';
import styles from './Avatar.module.css';

function initials(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  const first = parts[0]?.[0] ?? '';
  const last = parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : '';
  return (first + last).toUpperCase() || '·';
}

/**
 * Foto do perfil com recurso às iniciais. Fotos do Google exigem referrerPolicy="no-referrer"
 * para não serem recusadas.
 */
export function Avatar({ name, src, size = 48 }: { name: string; src?: string | null; size?: number }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [src]);

  const style = { width: size, height: size, fontSize: Math.round(size * 0.38) };
  if (src && !failed) {
    return (
      <img
        className={styles.avatar}
        style={style}
        src={apiAsset(src)}
        alt=""
        referrerPolicy="no-referrer"
        onError={() => setFailed(true)}
      />
    );
  }
  return (
    <span className={`${styles.avatar} ${styles.initials}`} style={style} aria-hidden="true">
      {initials(name)}
    </span>
  );
}
