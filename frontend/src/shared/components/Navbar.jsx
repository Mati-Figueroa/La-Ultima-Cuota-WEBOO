import React, { useState, useEffect } from 'react';
import { Navbar, Nav, Container, Badge, Dropdown } from 'react-bootstrap';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import DailyRewardButton from './DailyRewardButton';
import UserSearch from './UserSearch';
import api from '../services/api';

const ROUTE_LABELS = {
  '/dashboard': 'Inicio',
  '/establo': 'Establo',
  '/mercado': 'Mercado',
  '/subastas': 'Subastas',
  '/calendario': 'Carreras',
  '/gacha': 'Gacha',
  '/historial': 'Historial',
  '/simulador': 'Simulador',
};

function NavigationBar() {
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [wins, setWins] = useState([]);

  useEffect(() => {
    if (isAuthenticated) {
      api.get('/api/history/wins')
        .then((res) => {
          if (res.data.success) setWins(res.data.data.wins);
        })
        .catch(() => {});
    }
  }, [isAuthenticated]);

  const handleLogout = () => {
    logout();
    navigate('/');
  };

  const crumbs = location.pathname
    .split('/')
    .filter(Boolean)
    .map((segment, idx, arr) => {
      const path = '/' + arr.slice(0, idx + 1).join('/');
      const label = ROUTE_LABELS[path] || segment;
      return { path, label, isLast: idx === arr.length - 1 };
    });

  return (
    <Navbar
      sticky="top"
      variant="dark"
      expand="lg"
      style={{ backgroundColor: 'var(--color-text-dark)', padding: '0.5rem 0' }}
      className="shadow-sm"
    >
      <Container fluid>
        <Navbar.Brand
          as={Link}
          to="/dashboard"
          className="font-heading fw-bold d-flex align-items-center gap-2"
          style={{ fontSize: '1.25rem' }}
        >
          <img
            src="/logo.svg"
            alt="La Última Cuota"
            style={{ height: '36px', width: 'auto' }}
          />
          La Última Cuota
        </Navbar.Brand>

        <Navbar.Toggle aria-controls="main-navbar" />
        <Navbar.Collapse id="main-navbar">
          {isAuthenticated && (
            <Nav className="me-auto align-items-center">
              <span className="text-light opacity-50 mx-2">/</span>
              {crumbs.map((crumb) => (
                <React.Fragment key={crumb.path}>
                  {crumb.isLast ? (
                    <span className="text-light fw-medium" style={{ fontSize: '0.9rem' }}>
                      {crumb.label}
                    </span>
                  ) : (
                    <>
                      <Link
                        to={crumb.path}
                        className="text-light text-decoration-none opacity-75"
                        style={{ fontSize: '0.9rem' }}
                      >
                        {crumb.label}
                      </Link>
                      <span className="text-light opacity-50 mx-2">/</span>
                    </>
                  )}
                </React.Fragment>
              ))}
            </Nav>
          )}

          <Nav className="ms-auto align-items-center gap-2">
            {isAuthenticated && user && (
              <>
                <UserSearch />

                <span
                  className="font-mono fw-bold px-2 py-1 rounded d-inline-flex align-items-center gap-1"
                  style={{
                    backgroundColor: 'var(--color-primary)',
                    color: '#fff',
                    fontSize: '0.85rem',
                  }}
                >
                  <i className="bi bi-coin" style={{ fontSize: '0.8rem' }}></i>
                  ${(user.saldo ?? 0).toLocaleString('es-CL')} CC
                </span>

                <DailyRewardButton variant="outline-success" size="sm" />

                <Dropdown align="end">
                  <Dropdown.Toggle
                    variant="outline-light"
                    size="sm"
                    id="notif-dropdown"
                    style={{ borderColor: 'rgba(255,255,255,0.3)' }}
                  >
                    <i className="bi bi-bell"></i>
                    {wins.length > 0 && (
                      <Badge bg="success" pill className="ms-1" style={{ fontSize: '0.65rem' }}>
                        {wins.length}
                      </Badge>
                    )}
                  </Dropdown.Toggle>
                  <Dropdown.Menu style={{ minWidth: '280px' }}>
                    <Dropdown.Header className="fw-bold">Victorias recientes</Dropdown.Header>
                    {wins.length === 0 ? (
                      <Dropdown.ItemText className="text-muted">
                        Sin victorias recientes
                      </Dropdown.ItemText>
                    ) : (
                      wins.map((w, idx) => (
                        <Dropdown.Item key={idx} className="d-flex flex-column">
                          <span className="fw-medium">{w.carrera_nombre || `Carrera #${w.carrera_id}`}</span>
                          <small className="text-muted">
                            +${Number(w.ganancia).toLocaleString('es-CL')} CC -{' '}
                            {new Date(w.created_at).toLocaleDateString('es-CL')}
                          </small>
                        </Dropdown.Item>
                      ))
                    )}
                  </Dropdown.Menu>
                </Dropdown>

                <Dropdown align="end">
                  <Dropdown.Toggle
                    variant="outline-light"
                    size="sm"
                    id="user-dropdown"
                    style={{ borderColor: 'rgba(255,255,255,0.3)', display: 'flex', alignItems: 'center', gap: '6px' }}
                  >
                    {user.profilePhoto ? (
                      <img
                        src={user.profilePhoto}
                        alt=""
                        style={{ width: '22px', height: '22px', borderRadius: '50%', objectFit: 'cover' }}
                      />
                    ) : (
                      <i className="bi bi-person-circle"></i>
                    )}
                    {user.username}
                  </Dropdown.Toggle>
                  <Dropdown.Menu>
                    <Dropdown.ItemText className="fw-medium d-flex align-items-center gap-2">
                      {user.profilePhoto ? (
                        <img
                          src={user.profilePhoto}
                          alt=""
                          style={{ width: '32px', height: '32px', borderRadius: '50%', objectFit: 'cover' }}
                        />
                      ) : (
                        <i className="bi bi-person-circle" style={{ fontSize: '1.5rem' }}></i>
                      )}
                      <div>
                        <div className="fw-medium">{user.username}</div>
                        <div className="text-muted small">{user.email}</div>
                      </div>
                    </Dropdown.ItemText>
                    <Dropdown.Divider />
                    <Dropdown.Item as={Link} to={`/perfil/${user.id}`}>
                      <i className="bi bi-person me-2"></i>Mi Perfil
                    </Dropdown.Item>
                    <Dropdown.Item as={Link} to="/editar-perfil">
                      <i className="bi bi-pencil me-2"></i>Editar Perfil
                    </Dropdown.Item>
                    <Dropdown.Divider />
                    <Dropdown.Item onClick={handleLogout}>
                      <i className="bi bi-box-arrow-right me-2"></i>Cerrar Sesión
                    </Dropdown.Item>
                  </Dropdown.Menu>
                </Dropdown>
              </>
            )}
            {!isAuthenticated && (
              <>
                <Nav.Link as={Link} to="/" className="text-light">
                  Iniciar Sesión
                </Nav.Link>
                <Nav.Link as={Link} to="/" className="text-light">
                  Registrarse
                </Nav.Link>
              </>
            )}
          </Nav>
        </Navbar.Collapse>
      </Container>
    </Navbar>
  );
}

export default NavigationBar;
