import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRef, useState, type ChangeEvent } from 'react';
import { NavLink, Outlet, useLocation, useOutletContext, useParams } from 'react-router';
import { isApiError } from '../../api/client';
import { eventsApi, isOrganization, isParticipant, type MemoraEvent } from '../../api/events';
import { keys } from '../../api/keys';
import { friendlyMessage } from '../../api/problems';
import { withNext } from '../../auth/redirect';
import { useMe } from '../../auth/session';
import { EmptyState, PersonLine, Tag } from '../../components/Bits';
import { Button, LinkButton } from '../../components/Button';
import { ExpandableText } from '../../components/ExpandableText';
import { ImageCropDialog } from '../../components/ImageCropDialog';
import { Notice } from '../../components/Notice';
import page from '../../components/Page.module.css';
import { apiAsset } from '../../api/config';
import { EVENT_PRIVACY_LABEL, MODALITY_LABEL, ROLE_LABEL, formatEventWhen } from '../../lib/format';
import { DateBlock } from './EventCard';
import { prepareImage } from '../../lib/images';
import styles from './EventLayout.module.css';

export interface EventContext {
  event: MemoraEvent;
}

/** Evento carregado pelo layout, para as abas (mural, pessoas, convites, editar). */
export function useEventContext() {
  return useOutletContext<EventContext>();
}

export function useEvent(id: string) {
  return useQuery({
    queryKey: keys.event(id),
    queryFn: ({ signal }) => eventsApi.get(id, signal),
    enabled: !!id,
    retry: (count, error) => isApiError(error, 'NETWORK_ERROR') && count < 1,
  });
}

/** /events/:eventId e abas. Funciona sem login para eventos públicos. */
export function EventLayout() {
  const { eventId = '' } = useParams();
  const location = useLocation();
  const { data: me } = useMe();
  const query = useEvent(eventId);

  if (query.isPending) return <p className={page.muted}>Abrindo o evento…</p>;

  if (query.isError) {
    if (isApiError(query.error, 'EVENT_NOT_FOUND')) {
      return (
        <div className={page.page}>
          <EmptyState
            title="Evento não encontrado"
            action={
              !me ? (
                <LinkButton to={withNext('/login', `${location.pathname}${location.search}`)}>Entrar</LinkButton>
              ) : (
                <LinkButton to="/events" variant="outline">
                  Ver seus eventos
                </LinkButton>
              )
            }
          >
            {me
              ? 'O endereço pode estar errado, ou o evento é privado e você não participa dele.'
              : 'Ele pode ser privado. Se você participa, entre na sua conta para ver.'}
          </EmptyState>
        </div>
      );
    }
    return (
      <div className={page.page}>
        <EmptyState title="Não deu para abrir o evento" action={<Button onClick={() => query.refetch()}>Tentar de novo</Button>}>
          {friendlyMessage(query.error)}
        </EmptyState>
      </div>
    );
  }

  const event = query.data;
  const eventPath = `/events/${event.id}`;
  const onMural = location.pathname.replace(/\/$/, '') === eventPath;
  const descriptionBesideMural = onMural && !!event.description;
  return (
    <div className={`${styles.layout} ${descriptionBesideMural ? styles.withDescription : ''}`}>
      <EventHeader event={event} showDescription={!onMural} />
      {descriptionBesideMural && <EventDescription description={event.description!} />}
      <EventTabs event={event} />
      <div className={styles.content}>
        <Outlet context={{ event } satisfies EventContext} />
      </div>
    </div>
  );
}

function EventHeader({ event, showDescription }: { event: MemoraEvent; showDescription: boolean }) {
  const inPerson = event.modality === 'IN_PERSON';
  const place = inPerson ? [event.city, event.address].filter(Boolean).join(', ') : '';
  return (
    <header className={styles.header}>
      <CoverPhoto event={event} />
      <div className={styles.titleRow}>
        <DateBlock date={event.startDate} size="lg" />
        <div className={styles.titleText}>
          <h1 className={styles.title}>{event.name}</h1>
          <p className={styles.when}>{formatEventWhen(event)}</p>
          <p className={styles.where}>
            {MODALITY_LABEL[event.modality]}
            {place && <>, {place}</>}
          </p>
        </div>
      </div>

      <div className={styles.tags}>
        {event.category && <Tag>{event.category.name}</Tag>}
        <Tag tone="muted">{EVENT_PRIVACY_LABEL[event.privacy]}</Tag>
        {event.muralStatus === 'OPEN' ? <Tag tone="primary">Mural aberto</Tag> : <Tag tone="accent">Mural fechado</Tag>}
        {event.myRole && <Tag tone="neutral">Você: {ROLE_LABEL[event.myRole].toLowerCase()}</Tag>}
      </div>

      {showDescription && event.description && <ExpandableText text={event.description} className={styles.description} />}

      <div className={styles.organizer}>
        <span className={styles.organizerLabel}>Organização</span>
        <PersonLine person={event.organizer} size={32} />
      </div>
    </header>
  );
}

