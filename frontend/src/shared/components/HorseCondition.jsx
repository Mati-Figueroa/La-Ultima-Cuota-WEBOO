import React from 'react';
import { Badge } from 'react-bootstrap';

export const LESION_LABELS = { leve: 'Lesión leve', moderada: 'Lesión moderada', grave: 'Lesión grave' };
const LESION_VARIANTS = { leve: 'warning', moderada: 'orange', grave: 'danger' };

export function felicidadColor(f) {
  if (f >= 70) return 'var(--color-primary)';
  if (f >= 40) return '#ffc107';
  return '#dc3545';
}

export function felicidadEmoji(f) {
  if (f >= 85) return '😄';
  if (f >= 60) return '🙂';
  if (f >= 35) return '😐';
  if (f >= 15) return '😟';
  return '😢';
}

/** Tiempo restante legible hasta que la lesión sane sola, o null si no sana sola. */
export function tiempoRestante(lesionHasta) {
  if (!lesionHasta) return null;
  const ms = new Date(lesionHasta).getTime() - Date.now();
  if (Number.isNaN(ms)) return null;
  if (ms <= 0) return 'sanando…';
  const min = Math.ceil(ms / 60000);
  if (min < 60) return `${min} min`;
  const h = Math.floor(min / 60);
  const m = min % 60;
  return m ? `${h} h ${m} min` : `${h} h`;
}

export function LesionBadge({ lesion, lesionHasta }) {
  if (!lesion) {
    return <Badge bg="success" data-testid="lesion-badge" style={{ fontSize: '0.7rem' }}>Sano</Badge>;
  }
  const restante = tiempoRestante(lesionHasta);
  const variant = LESION_VARIANTS[lesion] || 'danger';
  return (
    <Badge
      bg={variant === 'orange' ? undefined : variant}
      data-testid="lesion-badge"
      title={restante ? `Sana sola en ${restante}` : 'Solo sana con una curación de la tienda'}
      style={{ fontSize: '0.7rem', ...(variant === 'orange' ? { backgroundColor: '#fd7e14' } : {}) }}
    >
      <i className="bi bi-bandaid me-1"></i>
      {LESION_LABELS[lesion] || lesion}
      {restante ? ` · ${restante}` : ' · requiere curación'}
    </Badge>
  );
}

/** Barra de felicidad + insignia de lesión. */
function HorseCondition({ felicidad = 70, lesion, lesionHasta, compact = false }) {
  const f = Math.max(0, Math.min(100, felicidad ?? 70));
  return (
    <div className="d-flex flex-column gap-1" style={{ fontSize: compact ? '0.8rem' : '0.85rem' }}>
      <div className="d-flex justify-content-between align-items-center">
        <span className="text-muted">Felicidad</span>
        <div className="d-flex align-items-center gap-2">
          <div style={{ width: '60px', height: '6px', backgroundColor: '#e9ecef', borderRadius: '3px' }}>
            <div style={{ width: `${f}%`, height: '100%', backgroundColor: felicidadColor(f), borderRadius: '3px' }}></div>
          </div>
          <span className="font-mono" data-testid="felicidad-valor" style={{ fontSize: '0.8rem' }}>
            {felicidadEmoji(f)} {f}
          </span>
        </div>
      </div>
      <div className="d-flex justify-content-between align-items-center">
        <span className="text-muted">Salud</span>
        <LesionBadge lesion={lesion} lesionHasta={lesionHasta} />
      </div>
    </div>
  );
}

export default HorseCondition;
