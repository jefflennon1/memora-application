import { describe, expect, it } from 'vitest';
import { commentTextLength, splitMentions, type Mention } from './interactions';

const person = { id: 'u2', username: 'bia.lima', displayName: 'Bia Lima', profileHidden: false };

describe('splitMentions', () => {
  it('uses UTF-16 positions, like String.prototype.slice (emoji before the mention)', () => {
    const text = 'Que foto 😍, @bia.lima!';
    const start = text.indexOf('@bia.lima');
    const mentions: Mention[] = [{ start, length: '@bia.lima'.length, person }];

    expect(splitMentions(text, mentions)).toEqual([
      { kind: 'text', text: 'Que foto 😍, ' },
      { kind: 'mention', text: '@bia.lima', mention: mentions[0] },
      { kind: 'text', text: '!' },
    ]);
  });

  it('keeps hidden and deleted people as the text the API sent', () => {
    const text = 'Oi Perfil privado e Usuário excluído';
    const segments = splitMentions(text, [
      { start: 3, length: 14, person: { id: 'x', profileHidden: true } },
      { start: 20, length: 16, person: { id: 'y', profileHidden: true } },
    ]);
    expect(segments.filter((s) => s.kind === 'mention').map((s) => s.text)).toEqual(['Perfil privado', 'Usuário excluído']);
  });

  it('ignores inconsistent positions instead of breaking the text', () => {
    expect(splitMentions('curto', [{ start: 2, length: 50, person }])).toEqual([{ kind: 'text', text: 'curto' }]);
  });
});

describe('commentTextLength', () => {
  it('counts code points after trimming, like the API', () => {
    expect(commentTextLength('  💛💛  ')).toBe(2);
  });
});
