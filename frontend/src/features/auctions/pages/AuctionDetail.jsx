import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { Container, Row, Col, Card, Button, Badge, Form, Alert } from 'react-bootstrap';
import { io } from 'socket.io-client';
import { useToast } from '../../../shared/context/ToastContext';
import { useAuth } from '../../../shared/context/AuthContext';
import api from '../../../shared/services/api';

function AuctionDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const showToast = useToast();
  const { user, updateUserSaldo } = useAuth();
  const socketRef = useRef(null);

  const [auction, setAuction] = useState(null);
  const [loading, setLoading] = useState(true);
  const [bidAmount, setBidAmount] = useState('');
  const [bidding, setBidding] = useState(false);
  const [timeLeft, setTimeLeft] = useState('');
  const [countdown, setCountdown] = useState(0);

  const fetchAuction = useCallback(async () => {
    try {
      const response = await api.get(`/api/auctions/${id}`);
      if (response.data.success) setAuction(response.data.data.auction);
    } catch {
      showToast('Subasta no encontrada', 'error');
    } finally {
      setLoading(false);
    }
  }, [id, showToast]);

  useEffect(() => { fetchAuction(); }, [fetchAuction]);

  // Socket.IO for real-time bids
  useEffect(() => {
    const socketUrl = process.env.REACT_APP_SOCKET_URL || process.env.REACT_APP_API_URL || 'http://localhost:4000';
    const socket = io(socketUrl, { transports: ['websocket', 'polling'] });
    socketRef.current = socket;

    socket.emit('join_auction', { subasta_id: Number(id) });

    socket.on('auction_bid', (data) => {
      if (data.subasta_id === Number(id)) {
        fetchAuction(); // Refresh auction data
      }
    });

    socket.on('auction_ended', (data) => {
      if (data.subasta_id === Number(id)) {
        fetchAuction();
      }
    });

    return () => {
      socket.emit('leave_auction', { subasta_id: Number(id) });
      socket.off('auction_bid');
      socket.off('auction_ended');
      socket.disconnect();
    };
  }, [id, fetchAuction]);

  // Countdown timer
  useEffect(() => {
    if (!auction || auction.estado !== 'activa') return;

    const updateCountdown = () => {
      const now = new Date();
      const end = new Date(auction.fecha_fin);
      const diff = end - now;

      if (diff <= 0) {
        setTimeLeft('Finalizada');
        setCountdown(0);
        return;
      }

      const hours = Math.floor(diff / (1000 * 60 * 60));
      const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
      const seconds = Math.floor((diff % (1000 * 60)) / 1000);

      if (hours > 24) {
        setTimeLeft(`${Math.floor(hours / 24)}d ${hours % 24}h`);
      } else {
        setTimeLeft(`${hours}h ${minutes}m ${seconds}s`);
      }
      setCountdown(diff);
    };

    updateCountdown();
    const interval = setInterval(updateCountdown, 1000);
    return () => clearInterval(interval);
  }, [auction]);

  const handleBid = async () => {
    if (!bidAmount || Number(bidAmount) <= 0) {
      showToast('Ingresa un monto válido', 'warning');
      return;
    }

    setBidding(true);
    try {
      const response = await api.post(`/api/auctions/${id}/bid`, {
        monto: Number(bidAmount),
      });
      if (response.data.success) {
        showToast(`Puja realizada: $${Number(bidAmount).toLocaleString('es-CL')} CC`, 'success');
        if (response.data.data.saldo !== undefined) {
          updateUserSaldo(response.data.data.saldo);
        }
        setBidAmount('');
        fetchAuction();

        // Broadcast bid via socket
        if (socketRef.current) {
          socketRef.current.emit('auction_bid', { subasta_id: Number(id) });
        }
      }
    } catch (err) {
      showToast(err.response?.data?.error || 'Error al pujar', 'error');
    } finally {
      setBidding(false);
    }
  };

  if (loading) {
    return (
      <Container className="py-5 text-center">
        <div className="spinner-border text-success" role="status"></div>
      </Container>
    );
  }

  if (!auction) {
    return (
      <Container className="py-5 text-center">
        <p className="text-muted">Subasta no encontrada.</p>
        <Button variant="primary" onClick={() => navigate('/subastas')}>Volver a subastas</Button>
      </Container>
    );
  }

  const isActive = auction.estado === 'activa' && countdown > 0;
  const isOwner = user?.id === auction.vendedor_id;
  const currentPrice = Number(auction.precio_actual);
  const minBid = currentPrice + 1;

  return (
    <Container className="py-4">
      <Button variant="outline-secondary" size="sm" className="mb-3" onClick={() => navigate('/subastas')} style={{ borderRadius: '8px' }}>
        <i className="bi bi-arrow-left me-1"></i>Volver a subastas
      </Button>

      <Row className="mb-4">
        <Col>
          <h2 className="font-heading fw-bold mb-1" style={{ color: 'var(--color-text-dark)' }}>
            <i className="bi bi-hammer me-2" style={{ color: 'var(--color-primary)' }}></i>
            Subasta de {auction.caballo_nombre}
          </h2>
          <div className="d-flex align-items-center gap-3">
            <Badge className="px-3 py-2" style={{
              backgroundColor: isActive ? '#0d6efd' : '#6c757d',
              color: '#fff',
              borderRadius: '20px',
            }}>
              {isActive ? `Termina en ${timeLeft}` : auction.estado === 'finalizada' ? 'Finalizada' : 'Cancelada'}
            </Badge>
            {auction.ganador_username && (
              <span className="text-muted" style={{ fontSize: '0.9rem' }}>
                Ganador: <strong>{auction.ganador_username}</strong>
              </span>
            )}
          </div>
        </Col>
      </Row>

      <Row className="g-4">
        <Col lg={8}>
          <Card className="border-0 shadow-sm mb-4">
            <Card.Body className="p-4">
              <h5 className="font-heading fw-bold mb-3">
                <i className="bi bi-heart-fill me-2" style={{ color: 'var(--color-primary)' }}></i>{auction.caballo_nombre}
              </h5>
              <div className="row g-3" style={{ fontSize: '0.9rem' }}>
                <div className="col-6 col-md-3">
                  <span className="text-muted d-block">Edad</span>
                  <span className="font-mono fw-bold">{auction.caballo_edad} años</span>
                </div>
                <div className="col-6 col-md-3">
                  <span className="text-muted d-block">Velocidad</span>
                  <span className="font-mono fw-bold">{auction.caballo_velocidad}/100</span>
                </div>
                <div className="col-6 col-md-3">
                  <span className="text-muted d-block">Resistencia</span>
                  <span className="font-mono fw-bold">{auction.caballo_resistencia}/100</span>
                </div>
                <div className="col-6 col-md-3">
                  <span className="text-muted d-block">Corazón</span>
                  <span className="font-mono fw-bold">{auction.caballo_corazon}/100</span>
                </div>
                <div className="col-6 col-md-3">
                  <span className="text-muted d-block">Fatiga</span>
                  <span className="font-mono fw-bold">{auction.caballo_fatiga}%</span>
                </div>
                <div className="col-6 col-md-3">
                  <span className="text-muted d-block">Carreras</span>
                  <span className="font-mono fw-bold">{auction.caballo_carreras}</span>
                </div>
                <div className="col-6 col-md-3">
                  <span className="text-muted d-block">Victorias</span>
                  <span className="font-mono fw-bold">{auction.caballo_victorias}</span>
                </div>
                <div className="col-6 col-md-3">
                  <span className="text-muted d-block">Winrate</span>
                  <span className="font-mono fw-bold">
                    {auction.caballo_carreras > 0 ? `${((auction.caballo_victorias / auction.caballo_carreras) * 100).toFixed(0)}%` : '0%'}
                  </span>
                </div>
              </div>
            </Card.Body>
          </Card>

          <Card className="border-0 shadow-sm">
            <Card.Body className="p-4">
              <h5 className="font-heading fw-bold mb-3">
                <i className="bi bi-list-ol me-2" style={{ color: 'var(--color-primary)' }}></i>Pujas ({auction.pujas?.length || 0})
              </h5>
              {auction.pujas?.length > 0 ? (
                <div className="d-flex flex-column gap-2">
                  {auction.pujas.map((bid, idx) => (
                    <div key={bid.id} className="d-flex justify-content-between align-items-center p-2 rounded"
                      style={{
                        backgroundColor: idx === 0 ? 'rgba(21, 189, 15, 0.08)' : 'transparent',
                        border: idx === 0 ? '1px solid var(--color-primary)' : '1px solid #e9ecef',
                      }}>
                      <div className="d-flex align-items-center gap-2">
                        {idx === 0 && <i className="bi bi-trophy-fill" style={{ color: '#FFD700' }}></i>}
                        <Link to={`/perfil/${bid.usuario_id}`} className="fw-medium text-decoration-none" style={{ color: 'var(--color-text-dark)' }}>
                          {bid.usuario_username}
                        </Link>
                      </div>
                      <span className="font-mono fw-bold" style={{ color: idx === 0 ? 'var(--color-primary)' : 'inherit' }}>
                        ${Number(bid.monto).toLocaleString('es-CL')} CC
                      </span>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-muted mb-0">Aún no hay pujas. Sé el primero en pujar.</p>
              )}
            </Card.Body>
          </Card>
        </Col>

        <Col lg={4}>
          <Card className="border-0 shadow-sm mb-4" style={{ borderTop: '4px solid var(--color-primary)' }}>
            <Card.Body className="p-4">
              <h5 className="font-heading fw-bold mb-3">Pujar</h5>

              <div className="d-flex justify-content-between mb-2" style={{ fontSize: '0.9rem' }}>
                <span className="text-muted">Precio actual</span>
                <span className="font-mono fw-bold" style={{ fontSize: '1.3rem', color: 'var(--color-primary)' }}>
                  ${currentPrice.toLocaleString('es-CL')} CC
                </span>
              </div>

              {auction.precio_reserva && (
                <div className="d-flex justify-content-between mb-3" style={{ fontSize: '0.85rem' }}>
                  <span className="text-muted">Reserva</span>
                  <span className="font-mono">
                    ${Number(auction.precio_reserva).toLocaleString('es-CL')} CC
                    {currentPrice >= auction.precio_reserva ? ' ✅' : ' ❌'}
                  </span>
                </div>
              )}

              <div className="d-flex justify-content-between mb-3" style={{ fontSize: '0.85rem' }}>
                <span className="text-muted">Total pujas</span>
                <span className="font-mono fw-bold">{auction.total_pujas}</span>
              </div>

              <hr />

              {isActive && !isOwner && (
                <>
                  <Form.Group className="mb-3">
                    <Form.Label className="fw-medium">Tu puja (mínimo ${minBid.toLocaleString('es-CL')} CC)</Form.Label>
                    <Form.Control
                      type="number"
                      min={minBid}
                      value={bidAmount}
                      onChange={(e) => setBidAmount(e.target.value)}
                      placeholder={`Mínimo: ${minBid.toLocaleString('es-CL')}`}
                      style={{ borderRadius: '8px' }}
                    />
                    <Form.Text className="text-muted">
                      Se descuenta de tu saldo como depósito
                    </Form.Text>
                  </Form.Group>

                  <p className="text-muted mb-3" style={{ fontSize: '0.85rem' }}>
                    Tu saldo: <span className="font-mono fw-bold">${(user?.saldo ?? 0).toLocaleString('es-CL')} CC</span>
                  </p>

                  <Button
                    variant="primary"
                    className="w-100"
                    disabled={!bidAmount || Number(bidAmount) < minBid || bidding || (user?.saldo ?? 0) < Number(bidAmount)}
                    onClick={handleBid}
                    style={{ borderRadius: '8px' }}
                  >
                    {bidding ? 'Procesando...' : 'Pujar'}
                  </Button>

                  {(user?.saldo ?? 0) < minBid && (
                    <p className="text-danger mt-2 mb-0" style={{ fontSize: '0.8rem' }}>
                      Saldo insuficiente para esta puja
                    </p>
                  )}
                </>
              )}

              {isOwner && (
                <Alert variant="info" className="mb-0">
                  Esta es tu subasta. No puedes pujar en ella.
                </Alert>
              )}

              {!isActive && auction.estado === 'finalizada' && (
                <Alert variant="secondary" className="mb-0">
                  {auction.ganador_username
                    ? `Ganador: ${auction.ganador_username}`
                    : 'Subasta finalizada sin ganador'}
                </Alert>
              )}
            </Card.Body>
          </Card>

          <Card className="border-0 shadow-sm" style={{ borderTop: '4px solid #6c757d' }}>
            <Card.Body className="p-4">
              <h6 className="font-heading fw-bold mb-2">Vendedor</h6>
              <Link to={`/perfil/${auction.vendedor_id}`} className="d-flex align-items-center gap-2 text-decoration-none">
                <i className="bi bi-person-circle" style={{ fontSize: '1.5rem', color: 'var(--color-primary)' }}></i>
                <span className="fw-medium" style={{ color: 'var(--color-text-dark)' }}>{auction.vendedor_username}</span>
              </Link>
            </Card.Body>
          </Card>
        </Col>
      </Row>
    </Container>
  );
}

export default AuctionDetail;
