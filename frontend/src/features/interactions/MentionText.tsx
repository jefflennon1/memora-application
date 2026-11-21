import { splitMentions, type Mention } from '../../api/interactions';
import { ExpandableText } from '../../components/ExpandableText';
import styles from './Interactions.module.css';

/**
 * Texto de comentário com as @menções destacadas, nas posições que a API devolve.
 * A API já troca o trecho por "@usuário atual", "Perfil privado" ou "Usuário excluído".
 */
export function MentionText({ text, mentions }: { text: string; mentions?: Mention[] }) {
  return (
    <ExpandableText
      text={text}
      className={styles.commentText}
      renderText={(visibleText) => splitMentions(visibleText, mentions).map((segment, i) => {
        if (segment.kind === 'text') return <span key={i}>{segment.text}</span>;
        const person = segment.mention.person;
        if (!person || person.profileHidden) {
          return (
            <span key={i} className={styles.mentionHidden}>
              {segment.text}
            </span>
          );
        }
        return (
          <span key={i} className={styles.mention} title={person.displayName ?? undefined}>
            {segment.text}
          </span>
        );
      })}
    />
  );
}
