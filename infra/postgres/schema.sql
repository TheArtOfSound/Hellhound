create extension if not exists vector;

create table if not exists users (
  id text primary key,
  created_at timestamptz not null default now(),
  profile jsonb not null default '{}'::jsonb
);

create table if not exists memories (
  id bigserial primary key,
  user_id text not null,
  kind text not null,
  content text not null,
  importance numeric not null default 0.5,
  embedding vector(1536),
  created_at timestamptz not null default now()
);

create table if not exists projects (
  id bigserial primary key,
  user_id text not null,
  title text not null,
  kind text not null default 'project',
  status text not null default 'active',
  notes text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists tool_runs (
  id bigserial primary key,
  user_id text not null,
  tool text not null,
  input jsonb not null,
  output jsonb not null,
  created_at timestamptz not null default now()
);

create table if not exists receipts (
  id bigserial primary key,
  user_id text not null,
  receipt jsonb not null,
  receipt_hash text not null unique,
  created_at timestamptz not null default now()
);
