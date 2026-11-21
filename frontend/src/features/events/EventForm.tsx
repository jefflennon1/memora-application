import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent, type ReactNode } from 'react';
import { eventsApi, type EventModality, type EventPrivacy, type MemoraEvent } from '../../api/events';
import { Button } from '../../components/Button';
import { ChoiceGroup } from '../../components/ChoiceGroup';
import { CharCounter, SelectField, TextAreaField } from '../../components/Fields';
import { Notice } from '../../components/Notice';
import { TextField } from '../../components/TextField';
import { todayISO } from '../../lib/format';
import {
  ADDRESS_MAX,
  CITY_MAX,
  DESCRIPTION_MAX,
  NAME_MAX,
  emptyValues,
  validate,
  valuesFrom,
  type EventFormErrors,
  type EventFormValues,
} from './eventFormModel';
import styles from './EventForm.module.css';

const MODALITIES = [
  { value: 'IN_PERSON' as EventModality, title: 'Presencial', description: 'Num lugar, com cidade e endereço.' },
  { value: 'ONLINE' as EventModality, title: 'Online', description: 'Por chamada de vídeo ou transmissão.' },
  { value: 'NO_LOCATION' as EventModality, title: 'Sem local', description: 'Uma data especial, sem encontro marcado.' },
];

const PRIVACIES = [
  {
    value: 'PRIVATE' as EventPrivacy,
    title: 'Privado',
    description: 'Só quem participa vê o evento e o mural. Para os demais, ele não existe.',
  },
  {
    value: 'PUBLIC' as EventPrivacy,
    title: 'Público',
    description: 'Aparece na busca e qualquer pessoa vê os dados e as memórias públicas.',
  },
];

export function useCategories() {
  return useQuery({
    queryKey: ['event-categories'],
    queryFn: ({ signal }) => eventsApi.categories(signal),
    staleTime: Infinity,
  });
}

export function EventForm({
  initial,
  canChangePrivacy = true,
  submitLabel,
  pending,
  serverErrors,
  generalError,
  onSubmit,
  footer,
}: {
  initial?: MemoraEvent;
  canChangePrivacy?: boolean;
  submitLabel: string;
  pending?: boolean;
  serverErrors?: EventFormErrors;
  generalError?: string | null;
  onSubmit: (values: EventFormValues) => void;
  footer?: ReactNode;
}) {
  const categories = useCategories();
  const [values, setValues] = useState<EventFormValues>(() => (initial ? valuesFrom(initial) : emptyValues()));
  const [submitted, setSubmitted] = useState(false);
  const localErrors = validate(values, initial);
  const errors: EventFormErrors = { ...(submitted ? localErrors : {}), ...serverErrors };

  const set = <K extends keyof EventFormValues>(key: K, value: EventFormValues[K]) =>
    setValues((v) => ({ ...v, [key]: value }));

  function submit(event: FormEvent) {
    event.preventDefault();
    setSubmitted(true);
    if (Object.keys(localErrors).length) return;
    onSubmit(values);
  }

  const today = todayISO();
  const startMin = !initial || initial.startDate >= today ? today : undefined;

  return (
    <form className={styles.form} onSubmit={submit} noValidate>
      <fieldset className={styles.group}>
        <legend className={styles.legend}>Sobre o evento</legend>
        <TextField
          label="Nome do evento"
          value={values.name}
          onChange={(e) => set('name', e.target.value)}
          maxLength={NAME_MAX}
          placeholder="Ex.: Chá de Fraldas do Liam"
          error={errors.name}
          required
        />
        <TextAreaField
          label="Descrição (opcional)"
          value={values.description}
          onChange={(e) => set('description', e.target.value)}
          maxLength={DESCRIPTION_MAX}
          rows={4}
          counter={<CharCounter used={values.description.length} max={DESCRIPTION_MAX} />}
          error={errors.description}
        />
        <SelectField
          label="Categoria (opcional)"
          value={values.categoryCode}
          onChange={(e) => set('categoryCode', e.target.value)}
          error={errors.categoryCode}
          disabled={categories.isPending}
        >
          <option value="">Sem categoria</option>
          {categories.data?.map((c) => (
            <option key={c.code} value={c.code}>
              {c.name}
            </option>
          ))}
        </SelectField>
      </fieldset>

      <fieldset className={styles.group}>
        <legend className={styles.legend}>Quando</legend>
        <div className={styles.pair}>
          <TextField
            label="Data de início"
            type="date"
            value={values.startDate}
            min={startMin}
            onChange={(e) => set('startDate', e.target.value)}
            error={errors.startDate}
            required
          />
          <TextField
            label="Horário (opcional)"
            type="time"
            value={values.startTime}
            onChange={(e) => set('startTime', e.target.value)}
            error={errors.startTime}
          />
        </div>
        <div className={styles.pair}>
          <TextField
            label="Data de término (opcional)"
            type="date"
            value={values.endDate}
            min={values.startDate || undefined}
            onChange={(e) => set('endDate', e.target.value)}
            error={errors.endDate}
            hint="Para eventos de mais de um dia."
          />
          <TextField
            label="Término (opcional)"
            type="time"
            value={values.endTime}
            onChange={(e) => set('endTime', e.target.value)}
            error={errors.endTime}
          />
        </div>
      </fieldset>

      <fieldset className={styles.group}>
        <legend className={styles.legend}>Onde</legend>
        <ChoiceGroup
          legend="Modalidade"
          hideLegend
          name="modality"
          value={values.modality}
          onChange={(m) => set('modality', m)}
          choices={MODALITIES}
        />
        {errors.modality && <p className={styles.error}>{errors.modality}</p>}
        {values.modality === 'IN_PERSON' && (
          <div className={styles.pair}>
            <TextField
              label="Cidade (opcional)"
              value={values.city}
              onChange={(e) => set('city', e.target.value)}
              maxLength={CITY_MAX}
              autoComplete="address-level2"
              error={errors.city}
            />
            <TextField
              label="Endereço (opcional)"
              value={values.address}
              onChange={(e) => set('address', e.target.value)}
              maxLength={ADDRESS_MAX}
              autoComplete="street-address"
              error={errors.address}
              hint="Só quem participa vê o endereço."
            />
          </div>
        )}
      </fieldset>

      {canChangePrivacy && (
        <fieldset className={styles.group}>
          <legend className={styles.legend}>Quem vê</legend>
          <ChoiceGroup
            legend="Privacidade do evento"
            hideLegend
            name="privacy"
            value={values.privacy}
            onChange={(p) => set('privacy', p)}
            choices={PRIVACIES}
          />
          {initial && initial.privacy === 'PRIVATE' && values.privacy === 'PUBLIC' && (
            <Notice title="O que já foi publicado continua restrito">
              Memórias criadas enquanto o evento era privado continuam só para participantes, até cada autor decidir
              torná-las públicas.
            </Notice>
          )}
        </fieldset>
      )}

      {generalError && (
        <Notice tone="error" title="Não deu para salvar">
          {generalError}
        </Notice>
      )}

      <div className={styles.actions}>
        <Button type="submit" loading={pending}>
          {submitLabel}
        </Button>
        {footer}
      </div>
    </form>
  );
}
