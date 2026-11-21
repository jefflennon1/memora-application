import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { isOrganization, type MemoraEvent } from '../../api/events';
import { keys } from '../../api/keys';
import {
  joinRequestsApi,
  participantsApi,
  type AttendanceStatus,
  type JoinRequest,
  type Participant,
} from '../../api/membership';
import { friendlyMessage } from '../../api/problems';
import { useMe } from '../../auth/session';
import { ConfirmAction, EmptyState, LoadMore, PersonLine, SectionHeader, Tag } from '../../components/Bits';
import { Button } from '../../components/Button';
import { Notice } from '../../components/Notice';
import page from '../../components/Page.module.css';
import { ROLE_LABEL, formatShortDate, personName, timeAgo } from '../../lib/format';
import { usePaged } from '../../lib/usePaged';
import { useEventContext } from '../events/EventLayout';
import styles from './Membership.module.css';

/** /events/:id/people: solicitações (organização), participantes e "sair do evento". */
export function PeopleTab() {
  const { event } = useEventContext();
  if (!event.myRole) {
    return <EmptyState title="Só quem participa vê a lista de pessoas">A lista de participantes é reservada.</EmptyState>;
  }
  return (
    <div className={page.page}>
      {event.modality === 'IN_PERSON' && <AttendanceOverview event={event} />}
      {isOrganization(event) && <JoinRequests event={event} />}
      <Participants event={event} />
      {event.myRole !== 'ORGANIZER' && <LeaveEvent event={event} />}
    </div>
  );
}

function AttendanceOverview({ event }: { event: MemoraEvent }) {
  const client = useQueryClient();
  const summary = useQuery({
    queryKey: keys.attendanceSummary(event.id),
    queryFn: ({ signal }) => participantsApi.attendanceSummary(event.id, signal),
  });
  const update = useMutation({
    mutationFn: (status: AttendanceStatus) => participantsApi.updateAttendance(event.id, status),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: keys.attendanceSummary(event.id) });
      void client.invalidateQueries({ queryKey: keys.participants(event.id) });
    },
  });

  return (
    <section className={`${page.section} ${styles.attendancePanel}`} aria-labelledby="attendance">
      <SectionHeader id="attendance" title="Presença no evento" />
      <div className={styles.myAttendance}>
        <div>
          <p className={styles.attendanceTitle}>Você vai comparecer presencialmente?</p>
          <p className={page.muted}>Você pode mudar esta resposta quando precisar.</p>
        </div>
        <div className={styles.attendanceButtons} role="group" aria-label="Minha confirmação de presença">
          <Button
            variant={summary.data?.myStatus === 'ATTENDING' ? 'primary' : 'outline'}
            onClick={() => update.mutate('ATTENDING')}
            loading={update.isPending && update.variables === 'ATTENDING'}
            aria-pressed={summary.data?.myStatus === 'ATTENDING'}
          >
            Vou comparecer
          </Button>
          <Button
            variant={summary.data?.myStatus === 'NOT_ATTENDING' ? 'primary' : 'outline'}
            onClick={() => update.mutate('NOT_ATTENDING')}
            loading={update.isPending && update.variables === 'NOT_ATTENDING'}
            aria-pressed={summary.data?.myStatus === 'NOT_ATTENDING'}
          >
            Não vou comparecer
          </Button>
        </div>
      </div>
      {summary.isPending && <p className={page.muted}>Carregando confirmações…</p>}
      {summary.isError && <Notice tone="error" title="Não deu para carregar as confirmações">{friendlyMessage(summary.error)}</Notice>}
      {update.isError && <Notice tone="error" title="Não deu para salvar sua resposta">{friendlyMessage(update.error)}</Notice>}
      {summary.data && (
        <dl className={styles.attendanceStats} aria-label="Resumo de presença">
          <div>
            <dt>Confirmaram</dt>
            <dd>{summary.data.attending}</dd>
          </div>
          <div>
            <dt>Não irão</dt>
            <dd>{summary.data.notAttending}</dd>
          </div>
          <div>
            <dt>Aguardando</dt>
            <dd>{summary.data.unanswered}</dd>
          </div>
          <div>
            <dt>Total</dt>
            <dd>{summary.data.total}</dd>
          </div>
        </dl>
      )}
    </section>
  );
}

