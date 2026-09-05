import React, { useState, useEffect, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import { Container, Row, Col, Card } from 'react-bootstrap';
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
      <Row className="justify-content-center">
        <Col md={6} lg={5}>
          <Card className="border-0 shadow-sm text-center" style={{ borderTop: '4px solid var(--color-primary)' }}>
            <Card.Body className="p-5">
              <div
                className="rounded-circle d-inline-flex align-items-center justify-content-center mb-4"
                style={{
                  width: '120px',
                  height: '120px',
                  backgroundColor: user.profilePhoto ? 'transparent' : 'rgba(21, 189, 15, 0.1)',
                  border: '3px solid var(--color-primary)',
                  overflow: 'hidden',
                }}
              >
                {user.profilePhoto ? (
                  <img
                    src={user.profilePhoto}
                    alt={user.username}
                    style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                  />
                ) : (
                  <i className="bi bi-person-fill" style={{ fontSize: '3rem', color: 'var(--color-primary)' }}></i>
                )}
              </div>

              <h3 className="font-heading fw-bold mb-2" style={{ color: 'var(--color-text-dark)' }}>
                {user.username}
              </h3>
              <p className="text-muted mb-3" style={{ fontSize: '0.9rem' }}>
                Miembro desde {new Date(user.created_at).toLocaleDateString('es-CL', { month: 'long', year: 'numeric' })}
              </p>

              <div className="d-flex justify-content-center gap-3">
                <Link to="/subastas" className="btn btn-outline-primary btn-sm" style={{ borderRadius: '8px' }}>
                  <i className="bi bi-hammer me-1"></i>Ver Subastas
                </Link>
              </div>
            </Card.Body>
          </Card>
        </Col>
      </Row>
    </Container>
  );
}

export default UserProfile;
