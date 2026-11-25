import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { Container, Row, Col, Card, Button, Badge, Modal, Nav } from 'react-bootstrap';
import { useAuth } from '../../../shared/context/AuthContext';
import { useToast } from '../../../shared/context/ToastContext';
import api from '../../../shared/services/api';

export const ESPECIALIDADES = { lluvia: '🌧️ Lluvia', barro: '🟤 Barro', sequia: '☀️ Sequía' };

const TABS = [
  { key: 'disponibles', label: 'Disponibles' },
  { key: 'todos', label: 'Todos' },
  { key: 'mios', label: 'Mis jinetes' },
];

function HabilidadBar({ valor }) {
  const color = valor >= 80 ? '#6f42c1' : valor >= 60 ? 'var(--color-primary)' : valor >= 45 ? '#ffc107' : '#6c757d';
  return (
    <div className="d-flex align-items-center gap-2">
      <div style={{ width: '80px', height: '6px', backgroundColor: '#e9ecef', borderRadius: '3px' }}>
        <div style={{ width: `${valor}%`, height: '100%', backgroundColor: color, borderRadius: '3px' }}></div>
      </div>
      <span className="font-mono fw-bold" style={{ fontSize: '0.8rem' }}>{valor}</span>
    </div>
  );
}

export function JockeyStats({ jinete }) {
  return (
    <div className="d-flex flex-column gap-1" style={{ fontSize: '0.85rem' }}>
      <div className="d-flex justify-content-between align-items-center">
        <span className="text-muted">Habilidad</span>
        <HabilidadBar valor={jinete.habilidad} />
      </div>
      <div className="d-flex justify-content-between">
        <span className="text-muted">Peso</span>
        <span className="font-mono">{jinete.peso} kg</span>
      </div>
      <div className="d-flex justify-content-between">
        <span className="text-muted">Experiencia</span>
        <span className="font-mono">{jinete.experiencia ?? 0} carrera{jinete.experiencia === 1 ? '' : 's'}</span>
      </div>
      <div className="d-flex justify-content-between">
        <span className="text-muted">Especialidad</span>
        <span>{jinete.especialidad ? ESPECIALIDADES[jinete.especialidad] : 'Ninguna'}</span>
      </div>
      <div className="d-flex justify-content-between">
        <span className="text-muted">Contratación</span>
        <span className="font-mono fw-bold">${Number(jinete.precio_contratacion).toLocaleString('es-CL')} CC</span>
      </div>
    </div>
  );
}

