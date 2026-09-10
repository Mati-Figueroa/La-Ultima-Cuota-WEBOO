import React, { useState, useEffect, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import { Container, Row, Col, Card, Badge, ProgressBar } from 'react-bootstrap';
import { useToast } from '../../../shared/context/ToastContext';
import api from '../../../shared/services/api';

function UserProfile() {
  const { id } = useParams();
  const showToast = useToast();
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const fetchUser = useCallback(async () => {
    try {
      const response = await api.get(`/api/users/${id}`);
      if (response.data.success) setUser(response.data.data.user);
    } catch {
      showToast('Usuario no encontrado', 'error');
    } finally {
      setLoading(false);
    }
  }, [id, showToast]);

  useEffect(() => { fetchUser(); }, [fetchUser]);

  if (loading) {
    return (
      <Container className="py-5 text-center">
        <div className="spinner-border text-success" role="status"></div>
      </Container>
    );
  }

  if (!user) {
    return (
      <Container className="py-5 text-center">
        <p className="text-muted">Usuario no encontrado.</p>
      </Container>
    );
  }

  return (
    <Container className="py-4">
      <Row className="g-4">
        <Col lg={4}>
          <Card className="border-0 shadow-sm text-center" style={{ borderTop: '4px solid var(--color-primary)' }}>
            <Card.Body className="p-4">
              <div
                className="rounded-circle d-inline-flex align-items-center justify-content-center mb-3"
                style={{
                  width: '100px',
                  height: '100px',
                  backgroundColor: (user.profile_photo || user.profilePhoto) ? 'transparent' : 'rgba(21, 189, 15, 0.1)',
                  border: '3px solid var(--color-primary)',
                  overflow: 'hidden',
                }}
              >
                {user.profile_photo || user.profilePhoto ? (
                  <img
                    src={user.profile_photo || user.profilePhoto}
                    alt={user.username}
                    style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                  />
                ) : (
                  <i className="bi bi-person-fill" style={{ fontSize: '2.5rem', color: 'var(--color-primary)' }}></i>
                )}
              </div>

              <h4 className="font-heading fw-bold mb-1" style={{ color: 'var(--color-text-dark)' }}>
                {user.username}
              </h4>
              <p className="text-muted mb-3" style={{ fontSize: '0.85rem' }}>
                Miembro desde {user.created_at ? new Date(user.created_at).toLocaleDateString('es-CL', { month: 'long', year: 'numeric' }) : 'recientemente'}
              </p>

              <div className="d-flex justify-content-center gap-2">
                <Link to="/subastas" className="btn btn-outline-primary btn-sm" style={{ borderRadius: '8px' }}>
                  <i className="bi bi-hammer me-1"></i>Ver Subastas
                </Link>
              </div>
            </Card.Body>
          </Card>
        </Col>

        <Col lg={8}>
          <Card className="border-0 shadow-sm">
            <Card.Body className="p-4">
              <h5 className="font-heading fw-bold mb-3" style={{ color: 'var(--color-text-dark)' }}>
                <i className="bi bi-grid me-2" style={{ color: 'var(--color-primary)' }}></i>
                Caballos de {user.username} ({user.caballos?.length || 0})
              </h5>

              {!user.caballos || user.caballos.length === 0 ? (
                <p className="text-muted text-center py-4">Este usuario no tiene caballos en su establo.</p>
              ) : (
                <Row className="g-3">
                  {user.caballos.map((horse) => (
                    <Col md={6} key={horse.id}>
                      <Card className="h-100 border-0 shadow-sm" style={{ backgroundColor: '#fcfcfc', border: '1px solid #eee' }}>
                        <Card.Body className="p-3">
                          <div className="d-flex justify-content-between align-items-start mb-2">
                            <h6 className="font-heading fw-bold mb-0">
                              <Link to={`/caballo/${horse.id}`} className="text-decoration-none" style={{ color: 'var(--color-text-dark)' }}>
                                {horse.nombre}
                              </Link>
                            </h6>
                            {horse.en_venta && (
                              <Badge bg="warning" style={{ fontSize: '0.7rem' }}>En venta</Badge>
                            )}
                          </div>

                          <div className="d-flex flex-column gap-1" style={{ fontSize: '0.82rem' }}>
                            <div className="d-flex justify-content-between">
                              <span className="text-muted">Edad:</span>
                              <span className="font-mono fw-semibold">{horse.edad} años</span>
                            </div>
                            <div className="d-flex justify-content-between">
                              <span className="text-muted">Victorias / Carreras:</span>
                              <span className="font-mono fw-semibold">{horse.victorias}/{horse.carreras_totales}</span>
                            </div>
                            <div className="d-flex justify-content-between">
                              <span className="text-muted">Winrate:</span>
                              <span className="font-mono fw-bold text-success">{horse.winrate}%</span>
                            </div>
                            <div className="d-flex justify-content-between align-items-center mt-1">
                              <span className="text-muted">Fatiga:</span>
                              <div className="d-flex align-items-center gap-2">
                                <ProgressBar
                                  now={horse.fatiga}
                                  variant={horse.fatiga >= 80 ? 'danger' : horse.fatiga >= 50 ? 'warning' : 'success'}
                                  style={{ width: '60px', height: '6px' }}
                                />
                                <span className="font-mono" style={{ fontSize: '0.75rem' }}>{horse.fatiga}%</span>
                              </div>
                            </div>
                          </div>
                        </Card.Body>
                      </Card>
                    </Col>
                  ))}
                </Row>
              )}
            </Card.Body>
          </Card>
        </Col>
      </Row>
    </Container>
  );
}

export default UserProfile;
