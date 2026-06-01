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
**Backend:** Backend: Java 21, Spring Boot, Spring Data JPA, Timefold Solver, PostgreSQL, Maven
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

## Status
Active thesis project — under development.

## Acknowledgements
Automated scheduling powered by Timefold Solver — the open-source successor to OptaPlanner.

## Author
**Panteleimon Michalakis** — CEID, University of Patras