function JoinRequests({ event }: { event: MemoraEvent }) {
  const client = useQueryClient();
  const requests = usePaged(keys.joinRequests(event.id), (p, signal) =>
    joinRequestsApi.list(event.id, { status: 'PENDING', page: p, size: 20 }, signal),
  );
  const refresh = () => {
    void client.invalidateQueries({ queryKey: keys.joinRequests(event.id) });
    void client.invalidateQueries({ queryKey: keys.participants(event.id) });
    void client.invalidateQueries({ queryKey: keys.attendanceSummary(event.id) });
  };
  const approve = useMutation({ mutationFn: (id: string) => joinRequestsApi.approve(event.id, id), onSettled: refresh });
  const reject = useMutation({ mutationFn: (id: string) => joinRequestsApi.reject(event.id, id), onSettled: refresh });
  const error = approve.error ?? reject.error;

  return (
    <section className={page.section} aria-labelledby="requests">
      <SectionHeader
        id="requests"
        title="Pedidos para participar"
        action={requests.total > 0 ? <Tag tone="accent">{requests.total} aguardando</Tag> : undefined}
      />
      {requests.isError && <Notice tone="error" title="Não deu para carregar os pedidos">{friendlyMessage(requests.error)}</Notice>}
      {error && <Notice tone="error" title="Não deu para responder">{friendlyMessage(error)}</Notice>}
      {requests.isSuccess && requests.items.length === 0 && (
        <p className={page.muted}>Nenhum pedido esperando. Quem entrar por um link de convite aparece aqui para você aprovar.</p>
      )}
      <ul className={page.list}>
        {requests.items.map((request) => (
          <RequestRow
            key={request.id}
            request={request}
            onApprove={() => approve.mutate(request.id)}
            onReject={() => reject.mutate(request.id)}
            busy={(approve.isPending && approve.variables === request.id) || (reject.isPending && reject.variables === request.id)}
          />
        ))}
      </ul>
      <LoadMore hasNext={!!requests.hasNextPage} loading={requests.isFetchingNextPage} onClick={() => requests.fetchNextPage()} />
    </section>
  );
}

function RequestRow({
  request,
  onApprove,
  onReject,
  busy,
}: {
  request: JoinRequest;
  onApprove: () => void;
  onReject: () => void;
  busy: boolean;
}) {
  return (
    <li className={styles.row}>
      <PersonLine
        person={request.person}
        extra={
          <span className={styles.meta}>
            Pediu {timeAgo(request.requestedAt)}
            {request.attendanceStatus && <> · <AttendanceLabel status={request.attendanceStatus} /></>}
          </span>
        }
      />
      <div className={styles.rowActions}>
        <Button onClick={onApprove} loading={busy}>
          Aprovar
        </Button>
        <ConfirmAction
          label="Recusar"
          variant="quiet"
          question={`Recusar o pedido de ${personName(request.person)}? Pelo link, a pessoa não poderá pedir de novo; só voltará se você mandar um convite nominal.`}
          onConfirm={onReject}
          pending={busy}
        />
      </div>
    </li>
  );
}

function Participants({ event }: { event: MemoraEvent }) {
  const client = useQueryClient();
  const { data: me } = useMe();
  const people = usePaged(keys.participants(event.id), (p, signal) =>
    participantsApi.list(event.id, { page: p, size: 30 }, signal),
  );
  const refresh = () => void client.invalidateQueries({ queryKey: keys.participants(event.id) });
  const changeRole = useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: 'CO_ORGANIZER' | 'PARTICIPANT' }) =>
      participantsApi.changeRole(event.id, userId, role),
    onSettled: refresh,
  });
  const remove = useMutation({
    mutationFn: (userId: string) => participantsApi.remove(event.id, userId),
    onSettled: refresh,
  });
  const error = changeRole.error ?? remove.error;

  return (
    <section className={page.section} aria-labelledby="participants">
      <SectionHeader
        id="participants"
        title="Participantes"
        action={people.total > 0 ? <span className={page.muted}>{people.total} no total</span> : undefined}
      />
      {people.isError && <Notice tone="error" title="Não deu para carregar">{friendlyMessage(people.error)}</Notice>}
      {error && <Notice tone="error" title="Não deu para concluir">{friendlyMessage(error)}</Notice>}
      <ul className={page.list}>
        {people.items.map((p) => (
          <ParticipantRow
            key={p.person.id}
            participant={p}
            event={event}
            isMe={p.person.id === me?.id}
            onChangeRole={(role) => changeRole.mutate({ userId: p.person.id, role })}
            onRemove={() => remove.mutate(p.person.id)}
            busy={
              (changeRole.isPending && changeRole.variables?.userId === p.person.id) ||
              (remove.isPending && remove.variables === p.person.id)
            }
          />
        ))}
      </ul>
      <LoadMore hasNext={!!people.hasNextPage} loading={people.isFetchingNextPage} onClick={() => people.fetchNextPage()} />
      <p className={page.muted}>Remover alguém não apaga as memórias que a pessoa já publicou.</p>
    </section>
  );
}

