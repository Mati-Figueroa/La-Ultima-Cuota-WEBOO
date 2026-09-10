import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Link } from 'react-router-dom';
import { Container, Row, Col, Card, Button, Form, InputGroup } from 'react-bootstrap';
import { io } from 'socket.io-client';
import { useToast } from '../../../shared/context/ToastContext';
import api from '../../../shared/services/api';

const PAGE_SIZE = 12;

function AuctionList() {
  const showToast = useToast();
  const [auctions, setAuctions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [sort, setSort] = useState('ending_soon');
  const [page, setPage] = useState(1);
  const socketRef = useRef(null);

  const fetchAuctions = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (search) params.set('search', search);
      if (sort) params.set('sort', sort);
      const response = await api.get(`/api/auctions?${params.toString()}`);
      if (response.data.success) setAuctions(response.data.data.auctions);
    } catch {
      showToast('Error al cargar subastas', 'error');
    } finally {
      setLoading(false);
    }
  }, [search, sort, showToast]);

  useEffect(() => { fetchAuctions(); }, [fetchAuctions]);

  // Real-time socket updates for auction cards
  useEffect(() => {
    const envUrl = process.env.REACT_APP_SOCKET_URL;
    const socketUrl = (envUrl && !envUrl.includes('localhost'))
      ? envUrl
      : `http://${window.location.hostname || 'localhost'}:9092`;
    const socket = io(socketUrl, {
      transports: ['polling', 'websocket'],
      reconnection: true,
      reconnectionDelay: 1000,
    });
    socketRef.current = socket;

    const handleBid = (data) => {
      const targetId = Number(data?.subasta_id || data?.id);
      if (!targetId) return;
      setAuctions((prev) =>
        prev.map((a) => {
          if (a.id === targetId) {
            return {
              ...a,
              precio_actual: data.precio_actual || (data.puja && data.puja.monto) || a.precio_actual,
              total_pujas: (a.total_pujas || 0) + 1,
            };
          }
          return a;
        })
      );
    };

    const handleEnded = (data) => {
      const targetId = Number(data?.subasta_id || data?.id);
      if (!targetId) return;
      setAuctions((prev) => prev.filter((a) => a.id !== targetId));
    };

    socket.on('new_bid', handleBid);
    socket.on('auction_bid', handleBid);
    socket.on('auction_ended', handleEnded);

    return () => {
      socket.off('new_bid', handleBid);
      socket.off('auction_bid', handleBid);
      socket.off('auction_ended', handleEnded);
      socket.disconnect();
    };
  }, []);

  // Tick countdown every second for accurate minute/second display
  const [, setTick] = useState(0);
  useEffect(() => {
    const timer = setInterval(() => setTick((t) => t + 1), 1000);
    return () => clearInterval(timer);
  }, []);

  const getTimeLeft = (fechaFin) => {
    const now = new Date();
    const end = new Date(fechaFin);
    const diff = end - now;
    if (diff <= 0) return 'Finalizada';
    const hours = Math.floor(diff / (1000 * 60 * 60));
    const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
    const seconds = Math.floor((diff % (1000 * 60)) / 1000);
    if (hours > 24) return `${Math.floor(hours / 24)}d ${hours % 24}h`;
    if (hours > 0) return `${hours}h ${minutes}m`;
    return `${minutes}m ${seconds}s`;
  };

  const totalPages = Math.max(1, Math.ceil(auctions.length / PAGE_SIZE));
  const paginatedAuctions = auctions.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  return (
    <Container className="py-4">
      <Row className="mb-4">
        <Col>
          <h2 className="font-heading fw-bold mb-1" style={{ color: 'var(--color-text-dark)' }}>
            <i className="bi bi-hammer me-2" style={{ color: 'var(--color-primary)' }}></i>Subastas
          </h2>
          <p className="text-muted mb-0">Compra caballos por puja — el mayor postor se lo lleva</p>
        </Col>
      </Row>

      <div className="mb-4 p-3" style={{
        backgroundColor: 'rgba(21, 189, 15, 0.08)',
        border: '1px solid var(--color-primary)',
        borderRadius: '12px',
        color: 'var(--color-text-dark)',
      }}>
        <strong>Cómo funcionan las subastas:</strong> Pujas con depósito — el monto de tu puja se descuenta
        de tu saldo como garantía. Si te superan, te devuelven automáticamente. Gana el mayor postor cuando
        expira el tiempo.
      </div>

      <Row className="mb-4 g-2 align-items-end">
        <Col md={6}>
          <InputGroup>
            <Form.Control
              type="text"
              placeholder="Buscar por nombre de caballo..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              style={{ borderRadius: '8px 0 0 8px' }}
            />
            <Button variant="primary" onClick={() => fetchAuctions()} style={{ borderRadius: '0 8px 8px 0' }}>
              <i className="bi bi-search"></i>
            </Button>
          </InputGroup>
        </Col>
        <Col md={3}>
          <Form.Select value={sort} onChange={(e) => { setSort(e.target.value); setPage(1); }} style={{ borderRadius: '8px' }}>
            <option value="ending_soon">Terminan pronto</option>
            <option value="newest">Más recientes</option>
            <option value="price_low">Menor precio</option>
          </Form.Select>
        </Col>
        <Col md={3}>
          <Link to="/subastas/crear" className="btn btn-primary w-100" style={{ borderRadius: '8px' }}>
            <i className="bi bi-plus-circle me-1"></i>Crear Subasta
          </Link>
        </Col>
      </Row>

      {loading ? (
        <div className="text-center py-5">
          <div className="spinner-border text-success" role="status"></div>
        </div>
      ) : auctions.length === 0 ? (
        <Card className="border-0 shadow-sm">
          <Card.Body className="text-center py-5">
            <i className="bi bi-hammer" style={{ fontSize: '3rem', color: 'var(--color-contrast-medium)' }}></i>
            <h5 className="font-heading fw-bold mt-3" style={{ color: 'var(--color-text-dark)' }}>
              No hay subastas activas
            </h5>
            <p className="text-muted mb-3">Sé el primero en crear una subasta con uno de tus caballos.</p>
            <Link to="/subastas/crear" className="btn btn-primary" style={{ borderRadius: '8px' }}>
              Crear Subasta
            </Link>
          </Card.Body>
        </Card>
      ) : (
        <>
          <Row className="g-3">
            {paginatedAuctions.map((auction) => (
              <Col xs={12} sm={6} lg={4} key={auction.id}>
                <Card className="h-100 border-0 shadow-sm">
                  <Card.Body className="d-flex flex-column">
                    <div className="d-flex justify-content-between align-items-start mb-2">
                      <h5 className="font-heading fw-bold mb-0" style={{ color: 'var(--color-text-dark)' }}>
                        {auction.caballo_nombre}
                      </h5>
                      <span className="badge bg-success" style={{ fontSize: '0.75rem' }}>
                        {getTimeLeft(auction.fecha_fin)}
                      </span>
                    </div>
                    <p className="text-muted mb-2" style={{ fontSize: '0.85rem' }}>
                      Vendedor: {auction.vendedor_username}
                    </p>
                    <div className="mb-3" style={{ fontSize: '0.85rem' }}>
                      <div className="d-flex justify-content-between mb-1">
                        <span className="text-muted">Winrate</span>
                        <span className="font-mono fw-bold">
                          {auction.caballo_carreras > 0 ? `${((auction.caballo_victorias / auction.caballo_carreras) * 100).toFixed(0)}%` : '0%'}
                        </span>
                      </div>
                      <div className="d-flex justify-content-between mb-1">
                        <span className="text-muted">Pujas</span>
                        <span className="font-mono fw-bold">{auction.total_pujas}</span>
                      </div>
                    </div>
                    <div className="mt-auto d-flex justify-content-between align-items-center">
                      <span className="font-mono fw-bold" style={{ fontSize: '1.2rem', color: 'var(--color-primary)' }}>
                        ${Number(auction.precio_actual).toLocaleString('es-CL')} CC
                      </span>
                      <Link to={`/subasta/${auction.id}`} className="btn btn-primary btn-sm" style={{ borderRadius: '8px' }}>
                        Ver Subasta
                      </Link>
                    </div>
                  </Card.Body>
                </Card>
              </Col>
            ))}
          </Row>

          {totalPages > 1 && (
            <div className="d-flex justify-content-center gap-2 mt-4">
              <Button variant="outline-secondary" size="sm" disabled={page <= 1} onClick={() => setPage(page - 1)} style={{ borderRadius: '8px' }}>
                Anterior
              </Button>
              <span className="align-self-center text-muted" style={{ fontSize: '0.85rem' }}>
                Página {page} de {totalPages}
              </span>
              <Button variant="outline-secondary" size="sm" disabled={page >= totalPages} onClick={() => setPage(page + 1)} style={{ borderRadius: '8px' }}>
                Siguiente
              </Button>
            </div>
          )}
        </>
      )}
    </Container>
  );
}

export default AuctionList;