function EventDescription({ description }: { description: string }) {
  return (
    <aside className={styles.descriptionPanel} aria-labelledby="event-description-title">
      <span className={styles.descriptionEyebrow}>Sobre o mural</span>
      <h2 id="event-description-title" className={styles.descriptionTitle}>Sobre este evento</h2>
      <ExpandableText text={description} className={styles.descriptionText} />
    </aside>
  );
}

function CoverPhoto({ event }: { event: MemoraEvent }) {
  const client = useQueryClient();
  const input = useRef<HTMLInputElement>(null);
  const [confirming, setConfirming] = useState(false);
  const [cropFile, setCropFile] = useState<File | null>(null);
  const canEdit = isOrganization(event);
  const upload = useMutation({
    mutationFn: async (file: File) => {
      const prepared = await prepareImage(file);
      return eventsApi.uploadCover(event.id, prepared.blob, prepared.name);
    },
    onSuccess: (updated) => {
      client.setQueryData(keys.event(event.id), updated);
      void client.invalidateQueries({ queryKey: keys.myEvents });
      setConfirming(false);
    },
  });
  const remove = useMutation({
    mutationFn: () => eventsApi.removeCover(event.id),
    onSuccess: (updated) => {
      client.setQueryData(keys.event(event.id), updated);
      void client.invalidateQueries({ queryKey: keys.myEvents });
      setConfirming(false);
    },
  });

  function choose(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (file) setCropFile(file);
  }

  if (!event.coverUrl && !canEdit) return null;
  return (
    <div className={styles.coverBlock}>
      <input
        ref={input}
        className={styles.coverInput}
        type="file"
        accept="image/jpeg,image/png,image/webp,image/gif,.heic,.heif"
        onChange={choose}
        tabIndex={-1}
      />
      <div className={`${styles.cover} ${event.coverUrl ? '' : styles.coverEmpty}`}>
        {event.coverUrl ? (
          <img src={apiAsset(event.coverUrl)} alt={`Capa de ${event.name}`} />
        ) : (
          <div className={styles.coverPlaceholder} aria-hidden="true">
            <LandscapeIcon />
            <span>Uma imagem para este encontro</span>
          </div>
        )}
        {canEdit && (
          <div className={styles.coverActions}>
            <Button
              variant="outline"
              className={styles.coverButton}
              icon={<CameraIcon />}
              onClick={() => input.current?.click()}
              loading={upload.isPending}
              disabled={remove.isPending}
            >
              {event.coverUrl ? 'Alterar capa' : 'Adicionar capa'}
            </Button>
            {event.coverUrl && !confirming && (
              <Button variant="outline" className={styles.coverButton} onClick={() => setConfirming(true)}>
                Remover
              </Button>
            )}
          </div>
        )}
      </div>
      {confirming && (
        <div className={styles.coverConfirm} role="group" aria-label="Confirmar remoção da capa">
          <span>Remover a foto de capa atual?</span>
          <Button variant="danger" onClick={() => remove.mutate()} loading={remove.isPending}>Remover capa</Button>
          <Button variant="quiet" onClick={() => setConfirming(false)} disabled={remove.isPending}>Cancelar</Button>
        </div>
      )}
      {upload.isError && <Notice tone="error" title="Não deu para enviar a capa">{friendlyMessage(upload.error)}</Notice>}
      {remove.isError && <Notice tone="error" title="Não deu para remover a capa">{friendlyMessage(remove.error)}</Notice>}
      {cropFile && (
        <ImageCropDialog
          file={cropFile}
          title="Recortar foto de capa"
          aspectRatio={16 / 6}
          onCancel={() => setCropFile(null)}
          onApply={(file) => {
            setCropFile(null);
            upload.mutate(file);
          }}
        />
      )}
    </div>
  );
}

function CameraIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M4 7.5h3l1.5-2h7l1.5 2h3a2 2 0 0 1 2 2v8.5a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9.5a2 2 0 0 1 2-2Z" fill="none" stroke="currentColor" strokeWidth="1.8" />
      <circle cx="12" cy="13.5" r="3.5" fill="none" stroke="currentColor" strokeWidth="1.8" />
    </svg>
  );
}

function LandscapeIcon() {
  return (
    <svg width="42" height="42" viewBox="0 0 48 48" aria-hidden="true">
      <circle cx="34" cy="14" r="5" fill="currentColor" opacity=".7" />
      <path d="M5 39 18 24l8 9 6-7 11 13H5Z" fill="currentColor" opacity=".5" />
    </svg>
  );
}

function EventTabs({ event }: { event: MemoraEvent }) {
  const base = `/events/${event.id}`;
  const tabs = [
    { to: base, label: 'Mural', end: true, show: true },
    { to: `${base}/people`, label: 'Pessoas', end: false, show: isParticipant(event) },
    { to: `${base}/invites`, label: 'Convites', end: false, show: isOrganization(event) },
    { to: `${base}/edit`, label: 'Editar', end: false, show: isOrganization(event) },
  ].filter((t) => t.show);
  if (tabs.length < 2) return null;
  return (
    <nav className={styles.tabs} aria-label="Seções do evento">
      {tabs.map((tab) => (
        <NavLink key={tab.to} to={tab.to} end={tab.end} className={styles.tab}>
          {tab.label}
        </NavLink>
      ))}
    </nav>
  );
}
