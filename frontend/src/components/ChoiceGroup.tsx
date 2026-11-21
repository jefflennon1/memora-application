import { useId, type ReactNode } from 'react';
import styles from './ChoiceGroup.module.css';

export interface Choice<T extends string> {
  value: T;
  title: string;
  description: ReactNode;
  visual?: ReactNode;
}

/** Grupo de opções em cartões (radio nativo por baixo, navegável pelo teclado). */
export function ChoiceGroup<T extends string>({
  legend,
  name,
  value,
  onChange,
  choices,
  hideLegend,
}: {
  legend: string;
  name: string;
  value: T;
  onChange: (value: T) => void;
  choices: Choice<T>[];
  hideLegend?: boolean;
}) {
  const id = useId();
  return (
    <fieldset className={styles.group}>
      <legend className={hideLegend ? 'visually-hidden' : styles.legend}>{legend}</legend>
      <div className={styles.options}>
        {choices.map((choice) => {
          const inputId = `${id}-${choice.value}`;
          return (
            <label key={choice.value} htmlFor={inputId} className={styles.option}>
              <input
                id={inputId}
                type="radio"
                name={name}
                value={choice.value}
                checked={value === choice.value}
                onChange={() => onChange(choice.value)}
                className={styles.radio}
              />
              {choice.visual}
              <span className={styles.text}>
                <span className={styles.title}>{choice.title}</span>
                <span className={styles.description}>{choice.description}</span>
              </span>
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}
