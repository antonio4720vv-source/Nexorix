-- ============================================================
-- Nexorix - datos de prueba para la demo (usuario "demo")
-- Ejecutar DESPUES de 01-bank-fraud-schema.sql. Idempotente.
--
--   psql -U postgres -d nexorix -f db/02-demo-seed.sql
--
-- Entra con usuario  demo  /  contraseña  Demo1234!  /  PIN  123456
-- (los hashes BCrypt los genera pgcrypto; Spring Security los acepta tal cual).
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

BEGIN;

-- Usuario con identidad ya "verificada" (se salta Didit) y WhatsApp activado.
INSERT INTO users (public_id, name, username, email, cedula, password_hash, pin_hash, kyc_status, active,
                   created_at, failed_password_attempts, failed_pin_attempts,
                   whatsapp_notifications_enabled, security_phone)
VALUES (gen_random_uuid()::text, 'Usuario Demo', 'demo', 'demo@nexorix.local', '1020304050',
        crypt('Demo1234!', gen_salt('bf', 10)), crypt('123456', gen_salt('bf', 10)),
        'VERIFIED', true, now(), 0, 0, true, '573001234567')
ON CONFLICT (username) DO NOTHING;

-- Tres cuentas, una por banco.
INSERT INTO accounts (name, type, bank, balance, user_id)
SELECT v.name, v.type, v.bank, v.balance, u.id
FROM users u,
     (VALUES ('Nequi',       'BILLETERA', 'Nequi',       2000000.00),
             ('Bancolombia', 'AHORROS',   'Bancolombia', 5000000.00),
             ('Davivienda',  'AHORROS',   'Davivienda',  1000000.00)) AS v(name, type, bank, balance)
WHERE u.username = 'demo'
  AND NOT EXISTS (SELECT 1 FROM accounts a WHERE a.user_id = u.id AND a.name = v.name);

-- Cuentas vinculadas: estas referencias son las que van en "accountRef" del webhook.
INSERT INTO bank_links (user_id, account_id, bank, external_ref, created_at)
SELECT u.id, a.id, upper(a.bank), lower(a.bank) || '-demo-001', now()
FROM users u JOIN accounts a ON a.user_id = u.id
WHERE u.username = 'demo'
ON CONFLICT (external_ref) DO NOTHING;

-- WhatsApp verificado (para que Nexorix pueda escribir al numero de la demo).
INSERT INTO whatsapp_links (user_id, phone, verified, enabled, updated_at)
SELECT id, '573001234567', true, true, now() FROM users WHERE username = 'demo'
ON CONFLICT (user_id) DO NOTHING;

-- Historial de comportamiento: compras habituales en Bogota (>= 5 filas para que
-- un comercio nuevo se marque como "inusual") y un destinatario frecuente.
-- La ultima compra con ubicacion es de hace 2 dias, asi que NO dispara la regla de viaje.
INSERT INTO user_behavior_log (user_id, event_type, merchant_key, merchant_name, category, amount,
                               country, city, latitude, longitude, occurred_at, created_at)
SELECT u.id, 'PURCHASE', v.mkey, v.mname, v.cat, v.amount, 'CO', 'Bogotá', 4.7110, -74.0721,
       now() - (v.days || ' days')::interval, now()
FROM users u,
     (VALUES ('exito',     'Éxito',     'MERCADO',      85000,  9),
             ('exito',     'Éxito',     'MERCADO',      64000,  7),
             ('rappi',     'Rappi',     'RESTAURANTES', 38000,  6),
             ('starbucks', 'Starbucks', 'RESTAURANTES', 21000,  4),
             ('exito',     'Éxito',     'MERCADO',      72000,  3),
             ('rappi',     'Rappi',     'RESTAURANTES', 41000,  2)) AS v(mkey, mname, cat, amount, days)
WHERE u.username = 'demo'
  AND NOT EXISTS (SELECT 1 FROM user_behavior_log l WHERE l.user_id = u.id);

INSERT INTO user_behavior_log (user_id, event_type, recipient_key, recipient_name, amount, occurred_at, created_at)
SELECT u.id, 'TRANSFER', 'nequi 3001112233', 'Carlos Pérez', 80000, now() - interval '5 days', now()
FROM users u
WHERE u.username = 'demo'
  AND NOT EXISTS (SELECT 1 FROM user_behavior_log l WHERE l.user_id = u.id AND l.recipient_key = 'nequi 3001112233');

COMMIT;
