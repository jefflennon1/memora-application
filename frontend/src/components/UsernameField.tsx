import { useQuery } from '@tanstack/react-query';
import { useEffect, useState, type ReactNode } from 'react';
import { friendlyMessage, messageForCode } from '../api/problems';
import { usersApi, type UsernameAvailability } from '../api/users';
import { FORMAT_HINTS, USERNAME_MAX_INPUT, normalizeUsername, usernameFormatProblem } from '../auth/username';
import { TextField } from './TextField';
import styles from './UsernameField.module.css';

function useDebounced<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delay);
    return () => window.clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}

export type UsernameCheck =
  | { state: 'idle' | 'invalid' | 'checking' | 'error'; normalized: string }
  | { state: 'available' | 'unavailable' | 'unchanged'; normalized: string; result?: UsernameAvailability };

/**
 * Estado da verificação do @usuário: primeiro valida o formato localmente (sem chamar a API),
 * depois consulta o endpoint de disponibilidade adequado com espera de 350 ms após a última tecla.
 */
type AvailabilityCheck = (username: string, signal?: AbortSignal) => Promise<UsernameAvailability>;

export function useUsernameCheck(
  raw: string,
  current?: string | null,
  checkAvailability: AvailabilityCheck = usersApi.usernameAvailability,
): UsernameCheck {
  const normalized = normalizeUsername(raw);
  const debounced = useDebounced(normalized, 350);
  const formatProblem = usernameFormatProblem(normalized);
  const isCurrent = !!current && normalized === current;

  const query = useQuery({
    queryKey: ['username-availability', debounced],
    queryFn: ({ signal }) => checkAvailability(debounced, signal),
    enabled: !isCurrent && usernameFormatProblem(debounced) === null,
    staleTime: 15_000,
    retry: false,
  });

  if (normalized.length === 0) return { state: 'idle', normalized };
  if (isCurrent) return { state: 'unchanged', normalized };
  if (formatProblem) return { state: 'invalid', normalized };
  if (debounced !== normalized || query.isPending) return { state: 'checking', normalized };
  if (query.isError) return { state: 'error', normalized };
  return { state: query.data.available ? 'available' : 'unavailable', normalized, result: query.data };
}

export function UsernameField({
  value,
  onChange,
  check,
  serverError,
  autoFocus,
}: {
  value: string;
  onChange: (value: string) => void;
  check: UsernameCheck;
  serverError?: string | null;
  autoFocus?: boolean;
}) {
  const normalized = check.normalized;
  const problem = usernameFormatProblem(normalized);

  let status: ReactNode = 'Letras sem acento, números, ponto e sublinhado. De 3 a 30 caracteres.';
  let error: string | null = serverError ?? null;

  if (!error) {
    switch (check.state) {
      case 'invalid':
        error = problem ? FORMAT_HINTS[problem] : null;
        break;
      case 'checking':
        status = <span className={styles.checking}>Verificando @{normalized}…</span>;
        break;
      case 'available':
        status = (
          <span className={styles.available}>
            <CheckIcon /> @{normalized} está livre
          </span>
        );
        break;
      case 'unavailable':
        error = messageForCode(check.result?.reason ?? 'USERNAME_TAKEN');
        break;
      case 'unchanged':
        status = 'Este é o seu @usuário atual.';
        break;
      case 'error':
        error = friendlyMessage(undefined);
        break;
    }
  }

  return (
    <TextField
      label="@usuário"
      prefix="@"
      value={value}
      onChange={(event) => onChange(event.target.value.replace(/^@+/, ''))}
      maxLength={USERNAME_MAX_INPUT}
      autoComplete="username"
      autoCapitalize="none"
      autoCorrect="off"
      spellCheck={false}
      inputMode="text"
      enterKeyHint="next"
      autoFocus={autoFocus}
      error={error}
      status={status}
      required
    />
  );
}

function CheckIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true" className={styles.icon}>
      <path d="M3.5 8.5l3 3 6-7" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
