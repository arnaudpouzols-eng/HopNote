CREATE TABLE device_sessions (
  id TEXT PRIMARY KEY,
  token_hash TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE TABLE notion_connections (
  device_id TEXT PRIMARY KEY REFERENCES device_sessions(id) ON DELETE CASCADE,
  encrypted_access_token TEXT NOT NULL,
  hopnote_page_id TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE oauth_states (
  state TEXT PRIMARY KEY,
  device_id TEXT NOT NULL REFERENCES device_sessions(id) ON DELETE CASCADE,
  expires_at INTEGER NOT NULL
);
