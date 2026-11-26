-- Seed data (idempotent: INSERT IGNORE, runs on every startup via spring.sql.init.mode=always)

-- users: password for both is "password" (BCrypt)
INSERT IGNORE INTO users (username, password, enabled) VALUES
  ('alice', '$2a$10$.6WaU4pAflYxOgEEEPv99eEzfYjjMNREyib56/TCmBwJKsaYSNSSm', TRUE),
  ('bob',   '$2a$10$.6WaU4pAflYxOgEEEPv99eEzfYjjMNREyib56/TCmBwJKsaYSNSSm', TRUE);

-- roles: alice may use weather tool, bob may use vip tool
INSERT IGNORE INTO authorities (username, authority) VALUES
  ('alice', 'ROLE_WEATHER'),
  ('bob',   'ROLE_VIP');

-- tool catalog (tool_name must match @Tool name attribute)
INSERT IGNORE INTO sys_tool (tool_name, description) VALUES
  ('getCurrentWeather', 'Query current weather of a city'),
  ('queryVipLevel', 'Query VIP level of a user');

-- role -> tool authorization
INSERT IGNORE INTO sys_role_tool (role, tool_name) VALUES
  ('ROLE_WEATHER', 'getCurrentWeather'),
  ('ROLE_VIP', 'queryVipLevel');
