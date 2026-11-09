-- Create schemas for each service
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS trip;
CREATE SCHEMA IF NOT EXISTS media;
CREATE SCHEMA IF NOT EXISTS gallery;
CREATE SCHEMA IF NOT EXISTS admin;
CREATE SCHEMA IF NOT EXISTS notification;
CREATE SCHEMA IF NOT EXISTS mail;
CREATE SCHEMA IF NOT EXISTS ai;
CREATE SCHEMA IF NOT EXISTS notice;
CREATE SCHEMA IF NOT EXISTS feedback;
CREATE SCHEMA IF NOT EXISTS weather;
CREATE SCHEMA IF NOT EXISTS report;

-- Enable extensions
CREATE EXTENSION IF NOT EXISTS citext;
-- Trigram search (substring matching for Japanese/Chinese). Created here so it lands in public,
-- which every service has on its search_path. A service that creates it during its own migration
-- captures it into that service's schema, where other services cannot resolve gin_trgm_ops.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
