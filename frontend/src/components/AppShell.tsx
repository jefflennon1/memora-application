import { useState } from 'react';
import { NavLink, Outlet, Link } from 'react-router';
import { useCurrentUser } from '../auth/useCurrentUser';
import { ThemeToggle } from '../theme/ThemeToggle';
import { Avatar } from './Avatar';
import { Wordmark } from './Wordmark';
import styles from './AppShell.module.css';

const NAV = [
  { to: '/', label: 'Início', icon: <HomeIcon />, end: true },
  { to: '/events', label: 'Eventos', icon: <EventsIcon />, end: false },
  { to: '/explore', label: 'Explorar', icon: <ExploreIcon />, end: false },
  { to: '/profile', label: 'Perfil', icon: <ProfileIcon />, end: false },
];

/**
 * Estrutura das telas autenticadas: barra superior + navegação inferior no celular,
 * coluna lateral a partir de telas médias.
 */
export function AppShell() {
  const me = useCurrentUser();
  const [collapsed, setCollapsed] = useState(() => {
    try {
      return window.localStorage.getItem('memora.sidebar.collapsed') === 'true';
    } catch {
      return false;
    }
  });

  function toggleSidebar() {
    setCollapsed((current) => {
      const next = !current;
      try {
        window.localStorage.setItem('memora.sidebar.collapsed', String(next));
      } catch {
        // A navegação continua funcionando mesmo se o navegador bloquear o armazenamento local.
      }
      return next;
    });
  }

  return (
    <div className={`${styles.shell} ${collapsed ? styles.shellCollapsed : ''}`}>
      <a href="#conteudo" className={styles.skip}>
        Pular para o conteúdo
      </a>

      <header className={styles.top}>
        <Link to="/" className={styles.brand} aria-label="Memora, início">
          <Wordmark />
        </Link>
        <div className={styles.topActions}>
          <ThemeToggle />
          <Link to="/profile" className={styles.me} aria-label={`Seu perfil, ${me.displayName}`}>
            <Avatar name={me.displayName} src={me.photoUrl} size={36} />
          </Link>
        </div>
      </header>

      <nav className={styles.nav} aria-label="Principal">
        <div className={styles.navHeader}>
          <Link to="/" className={styles.navBrand} aria-label="Memora, início">
            <span className={styles.fullBrand}><Wordmark /></span>
            <span className={styles.compactBrand} aria-hidden="true">m.</span>
          </Link>
          <button
            type="button"
            className={styles.collapseButton}
            onClick={toggleSidebar}
            aria-label={collapsed ? 'Expandir menu lateral' : 'Minimizar menu lateral'}
            aria-expanded={!collapsed}
            title={collapsed ? 'Expandir menu' : 'Minimizar menu'}
          >
            <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
              <path d={collapsed ? 'm9 5 7 7-7 7' : 'm15 5-7 7 7 7'} fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </button>
        </div>
        <ul className={styles.navList}>
          {NAV.map((item) => (
            <li key={item.to}>
              <NavLink
                to={item.to}
                end={item.end}
                className={styles.navLink}
                title={collapsed ? item.label : undefined}
              >
                {item.icon}
                <span>{item.label}</span>
              </NavLink>
            </li>
          ))}
        </ul>
        <div className={styles.navTheme}>
          <ThemeToggle withLabel={!collapsed} />
        </div>
        <Link
          to="/profile"
          className={styles.navMe}
          aria-label={collapsed ? `Seu perfil, ${me.displayName}` : undefined}
          title={collapsed ? me.displayName : undefined}
        >
          <Avatar name={me.displayName} src={me.photoUrl} size={40} />
          <span className={styles.navMeText}>
            <span className={styles.navMeName}>{me.displayName}</span>
            <span className={styles.navMeHandle}>@{me.username}</span>
          </span>
        </Link>
      </nav>

      <main id="conteudo" className={styles.main} tabIndex={-1}>
        <Outlet />
      </main>
    </div>
  );
}

function HomeIcon() {
  return (
    <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true">
      <path d="M4 10.5 12 4l8 6.5V19a1 1 0 0 1-1 1h-4.5v-5.5h-5V20H5a1 1 0 0 1-1-1z" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
    </svg>
  );
}

function EventsIcon() {
  return (
    <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true">
      <rect x="4" y="5.5" width="16" height="14.5" rx="2.5" fill="none" stroke="currentColor" strokeWidth="1.8" />
      <path d="M8 3.5v4M16 3.5v4M4 10h16" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      <circle cx="15.5" cy="15" r="1.6" fill="currentColor" />
    </svg>
  );
}

function ExploreIcon() {
  return (
    <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true">
      <circle cx="12" cy="12" r="8.2" fill="none" stroke="currentColor" strokeWidth="1.8" />
      <path d="m14.9 9.1-1.7 4.1-4.1 1.7 1.7-4.1z" fill="currentColor" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
    </svg>
  );
}

function ProfileIcon() {
  return (
    <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true">
      <circle cx="12" cy="8.5" r="3.8" fill="none" stroke="currentColor" strokeWidth="1.8" />
      <path d="M4.5 20c1.2-3.6 4-5.4 7.5-5.4s6.3 1.8 7.5 5.4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  );
}
