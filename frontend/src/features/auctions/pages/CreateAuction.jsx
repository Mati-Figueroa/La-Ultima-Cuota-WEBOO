import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Container, Row, Col, Card, Form, Button, Alert } from 'react-bootstrap';
import { useToast } from '../../../shared/context/ToastContext';
import api from '../../../shared/services/api';

function CreateAuction() {
  const navigate = useNavigate();
  const showToast = useToast();
  const [myHorses, setMyHorses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [selectedHorse, setSelectedHorse] = useState('');
  const [precioInicial, setPrecioInicial] = useState('');
  const [precioReserva, setPrecioReserva] = useState('');
  const [duracionHoras, setDuracionHoras] = useState('24');

  const fetchMyHorses = useCallback(async () => {
    try {
      const response = await api.get('/api/stable');
      if (response.data.success) {
        const eligible = response.data.data.horses.filter(
          (h) => !h.en_venta && !h.es_bot
        );
        setMyHorses(eligible);
      }
    } catch {
      showToast('Error al cargar tu establo', 'error');
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  useEffect(() => { fetchMyHorses(); }, [fetchMyHorses]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!selectedHorse || !precioInicial || !duracionHoras) {
      showToast('Completa todos los campos obligatorios', 'warning');
      return;
    }

    setSubmitting(true);
    try {
      const response = await api.post('/api/auctions', {
        caballoId: Number(selectedHorse),
        precioInicial: Number(precioInicial),
        precioReserva: precioReserva ? Number(precioReserva) : null,
        duracionHoras: Number(duracionHoras),
      });
      if (response.data.success) {
        showToast('Subasta creada exitosamente', 'success');
        navigate(`/subasta/${response.data.data.subasta.id}`);
      }
    } catch (err) {
      showToast(err.response?.data?.error || 'Error al crear subasta', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <Container className="py-5 text-center">
        <div className="spinner-border text-success" role="status"></div>
      </Container>
    );
  }

  return (
    <Container className="py-4">
      <Row className="justify-content-center">
        <Col md={8} lg={6}>
          <Card className="border-0 shadow-sm" style={{ borderTop: '4px solid var(--color-primary)' }}>
            <Card.Body className="p-4">
              <h3 className="font-heading fw-bold mb-4 text-center" style={{ color: 'var(--color-text-dark)' }}>
                <i className="bi bi-plus-circle me-2" style={{ color: 'var(--color-primary)' }}></i>Crear Subasta
              </h3>

              <div className="mb-4 p-3" style={{
                backgroundColor: 'rgba(21, 189, 15, 0.08)',
                border: '1px solid var(--color-primary)',
                borderRadius: '12px',
                fontSize: '0.85rem',
              }}>
                <strong>Recuerda:</strong> El precio inicial es lo que el pujador paga como depósito.
                Si nadie puja, devuelves el caballo. Si alguien puja, gana el mayor postor al expirar el tiempo.
              </div>

              {myHorses.length === 0 ? (
                <Alert variant="info">
                  No tienes caballos elegibles para subastar. Los caballos bot y los en venta no se pueden subastar.
                </Alert>
              ) : (
                <Form onSubmit={handleSubmit}>
                  <Form.Group className="mb-3">
                    <Form.Label className="fw-medium">Caballo a subastar *</Form.Label>
                    <Form.Select
                      value={selectedHorse}
                      onChange={(e) => setSelectedHorse(e.target.value)}
                      required
                      style={{ borderRadius: '8px' }}
                    >
                      <option value="">Seleccionar caballo...</option>
                      {myHorses.map((h) => (
                        <option key={h.id} value={h.id}>
                          {h.nombre} — V:{h.velocidad || '?'} / R:{h.resistencia || '?'} / C:{h.corazon || '?'} — F:{h.fatiga}%
                        </option>
                      ))}
                    </Form.Select>
                  </Form.Group>

                  <Form.Group className="mb-3">
                    <Form.Label className="fw-medium">Precio inicial en $CC *</Form.Label>
                    <Form.Control
                      type="number"
                      min="100"
                      value={precioInicial}
                      onChange={(e) => setPrecioInicial(e.target.value)}
                      placeholder="Ej: 500"
                      required
                      style={{ borderRadius: '8px' }}
                    />
                    <Form.Text className="text-muted">
                      Mínimo $100 CC — es el depósito que paga el pujador
                    </Form.Text>
                  </Form.Group>

                  <Form.Group className="mb-3">
                    <Form.Label className="fw-medium">Precio reserva (opcional)</Form.Label>
                    <Form.Control
                      type="number"
                      min="0"
                      value={precioReserva}
                      onChange={(e) => setPrecioReserva(e.target.value)}
                      placeholder="Precio mínimo para vender"
                      style={{ borderRadius: '8px' }}
                    />
                    <Form.Text className="text-muted">
                      Si la puja ganadora no alcanza este precio, la subasta se cancela y se devuelven los depósitos
                    </Form.Text>
                  </Form.Group>

                  <Form.Group className="mb-4">
                    <Form.Label className="fw-medium">Duración *</Form.Label>
                    <Form.Select
                      value={duracionHoras}
                      onChange={(e) => setDuracionHoras(e.target.value)}
                      required
                      style={{ borderRadius: '8px' }}
                    >
                      <option value="1">1 hora</option>
                      <option value="6">6 horas</option>
                      <option value="24">24 horas</option>
                      <option value="48">48 horas</option>
                      <option value="72">72 horas</option>
                      <option value="168">7 días</option>
                    </Form.Select>
                  </Form.Group>

                  <Button
                    type="submit"
                    variant="primary"
                    className="w-100"
                    disabled={submitting || myHorses.length === 0}
                    style={{ borderRadius: '8px' }}
                  >
                    {submitting ? 'Creando...' : 'Crear Subasta'}
                  </Button>
                </Form>
              )}
            </Card.Body>
          </Card>
        </Col>
      </Row>
    </Container>
  );
}

export default CreateAuction;
