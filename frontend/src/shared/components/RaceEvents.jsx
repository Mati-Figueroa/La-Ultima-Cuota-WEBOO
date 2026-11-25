import React from 'react';
import { Card } from 'react-bootstrap';

export const EVENT_META = {
  lluvia: { icon: '🌧️', label: 'Lluvia', color: '#0d6efd' },
  barro: { icon: '🟤', label: 'Barro', color: '#795548' },
  sequia: { icon: '☀️', label: 'Sequía', color: '#fd7e14' },
  rayo: { icon: '⚡', label: 'Rayo', color: '#ffc107' },
  bomba: { icon: '💣', label: 'Bomba', color: '#dc3545' },
  envenenamiento: { icon: '☠️', label: 'Envenenamiento', color: '#6f42c1' },
  abduccion: { icon: '🛸', label: 'Abducción', color: '#20c997' },
  muerte_subita: { icon: '💀', label: 'Colapso súbito', color: '#343a40' },
};

export const ESTADO_ICON = {
  aturdido: '⚡',
  envenenado: '☠️',
  abducido: '🛸',
  colapso: '💀',
};

export const ESTADO_LABEL = {
  aturdido: 'Aturdido',
  envenenado: 'Envenenado',
  abducido: 'Abducido',
  colapso: 'Colapso',
};

const CLIMA_FONDO = {
  lluvia: 'repeating-linear-gradient(100deg, rgba(13,110,253,0.10) 0 2px, transparent 2px 14px), #e3ecf5',
  barro: 'radial-gradient(circle at 20% 40%, rgba(121,85,72,0.25) 0 12%, transparent 13%), radial-gradient(circle at 70% 70%, rgba(121,85,72,0.25) 0 10%, transparent 11%), #d7c4ab',
  sequia: 'linear-gradient(180deg, rgba(253,126,20,0.15), rgba(255,193,7,0.10)), #f7e7c6',
};

export function fondoPista(clima) {
  return CLIMA_FONDO[clima] || '#f5f0e8';
}

export function ClimaBanner({ clima }) {
  if (!clima) return null;
  const meta = EVENT_META[clima];
  return (
    <div
      data-testid="clima-banner"
      className="d-inline-flex align-items-center gap-2 px-3 py-1 mb-2 rounded-pill fw-semibold"
      style={{ backgroundColor: `${meta.color}22`, color: meta.color, fontSize: '0.85rem' }}
    >
      <span>{meta.icon}</span> Clima: {meta.label}
    </div>
  );
}

/** Lista de eventos de la carrera, del más reciente al más antiguo. */
export function EventFeed({ eventos = [], titulo = 'Eventos de la carrera', vacio = 'Aún no ocurre nada inesperado…' }) {
  const ordenados = [...eventos].reverse();
  return (
    <Card className="border-0 shadow-sm" style={{ borderTop: '4px solid #ffc107' }}>
      <Card.Body className="p-3">
        <h6 className="font-heading fw-bold mb-2">
          <i className="bi bi-lightning-charge-fill me-2" style={{ color: '#ffc107' }}></i>{titulo}
        </h6>
        {ordenados.length === 0 ? (
          <p className="text-muted mb-0" style={{ fontSize: '0.85rem' }}>{vacio}</p>
        ) : (
          <div className="d-flex flex-column gap-2" data-testid="feed-eventos">
            {ordenados.map((e, idx) => {
              const meta = EVENT_META[e.tipo] || { icon: '❗', label: e.tipo, color: '#6c757d' };
              return (
                <div
                  key={e.id || `${e.tipo}-${e.segundo}-${idx}`}
                  data-testid="evento"
                  data-tipo={e.tipo}
                  className="d-flex align-items-start gap-2 p-2 rounded"
                  style={{ backgroundColor: `${meta.color}12`, borderLeft: `3px solid ${meta.color}` }}
                >
                  <span style={{ fontSize: '1.2rem', lineHeight: 1 }}>{meta.icon}</span>
                  <div style={{ fontSize: '0.85rem' }}>
                    <span className="fw-bold" style={{ color: meta.color }}>{meta.label}</span>
                    <span className="text-muted ms-2 font-mono">{e.segundo != null ? `${e.segundo}s` : ''}</span>
                    <div>{e.descripcion}</div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </Card.Body>
    </Card>
  );
}
