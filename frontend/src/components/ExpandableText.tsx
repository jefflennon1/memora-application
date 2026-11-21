import { useEffect, useId, useState, type ReactNode } from 'react';
import styles from './ExpandableText.module.css';

/** Política global: até 200 caracteres aparecem por inteiro; acima disso, a prévia tem 200. */
export const TEXT_PREVIEW_LENGTH = 200;
export const TEXT_COLLAPSE_THRESHOLD = 200;

interface ExpandableTextProps {
  text: string;
  className?: string;
  renderText?: (visibleText: string) => ReactNode;
}

export function ExpandableText({ text, className, renderText = (value) => value }: ExpandableTextProps) {
  const [expanded, setExpanded] = useState(false);
  const contentId = useId();
  const characters = [...text];
  const collapsible = characters.length > TEXT_COLLAPSE_THRESHOLD;
  const visibleText = collapsible && !expanded ? characters.slice(0, TEXT_PREVIEW_LENGTH).join('') : text;

  useEffect(() => setExpanded(false), [text]);

  return (
    <p className={className}>
      <span id={contentId}>{renderText(visibleText)}</span>
      {collapsible && !expanded && <span aria-hidden="true">…</span>}
      {collapsible && (
        <button
          type="button"
          className={styles.toggle}
          aria-expanded={expanded}
          aria-controls={contentId}
          onClick={() => setExpanded((current) => !current)}
        >
          {expanded ? 'Ver menos' : 'Ver mais'}
        </button>
      )}
    </p>
  );
}
