import React, { useState, useEffect } from 'react';
import { Modal, Button, Form, InputGroup } from 'react-bootstrap';
import { useAuth } from '../../../shared/context/AuthContext';
import { useToast } from '../../../shared/context/ToastContext';
import api from '../../../shared/services/api';

const MAX_MENSAJE = 200;

function GiftModal({ show, onHide, destinatario, onSent }) {
  const { user, updateUserSaldo } = useAuth();
  const showToast = useToast();
  const [monto, setMonto] = useState('');
  const [itemId, setItemId] = useState('');
  const [cantidad, setCantidad] = useState(1);
  const [mensaje, setMensaje] = useState('');
  const [inventario, setInventario] = useState([]);
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    if (!show) return;
    setMonto('');
    setItemId('');
    setCantidad(1);
    setMensaje('');
    api.get('/api/inventory').then((r) => {
      if (r.data.success) setInventario(r.data.data.inventario);
    }).catch(() => setInventario([]));
  }, [show]);

  const itemElegido = inventario.find((i) => String(i.item_id) === String(itemId));
  const montoNum = Number(monto || 0);
  const saldo = Number(user?.saldo ?? 0);
  const error =
    montoNum < 0 ? 'El monto no puede ser negativo'
      : montoNum > saldo ? 'No tienes saldo suficiente'
      : montoNum === 0 && !itemId ? 'Agrega dinero o un producto'
      : itemElegido && (cantidad < 1 || cantidad > itemElegido.cantidad) ? `Tienes ${itemElegido.cantidad} de ese producto`
      : null;

  const enviar = async () => {
    if (error) return;
    setEnviando(true);
    try {
      const body = { destinatarioId: destinatario.id, monto: montoNum, mensaje: mensaje.trim() || null };
      if (itemId) {
        body.itemId = Number(itemId);
        body.cantidad = Number(cantidad);
      }
      const response = await api.post('/api/gifts', body);
      updateUserSaldo(response.data.data.saldo);
      showToast(response.data.data.message, 'success');
      onSent?.();
      onHide();
    } catch (err) {
      showToast(err.response?.data?.error || 'No se pudo enviar el regalo', 'error');
    } finally {
      setEnviando(false);
    }
  };

  return (
    <Modal show={show} onHide={onHide} centered>
      <Modal.Header closeButton>
        <Modal.Title className="font-heading fw-bold">
          <i className="bi bi-gift me-2" style={{ color: 'var(--color-primary)' }}></i>
          Regalo para {destinatario?.username}
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <Form.Group className="mb-3">
          <Form.Label>Dinero</Form.Label>
          <InputGroup>
            <InputGroup.Text>$</InputGroup.Text>
            <Form.Control
              type="number" min="0" step="1" placeholder="0"
              data-testid="regalo-monto"
              value={monto}
              onChange={(e) => setMonto(e.target.value)}
            />
            <InputGroup.Text>CC</InputGroup.Text>
          </InputGroup>
          <Form.Text className="text-muted">Tu saldo: ${saldo.toLocaleString('es-CL')} CC</Form.Text>
        </Form.Group>

        <Form.Group className="mb-3">
          <Form.Label>Producto de tu inventario (opcional)</Form.Label>
          <div className="d-flex gap-2">
            <Form.Select value={itemId} onChange={(e) => { setItemId(e.target.value); setCantidad(1); }} disabled={inventario.length === 0}>
              <option value="">{inventario.length === 0 ? 'Tu inventario está vacío' : 'Sin producto'}</option>
              {inventario.map((it) => (
                <option key={it.item_id} value={it.item_id}>{it.nombre} (tienes {it.cantidad})</option>
              ))}
            </Form.Select>
            {itemId && (
              <Form.Control
                type="number" min="1" max={itemElegido?.cantidad || 1} style={{ width: '90px' }}
                aria-label="Cantidad" value={cantidad} onChange={(e) => setCantidad(Number(e.target.value))}
              />
            )}
          </div>
        </Form.Group>

        <Form.Group>
          <Form.Label>Mensaje (opcional)</Form.Label>
          <Form.Control
            as="textarea" rows={2} maxLength={MAX_MENSAJE}
            data-testid="regalo-mensaje"
            value={mensaje}
            onChange={(e) => setMensaje(e.target.value)}
            placeholder="¡Suerte en la próxima carrera!"
          />
          <Form.Text className="text-muted">{mensaje.length}/{MAX_MENSAJE}</Form.Text>
        </Form.Group>
        {error && (monto !== '' || itemId) && <p className="text-danger mt-2 mb-0" style={{ fontSize: '0.85rem' }}>{error}</p>}
      </Modal.Body>
      <Modal.Footer>
        <Button variant="secondary" onClick={onHide}>Cancelar</Button>
        <Button variant="primary" onClick={enviar} disabled={!!error || enviando} data-testid="regalo-enviar">
          <i className="bi bi-send me-1"></i>Enviar regalo
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

export default GiftModal;
