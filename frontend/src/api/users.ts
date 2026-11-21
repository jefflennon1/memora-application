import { api } from './client';

export type ProfilePrivacy = 'PUBLIC' | 'PRIVATE';
/** SUSPENDED e DELETED existem na API, mas essas contas recebem 403 ACCOUNT_DISABLED antes de chegar aqui. */
export type UserStatus = 'PENDING_ONBOARDING' | 'ACTIVE' | 'SUSPENDED' | 'DELETED';

export interface LinkedIdentity {
  provider: 'GOOGLE' | 'APPLE' | 'FACEBOOK';
  email: string | null;
  emailVerified: boolean | null;
  linkedAt: string;
}

/** GET /users/me */
export interface Me {
  id: string;
  /** Ausente até concluir o onboarding. */
  username?: string | null;
  displayName: string;
  photoUrl?: string | null;
  /** Telefone privado, devolvido somente ao próprio usuário. */
  phone?: string | null;
  profilePrivacy: ProfilePrivacy;
  status: UserStatus;
  onboardingCompleted: boolean;
  createdAt: string;
  onboardingCompletedAt?: string | null;
  passwordLogin: boolean;
  identities: LinkedIdentity[];
}

export type UsernameUnavailableReason = 'INVALID_USERNAME' | 'USERNAME_RESERVED' | 'USERNAME_TAKEN';

export interface UsernameAvailability {
  username: string;
  available: boolean;
  reason?: UsernameUnavailableReason | null;
}

export interface OnboardingRequest {
  username: string;
  displayName: string;
  keepSuggestedPhoto: boolean;
  profilePrivacy: ProfilePrivacy;
}

export interface ProfileUpdateRequest {
  username?: string;
  displayName?: string;
  removePhoto?: boolean;
  profilePrivacy?: ProfilePrivacy;
}

export const usersApi = {
  me: (signal?: AbortSignal) => api<Me>('/users/me', { signal }),

  usernameAvailability: (username: string, signal?: AbortSignal) =>
    api<UsernameAvailability>('/users/username-availability', { query: { username }, signal }),

  completeOnboarding: (body: OnboardingRequest) =>
    api<Me>('/users/me/onboarding', { method: 'PATCH', body }),

  updateProfile: (body: ProfileUpdateRequest) => api<Me>('/users/me', { method: 'PATCH', body }),

  uploadPhoto: (blob: Blob, filename: string) => {
    const form = new FormData();
    form.append('file', blob, filename);
    return api<Me>('/users/me/photo', { method: 'PUT', form, timeoutMs: 60_000 });
  },

  removePhoto: () => api<Me>('/users/me/photo', { method: 'DELETE' }),

  logout: () => api<void>('/auth/logout', { method: 'POST' }),
};