function JockeyList() {
  const { user, updateUserSaldo } = useAuth();
  const showToast = useToast();
  const [tab, setTab] = useState('disponibles');
  const [jinetes, setJinetes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [confirm, setConfirm] = useState({ show: false, accion: null, jinete: null });
  const [saving, setSaving] = useState(false);

  const fetchJinetes = useCallback(async () => {
    setLoading(true);
    try {
      const url = tab === 'mios' ? '/api/jockeys/mine' : `/api/jockeys?estado=${tab}`;
      const response = await api.get(url);
      if (response.data.success) setJinetes(response.data.data.jinetes);
    } catch {
      showToast('Error al cargar los jinetes', 'error');
    } finally {
      setLoading(false);
    }
  }, [tab, showToast]);

  useEffect(() => { fetchJinetes(); }, [fetchJinetes]);

  const ejecutar = async () => {
    const { accion, jinete } = confirm;
    setSaving(true);
    try {
      const response = await api.post(`/api/jockeys/${jinete.id}/${accion === 'contratar' ? 'hire' : 'fire'}`);
      const data = response.data.data;
      if (data.saldo !== undefined) updateUserSaldo(data.saldo);
      showToast(data.message, 'success');
      setConfirm({ show: false, accion: null, jinete: null });
      fetchJinetes();
    } catch (err) {
      showToast(err.response?.data?.error || 'No se pudo completar la acción', 'error');
    } finally {
      setSaving(false);
    }
  };

  const esMio = (j) => j.empleador && user && j.empleador.id === user.id;

  return (
    <Container className="py-4">
      <Row className="mb-4 align-items-center">
        <Col>
          <h2 className="font-heading fw-bold mb-1" style={{ color: 'var(--color-text-dark)' }}>
            <i className="bi bi-person-badge me-2" style={{ color: 'var(--color-primary)' }}></i>Jinetes
          </h2>
          <p className="text-muted mb-0">
            Contrata jinetes para tus caballos: suman velocidad y, si son expertos, aguantan mejor el clima.
          </p>
        </Col>
      </Row>

      <Nav variant="pills" className="mb-4 gap-2" activeKey={tab} onSelect={(k) => setTab(k)}>
        {TABS.map((t) => (
          <Nav.Item key={t.key}>
            <Nav.Link eventKey={t.key} style={{ borderRadius: '8px' }}>{t.label}</Nav.Link>
          </Nav.Item>
        ))}
      </Nav>

      {loading ? (
        <div className="py-5 text-center"><div className="spinner-border text-success" role="status"></div></div>
      ) : jinetes.length === 0 ? (
        <Card className="border-0 shadow-sm">
          <Card.Body className="text-center py-5 text-muted">
            {tab === 'mios' ? 'Aún no has contratado jinetes.' : 'No hay jinetes para mostrar.'}
          </Card.Body>
        </Card>
      ) : (
        <Row className="g-3">
          {jinetes.map((j) => (
            <Col xs={12} sm={6} lg={4} key={j.id}>
              <Card className="h-100 border-0 shadow-sm" data-testid="jinete-card">
                <Card.Body className="d-flex flex-column">
                  <div className="d-flex justify-content-between align-items-start mb-2 gap-2">
                    <h5 className="font-heading fw-bold mb-0">
                      <Link to={`/jinete/${j.id}`} className="text-decoration-none" style={{ color: 'var(--color-text-dark)' }}>
                        {j.nombre}
                      </Link>
                    </h5>
                    {tab === 'mios' || esMio(j) ? (
                      <Badge bg="primary">Tuyo</Badge>
                    ) : j.disponible === false ? (
                      <Badge bg="secondary">Contratado</Badge>
                    ) : (
                      <Badge bg="success">Disponible</Badge>
                    )}
                  </div>
                  <JockeyStats jinete={j} />
                  {j.empleador && !esMio(j) && (
                    <p className="text-muted mt-2 mb-0" style={{ fontSize: '0.8rem' }}>
                      Trabaja para <Link to={`/perfil/${j.empleador.id}`}>{j.empleador.username}</Link>
                    </p>
                  )}
                  {(tab === 'mios' || esMio(j)) && (
                    <p className="text-muted mt-2 mb-0" style={{ fontSize: '0.8rem' }}>
                      {j.caballo_nombre ? <>Monta a <strong>{j.caballo_nombre}</strong></> : 'Sin caballo asignado'}
                    </p>
                  )}
                  <div className="mt-auto pt-3 d-flex gap-2">
                    <Link to={`/jinete/${j.id}`} className="btn btn-outline-primary btn-sm" style={{ borderRadius: '6px' }}>
                      <i className="bi bi-eye me-1"></i>Ver ficha
                    </Link>
                    {j.disponible !== false && tab !== 'mios' && (
                      <Button size="sm" variant="primary" style={{ borderRadius: '6px' }}
                              onClick={() => setConfirm({ show: true, accion: 'contratar', jinete: j })}>
                        <i className="bi bi-pen me-1"></i>Contratar
                      </Button>
                    )}
                    {(tab === 'mios' || esMio(j)) && (
                      <Button size="sm" variant="outline-danger" style={{ borderRadius: '6px' }}
                              onClick={() => setConfirm({ show: true, accion: 'despedir', jinete: j })}>
                        <i className="bi bi-door-open me-1"></i>Despedir
                      </Button>
                    )}
                  </div>
                </Card.Body>
              </Card>
            </Col>
          ))}
        </Row>
      )}

      <Modal show={confirm.show} onHide={() => setConfirm({ show: false, accion: null, jinete: null })} centered>
        <Modal.Header closeButton>
          <Modal.Title className="font-heading fw-bold">
            {confirm.accion === 'contratar' ? 'Contratar jinete' : 'Despedir jinete'}
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {confirm.accion === 'contratar' ? (
            <p className="mb-0">
              ¿Contratar a <strong>{confirm.jinete?.nombre}</strong> por{' '}
              <strong>${Number(confirm.jinete?.precio_contratacion || 0).toLocaleString('es-CL')} CC</strong>?
              Es un pago único y el jinete trabajará solo para ti.
            </p>
          ) : (
            <p className="mb-0">
              ¿Despedir a <strong>{confirm.jinete?.nombre}</strong>? No se reembolsa el pago de contratación
              y quedará disponible para otros usuarios.
            </p>
          )}
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={() => setConfirm({ show: false, accion: null, jinete: null })}>Cancelar</Button>
          <Button variant={confirm.accion === 'contratar' ? 'primary' : 'danger'} onClick={ejecutar} disabled={saving}>
            {confirm.accion === 'contratar' ? 'Contratar' : 'Despedir'}
          </Button>
        </Modal.Footer>
      </Modal>
    </Container>
  );
}

export default JockeyList;
