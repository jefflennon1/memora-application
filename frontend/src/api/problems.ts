import { ApiError } from './client';

/**
 * Mensagens amigáveis por `code` do Problem Details.
 * Quando um código não está aqui, usamos o `detail` da API (já em português) e, por fim, uma mensagem genérica.
 */
const MESSAGES: Record<string, string> = {
  // Infraestrutura e sessão
  NETWORK_ERROR: 'Não conseguimos falar com a Memora. Confira sua conexão e tente de novo.',
  TIMEOUT: 'A Memora demorou demais para responder. Tente de novo em instantes.',
  UNAUTHENTICATED:'Sua sessão terminou. Entre de novo para continuar.',
  FORBIDDEN: 'Você não tem permissão para fazer isso.',
  ACCOUNT_DISABLED: 'Esta conta está desativada e não pode entrar na Memora.',
  INVALID_CREDENTIALS: 'Usuário ou senha incorretos.',
  ONBOARDING_REQUIRED: 'Termine seu cadastro para continuar.',
  ONBOARDING_ALREADY_COMPLETED: 'Seu cadastro já foi concluído.',
  GOOGLE_LOGIN_NOT_CONFIGURED: 'O login com Google ainda não foi configurado neste servidor.',
  INTERNAL_ERROR: 'Algo deu errado do nosso lado. Tente de novo em instantes.',
  UNKNOWN_ERROR: 'Algo deu errado. Tente de novo em instantes.',
  USER_NOT_FOUND: 'Não encontramos esta pessoa.',
  PROFILE_PHOTO_NOT_FOUND: 'Esta foto de perfil não está mais disponível.',

  // Validação e perfil
  VALIDATION_FAILED: 'Alguns campos precisam de ajuste.',
  INVALID_USERNAME:
    'Use de 3 a 30 caracteres: letras sem acento, números, ponto ou sublinhado, começando e terminando com letra ou número.',
  USERNAME_RESERVED: 'Este @usuário é reservado pela Memora. Escolha outro.',
  USERNAME_TAKEN: 'Este @usuário já está em uso. Tente uma variação.',
  INVALID_DISPLAY_NAME: 'O nome precisa ter de 1 a 80 caracteres.',
  INVALID_PASSWORD: 'A senha deve ter pelo menos 8 caracteres.',
  PASSWORDS_DO_NOT_MATCH: 'As duas senhas precisam ser iguais.',
  INVALID_PHONE: 'Informe um telefone válido, com DDD.',

  // Eventos (Entrega 3)
  MALFORMED_REQUEST: 'Algum dado veio em formato inválido. Confira datas, horários e opções.',
  EVENT_NOT_FOUND: 'Este evento não existe ou você não tem acesso a ele.',
  EVENT_COVER_NOT_FOUND: 'Esta foto de capa não está mais disponível.',
  EVENT_ORGANIZATION_ONLY: 'Só a organização do evento pode fazer isso.',
  EVENT_ORGANIZER_ONLY: 'Só quem organiza o evento pode fazer isso.',
  EVENT_PARTICIPANTS_ONLY: 'Só quem participa do evento pode fazer isso.',
  EVENT_START_REQUIRED: 'Informe a data de início do evento.',
  EVENT_START_IN_PAST: 'A data de início não pode estar no passado.',
  EVENT_END_BEFORE_START: 'O término não pode ser antes do início.',
  EVENT_MODALITY_REQUIRED: 'Escolha se o evento é presencial, online ou sem local.',
  INVALID_EVENT_NAME: 'Dê um nome ao evento, com até 120 caracteres.',
  INVALID_EVENT_DESCRIPTION: 'A descrição pode ter até 2.000 caracteres.',
  INVALID_EVENT_CATEGORY: 'Escolha uma categoria da lista.',
  INVALID_EVENT_LOCATION: 'Confira a cidade e o endereço do evento.',
  MURAL_CLOSED: 'O mural deste evento está fechado para novas memórias.',

  // Participantes e convites (Entrega 4)
  INVITE_NOT_FOUND: 'Este convite não existe ou não é para você.',
  INVITE_EXPIRED: 'Este convite expirou. Peça um novo para quem organiza o evento.',
  INVITE_REVOKED: 'Este convite foi cancelado pela organização do evento.',
  INVITE_ALREADY_USED: 'Este convite já foi usado.',
  INVITEE_REQUIRED: 'Informe o @usuário de quem você quer convidar.',
  INVITEE_NOT_ALLOWED: 'Só o convite nominal leva um @usuário.',
  INVALID_INVITE_EXPIRATION: 'A validade do convite vai de 1 a 90 dias.',
  ALREADY_PARTICIPANT: 'Esta pessoa já participa do evento.',
  PARTICIPANT_NOT_FOUND: 'Esta pessoa não participa mais do evento.',
  JOIN_REQUEST_NOT_FOUND: 'Esta solicitação não existe mais.',
  JOIN_REQUEST_ALREADY_DECIDED: 'Esta solicitação já foi respondida.',
  ORGANIZER_IS_PERMANENT: 'Quem organiza o evento não pode sair nem ser removido.',
  ATTENDANCE_REQUIRED: 'Confirme se você vai comparecer presencialmente ao evento.',
  ATTENDANCE_NOT_APPLICABLE: 'A confirmação de presença só se aplica a eventos presenciais.',
  CONCURRENT_MODIFICATION: 'Outra pessoa alterou isto ao mesmo tempo. Recarregue e tente de novo.',

  // Memórias e imagens (Entrega 5)
  MEMORY_NOT_FOUND: 'Esta memória não existe ou você não tem acesso a ela.',
  MEMORY_AUTHOR_ONLY: 'Só quem publicou a memória pode fazer isso.',
  MEMORY_EMPTY: 'Escreva algo ou adicione uma foto ou áudio.',
  MEMORY_TEXT_TOO_LONG: 'O texto pode ter até 500 caracteres.',
  MEMORY_PUBLIC_REQUIRES_PUBLIC_EVENT: 'Memórias públicas só existem em eventos públicos.',
  TOO_MANY_MEDIA: 'Cada memória pode ter até 10 mídias.',
  TOO_MANY_AUDIO: 'Cada memória pode ter um áudio.',
  TOO_MANY_VIDEO: 'Cada memória pode ter um vídeo.',
  TOO_MANY_PENDING_MEDIA: 'Você tem fotos demais esperando publicação neste evento. Publique ou descarte algumas.',
  INVALID_MEDIA: 'Uma das mídias não pode ser usada. Envie de novo.',
  DUPLICATE_MEDIA: 'A mesma mídia aparece duas vezes.',
  MEDIA_NOT_FOUND: 'Esta mídia não existe mais.',
  MEDIA_ALREADY_PUBLISHED: 'Esta mídia já foi publicada.',
  MEDIA_TOO_LARGE: 'O arquivo é grande demais.',
  INVALID_AUDIO: 'Este arquivo não parece ser um áudio válido.',
  AUDIO_TOO_LONG: 'O áudio pode ter no máximo 30 segundos.',
  INVALID_VIDEO: 'Este arquivo não parece ser um vídeo MP4/MOV válido.',
  VIDEO_TOO_LONG: 'O vídeo pode ter no máximo 60 segundos.',
  VIDEO_DIMENSIONS_TOO_LARGE: 'As dimensões deste vídeo são grandes demais.',
  VIDEO_FORMAT_NOT_SUPPORTED: 'Use um vídeo MP4 ou MOV.',
  INVALID_IMAGE: 'Este arquivo não parece ser uma imagem válida.',
  EMPTY_FILE: 'O arquivo está vazio.',
  IMAGE_DIMENSIONS_TOO_LARGE: 'A foto tem dimensões grandes demais.',
  UNSUPPORTED_MEDIA_TYPE: 'Use fotos em JPEG, PNG, WebP ou GIF.',
  IMAGE_FORMAT_NOT_SUPPORTED: 'Este formato de foto (HEIC/AVIF) ainda não é aceito. Use JPEG, PNG, WebP ou GIF.',
  AUDIO_USE_AUDIO_BUTTON: 'Use o botão de áudio para adicionar esta gravação.',
  // Comentários, respostas e reações (Entrega 6)
  COMMENT_EMPTY: 'Escreva algo ou adicione um áudio antes de enviar.',
  COMMENT_TEXT_TOO_LONG: 'O comentário pode ter até 200 caracteres.',
  COMMENT_NOT_FOUND: 'Este comentário não existe mais ou você não tem acesso a ele.',
  COMMENT_AUTHOR_ONLY: 'Só quem escreveu o comentário pode fazer isso.',
  REPLY_TO_REPLY_NOT_ALLOWED: 'Só dá para responder a um comentário, não a uma resposta.',
  COMMENT_PUBLIC_REQUIRES_PUBLIC_MEMORY: 'Comentários públicos só existem em memórias públicas de eventos públicos.',
  REPLY_PUBLIC_REQUIRES_PUBLIC_COMMENT: 'Uma resposta só pode ser pública se o comentário também for.',
  INVALID_EMOJI: 'Use exatamente um emoji.',
  STORAGE_UNAVAILABLE: 'O envio de mídia está indisponível agora. Tente de novo em instantes.',
};

export function messageForCode(code: string, fallback?: string): string {
  return MESSAGES[code] ?? fallback ?? MESSAGES.UNKNOWN_ERROR!;
}

/** Mensagem amigável para qualquer erro vindo da API (ou não). */
export function friendlyMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return messageForCode(error.code, error.problem?.detail);
  }
  return MESSAGES.UNKNOWN_ERROR!;
}
