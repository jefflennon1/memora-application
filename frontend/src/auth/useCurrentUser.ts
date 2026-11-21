import type { Me } from '../api/users';
import { useMe } from './session';

/**
 * Usuário autenticado e com cadastro concluído. Use apenas em telas protegidas por
 * <RequireOnboarded>, que só renderiza seus filhos depois que a conta foi carregada.
 */
export function useCurrentUser(): Me {
  const { data } = useMe();
  if (!data) throw new Error('useCurrentUser fora de uma rota protegida');
  return data;
}
