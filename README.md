# La Última Cuota - WebOO (Spring Boot Edition)

Simulador de carreras de caballos con apuestas estilo pari-mutuel.

## Stack

- **Backend**: Java 21 + Spring Boot 3.3 + Spring Data JPA + Spring Security + Socket.IO (netty-socketio)
- **Frontend**: React 19 + Bootstrap 5 + socket.io-client
- **Database**: PostgreSQL 16
- **Containerization**: Docker + Docker Compose

## Quick Start

```bash
# 1. Copy environment variables
cp .env.example .env

# 2. Start all services
docker-compose up --build

# 3. Access the app
# Frontend: http://localhost:3000
# Backend API: http://localhost:4000/api/health
# Socket.IO: ws://localhost:4000
```

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/register` | No | Register user |
| POST | `/api/auth/login` | No | Login |
| GET | `/api/auth/me` | Yes | Current user |
| GET | `/api/races` | No | List races |
| GET | `/api/races/:id` | No | Race detail |
| GET | `/api/races/:id/odds` | Yes | Pari-mutuel odds |
| GET | `/api/races/:id/results` | Yes | Race results |
| POST | `/api/races/:id/inscribe` | Yes | Inscribe horse |
| POST | `/api/races/:id/bet` | Yes | Place bet |
| GET | `/api/daily/status` | Yes | Daily reward status |
| POST | `/api/daily/claim` | Yes | Claim daily reward |
| POST | `/api/gacha/pull` | Yes | Pull random horse ($300) |
| GET | `/api/stable` | Yes | My horses |
| GET | `/api/stable/:id/history` | Yes | Horse history |
| PATCH | `/api/stable/:id/rename` | Yes | Rename horse |
| DELETE | `/api/stable/:id` | Yes | Delete horse |
| PATCH | `/api/stable/:id/sell` | Yes | Put horse for sale |
| PATCH | `/api/stable/:id/unsell` | Yes | Remove from sale |
| GET | `/api/market` | No | Browse market |
| POST | `/api/market/:id/buy` | Yes | Buy horse |
| GET | `/api/history/bets` | Yes | Bet history |
| GET | `/api/history/wins` | Yes | Recent wins |
| GET | `/api/history/stats` | Yes | Bet stats |
| GET | `/api/health` | No | Health check |

## Development

```bash
# Backend only (requires PostgreSQL running)
cd backend
mvn spring-boot:run

# Frontend only
cd frontend
npm install
npm start
```

## Features

- Pari-mutuel betting system
- Automated race lifecycle with bot horses
- Real-time race simulation via Socket.IO
- Horse gacha system
- Horse marketplace
- Daily rewards
- Bet history and statistics