function ParticipantRow({
  participant,
  event,
  isMe,
  onChangeRole,
  onRemove,
  busy,
}: {
  participant: Participant;
  event: MemoraEvent;
  isMe: boolean;
  onChangeRole: (role: 'CO_ORGANIZER' | 'PARTICIPANT') => void;
  onRemove: () => void;
  busy: boolean;
}) {
  const { role } = participant;
  const amOrganizer = event.myRole === 'ORGANIZER';
  // Regras da API: o organizador mexe em todos (menos nele mesmo); o coorganizador só remove participantes comuns.
  const canPromote = amOrganizer && !isMe && role !== 'ORGANIZER';
  const canRemove = !isMe && role !== 'ORGANIZER' && (amOrganizer || (event.myRole === 'CO_ORGANIZER' && role === 'PARTICIPANT'));
  const name = personName(participant.person);

  return (
    <li className={styles.row}>
      <PersonLine
        person={participant.person}
        extra={
          <span className={styles.meta}>
            {isMe ? 'Você, ' : ''}desde {formatShortDate(participant.joinedAt)}
          </span>
        }
      />
      <div className={styles.rowActions}>
        {event.modality === 'IN_PERSON' && <AttendanceTag status={participant.attendanceStatus} />}
        <Tag tone={role === 'PARTICIPANT' ? 'neutral' : 'primary'}>{ROLE_LABEL[role]}</Tag>
        {canPromote &&
          (role === 'PARTICIPANT' ? (
            <Button variant="quiet" onClick={() => onChangeRole('CO_ORGANIZER')} loading={busy}>
              Tornar coorganização
            </Button>
          ) : (
            <Button variant="quiet" onClick={() => onChangeRole('PARTICIPANT')} loading={busy}>
              Tornar participante
            </Button>
          ))}
        {canRemove && (
          <ConfirmAction
            label="Remover"
            variant="quiet"
            question={`Remover ${name} do evento? As memórias que a pessoa publicou continuam no mural.`}
            onConfirm={onRemove}
            pending={busy}
          />
        )}
      </div>
    </li>
  );
}

function AttendanceLabel({ status }: { status: AttendanceStatus }) {
  return <>{status === 'ATTENDING' ? 'vai comparecer' : 'não vai comparecer'}</>;
}

function AttendanceTag({ status }: { status?: AttendanceStatus | null }) {
  if (status === 'ATTENDING') return <Tag tone="primary">Presença confirmada</Tag>;
  if (status === 'NOT_ATTENDING') return <Tag tone="muted">Não comparecerá</Tag>;
  return <Tag tone="neutral">Aguardando resposta</Tag>;
}

function LeaveEvent({ event }: { event: MemoraEvent }) {
  const client = useQueryClient();
  const navigate = useNavigate();
  const { data: me } = useMe();
  const leave = useMutation({
    mutationFn: () => participantsApi.remove(event.id, me!.id),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: keys.myEvents });
      void client.removeQueries({ queryKey: keys.event(event.id) });
      navigate('/events', { replace: true });
    },
  });
  if (!me) return null;
  return (
    <section className={page.section} aria-labelledby="leave">
      <SectionHeader id="leave" title="Sair do evento" />
      <p className={page.muted}>
        Suas memórias continuam no mural. Para voltar, você vai precisar de um novo convite.
      </p>
      {leave.isError && <Notice tone="error" title="Não deu para sair">{friendlyMessage(leave.error)}</Notice>}
      <div>
        <ConfirmAction
          label="Sair do evento"
          question={`Sair de "${event.name}"?`}
          onConfirm={() => leave.mutate()}
          pending={leave.isPending}
        />
      </div>
    </section>
  );
}
