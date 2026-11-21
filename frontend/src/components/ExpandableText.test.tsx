import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { ExpandableText } from './ExpandableText';

describe('ExpandableText', () => {
  it.each([100, 110, 199, 200])('shows all %i characters without a control', (length) => {
    const text = 'a'.repeat(length);
    render(<ExpandableText text={text} />);

    expect(screen.getByText(text)).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('hides characters after 200 when the text has more than 200', async () => {
    const text = `${'a'.repeat(200)}b`;
    const user = userEvent.setup();
    render(<ExpandableText text={text} />);

    expect(screen.getByText('a'.repeat(200))).toBeInTheDocument();
    expect(screen.queryByText(text)).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Ver mais' }));
    expect(screen.getByText(text)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ver menos' })).toHaveAttribute('aria-expanded', 'true');

    await user.click(screen.getByRole('button', { name: 'Ver menos' }));
    expect(screen.queryByText(text)).not.toBeInTheDocument();
  });

  it('counts an emoji as one character', () => {
    const text = `${'a'.repeat(199)}😊`;
    render(<ExpandableText text={text} />);

    expect(screen.getByText(text)).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});
