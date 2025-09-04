# ChaptGen

ChaptGen is a full-stack web application that generates YouTube-style video chapters automatically using AI.  
Users can paste a YouTube link, generate chapters with Google Gemini, and save/manage them in a personal gallery. Authentication is built-in so each user has their own generations.

---

## Features

- **Sign up / Sign in** with email + password (stored securely with BCrypt).
- **JWT Authentication** with secure httpOnly cookies.
- **Generate chapters** for any YouTube video that has transcripts.
- **AI-powered summaries** using Google Gemini API.
- **Personal gallery** of saved generations (per user).
- **Edit / delete** saved chapters in detail view.
- **Search** saved generations by title.
- **Clean UI** built with Next.js and Tailwind.

---

## Tech Stack

### Frontend
- [Next.js 13+](https://nextjs.org/) (React, App Router)
- TypeScript
- Tailwind CSS

### Backend
- [Spring Boot 3](https://spring.io/projects/spring-boot) (Java 17+)
- PostgreSQL database
- [Flyway](https://flywaydb.org/) for DB migrations
- Spring Security (lightweight, with custom JWT handling)
- Google Gemini API (chapter generation)
- [youtube-transcript.io](https://youtube-transcript.io) (video transcripts)

---

## Architecture Overview

- **Frontend (Next.js)** calls backend REST API at `http://localhost:8080`.  
  Uses `fetch` wrappers (`api.ts`) with credentials so cookies are sent.

- **Backend (Spring Boot)** exposes REST endpoints under `/auth` and `/transcripts`.  
  Controllers enforce authentication by reading the JWT cookie and verifying ownership in SQL.

- **Database (Postgres)** has two main tables:
  - `users` — user accounts (`id`, `email`, `password_hash`)
  - `generations` — saved chapter generations (with `user_id` foreign key)

---

## Key Endpoints

### Auth
- `POST /auth/signup` — create account
- `POST /auth/login` — authenticate, set JWT cookie
- `POST /auth/logout` — clear cookie
- `GET /auth/me` — return current user info if logged in

### Transcripts
- `POST /transcripts/preview` — generate chapters (not saved)
- `POST /transcripts` — save chapters for logged-in user
- `GET /transcripts/mine` — list my saved generations
- `GET /transcripts/{id}` — view one generation
- `PUT /transcripts/{id}` — update/edit generation
- `DELETE /transcripts/{id}` — delete generation

---

## Setup

### Prerequisites
- Node.js 18+
- Java 17+
- PostgreSQL 14+

### 1. Database

```sql
CREATE DATABASE chaptgen;
CREATE USER chaptgen WITH PASSWORD 'chaptgen';
GRANT ALL PRIVILEGES ON DATABASE chaptgen TO chaptgen;
```

### 2. Backend

Configure API keys in application.yml or environment variables ([youtube-transcript API key](https://youtube-transcript.io) and [Google Gemini API key](https://ai.google.dev/gemini-api/docs/quickstart#java)) are required):

Run backend:
`./mvnw spring-boot:run`

Backend starts on:
`http://localhost:8080`

### 3. Frontend

Navigate to frontend folder:
`npm install`
`npm run dev`

Frontend starts on:
`http://localhost:3000`

### 4. Additional Notes

#### Usage
	1.	Open frontend at http://localhost:3000.
	2.	Sign up with a new email/password.
	3.	Paste a YouTube video URL and click Generate Chapters.
	4.	Preview results, then click Save to keep them in your gallery.
	5.	Access My Generations to search, view, edit, or delete.

#### Authentication Notes
	•	Passwords are stored securely using BCrypt.
	•	A JWT token is set in an httpOnly cookie (chaptgen_token) when you log in or sign up.
	•	Backend checks this cookie on every /transcripts request.
	•	Logout clears the cookie.

#### Known Limitations
	•	Only works for YouTube videos with transcripts available.
	•	AI results (Gemini) are limited to the first ~hour of transcript (no chunking yet).
	•	Cookie is not marked Secure in local dev (so it works on http://localhost). Enable it in production.

#### Possible Improvements
	•	Support longer videos by chunking transcripts.
	•	Multi-language transcript selection.
	•	Deployment config (Docker).
	•	Richer chapter editing UI.