import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { Card, Button, ButtonGroup, Badge } from 'react-bootstrap';
import { useToast } from '../../../shared/context/ToastContext';
import api from '../../../shared/services/api';

function GiftHistory() {
  const showToast = useToast();
  const [tipo, setTipo] = useState('recibidos');
  const [regalos, setRegalos] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchRegalos = useCallback(async () => {
    setLoading(true);
    try {
      const response = await api.get(`/api/gifts?tipo=${tipo}`);
      if (response.data.success) setRegalos(response.data.data.regalos);
    } catch {
      showToast('Error al cargar los regalos', 'error');
    } finally {
      setLoading(false);
    }
  }, [tipo, showToast]);

  useEffect(() => { fetchRegalos(); }, [fetchRegalos]);

  return (
    <>
      <ButtonGroup className="mb-3" style={{ gap: '0.4rem' }}>
        {[{ key: 'recibidos', label: 'Recibidos' }, { key: 'enviados', label: 'Enviados' }].map((t) => (
          <Button
            key={t.key}
            variant={tipo === t.key ? 'primary' : 'outline-secondary'}
            size="sm"
            onClick={() => setTipo(t.key)}
            style={{ borderRadius: '20px', fontWeight: 600, padding: '0.4rem 1rem' }}
          >
            {t.label}
          </Button>
        ))}
      </ButtonGroup>

      {loading ? (
        <div className="text-center py-5"><div className="spinner-border text-success" role="status"></div></div>
      ) : regalos.length === 0 ? (
        <Card className="border-0 shadow-sm">
          <Card.Body className="text-center py-5">
            <i className="bi bi-gift" style={{ fontSize: '3rem', color: 'var(--color-contrast-medium)' }}></i>
            <h5 className="font-heading fw-bold mt-3">
              {tipo === 'recibidos' ? 'Aún no recibes regalos' : 'Aún no envías regalos'}
            </h5>
            <p className="text-muted mb-0">Envía uno desde el perfil de otro usuario.</p>
          </Card.Body>
        </Card>
      ) : (
        <div className="d-flex flex-column gap-2" data-testid="lista-regalos">
          {regalos.map((r) => {
            const otro = tipo === 'recibidos' ? r.remitente : r.destinatario;
            return (
              <Card key={r.id} className="border-0 shadow-sm" data-testid="regalo">
                <Card.Body className="d-flex align-items-start gap-3 p-3">
                  <i className="bi bi-gift-fill" style={{ fontSize: '1.6rem', color: 'var(--color-primary)' }}></i>
                  <div className="flex-grow-1">
                    <div className="d-flex flex-wrap gap-2 align-items-center">
                      <span>
                        {tipo === 'recibidos' ? 'De ' : 'Para '}
                        <Link to={`/perfil/${otro.id}`} className="fw-bold">{otro.username}</Link>
                      </span>
                      {Number(r.monto) > 0 && (
                        <Badge bg="success" className="font-mono">${Number(r.monto).toLocaleString('es-CL')} CC</Badge>
                      )}
                      {r.item && <Badge bg="info">{r.cantidad} × {r.item.nombre}</Badge>}
                    </div>
                    {r.mensaje && <p className="mb-0 mt-1 fst-italic">“{r.mensaje}”</p>}
                  </div>
                  <small className="text-muted text-nowrap">
                    {r.created_at ? new Date(r.created_at).toLocaleString('es-CL') : ''}
                  </small>
                </Card.Body>
              </Card>
            );
          })}
        </div>
      )}
    </>
  );
}

export default GiftHistory;
