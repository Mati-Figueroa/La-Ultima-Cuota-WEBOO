import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { Container, Row, Col, Card, Button, Badge, Form, InputGroup } from 'react-bootstrap';
import { useAuth } from '../../../shared/context/AuthContext';
import { useToast } from '../../../shared/context/ToastContext';
import api from '../../../shared/services/api';

const CATEGORIAS = [
  { key: 'curacion', label: 'Curaciones', icon: 'bi-bandaid', desc: 'Sanan las lesiones de tus caballos.' },
  { key: 'comida', label: 'Comida', icon: 'bi-basket', desc: 'Recuperan energía (fatiga) y alegran al caballo.' },
  { key: 'cuidado', label: 'Cuidados y premios', icon: 'bi-heart', desc: 'Suben la felicidad o rehidratan.' },
];

const CALIDAD = {
  basica: { label: 'Básica', bg: 'secondary' },
  estandar: { label: 'Estándar', bg: 'info' },
  premium: { label: 'Premium', bg: 'warning' },
};

export function efectosItem(it) {
  const efectos = [];
  if (it.cura_nivel > 0) efectos.push(`Cura hasta lesión ${['', 'leve', 'moderada', 'grave'][it.cura_nivel]}`);
  if (it.reduce_fatiga > 0) efectos.push(`−${it.reduce_fatiga} fatiga`);
  if (it.aumenta_felicidad > 0) efectos.push(`+${it.aumenta_felicidad} felicidad`);
  return efectos;
}

function Shop() {
  const { updateUserSaldo } = useAuth();
  const showToast = useToast();
  const [items, setItems] = useState([]);
  const [inventario, setInventario] = useState([]);
  const [cantidades, setCantidades] = useState({});
  const [loading, setLoading] = useState(true);
  const [comprando, setComprando] = useState(null);

  const fetchAll = useCallback(async () => {
    try {
      const [itemsRes, invRes] = await Promise.all([api.get('/api/shop/items'), api.get('/api/inventory')]);
      if (itemsRes.data.success) setItems(itemsRes.data.data.items);
      if (invRes.data.success) setInventario(invRes.data.data.inventario);
    } catch {
      showToast('Error al cargar la tienda', 'error');
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  useEffect(() => { fetchAll(); }, [fetchAll]);

  const comprar = async (item) => {
    const cantidad = Math.max(1, Math.min(99, Number(cantidades[item.id] || 1)));
    setComprando(item.id);
    try {
      const response = await api.post(`/api/shop/items/${item.id}/buy`, { cantidad });
      const data = response.data.data;
      updateUserSaldo(data.saldo);
      showToast(data.message, 'success');
      setCantidades({ ...cantidades, [item.id]: 1 });
      const invRes = await api.get('/api/inventory');
      if (invRes.data.success) setInventario(invRes.data.data.inventario);
    } catch (err) {
      showToast(err.response?.data?.error || 'No se pudo comprar', 'error');
    } finally {
      setComprando(null);
    }
  };

  if (loading) {
    return <Container className="py-5 text-center"><div className="spinner-border text-success" role="status"></div></Container>;
  }

  return (
    <Container className="py-4">
      <Row className="mb-4">
        <Col>
          <h2 className="font-heading fw-bold mb-1" style={{ color: 'var(--color-text-dark)' }}>
            <i className="bi bi-bag me-2" style={{ color: 'var(--color-primary)' }}></i>Tienda
          </h2>
          <p className="text-muted mb-0">
            Compra curaciones, comida y cuidados. Los productos van a tu inventario y se usan desde tu{' '}
            <Link to="/establo">establo</Link>. También puedes regalarlos a otros usuarios.
          </p>
        </Col>
      </Row>

      <Row className="g-4">
        <Col lg={8}>
          {CATEGORIAS.map((cat) => {
            const delaCategoria = items.filter((i) => i.categoria === cat.key);
            if (delaCategoria.length === 0) return null;
            return (
              <div key={cat.key} className="mb-4">
                <h5 className="font-heading fw-bold mb-1">
                  <i className={`bi ${cat.icon} me-2`} style={{ color: 'var(--color-primary)' }}></i>{cat.label}
                </h5>
                <p className="text-muted mb-2" style={{ fontSize: '0.85rem' }}>{cat.desc}</p>
                <Row className="g-3">
                  {delaCategoria.map((it) => (
                    <Col md={6} key={it.id}>
                      <Card className="h-100 border-0 shadow-sm" data-testid="item-tienda">
                        <Card.Body className="d-flex flex-column">
                          <div className="d-flex justify-content-between align-items-start gap-2 mb-1">
                            <h6 className="font-heading fw-bold mb-0">{it.nombre}</h6>
                            <Badge bg={CALIDAD[it.calidad]?.bg || 'secondary'} text={it.calidad === 'premium' ? 'dark' : undefined}>
                              {CALIDAD[it.calidad]?.label || it.calidad}
                            </Badge>
                          </div>
                          <p className="text-muted mb-2" style={{ fontSize: '0.82rem' }}>{it.descripcion}</p>
                          <div className="d-flex flex-wrap gap-1 mb-3">
                            {efectosItem(it).map((e) => (
                              <Badge key={e} bg="light" text="dark" style={{ fontWeight: 500 }}>{e}</Badge>
                            ))}
                          </div>
                          <div className="mt-auto d-flex align-items-center justify-content-between gap-2">
                            <span className="font-mono fw-bold">${Number(it.precio).toLocaleString('es-CL')} CC</span>
                            <InputGroup size="sm" style={{ width: '150px' }}>
                              <Form.Control
                                type="number" min="1" max="99"
                                aria-label={`Cantidad de ${it.nombre}`}
                                value={cantidades[it.id] ?? 1}
                                onChange={(e) => setCantidades({ ...cantidades, [it.id]: e.target.value })}
                              />
                              <Button variant="primary" onClick={() => comprar(it)} disabled={comprando === it.id}
                                      data-testid={`comprar-${it.codigo}`}>
                                Comprar
                              </Button>
                            </InputGroup>
                          </div>
                        </Card.Body>
                      </Card>
                    </Col>
                  ))}
                </Row>
              </div>
            );
          })}
        </Col>

        <Col lg={4}>
          <Card className="border-0 shadow-sm" style={{ position: 'sticky', top: '1rem', borderTop: '4px solid var(--color-primary)' }}>
            <Card.Body className="p-4">
              <h5 className="font-heading fw-bold mb-3">
                <i className="bi bi-box-seam me-2" style={{ color: 'var(--color-primary)' }}></i>Mi inventario
              </h5>
              {inventario.length === 0 ? (
                <p className="text-muted mb-0" style={{ fontSize: '0.9rem' }}>Todavía no tienes productos.</p>
              ) : (
                <div className="d-flex flex-column gap-2" data-testid="inventario">
                  {inventario.map((it) => (
                    <div key={it.item_id} className="d-flex justify-content-between align-items-center">
                      <span>{it.nombre}</span>
                      <Badge bg="primary" pill data-testid={`inventario-${it.codigo}`}>×{it.cantidad}</Badge>
                    </div>
                  ))}
                </div>
              )}
            </Card.Body>
          </Card>
        </Col>
      </Row>
    </Container>
  );
}

export default Shop;
