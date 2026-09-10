import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { Container, Row, Col, Card, Badge, ProgressBar, Button } from 'react-bootstrap';
import { useToast } from '../../../shared/context/ToastContext';
import api from '../../../shared/services/api';

function HorseDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const showToast = useToast();
  const [horse, setHorse] = useState(null);
  const [loading, setLoading] = useState(true);

  const fetchHorse = useCallback(async () => {
    try {
      const response = await api.get(`/api/horses/${id}`);
      if (response.data.success) setHorse(response.data.data.horse);
    } catch {
      showToast('Caballo no encontrado', 'error');
    } finally {
      setLoading(false);
    }
  }, [id, showToast]);

  useEffect(() => { fetchHorse(); }, [fetchHorse]);

  if (loading) {
    return (
      <Container className="py-5 text-center">
        <div className="spinner-border text-success" role="status"></div>
      </Container>
    );
  }

  if (!horse) {
    return (
      <Container className="py-5 text-center">
        <p className="text-muted">Caballo no encontrado.</p>
        <Button variant="primary" onClick={() => navigate(-1)}>Volver</Button>
      </Container>
    );
  }

  const getWinrate = () => {
    if (!horse.carreras_totales || horse.carreras_totales === 0) return '0%';
    return `${((horse.victorias / horse.carreras_totales) * 100).toFixed(0)}%`;
  };

  return (
    <Container className="py-4">
      <Button variant="outline-secondary" size="sm" className="mb-3" onClick={() => navigate(-1)} style={{ borderRadius: '8px' }}>
        <i className="bi bi-arrow-left me-1"></i>Volver
      </Button>

      <Row className="mb-4">
        <Col>
          <h2 className="font-heading fw-bold mb-1" style={{ color: 'var(--color-text-dark)' }}>
            <i className="bi bi-heart-fill me-2" style={{ color: 'var(--color-primary)' }}></i>{horse.nombre}
          </h2>
          <div className="d-flex align-items-center gap-3">
            {horse.en_venta && (
              <Badge bg="warning" style={{ fontSize: '0.8rem' }}>En venta - ${horse.precio_venta?.toLocaleString('es-CL')} CC</Badge>
            )}
            {horse.es_bot && (
              <Badge bg="secondary" style={{ fontSize: '0.8rem' }}>Bot</Badge>
            )}
          </div>
        </Col>
      </Row>

      <Row className="g-4">
        <Col md={6}>
          <Card className="border-0 shadow-sm mb-4">
            <Card.Body className="p-4">
              <h5 className="font-heading fw-bold mb-3">
                <i className="bi bi-bar-chart me-2" style={{ color: 'var(--color-primary)' }}></i>Estadísticas
              </h5>
              <div className="d-flex flex-column gap-3">
                <div>
                  <div className="d-flex justify-content-between mb-1">
                    <span className="text-muted">Winrate</span>
                    <span className="font-mono fw-bold text-success">{getWinrate()}</span>
                  </div>
                  <ProgressBar
                    now={horse.carreras_totales > 0 ? (horse.victorias / horse.carreras_totales) * 100 : 0}
                    variant="success"
                    style={{ height: '8px' }}
                  />
                </div>
                <div>
                  <div className="d-flex justify-content-between mb-1">
                    <span className="text-muted">Fatiga</span>
                    <span className="font-mono fw-bold">{horse.fatiga}%</span>
                  </div>
                  <ProgressBar
                    now={horse.fatiga}
                    variant={horse.fatiga >= 80 ? 'danger' : horse.fatiga >= 50 ? 'warning' : 'success'}
                    style={{ height: '8px' }}
                  />
                </div>
              </div>
            </Card.Body>
          </Card>
        </Col>

        <Col md={6}>
          <Card className="border-0 shadow-sm mb-4">
            <Card.Body className="p-4">
              <h5 className="font-heading fw-bold mb-3">
                <i className="bi bi-info-circle me-2" style={{ color: 'var(--color-primary)' }}></i>Información
              </h5>
              <div className="d-flex flex-column gap-2" style={{ fontSize: '0.9rem' }}>
                <div className="d-flex justify-content-between">
                  <span className="text-muted">Edad</span>
                  <span className="font-mono fw-bold">{horse.edad} años</span>
                </div>
                <div className="d-flex justify-content-between">
                  <span className="text-muted">Carreras</span>
                  <span className="font-mono fw-bold">{horse.carreras_totales}</span>
                </div>
                <div className="d-flex justify-content-between">
                  <span className="text-muted">Victorias</span>
                  <span className="font-mono fw-bold">{horse.victorias}</span>
                </div>
                <div className="d-flex justify-content-between">
                  <span className="text-muted">Winrate</span>
                  <span className="font-mono fw-bold">{getWinrate()}</span>
                </div>
                <div className="d-flex justify-content-between">
                  <span className="text-muted">Posición promedio</span>
                  <span className="font-mono fw-bold">#{horse.posicion_promedio || '---'}</span>
                </div>
              </div>
            </Card.Body>
          </Card>

          {horse.owner && (
            <Card className="border-0 shadow-sm mb-4">
              <Card.Body className="p-4">
                <h5 className="font-heading fw-bold mb-3">
                  <i className="bi bi-person me-2" style={{ color: 'var(--color-primary)' }}></i>Dueño
                </h5>
                <Link to={`/perfil/${horse.owner.id}`} className="d-flex align-items-center gap-3 text-decoration-none">
                  <div
                    className="rounded-circle d-inline-flex align-items-center justify-content-center"
                    style={{
                      width: '48px',
                      height: '48px',
                      backgroundColor: (horse.owner.profile_photo || horse.owner.profilePhoto) ? 'transparent' : 'rgba(21, 189, 15, 0.1)',
                      border: '2px solid var(--color-primary)',
                      overflow: 'hidden',
                    }}
                  >
                    {horse.owner.profile_photo || horse.owner.profilePhoto ? (
                      <img src={horse.owner.profile_photo || horse.owner.profilePhoto} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                    ) : (
                      <i className="bi bi-person-fill" style={{ fontSize: '1.2rem', color: 'var(--color-primary)' }}></i>
                    )}
                  </div>
                  <span className="fw-medium" style={{ color: 'var(--color-text-dark)' }}>{horse.owner.username}</span>
                </Link>
              </Card.Body>
            </Card>
          )}
        </Col>
      </Row>
    </Container>
  );
}

export default HorseDetail;
