import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { Container, Row, Col, Card, Button, Badge, Modal, Form } from 'react-bootstrap';
import { useToast } from '../../../shared/context/ToastContext';
import HorseCondition, { LESION_LABELS } from '../../../shared/components/HorseCondition';
import api from '../../../shared/services/api';

const PAGE_SIZE = 10;

function Stable() {
  const showToast = useToast();
  const [horses, setHorses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);

  const [renameModal, setRenameModal] = useState({ show: false, id: null, nombre: '' });
  const [sellModal, setSellModal] = useState({ show: false, id: null, precio: '' });
  const [historyModal, setHistoryModal] = useState({ show: false, id: null, history: [] });
  const [deleteModal, setDeleteModal] = useState({ show: false, id: null, nombre: '' });
  const [jockeyModal, setJockeyModal] = useState({ show: false, horse: null, jineteId: '' });
  const [itemModal, setItemModal] = useState({ show: false, horse: null, itemId: '' });
  const [misJinetes, setMisJinetes] = useState([]);
  const [inventario, setInventario] = useState([]);
  const [saving, setSaving] = useState(false);

  const fetchExtras = useCallback(async () => {
    try {
      const [jRes, iRes] = await Promise.all([api.get('/api/jockeys/mine'), api.get('/api/inventory')]);
      if (jRes.data.success) setMisJinetes(jRes.data.data.jinetes);
      if (iRes.data.success) setInventario(iRes.data.data.inventario);
    } catch {
      /* el establo funciona aunque fallen los extras */
    }
  }, []);

  const fetchHorses = useCallback(async () => {
    try {
      const response = await api.get('/api/stable');
      if (response.data.success) setHorses(response.data.data.horses);
    } catch {
      showToast('Error al cargar el establo', 'error');
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  useEffect(() => { fetchHorses(); fetchExtras(); }, [fetchHorses, fetchExtras]);

  const openJockeyModal = (horse) => {
    setJockeyModal({ show: true, horse, jineteId: horse.jinete ? String(horse.jinete.id) : '' });
  };

  const handleAssignJockey = async () => {
    const { horse, jineteId } = jockeyModal;
    setSaving(true);
    try {
      let response;
      if (jineteId) {
        response = await api.patch(`/api/jockeys/${jineteId}/assign`, { caballoId: horse.id });
      } else if (horse.jinete) {
        response = await api.patch(`/api/jockeys/${horse.jinete.id}/assign`, { caballoId: null });
      }
      if (response) showToast(response.data.data.message, 'success');
      setJockeyModal({ show: false, horse: null, jineteId: '' });
      fetchHorses();
      fetchExtras();
    } catch (err) {
      showToast(err.response?.data?.error || 'Error al asignar el jinete', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleUseItem = async () => {
    const { horse, itemId } = itemModal;
    if (!itemId) return;
    setSaving(true);
    try {
      const response = await api.post(`/api/inventory/${itemId}/use`, { caballoId: horse.id });
      showToast(response.data.data.message, 'success');
      setItemModal({ show: false, horse: null, itemId: '' });
      fetchHorses();
      fetchExtras();
    } catch (err) {
      showToast(err.response?.data?.error || 'No se pudo usar el producto', 'error');
    } finally {
      setSaving(false);
    }
  };

  const describirItem = (it) => {
    const efectos = [];
    if (it.cura_nivel > 0) efectos.push(`cura hasta ${['', 'leve', 'moderada', 'grave'][it.cura_nivel]}`);
    if (it.reduce_fatiga > 0) efectos.push(`-${it.reduce_fatiga} fatiga`);
    if (it.aumenta_felicidad > 0) efectos.push(`+${it.aumenta_felicidad} felicidad`);
    return efectos.join(', ');
  };

  const handleRename = async () => {
    if (!renameModal.nombre.trim()) return;
    try {
      await api.patch(`/api/stable/${renameModal.id}/rename`, { nombre: renameModal.nombre.trim() });
      showToast('Caballo renombrado', 'success');
      setRenameModal({ show: false, id: null, nombre: '' });
      fetchHorses();
    } catch (err) {
      showToast(err.response?.data?.error || 'Error al renombrar', 'error');
    }
  };

  const handleSell = async () => {
    const precio = Number(sellModal.precio);
    if (!precio || precio <= 0) return;
    try {
      await api.patch(`/api/stable/${sellModal.id}/sell`, { precio });
      showToast('Caballo puesto a la venta', 'success');
      setSellModal({ show: false, id: null, precio: '' });
      fetchHorses();
    } catch (err) {
      showToast(err.response?.data?.error || 'Error al poner a la venta', 'error');
    }
  };

  const handleUnsell = async (id) => {
    try {
      await api.patch(`/api/stable/${id}/unsell`);
      showToast('Caballo removido de la venta', 'success');
      fetchHorses();
    } catch (err) {
      showToast(err.response?.data?.error || 'Error', 'error');
    }
  };

  const handleDelete = async () => {
    try {
      await api.delete(`/api/stable/${deleteModal.id}`);
      showToast('Caballo eliminado del establo', 'success');
      setDeleteModal({ show: false, id: null, nombre: '' });
      setPage(1);
      fetchHorses();
    } catch (err) {
      showToast(err.response?.data?.error || 'Error al eliminar', 'error');
    }
  };

  const showHistory = async (id) => {
    try {
      const response = await api.get(`/api/stable/${id}/history`);
      if (response.data.success) {
        setHistoryModal({ show: true, id, history: response.data.data.history });
      }
    } catch {
      showToast('Error al cargar historial', 'error');
    }
  };

  const getWinrate = (h) => {
    if (!h.carreras_totales || h.carreras_totales === 0) return '0%';
    return `${((h.victorias / h.carreras_totales) * 100).toFixed(0)}%`;
  };

  const totalPages = Math.max(1, Math.ceil(horses.length / PAGE_SIZE));
  const paginatedHorses = horses.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  if (loading) {
    return (
      <Container className="py-5 text-center">
        <div className="spinner-border text-success" role="status">
          <span className="visually-hidden">Cargando...</span>
        </div>
      </Container>
    );
  }

  return (
    <Container className="py-4">
      <Row className="mb-4">
        <Col>
          <h2 className="font-heading fw-bold mb-1" style={{ color: 'var(--color-text-dark)' }}>
            <i className="bi bi-grid me-2" style={{ color: 'var(--color-primary)' }}></i>Mi Establo
          </h2>
          <p className="text-muted mb-0">{horses.length} caballo{horses.length !== 1 ? 's' : ''} en tu establo</p>
        </Col>
      </Row>

      {horses.length > 0 && (
        <div
          className="mb-4 p-3"
          style={{
            backgroundColor: 'rgba(21, 189, 15, 0.08)',
            border: '1px solid var(--color-primary)',
            borderRadius: '12px',
            color: 'var(--color-text-dark)',
          }}
        >
          <strong>Tu establo:</strong> Aquí ves todos tus caballos. Puedes renombrarlos, ponerlos a la venta,
          asignarles un jinete o darles productos de la <Link to="/tienda">tienda</Link>. Los caballos lesionados,
          muy tristes o con fatiga alta no pueden competir hasta que se recuperen.
        </div>
      )}

      {horses.length === 0 ? (
        <Card className="border-0 shadow-sm">
          <Card.Body className="text-center py-5">
            <i className="bi bi-house-heart" style={{ fontSize: '3rem', color: 'var(--color-contrast-medium)' }}></i>
            <h5 className="font-heading fw-bold mt-3" style={{ color: 'var(--color-text-dark)' }}>
              Tu establo está vacío
            </h5>
            <p className="text-muted mb-3">
              Usa el Gacha para obtener tu primer caballo o visita el Mercado para comprar uno.
            </p>
          </Card.Body>
        </Card>
      ) : (
        <>
          <Row className="g-3">
            {paginatedHorses.map((horse) => (
              <Col xs={12} sm={6} lg={4} key={horse.id}>
                <Card className="h-100 border-0 shadow-sm">
                  <Card.Body className="d-flex flex-column">
                    <div className="d-flex justify-content-between align-items-start mb-2">
                      <h5 className="font-heading fw-bold mb-0" style={{ color: 'var(--color-text-dark)' }}>
                        {horse.nombre}
                      </h5>
                      {horse.en_venta && (
                        <Badge bg="warning" style={{ fontSize: '0.7rem' }}>En venta</Badge>
                      )}
                    </div>

                    <div className="mb-3" style={{ fontSize: '0.85rem' }}>
                      <div className="d-flex justify-content-between mb-1">
                        <span className="text-muted">Victorias / Carreras</span>
                        <span className="font-mono fw-bold">{horse.victorias}/{horse.carreras_totales}</span>
                      </div>
                      <div className="d-flex justify-content-between mb-1">
                        <span className="text-muted">Winrate</span>
                        <span className="font-mono fw-bold">{getWinrate(horse)}</span>
                      </div>
                      <div className="d-flex justify-content-between mb-1">
                        <span className="text-muted">Fatiga</span>
                        <div className="d-flex align-items-center gap-2">
                          <div style={{ width: '60px', height: '6px', backgroundColor: '#e9ecef', borderRadius: '3px' }}>
                            <div
                              style={{
                                width: `${horse.fatiga}%`,
                                height: '100%',
                                backgroundColor: horse.fatiga >= 80 ? '#dc3545' : horse.fatiga >= 50 ? '#ffc107' : 'var(--color-primary)',
                                borderRadius: '3px',
                              }}
                            ></div>
                          </div>
                          <span className="font-mono" style={{ fontSize: '0.8rem' }}>{horse.fatiga}%</span>
                        </div>
                      </div>
                      <HorseCondition felicidad={horse.felicidad} lesion={horse.lesion} lesionHasta={horse.lesion_hasta} />
                      <div className="d-flex justify-content-between mt-1">
                        <span className="text-muted">Jinete</span>
                        <span className="fw-semibold" data-testid="jinete-caballo">
                          {horse.jinete ? (
                            <Link to={`/jinete/${horse.jinete.id}`} className="text-decoration-none">
                              <i className="bi bi-person-badge me-1"></i>{horse.jinete.nombre}
                            </Link>
                          ) : (
                            <span className="text-muted fw-normal">Sin jinete</span>
                          )}
                        </span>
                      </div>
                    </div>

                    <div className="mt-auto d-flex flex-wrap gap-1">
                      <Button
                        variant="outline-dark"
                        size="sm"
                        style={{ borderRadius: '6px', fontSize: '0.78rem' }}
                        onClick={() => openJockeyModal(horse)}
                      >
                        <i className="bi bi-person-badge me-1"></i>Asignar jinete
                      </Button>
                      <Button
                        variant="outline-success"
                        size="sm"
                        style={{ borderRadius: '6px', fontSize: '0.78rem' }}
                        onClick={() => setItemModal({ show: true, horse, itemId: '' })}
                      >
                        <i className="bi bi-basket me-1"></i>Usar ítem
                      </Button>
                      <Link
                        to={`/caballo/${horse.id}`}
                        className="btn btn-outline-primary btn-sm"
                        style={{ borderRadius: '6px', fontSize: '0.78rem' }}
                      >
                        <i className="bi bi-eye me-1"></i>Ver detalle
                      </Link>
                      <Button
                        variant="outline-primary"
                        size="sm"
                        style={{ borderRadius: '6px', fontSize: '0.78rem' }}
                        onClick={() => setRenameModal({ show: true, id: horse.id, nombre: horse.nombre })}
                      >
                        <i className="bi bi-pencil me-1"></i>Renombrar
                      </Button>
                      <Button
                        variant="outline-info"
                        size="sm"
                        style={{ borderRadius: '6px', fontSize: '0.78rem' }}
                        onClick={() => showHistory(horse.id)}
                      >
                        <i className="bi bi-clock-history me-1"></i>Historial
                      </Button>
                      {horse.en_venta ? (
                        <Button
                          variant="outline-warning"
                          size="sm"
                          style={{ borderRadius: '6px', fontSize: '0.78rem' }}
                          onClick={() => handleUnsell(horse.id)}
                        >
                          <i className="bi bi-x-circle me-1"></i>Quitar de venta
                        </Button>
                      ) : (
                        <Button
                          variant="outline-success"
                          size="sm"
                          style={{ borderRadius: '6px', fontSize: '0.78rem' }}
                          onClick={() => setSellModal({ show: true, id: horse.id, precio: '' })}
                        >
                          <i className="bi bi-tag me-1"></i>Vender
                        </Button>
                      )}
                      <Button
                        variant="outline-danger"
                        size="sm"
                        style={{ borderRadius: '6px', fontSize: '0.78rem' }}
                        onClick={() => setDeleteModal({ show: true, id: horse.id, nombre: horse.nombre })}
                      >
                        <i className="bi bi-trash me-1"></i>Eliminar
                      </Button>
                    </div>
                  </Card.Body>
                </Card>
              </Col>
            ))}
          </Row>

          {totalPages > 1 && (
            <div className="d-flex justify-content-center gap-2 mt-4">
              <Button
                variant="outline-secondary"
                size="sm"
                disabled={page <= 1}
                onClick={() => setPage(page - 1)}
                style={{ borderRadius: '8px' }}
              >
                Anterior
              </Button>
              <span className="align-self-center text-muted" style={{ fontSize: '0.85rem' }}>
                Página {page} de {totalPages}
              </span>
              <Button
                variant="outline-secondary"
                size="sm"
                disabled={page >= totalPages}
                onClick={() => setPage(page + 1)}
                style={{ borderRadius: '8px' }}
              >
                Siguiente
              </Button>
            </div>
          )}
        </>
      )}

      <Modal show={renameModal.show} onHide={() => setRenameModal({ show: false, id: null, nombre: '' })} centered>
        <Modal.Header closeButton>
          <Modal.Title className="font-heading fw-bold">Renombrar caballo</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <Form.Group>
            <Form.Label>Nuevo nombre</Form.Label>
            <Form.Control
              type="text"
              value={renameModal.nombre}
              onChange={(e) => setRenameModal({ ...renameModal, nombre: e.target.value })}
              maxLength={100}
              style={{ borderRadius: '8px' }}
            />
          </Form.Group>
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={() => setRenameModal({ show: false, id: null, nombre: '' })}>
            Cancelar
          </Button>
          <Button variant="primary" onClick={handleRename}>Guardar</Button>
        </Modal.Footer>
      </Modal>

      <Modal show={sellModal.show} onHide={() => setSellModal({ show: false, id: null, precio: '' })} centered>
        <Modal.Header closeButton>
          <Modal.Title className="font-heading fw-bold">Poner a la venta</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <Form.Group>
            <Form.Label>Precio en $CC</Form.Label>
            <Form.Control
              type="number"
              min="1"
              value={sellModal.precio}
              onChange={(e) => setSellModal({ ...sellModal, precio: e.target.value })}
              placeholder="Ej: 5000"
              style={{ borderRadius: '8px' }}
            />
          </Form.Group>
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={() => setSellModal({ show: false, id: null, precio: '' })}>
            Cancelar
          </Button>
          <Button variant="primary" onClick={handleSell}>Publicar</Button>
        </Modal.Footer>
      </Modal>

      <Modal show={deleteModal.show} onHide={() => setDeleteModal({ show: false, id: null, nombre: '' })} centered>
        <Modal.Header closeButton>
          <Modal.Title className="font-heading fw-bold">Eliminar caballo</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <p>¿Estás seguro de que quieres eliminar a <strong>{deleteModal.nombre}</strong>?</p>
          <p className="text-muted mb-0" style={{ fontSize: '0.85rem' }}>Esta acción no se puede deshacer.</p>
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={() => setDeleteModal({ show: false, id: null, nombre: '' })}>
            Cancelar
          </Button>
          <Button variant="danger" onClick={handleDelete}>Eliminar</Button>
        </Modal.Footer>
      </Modal>

      <Modal show={jockeyModal.show} onHide={() => setJockeyModal({ show: false, horse: null, jineteId: '' })} centered>
        <Modal.Header closeButton>
          <Modal.Title className="font-heading fw-bold">Jinete de {jockeyModal.horse?.nombre}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {misJinetes.length === 0 ? (
            <p className="text-muted mb-0">
              Aún no tienes jinetes. Contrata uno en <Link to="/jinetes">Jinetes</Link>.
            </p>
          ) : (
            <Form.Group>
              <Form.Label>Elige uno de tus jinetes</Form.Label>
              <Form.Select
                data-testid="select-jinete"
                value={jockeyModal.jineteId}
                onChange={(e) => setJockeyModal({ ...jockeyModal, jineteId: e.target.value })}
                style={{ borderRadius: '8px' }}
              >
                <option value="">Sin jinete</option>
                {misJinetes.map((j) => (
                  <option key={j.id} value={j.id}>
                    {j.nombre} · habilidad {j.habilidad}
                    {j.especialidad ? ` · experto en ${j.especialidad}` : ''}
                    {j.caballo_nombre && j.caballo_id !== jockeyModal.horse?.id ? ` (monta a ${j.caballo_nombre})` : ''}
                  </option>
                ))}
              </Form.Select>
              <Form.Text className="text-muted">
                Si el jinete ya monta otro caballo, se cambia a este. Un caballo tiene un solo jinete.
              </Form.Text>
            </Form.Group>
          )}
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={() => setJockeyModal({ show: false, horse: null, jineteId: '' })}>
            Cancelar
          </Button>
          <Button
            variant="primary"
            onClick={handleAssignJockey}
            disabled={saving || misJinetes.length === 0 || (!jockeyModal.jineteId && !jockeyModal.horse?.jinete)}
          >
            Guardar
          </Button>
        </Modal.Footer>
      </Modal>

      <Modal show={itemModal.show} onHide={() => setItemModal({ show: false, horse: null, itemId: '' })} centered>
        <Modal.Header closeButton>
          <Modal.Title className="font-heading fw-bold">Cuidar a {itemModal.horse?.nombre}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {itemModal.horse && (
            <p className="text-muted" style={{ fontSize: '0.85rem' }}>
              Fatiga {itemModal.horse.fatiga}% · Felicidad {itemModal.horse.felicidad} ·{' '}
              {itemModal.horse.lesion ? LESION_LABELS[itemModal.horse.lesion] : 'Sano'}
            </p>
          )}
          {inventario.length === 0 ? (
            <p className="text-muted mb-0">
              Tu inventario está vacío. Compra comida y curaciones en la <Link to="/tienda">Tienda</Link>.
            </p>
          ) : (
            <Form.Group>
              <Form.Label>Producto</Form.Label>
              <Form.Select
                data-testid="select-item"
                value={itemModal.itemId}
                onChange={(e) => setItemModal({ ...itemModal, itemId: e.target.value })}
                style={{ borderRadius: '8px' }}
              >
                <option value="">Elige un producto…</option>
                {inventario.map((it) => (
                  <option key={it.item_id} value={it.item_id}>
                    {it.nombre} ×{it.cantidad} — {describirItem(it)}
                  </option>
                ))}
              </Form.Select>
            </Form.Group>
          )}
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={() => setItemModal({ show: false, horse: null, itemId: '' })}>
            Cancelar
          </Button>
          <Button variant="success" onClick={handleUseItem} disabled={saving || !itemModal.itemId}>
            Usar
          </Button>
        </Modal.Footer>
      </Modal>

      <Modal show={historyModal.show} onHide={() => setHistoryModal({ show: false, id: null, history: [] })} centered size="lg">
        <Modal.Header closeButton>
          <Modal.Title className="font-heading fw-bold">Historial de carreras</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {historyModal.history.length === 0 ? (
            <p className="text-muted text-center py-3">Este caballo aún no ha competido.</p>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table className="table table-hover mb-0">
                <thead>
                  <tr>
                    <th>Carrera</th>
                    <th>Fecha</th>
                    <th>Posición</th>
                    <th>Tiempo</th>
                  </tr>
                </thead>
                <tbody>
                  {historyModal.history.map((h, idx) => (
                    <tr key={idx}>
                      <td className="fw-medium">{h.carrera_nombre || `Carrera #${h.carrera_id}`}</td>
                      <td>{new Date(h.fecha_programada).toLocaleDateString('es-CL')}</td>
                      <td>
                        <Badge bg={h.posicion_final === 1 ? 'success' : h.posicion_final <= 3 ? 'primary' : 'secondary'}>
                          {h.posicion_final ? `#${h.posicion_final}` : '---'}
                        </Badge>
                      </td>
                      <td className="font-mono">{h.tiempo_final ? `${h.tiempo_final}s` : '---'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Modal.Body>
      </Modal>
    </Container>
  );
}

export default Stable;
