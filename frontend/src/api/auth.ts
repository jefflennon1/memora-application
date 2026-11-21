import { api } from './client';
import type { UsernameAvailability } from './users';

export interface LocalLoginRequest {
  username: string;
  password: string;
}

export interface LocalRegisterRequest extends LocalLoginRequest {
  passwordConfirmation: string;
  phone: string;
}

export const authApi = {
  usernameAvailability: (username: string, signal?: AbortSignal) =>
    api<UsernameAvailability>('/auth/username-availability', { query: { username }, signal }),

  login: (body: LocalLoginRequest) => api<void>('/auth/login', { method: 'POST', body }),

  register: (body: LocalRegisterRequest) => api<void>('/auth/register', { method: 'POST', body }),
};
