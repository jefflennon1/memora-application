import { useSyncExternalStore } from 'react';

/**
 * Tema da interface.
 * - Sem escolha salva: segue o sistema (prefers-color-scheme), sem atributo no <html>.
 * - Com escolha: grava em localStorage e aplica <html data-theme="light|dark">, que tem
 *   precedência sobre o sistema nos tokens (src/styles/tokens.css).
 * O script em index.html aplica a escolha salva antes da primeira pintura, evitando piscar.
 */
export type Theme = 'light' | 'dark';

export const THEME_STORAGE_KEY = 'memora-theme';
const META_COLORS: Record<Theme, string> = { light: '#F6FBFC', dark: '#0D1E22' };

function readStored(): Theme | null {
  try {
    const value = localStorage.getItem(THEME_STORAGE_KEY);
    return value === 'light' || value === 'dark' ? value : null;
  } catch {
    return null;
  }
}

function systemQuery(): MediaQueryList | null {
  return typeof window.matchMedia === 'function' ? window.matchMedia('(prefers-color-scheme: dark)') : null;
}

function systemTheme(): Theme {
  return systemQuery()?.matches ? 'dark' : 'light';
}

let chosen: Theme | null = readStored();
const listeners = new Set<() => void>();

function current(): Theme {
  return chosen ?? systemTheme();
}

function apply(theme: Theme) {
  document.documentElement.dataset.theme = theme;
  document.querySelectorAll('meta[name="theme-color"]').forEach((meta) => meta.setAttribute('content', META_COLORS[theme]));
}

export function setTheme(theme: Theme) {
  chosen = theme;
  try {
    localStorage.setItem(THEME_STORAGE_KEY, theme);
  } catch {
    // Sem armazenamento (janela privada etc.): vale só para esta visita.
  }
  apply(theme);
  listeners.forEach((notify) => notify());
}

function subscribe(notify: () => void) {
  listeners.add(notify);
  // Sem escolha salva, acompanha a mudança de tema do sistema.
  const query = systemQuery();
  const onSystemChange = () => chosen === null && notify();
  query?.addEventListener('change', onSystemChange);
  return () => {
    listeners.delete(notify);
    query?.removeEventListener('change', onSystemChange);
  };
}

export function useTheme() {
  const theme = useSyncExternalStore(subscribe, current, () => 'light' as Theme);
  return { theme, toggle: () => setTheme(theme === 'dark' ? 'light' : 'dark') };
}
