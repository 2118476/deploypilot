-- JobPilot database schema (idempotent, non-destructive).
create table if not exists jobs (
    id bigint generated always as identity primary key,
    title text not null,
    company text not null,
    status text not null default 'applied',
    user_id uuid not null,
    created_at timestamptz not null default now()
);

-- Row Level Security so the public anon key is safe in the browser.
alter table jobs enable row level security;

create policy if not exists "users read their own jobs"
    on jobs for select
    using (auth.uid() = user_id);

create policy if not exists "users insert their own jobs"
    on jobs for insert
    with check (auth.uid() = user_id);
