-- Admin-triggered refresh: re-fetch from WeatherKit instead of reusing cached observations.
-- Needed because an hour that was still a forecast at collection time is later revised to an
-- actual observation; a plain requeue would just replay the cached (forecast) values.
ALTER TABLE trip_weather ADD COLUMN force_refresh BOOLEAN NOT NULL DEFAULT FALSE;
