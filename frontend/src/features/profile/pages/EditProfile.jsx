import React, { useState, useEffect, useRef } from 'react';
import { Container, Row, Col, Card, Form, Button, Alert } from 'react-bootstrap';
import { useToast } from '../../../shared/context/ToastContext';
import { useAuth } from '../../../shared/context/AuthContext';
import api from '../../../shared/services/api';

const MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

function EditProfile() {
  const { user, updateUser } = useAuth();
  const showToast = useToast();
  const fileInputRef = useRef(null);
  const [username, setUsername] = useState('');
  const [preview, setPreview] = useState('');
  const [profilePhoto, setProfilePhoto] = useState('');
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState('');

  useEffect(() => {
    if (user) {
      setUsername(user.username || '');
      setProfilePhoto(user.profile_photo || user.profilePhoto || '');
      setPreview(user.profile_photo || user.profilePhoto || '');
    }
  }, [user]);

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (!file) return;

    if (file.size > MAX_FILE_SIZE) {
      showToast('La imagen no puede superar 5MB', 'error');
      e.target.value = '';
      return;
    }

    if (!file.type.startsWith('image/')) {
      showToast('Solo se permiten archivos de imagen', 'error');
      e.target.value = '';
      return;
    }

    const reader = new FileReader();
    reader.onload = (event) => {
      const base64 = event.target.result;
      setProfilePhoto(base64);
      setPreview(base64);
    };
    reader.readAsDataURL(file);
  };

  const handleRemovePhoto = () => {
    setProfilePhoto('');
    setPreview('');
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setSuccess('');
    try {
      const response = await api.patch('/api/auth/me', {
        username: username.trim(),
        profilePhoto: profilePhoto || '',
      });
      if (response.data.success) {
        showToast('Perfil actualizado', 'success');
        setSuccess('Perfil actualizado correctamente');
        const updatedUser = response.data.data.user;
        updateUser(updatedUser);
      }
    } catch (err) {
      showToast(err.response?.data?.error || 'Error al actualizar perfil', 'error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Container className="py-4">
      <Row className="justify-content-center">
        <Col md={6} lg={5}>
          <Card className="border-0 shadow-sm" style={{ borderTop: '4px solid var(--color-primary)' }}>
            <Card.Body className="p-4">
              <h3 className="font-heading fw-bold mb-4 text-center" style={{ color: 'var(--color-text-dark)' }}>
                <i className="bi bi-pencil-square me-2" style={{ color: 'var(--color-primary)' }}></i>Editar Perfil
              </h3>

              {success && (
                <Alert variant="success" className="mb-3">{success}</Alert>
              )}

              <Form onSubmit={handleSubmit}>
                <Form.Group className="mb-3">
                  <Form.Label className="fw-medium">Nombre de Usuario</Form.Label>
                  <Form.Control
                    type="text"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    maxLength={50}
                    required
                    style={{ borderRadius: '8px' }}
                  />
                </Form.Group>

                <Form.Group className="mb-4">
                  <Form.Label className="fw-medium">Foto de Perfil</Form.Label>
                  <Form.Control
                    ref={fileInputRef}
                    type="file"
                    accept="image/*"
                    onChange={handleFileChange}
                    style={{ borderRadius: '8px' }}
                  />
                  <Form.Text className="text-muted">
                    JPG, PNG o GIF. Máximo 5MB.
                  </Form.Text>
                </Form.Group>

                {preview && (
                  <div className="text-center mb-4">
                    <p className="text-muted mb-2" style={{ fontSize: '0.85rem' }}>Vista previa:</p>
                    <div
                      className="rounded-circle d-inline-flex align-items-center justify-content-center"
                      style={{
                        width: '100px',
                        height: '100px',
                        border: '3px solid var(--color-primary)',
                        overflow: 'hidden',
                      }}
                    >
                      <img
                        src={preview}
                        alt="Preview"
                        style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                      />
                    </div>
                    <div className="mt-2">
                      <Button
                        variant="outline-danger"
                        size="sm"
                        onClick={handleRemovePhoto}
                        style={{ borderRadius: '8px' }}
                      >
                        <i className="bi bi-trash me-1"></i>Quitar foto
                      </Button>
                    </div>
                  </div>
                )}

                <Button
                  type="submit"
                  variant="primary"
                  className="w-100"
                  disabled={loading || !username.trim()}
                  style={{ borderRadius: '8px' }}
                >
                  {loading ? 'Guardando...' : 'Guardar Cambios'}
                </Button>
              </Form>
            </Card.Body>
          </Card>
        </Col>
      </Row>
    </Container>
  );
}

export default EditProfile;
