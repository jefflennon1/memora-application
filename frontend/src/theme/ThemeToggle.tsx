import { useTheme } from './theme';
import styles from './ThemeToggle.module.css';

/**
 * Botão de troca de tema. `withLabel` mostra o texto ao lado do ícone (coluna lateral);
 * sem ele, o texto fica só para leitores de tela e na dica do mouse.
 */
export function ThemeToggle({ withLabel = false }: { withLabel?: boolean }) {
  const { theme, toggle } = useTheme();
  const label = theme === 'dark' ? 'Usar tema claro' : 'Usar tema escuro';
  return (
    <button
      type="button"
      onClick={toggle}
      className={withLabel ? `${styles.toggle} ${styles.labeled}` : styles.toggle}
      aria-label={withLabel ? undefined : label}
      title={label}
    >
      {theme === 'dark' ? <SunIcon /> : <MoonIcon />}
      {withLabel && <span>{theme === 'dark' ? 'Tema claro' : 'Tema escuro'}</span>}
    </button>
  );
}

function SunIcon() {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
      <circle cx="12" cy="12" r="4.2" fill="none" stroke="currentColor" strokeWidth="1.8" />
      <path
        d="M12 2.8v2.4M12 18.8v2.4M2.8 12h2.4M18.8 12h2.4M5.5 5.5l1.7 1.7M16.8 16.8l1.7 1.7M5.5 18.5l1.7-1.7M16.8 7.2l1.7-1.7"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
      />
    </svg>
  );
}

function MoonIcon() {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
      <path
        d="M19.5 14.6A7.8 7.8 0 0 1 9.4 4.5a7.8 7.8 0 1 0 10.1 10.1z"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
    </svg>
  );
}
