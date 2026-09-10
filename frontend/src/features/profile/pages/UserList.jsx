import React, { useState, useEffect, useCallback } from 'react';
import { Container, Row, Col, Card, Form, InputGroup, Pagination } from 'react-bootstrap';
import { Link } from 'react-router-dom';
import { useToast } from '../../../shared/context/ToastContext';
import api from '../../../shared/services/api';

function UserList() {
  const showToast = useToast();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalUsers, setTotalUsers] = useState(0);

  const fetchUsers = useCallback(async (pageNum, searchQuery) => {
    setLoading(true);
    try {
      const response = await api.get(`/api/users?page=${pageNum}&size=8&search=${encodeURIComponent(searchQuery || '')}`);
      if (response.data.success) {
        const data = response.data.data;
        setUsers(data.users || []);
        setTotalPages(data.total_pages || 1);
        setTotalUsers(data.total_users || 0);
        setPage(data.current_page || pageNum);
      }
    } catch {
      showToast('Error al cargar la lista de usuarios', 'error');
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  useEffect(() => {
    fetchUsers(page, search);
  }, [page, fetchUsers]);

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    setPage(1);
    fetchUsers(1, search);
  };

  const handleSearchChange = (e) => {
    const value = e.target.value;
    setSearch(value);
    setPage(1);
    fetchUsers(1, value);
  };

  return (
    <Container className="py-4">
      <Row className="mb-4 align-items-center">
        <Col md={6}>
          <h2 className="font-heading fw-bold mb-1" style={{ color: 'var(--color-text-dark)' }}>
            <i className="bi bi-people me-2" style={{ color: 'var(--color-primary)' }}></i>Usuarios
          </h2>
          <p className="text-muted mb-0">Total: {totalUsers} usuario{totalUsers !== 1 ? 's' : ''} registrados</p>
        </Col>
        <Col md={6}>
          <Form onSubmit={handleSearchSubmit}>
            <InputGroup>
              <InputGroup.Text style={{ backgroundColor: '#fff', borderRight: 'none' }}>
                <i className="bi bi-search text-muted"></i>
              </InputGroup.Text>
              <Form.Control
                type="text"
                placeholder="Buscar usuarios por nombre..."
                value={search}
                onChange={handleSearchChange}
                style={{ borderLeft: 'none', borderRadius: '0 8px 8px 0' }}
              />
            </InputGroup>
          </Form>
        </Col>
      </Row>

      {loading ? (
        <div className="py-5 text-center">
          <div className="spinner-border text-success" role="status"></div>
        </div>
      ) : users.length === 0 ? (
        <Card className="border-0 shadow-sm text-center py-5">
          <Card.Body>
            <i className="bi bi-person-x" style={{ fontSize: '3rem', color: '#adb5bd' }}></i>
            <h5 className="font-heading fw-bold mt-3">No se encontraron usuarios</h5>
            <p className="text-muted">Prueba con otra búsqueda.</p>
          </Card.Body>
        </Card>
      ) : (
        <>
          <Row className="g-3 mb-4">
            {users.map((u) => (
              <Col xs={12} sm={6} md={4} lg={3} key={u.id}>
                <Card className="h-100 border-0 shadow-sm text-center" style={{ borderRadius: '12px', borderTop: '3px solid var(--color-primary)' }}>
                  <Card.Body className="d-flex flex-column align-items-center p-4">
                    <div
                      className="rounded-circle d-inline-flex align-items-center justify-content-center mb-3"
                      style={{
                        width: '64px',
                        height: '64px',
                        backgroundColor: (u.profile_photo || u.profilePhoto) ? 'transparent' : 'rgba(21, 189, 15, 0.1)',
                        border: '2px solid var(--color-primary)',
                        overflow: 'hidden',
                      }}
                    >
                      {u.profile_photo || u.profilePhoto ? (
                        <img src={u.profile_photo || u.profilePhoto} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                      ) : (
                        <i className="bi bi-person-fill" style={{ fontSize: '1.8rem', color: 'var(--color-primary)' }}></i>
                      )}
                    </div>

                    <h5 className="font-heading fw-bold mb-1" style={{ color: 'var(--color-text-dark)', fontSize: '1.05rem' }}>
                      {u.username}
                    </h5>

                    <small className="text-muted mb-3" style={{ fontSize: '0.78rem' }}>
                      Miembro desde {u.created_at ? new Date(u.created_at).toLocaleDateString('es-CL') : 'reciente'}
                    </small>

                    <Link
                      to={`/perfil/${u.id}`}
                      className="btn btn-outline-primary btn-sm mt-auto w-100"
                      style={{ borderRadius: '8px' }}
                    >
                      <i className="bi bi-person me-1"></i>Ver perfil
                    </Link>
                  </Card.Body>
                </Card>
              </Col>
            ))}
          </Row>

          {totalPages > 1 && (
            <div className="d-flex justify-content-center mt-4">
              <Pagination>
                <Pagination.Prev disabled={page <= 1} onClick={() => setPage(page - 1)} />
                {[...Array(totalPages)].map((_, idx) => (
                  <Pagination.Item
                    key={idx + 1}
                    active={idx + 1 === page}
                    onClick={() => setPage(idx + 1)}
                  >
                    {idx + 1}
                  </Pagination.Item>
                ))}
                <Pagination.Next disabled={page >= totalPages} onClick={() => setPage(page + 1)} />
              </Pagination>
            </div>
          )}
        </>
      )}
    </Container>
  );
}

export default UserList;
