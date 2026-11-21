import type { AnchorHTMLAttributes, ButtonHTMLAttributes, ReactNode } from 'react';
import { Link, type LinkProps } from 'react-router';
import styles from './Button.module.css';

type Variant = 'primary' | 'quiet' | 'outline' | 'danger';

interface CommonProps {
  variant?: Variant;
  block?: boolean;
  icon?: ReactNode;
}

function classes(variant: Variant, block?: boolean, extra?: string) {
  return [styles.button, styles[variant], block ? styles.block : '', extra ?? ''].filter(Boolean).join(' ');
}

export function Button({
  variant = 'primary',
  block,
  icon,
  loading,
  children,
  className,
  disabled,
  ...rest
}: CommonProps & ButtonHTMLAttributes<HTMLButtonElement> & { loading?: boolean }) {
  return (
    <button
      type="button"
      {...rest}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={classes(variant, block, className)}
    >
      {loading ? <span className={styles.spinner} aria-hidden="true" /> : icon}
      <span>{children}</span>
    </button>
  );
}

/** Link com aparência de botão, para navegações (como o login com Google). */
export function ButtonLink({
  variant = 'primary',
  block,
  icon,
  children,
  className,
  ...rest
}: CommonProps & AnchorHTMLAttributes<HTMLAnchorElement>) {
  return (
    <a {...rest} className={classes(variant, block, className)}>
      {icon}
      <span>{children}</span>
    </a>
  );
}

/** Navegação interna (react-router) com aparência de botão. */
export function LinkButton({
  variant = 'primary',
  block,
  icon,
  children,
  className,
  ...rest
}: CommonProps & LinkProps) {
  return (
    <Link {...rest} className={classes(variant, block, className)}>
      {icon}
      <span>{children}</span>
    </Link>
  );
}
