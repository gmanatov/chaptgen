CREATE TABLE IF NOT EXISTS users (
  id BIGSERIAL PRIMARY KEY,
  email TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS generations (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  url TEXT NOT NULL,
  title TEXT,
  status TEXT NOT NULL DEFAULT 'queued', -- queued|processing|completed|failed
  chapters_json JSONB,
  transcript TEXT,
  model TEXT,
  error TEXT,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_generations_user_id_created_at
  ON generations(user_id, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_generations_user_url
  ON generations(user_id, url);