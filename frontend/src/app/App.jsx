import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from '../shared/context/AuthContext';
import { ToastProvider } from '../shared/context/ToastContext';
import { SocketProvider } from '../shared/context/SocketContext';
import DashboardLayout from '../shared/components/DashboardLayout';
import PrivateRoute from '../shared/components/PrivateRoute';
import Landing from '../features/landing/pages/Landing';
import Login from '../features/auth/pages/Login';
import Register from '../features/auth/pages/Register';
import Home from '../features/races/pages/Home';
import Calendar from '../features/races/pages/Calendar';
import RaceDetail from '../features/races/pages/RaceDetail';
import RaceSimulation from '../features/races/pages/RaceSimulation';
import Gacha from '../features/gacha/pages/Gacha';
import Stable from '../features/stable/pages/Stable';
import Market from '../features/market/pages/Market';
import History from '../features/history/pages/History';
import Simulador from '../features/races/pages/Simulador';
import UserProfile from '../features/profile/pages/UserProfile';
import EditProfile from '../features/profile/pages/EditProfile';
import HorseDetail from '../features/horses/pages/HorseDetail';
import AuctionList from '../features/auctions/pages/AuctionList';
import AuctionDetail from '../features/auctions/pages/AuctionDetail';
import CreateAuction from '../features/auctions/pages/CreateAuction';

function App() {
  return (
    <AuthProvider>
      <ToastProvider>
        <Router>
          <SocketProvider>
            <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route path="/" element={<Landing />} />
            <Route
              path="/dashboard"
              element={
                <PrivateRoute>
                  <DashboardLayout>
                    <Home />
                  </DashboardLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/calendario"
              element={
                <PrivateRoute>
                  <DashboardLayout>
                    <Calendar />
                  </DashboardLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/carrera/:id"
              element={
                <PrivateRoute>
                  <DashboardLayout>
                    <RaceDetail />
                  </DashboardLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/carrera/:id/simulacion"
              element={
                <PrivateRoute>
                  <DashboardLayout>
                    <RaceSimulation />
                  </DashboardLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/gacha"
              element={
                <PrivateRoute>
                  <DashboardLayout>
                    <Gacha />
                  </DashboardLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/establo"
              element={
                <PrivateRoute>
                  <DashboardLayout>
                    <Stable />
                  </DashboardLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/mercado"
              element={
                <PrivateRoute>
                  <DashboardLayout>
                    <Market />
                  </DashboardLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/historial"
              element={
                <PrivateRoute>
                  <DashboardLayout>
                    <History />
                  </DashboardLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/simulador"
              element={
                <PrivateRoute>
                  <DashboardLayout>
                    <Simulador />
                  </DashboardLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/perfil/:id"
              element={
                <PrivateRoute>
                  <DashboardLayout>
                    <UserProfile />
                  </DashboardLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/editar-perfil"
              element={
                <PrivateRoute>
                  <DashboardLayout>
                    <EditProfile />
                  </DashboardLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/caballo/:id"
              element={
                <PrivateRoute>
                  <DashboardLayout>
                    <HorseDetail />
                  </DashboardLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/subastas"
              element={
                <PrivateRoute>
                  <DashboardLayout>
                    <AuctionList />
                  </DashboardLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/subasta/:id"
              element={
                <PrivateRoute>
                  <DashboardLayout>
                    <AuctionDetail />
                  </DashboardLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/subastas/crear"
              element={
                <PrivateRoute>
                  <DashboardLayout>
                    <CreateAuction />
                  </DashboardLayout>
                </PrivateRoute>
              }
            />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
          </SocketProvider>
        </Router>
      </ToastProvider>
    </AuthProvider>
  );
}

export default App;
