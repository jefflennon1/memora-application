import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import type { InvitePageContent, InvitePageInput } from '../../api/membership';
import { apiAsset } from '../../api/config';
import { Button } from '../../components/Button';
import { CharCounter, SelectField, TextAreaField } from '../../components/Fields';
import { TextField } from '../../components/TextField';
import styles from './Membership.module.css';

const TITLE_MAX = 120;
const BODY_MAX = 3000;
const FOOTER_MAX = 500;
const LOCATION_MAX = 300;

export interface InvitePageFormValue extends InvitePageInput {
  backgroundImageUrl?: string | null;
}

export function defaultInvitePage(eventName: string, eventDescription?: string | null): InvitePageFormValue {
  return {
    title: `Você está convidado para ${eventName}`,
    body: eventDescription?.trim() || 'Esperamos você para compartilhar este momento com a gente.',
    footer: '',
    location: '',
    backgroundColor: '#163f47',
    backgroundGradientColor: null,
    backgroundImageLayout: 'COVER',
  };
}

function inputFrom(value: InvitePageFormValue): InvitePageInput {
  return {
    title: value.title.trim(),
    body: value.body.trim(),
    footer: value.footer?.trim() || null,
    location: value.location?.trim() || null,
    backgroundColor: value.backgroundColor,
    backgroundGradientColor: value.backgroundGradientColor || null,
    backgroundImageLayout: value.backgroundImageLayout,
  };
}

export function InvitePageForm({
  initial,
  submitLabel,
  pending,
  onSubmit,
  onCancel,
}: {
  initial: InvitePageFormValue;
  submitLabel: string;
  pending: boolean;
  onSubmit: (page: InvitePageInput, background: File | null, removeBackground: boolean) => void;
  onCancel?: () => void;
}) {
  const [value, setValue] = useState(initial);
  const [background, setBackground] = useState<File | null>(null);
  const [removeBackground, setRemoveBackground] = useState(false);
  const [touched, setTouched] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  const hasExistingImage = !!initial.backgroundImageUrl && !removeBackground;
  const selectedImage = useMemo(() => background ? URL.createObjectURL(background) : null, [background]);

  useEffect(() => {
    return () => {
      if (selectedImage) URL.revokeObjectURL(selectedImage);
    };
  }, [selectedImage]);

  const titleError = touched && !value.title.trim() ? 'Informe o título do convite.' : value.title.length > TITLE_MAX ? `Use até ${TITLE_MAX} caracteres.` : null;
  const bodyError = touched && !value.body.trim() ? 'Informe o texto do convite.' : value.body.length > BODY_MAX ? `Use até ${BODY_MAX} caracteres.` : null;
  const footerError = value.footer && value.footer.length > FOOTER_MAX ? `Use até ${FOOTER_MAX} caracteres.` : null;
  const locationError = value.location && value.location.length > LOCATION_MAX ? `Use até ${LOCATION_MAX} caracteres.` : null;

  function set<K extends keyof InvitePageFormValue>(key: K, next: InvitePageFormValue[K]) {
    setValue((current) => ({ ...current, [key]: next }));
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    setTouched(true);
    if (titleError || bodyError || footerError || locationError || !value.title.trim() || !value.body.trim()) return;
    onSubmit(inputFrom(value), background, removeBackground);
  }

  return (
    <form className={styles.pageForm} onSubmit={submit} noValidate>
      <div className={styles.formSection}>
        <h3>Conteúdo da página</h3>
        <TextField
          label="Título"
          value={value.title}
          onChange={(event) => set('title', event.target.value)}
          maxLength={TITLE_MAX + 1}
          error={titleError}
        />
        <TextAreaField
          label="Texto do convite"
          value={value.body}
          onChange={(event) => set('body', event.target.value)}
          rows={6}
          maxLength={BODY_MAX + 1}
          error={bodyError}
          counter={<CharCounter used={value.body.length} max={BODY_MAX} />}
        />
        <TextAreaField
          label="Footer"
          value={value.footer ?? ''}
          onChange={(event) => set('footer', event.target.value)}
          rows={3}
          maxLength={FOOTER_MAX + 1}
          error={footerError}
          hint="Uma despedida, observação ou assinatura para o final do convite."
        />
        <TextField
          label="Localização"
          value={value.location ?? ''}
          onChange={(event) => set('location', event.target.value)}
          maxLength={LOCATION_MAX + 1}
          error={locationError}
          hint="Informe o nome do local e o endereço. O mapa será exibido no final da página."
          autoComplete="street-address"
        />
      </div>

      <fieldset className={styles.backgroundFields}>
        <legend>Background</legend>
        <div className={styles.backgroundUpload}>
          {(selectedImage || hasExistingImage) && (
            <img
              src={selectedImage ?? (initial.backgroundImageUrl ? apiAsset(initial.backgroundImageUrl) : '')}
              alt="Background atual do convite"
            />
          )}
          <input
            ref={input}
            className={styles.fileInput}
            type="file"
            accept="image/jpeg,image/png,image/webp,image/gif"
            onChange={(event) => {
              const file = event.target.files?.[0] ?? null;
              setBackground(file);
              if (file) setRemoveBackground(false);
            }}
          />
          <div className={styles.rowActions}>
            <Button variant="outline" onClick={() => input.current?.click()} disabled={pending}>
              {selectedImage || hasExistingImage ? 'Trocar imagem ou GIF' : 'Enviar imagem ou GIF'}
            </Button>
            {(selectedImage || hasExistingImage) && (
              <Button
                variant="quiet"
                onClick={() => {
                  setBackground(null);
                  setRemoveBackground(true);
                  if (input.current) input.current.value = '';
                }}
                disabled={pending}
              >
                Remover imagem
              </Button>
            )}
          </div>
          <span className={styles.meta}>JPEG, PNG, WebP ou GIF. A cor abaixo também serve como fallback.</span>
        </div>

        {(selectedImage || hasExistingImage) && (
          <SelectField
            label="Disposição da imagem"
            value={value.backgroundImageLayout}
            onChange={(event) => set('backgroundImageLayout', event.target.value as 'COVER' | 'REPEAT')}
          >
            <option value="COVER">Preencher a tela inteira</option>
            <option value="REPEAT">Repetir a imagem</option>
          </SelectField>
        )}

        <div className={styles.colorFields}>
          <label>
            <span>Cor do background</span>
            <input type="color" value={value.backgroundColor} onChange={(event) => set('backgroundColor', event.target.value)} />
          </label>
          <label className={styles.gradientToggle}>
            <input
              type="checkbox"
              checked={!!value.backgroundGradientColor}
              onChange={(event) => set('backgroundGradientColor', event.target.checked ? '#ec3f97' : null)}
            />
            <span>Usar gradiente</span>
          </label>
          {value.backgroundGradientColor && (
            <label>
              <span>Segunda cor</span>
              <input
                type="color"
                value={value.backgroundGradientColor}
                onChange={(event) => set('backgroundGradientColor', event.target.value)}
              />
            </label>
          )}
        </div>
      </fieldset>

      <div className={styles.rowActions}>
        <Button type="submit" loading={pending}>{submitLabel}</Button>
        {onCancel && <Button variant="quiet" onClick={onCancel} disabled={pending}>Cancelar</Button>}
      </div>
    </form>
  );
}

export function pageValueFrom(content: InvitePageContent): InvitePageFormValue {
  return { ...content };
}
