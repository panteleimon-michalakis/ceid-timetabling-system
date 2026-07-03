# CEID Timetabling & Exam-Scheduling System

A full-stack web platform for managing **semester timetables** and **examination
schedules** at the Computer Engineering & Informatics Department (CEID),
University of Patras. Developed as my Diploma Thesis (Integrated Master).

Faculty create and edit schedules through a management interface; students get a
synchronized, read-only view. It uses the open-source Timefold Solver for automated, constraint-aware schedule generation.

## Features
- Role-based access control: **Admin / Teacher / Student**
- Management of courses, rooms and weekly time slots
- Semester timetable **and** exam-period scheduling
- Constraint-aware design (course-gap limits, protected midday slot, room
  restrictions, exam spreading)
- REST API backend with a React single-page front-end

## Tech stack
**Backend:** Java 21, Spring Boot, Spring Data JPA, Timefold Solver, PostgreSQL, Maven  
**Frontend:** React, TypeScript, Vite, React Router, Axios

## Project structure
timetable/      Spring Boot backend (REST API, JPA entities, security)
frontend/       React + TypeScript + Vite client
## Getting started

### Prerequisites
- JDK 21, Maven, Node.js, PostgreSQL

### 1. Database
Create a database `ceid_timetable` and a user `ceid_admin`, then set the password
as an environment variable:
setx DB_PASSWORD "your_password"
### 2. Backend
cd timetable
mvn spring-boot:run
Runs on http://localhost:8080

### 3. Frontend
cd frontend
npm install
npm run dev
Runs on http://localhost:5173

## Deployment (Docker)

### Prerequisites
- Docker + Docker Compose

### Quick start
```
git clone <repo-url>
cd timetable
cp .env.example .env    # fill in real values, see below
docker compose up --build
```
This builds and starts three containers: PostgreSQL 16, the Spring Boot
backend, and the React frontend (served by nginx). Flyway applies all schema
migrations automatically on first startup against the empty container database.

### Where it lands
- Frontend: http://localhost (port 80)
- Backend API: http://localhost:8080 (also reachable through nginx's `/api/`
  reverse proxy on port 80, which is what the frontend actually uses)

### Before a real deployment
The default `docker-compose.yml` config targets **local/LAN use only**. For a
real domain you additionally need to update:
- `CORS_ORIGINS` (backend service in `docker-compose.yml`) to the real
  frontend origin
- `VITE_API_URL` build arg (currently hardcoded to `/api` in
  `frontend/Dockerfile`)
- HTTPS — not handled by the compose stack itself; this is an infrastructure
  concern (reverse proxy/TLS termination in front of the containers)

### First login
On first startup (any non-`prod` Spring profile — the default), the backend
seeds three accounts for `admin`/`teacher`/`student` roles (see
`UserSeeder`). These are well-known development credentials, **not** meant
for real use — change all three passwords immediately after first login in
any deployment reachable outside your own machine.

### Manual build (χωρίς Docker)
Both artifacts can also be built and run directly, without Docker:

**Backend JAR:**
```
cd timetable
mvnw.cmd package -DskipTests
java -jar target\*.jar
```
Needs `DB_PASSWORD` (and `DB_URL`/`DB_USERNAME` if not using the defaults)
plus `JWT_SECRET` set as environment variables, and a reachable PostgreSQL
instance (local dev DB or the same container from the Docker path above).

**Frontend static build:**
```
cd frontend
npm run build
```
Produces a static bundle in `frontend/dist/`, servable by any static file
server (or `npm run preview` for a quick local check).

## Status
Feature-complete for thesis submission (July 2026) — core functionality
(weekly & exam scheduling, solver, auth, CRUD management) is stable and
tested (224 backend + 13 frontend tests passing). Some architectural
extensions remain on the roadmap for future work.

## Acknowledgements
Automated scheduling powered by Timefold Solver — the open-source successor to OptaPlanner.

## Author
**Panteleimon Michalakis** — CEID, University of Patras