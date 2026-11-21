import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { eventsApi, isOrganization, type MemoraEvent, type MuralStatus } from '../../api/events';
import { keys } from '../../api/keys';
import { friendlyMessage } from '../../api/problems';
import { ConfirmAction, EmptyState, SectionHeader } from '../../components/Bits';
import { Button } from '../../components/Button';
import { Notice } from '../../components/Notice';
import page from '../../components/Page.module.css';
import { formatDateTime } from '../../lib/format';
import { EventForm } from './EventForm';
import { errorsFromApi, toUpdateRequest } from './eventFormModel';
import { useEventContext } from './EventLayout';

/** /events/:id/edit: dados do evento (organização) e abrir/fechar o mural (só o organizador). */
export function EditEventTab() {
  const { event } = useEventContext();
  if (!isOrganization(event)) {
    return <EmptyState title="Só a organização edita o evento">Você pode ver o mural e as pessoas do evento.</EmptyState>;
  }
  return (
    <div className={page.page}>
      <EditDetails event={event} />
      {event.myRole === 'ORGANIZER' && <MuralControl event={event} />}
    </div>
  );
}

function useStoreEvent() {
  const client = useQueryClient();
  return (updated: MemoraEvent) => {
    client.setQueryData(keys.event(updated.id), updated);
    void client.invalidateQueries({ queryKey: keys.myEvents });
  };
}

function EditDetails({ event }: { event: MemoraEvent }) {
  const store = useStoreEvent();
  const [saved, setSaved] = useState(false);
  const [nothing, setNothing] = useState(false);
  const update = useMutation({
    mutationFn: (body: Parameters<typeof eventsApi.update>[1]) => eventsApi.update(event.id, body),
    onSuccess: (updated) => {
      store(updated);
      setSaved(true);
    },
  });
  const apiErrors = update.error ? errorsFromApi(update.error) : null;
  const canChangePrivacy = event.myRole === 'ORGANIZER';

  return (
    <section className={page.section} aria-label="Dados do evento">
      {!canChangePrivacy && (
        <p className={page.muted}>A privacidade do evento só pode ser mudada pelo organizador.</p>
      )}
      <EventForm
        key={event.updatedAt}
        initial={event}
        canChangePrivacy={canChangePrivacy}
        submitLabel="Salvar alterações"
        pending={update.isPending}
        serverErrors={apiErrors?.fields}
        generalError={apiErrors?.general}
        onSubmit={(values) => {
          const body = toUpdateRequest(values, event, canChangePrivacy);
          setSaved(false);
          if (Object.keys(body).length === 0) {
            setNothing(true);
            return;
          }
          setNothing(false);
          update.mutate(body);
        }}
        footer={
          <>
            {saved && <Notice tone="success" title="Evento salvo" />}
            {nothing && <span className={page.muted}>Nada mudou.</span>}
          </>
        }
      />
    </section>
  );
}

function MuralControl({ event }: { event: MemoraEvent }) {
  const store = useStoreEvent();
  const change = useMutation({
    mutationFn: (status: MuralStatus) => eventsApi.setMural(event.id, status),
    onSuccess: store,
  });
  const open = event.muralStatus === 'OPEN';

  return (
    <section className={page.section} aria-labelledby="mural-control">
      <SectionHeader id="mural-control" title="Mural" />
      <p>
        {open
          ? 'O mural está aberto: quem participa pode publicar memórias. Ele não fecha sozinho quando o evento acontece.'
          : 'O mural está fechado: as memórias continuam visíveis, mas ninguém publica nem edita.'}
        {event.muralStatusChangedAt && (
          <span className={page.muted}> Última mudança em {formatDateTime(event.muralStatusChangedAt)}.</span>
        )}
      </p>
      {change.isError && <Notice tone="error" title="Não deu para mudar o mural">{friendlyMessage(change.error)}</Notice>}
      <div>
        {open ? (
          <ConfirmAction
            label="Fechar mural"
            question="Fechar o mural? Ninguém mais publica nem edita memórias até você abrir de novo. Quem publicou ainda pode excluir as próprias memórias."
            onConfirm={() => change.mutate('CLOSED')}
            pending={change.isPending}
          />
        ) : (
          <Button onClick={() => change.mutate('OPEN')} loading={change.isPending}>
            Abrir mural
          </Button>
        )}
      </div>
    </section>
  );
}
