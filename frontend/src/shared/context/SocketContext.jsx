import React, { createContext, useContext, useEffect, useRef } from 'react';
import { io } from 'socket.io-client';
import { useAuth } from './AuthContext';
import { useToast } from './ToastContext';

const SocketContext = createContext(null);

export function useSocket() {
  return useContext(SocketContext);
}

export const ActiveLiveRaceContext = createContext(null);

export function useActiveLiveRace() {
  return useContext(ActiveLiveRaceContext);
}

export function SocketProvider({ children }) {
  const { user } = useAuth();
  const showToast = useToast();
  const socketRef = useRef(null);
  const [activeLiveRaceId, setActiveLiveRaceId] = React.useState(null);

  useEffect(() => {
    if (!user) return;

    const connect = () => {
      if (socketRef.current?.connected) return;
      const envUrl = process.env.REACT_APP_SOCKET_URL;
      const socketHost = (envUrl && !envUrl.includes('localhost'))
        ? envUrl
        : `http://${window.location.hostname || 'localhost'}:9092`;
      const socket = io(socketHost, {
        transports: ['websocket', 'polling'],
        reconnection: true,
        reconnectionDelay: 1000,
        reconnectionAttempts: 10,
      });
      socketRef.current = socket;

      socket.on('race_starting_soon', (data) => {
        if (data?.carrera_id) {
          setActiveLiveRaceId(data.carrera_id);
          showToast(`¡La carrera #${data.carrera_id} comenzará en 10 segundos!`, 'warning');
        }
      });

      socket.on('race_started', (data) => {
        if (data?.carrera_id) {
          setActiveLiveRaceId(data.carrera_id);
          showToast(`¡Carrera #${data.carrera_id} en vivo ahora!`, 'info');
        }
      });
    };

    connect();

    const handleVisibility = () => {
      if (document.visibilityState === 'visible') {
        connect();
      }
    };
    document.addEventListener('visibilitychange', handleVisibility);

    return () => {
      document.removeEventListener('visibilitychange', handleVisibility);
      if (socketRef.current) {
        socketRef.current.off('race_starting_soon');
        socketRef.current.off('race_started');
        socketRef.current.disconnect();
        socketRef.current = null;
      }
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  return (
    <SocketContext.Provider value={socketRef}>
      <ActiveLiveRaceContext.Provider value={{ activeLiveRaceId, setActiveLiveRaceId }}>
        {children}
      </ActiveLiveRaceContext.Provider>
    </SocketContext.Provider>
  );
}
