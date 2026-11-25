import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { Container, Row, Col, Card, Button, Badge, Form, Modal } from 'react-bootstrap';
import { useAuth } from '../../../shared/context/AuthContext';
import { useToast } from '../../../shared/context/ToastContext';
import api from '../../../shared/services/api';
import { JockeyStats } from './JockeyList';

function JockeyDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user, updateUserSaldo } = useAuth();
  const showToast = useToast();
  const [jinete, setJinete] = useState(null);
  const [loading, setLoading] = useState(true);
  const [caballos, setCaballos] = useState([]);
  const [caballoId, setCaballoId] = useState('');
  const [saving, setSaving] = useState(false);
  const [confirm, setConfirm] = useState(null);

  const fetchJinete = useCallback(async () => {
    try {
      const response = await api.get(`/api/jockeys/${id}`);
      if (response.data.success) {
        const j = response.data.data.jinete;
        setJinete(j);
        setCaballoId(j.caballo_id ? String(j.caballo_id) : '');
      }
    } catch {
      showToast('Jinete no encontrado', 'error');
    } finally {
      setLoading(false);
    }
  }, [id, showToast]);

  useEffect(() => { fetchJinete(); }, [fetchJinete]);

  const esMio = jinete?.empleador && user && jinete.empleador.id === user.id;

  useEffect(() => {
    if (!esMio) return;
    api.get('/api/stable').then((r) => {
      if (r.data.success) setCaballos(r.data.data.horses);
    }).catch(() => {});
  }, [esMio]);

  const accion = async (fn, okMsg) => {
    setSaving(true);
    try {
      const response = await fn();
      const data = response.data.data;
      if (data.saldo !== undefined) updateUserSaldo(data.saldo);
      showToast(data.message || okMsg, 'success');
      setConfirm(null);
      fetchJinete();
      return true;
    } catch (err) {
      showToast(err.response?.data?.error || 'No se pudo completar la acción', 'error');
      return false;
    } finally {
      setSaving(false);
    }
  };

  const contratar = () => accion(() => api.post(`/api/jockeys/${id}/hire`), 'Jinete contratado');
  const despedir = () => accion(() => api.post(`/api/jockeys/${id}/fire`), 'Jinete despedido');
  const asignar = () => accion(
    () => api.patch(`/api/jockeys/${id}/assign`, { caballoId: caballoId ? Number(caballoId) : null }),
    'Asignación actualizada'
  );

  if (loading) {
    return <Container className="py-5 text-center"><div className="spinner-border text-success" role="status"></div></Container>;
  }
  if (!jinete) {
    return <Container className="py-5 text-center"><p className="text-muted">Jinete no encontrado.</p></Container>;
  }

  return (
    <Container className="py-4">
      <Button variant="outline-secondary" size="sm" className="mb-3" onClick={() => navigate('/jinetes')} style={{ borderRadius: '8px' }}>
        <i className="bi bi-arrow-left me-1"></i>Jinetes
      </Button>
      <Row className="g-4">
        <Col lg={5}>
          <Card className="border-0 shadow-sm" style={{ borderTop: '4px solid var(--color-primary)' }}>
            <Card.Body className="p-4">
              <div className="text-center mb-3">
                <div className="rounded-circle d-inline-flex align-items-center justify-content-center mb-2"
                     style={{ width: '80px', height: '80px', backgroundColor: 'rgba(21, 189, 15, 0.1)', border: '3px solid var(--color-primary)' }}>
                  <i className="bi bi-person-badge" style={{ fontSize: '2.2rem', color: 'var(--color-primary)' }}></i>
                </div>
                <h3 className="font-heading fw-bold mb-1" data-testid="jinete-nombre">{jinete.nombre}</h3>
                {esMio ? <Badge bg="primary">Trabaja para ti</Badge>
                  : jinete.disponible ? <Badge bg="success">Disponible</Badge>
                  : <Badge bg="secondary">Contratado</Badge>}
              </div>
              <JockeyStats jinete={jinete} />
              {jinete.empleador && !esMio && (
                <p className="text-muted mt-3 mb-0" style={{ fontSize: '0.85rem' }}>
                  Trabaja para <Link to={`/perfil/${jinete.empleador.id}`}>{jinete.empleador.username}</Link>
                  {jinete.caballo_nombre && <> y monta a <Link to={`/caballo/${jinete.caballo_id}`}>{jinete.caballo_nombre}</Link></>}.
                </p>
              )}
              {jinete.disponible && (
                <Button className="w-100 mt-3" variant="primary" onClick={() => setConfirm('contratar')} disabled={saving}>
                  <i className="bi bi-pen me-1"></i>Contratar por ${Number(jinete.precio_contratacion).toLocaleString('es-CL')} CC
                </Button>
              )}
            </Card.Body>
          </Card>
        </Col>

        <Col lg={7}>
          <Card className="border-0 shadow-sm mb-3">
            <Card.Body className="p-4">
              <h5 className="font-heading fw-bold mb-3">¿Cómo ayuda un jinete?</h5>
              <ul className="mb-0 text-muted" style={{ fontSize: '0.9rem' }}>
                <li>Suma hasta un {Math.round(jinete.habilidad / 10)} % de velocidad al caballo que monta.</li>
                <li>Si es experto en lluvia, barro o sequía, el caballo sufre menos ese clima.</li>
                <li>Gana experiencia en cada carrera y cada 10 carreras mejora su habilidad.</li>
              </ul>
            </Card.Body>
          </Card>

          {esMio && (
            <Card className="border-0 shadow-sm">
              <Card.Body className="p-4">
                <h5 className="font-heading fw-bold mb-3">Asignación</h5>
                <Form.Group className="mb-3">
                  <Form.Label>Caballo que monta</Form.Label>
                  <Form.Select data-testid="select-caballo" value={caballoId} onChange={(e) => setCaballoId(e.target.value)} style={{ borderRadius: '8px' }}>
                    <option value="">Sin caballo</option>
                    {caballos.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.nombre}{c.jinete && c.jinete.id !== jinete.id ? ` (ahora con ${c.jinete.nombre})` : ''}
                      </option>
                    ))}
                  </Form.Select>
                </Form.Group>
                <div className="d-flex gap-2">
                  <Button variant="primary" onClick={asignar}
                          disabled={saving || caballoId === (jinete.caballo_id ? String(jinete.caballo_id) : '')}>
                    <i className="bi bi-check2 me-1"></i>Guardar asignación
                  </Button>
                  <Button variant="outline-danger" onClick={() => setConfirm('despedir')} disabled={saving}>
                    <i className="bi bi-door-open me-1"></i>Despedir
                  </Button>
                </div>
              </Card.Body>
            </Card>
          )}
        </Col>
      </Row>

      <Modal show={!!confirm} onHide={() => setConfirm(null)} centered>
        <Modal.Header closeButton>
          <Modal.Title className="font-heading fw-bold">{confirm === 'contratar' ? 'Contratar jinete' : 'Despedir jinete'}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {confirm === 'contratar'
            ? <>¿Contratar a <strong>{jinete.nombre}</strong> por <strong>${Number(jinete.precio_contratacion).toLocaleString('es-CL')} CC</strong>? Es un pago único.</>
            : <>¿Despedir a <strong>{jinete.nombre}</strong>? No se reembolsa el pago y quedará disponible para otros.</>}
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={() => setConfirm(null)}>Cancelar</Button>
          <Button variant={confirm === 'contratar' ? 'primary' : 'danger'} disabled={saving}
                  onClick={confirm === 'contratar' ? contratar : despedir}>
            {confirm === 'contratar' ? 'Contratar' : 'Despedir'}
          </Button>
        </Modal.Footer>
      </Modal>
    </Container>
  );
}

export default JockeyDetail;
