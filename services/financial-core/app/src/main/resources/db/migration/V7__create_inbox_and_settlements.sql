-- Inbox idempotente do consumidor de eventos de pagamento (RF-07).
--
-- O event_id é a chave de deduplicação: a inserção usa ON CONFLICT DO NOTHING,
-- então a mesma entrega (at-least-once) não executa o efeito financeiro duas
-- vezes. O replay audita cada reprocessamento em inbox_replays.

create table inbox_events (
  id uuid primary key,
  event_id uuid not null unique,
  event_type varchar(120) not null,
  event_version int not null,
  aggregate_id uuid not null,
  topic varchar(200) not null,
  kafka_partition int not null,
  kafka_offset bigint not null,
  payload jsonb not null,
  status varchar(20) not null,
  attempts int not null default 0,
  last_error text,
  received_at timestamptz not null,
  processed_at timestamptz,
  constraint inbox_events_status_check check (status in ('RECEIVED', 'PROCESSED', 'FAILED'))
);

create index idx_inbox_events_status on inbox_events (status, received_at);

-- Efeito financeiro do consumidor: liquidação do pagamento confirmado.
-- payment_id e event_id são únicos, então reprocessar é um no-op financeiro.
create table settlements (
  id uuid primary key,
  payment_id uuid not null unique,
  event_id uuid not null unique,
  amount bigint not null,
  currency varchar(3) not null,
  settled_at timestamptz not null,
  created_at timestamptz not null
);

create index idx_settlements_settled_at on settlements (settled_at);

-- Trilha de auditoria de replay: quem pediu, quando e qual foi o resultado.
create table inbox_replays (
  id uuid primary key,
  inbox_event_id uuid not null references inbox_events (id),
  replayed_by uuid not null,
  outcome varchar(40) not null,
  replayed_at timestamptz not null
);

create index idx_inbox_replays_event on inbox_replays (inbox_event_id, replayed_at desc);
