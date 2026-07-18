-- Intentionally destructive schema for safety-blocking tests.
DROP TABLE users;
CREATE TABLE users (id bigint primary key);
