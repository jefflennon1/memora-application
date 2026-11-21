import type { ProfilePrivacy } from '../../api/users';
import type { Choice } from '../../components/ChoiceGroup';

/** Textos da privacidade do perfil (README da API, seção 5.4). */
export const PRIVACY_CHOICES: Choice<ProfilePrivacy>[] = [
  {
    value: 'PUBLIC',
    title: 'Aberto',
    description: 'Nome, foto e @usuário visíveis para quem tem conta na Memora.',
  },
  {
    value: 'PRIVATE',
    title: 'Reservado',
    description: 'Só quem participa de algum evento com você vê seu perfil.',
  },
];

export const PRIVACY_LABEL: Record<ProfilePrivacy, string> = {
  PUBLIC: 'Aberto',
  PRIVATE: 'Reservado',
};
