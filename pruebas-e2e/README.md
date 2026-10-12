# Pruebas de punta a punta

Corren contra la app **real** (PostgreSQL incluido), por HTTP, sin simular nada del servidor: registro, identidad
(webhook de Didit firmado con tu secreto), PIN, login, recuperación de contraseña, avisos del celular, duplicados,
antifraude por distancia y cobros de Dividir gastos.

Lo que no pueden probar es lo que está en servicios de terceros (Didit, WhatsApp, correo real, bancos reales): para eso
el webhook de Didit se firma aquí con `DIDIT_WEBHOOK_SECRET` y el código de recuperación se lee del registro de la app
(solo existe cuando el correo **no** está configurado y `NEXORIX_COOKIE_SECURE` es `false`).

## Cómo correrlas
1. PostgreSQL con una base `nexorix` vacía y `psql` en el PATH (usuario `postgres` sin contraseña desde `localhost`; ajusta `PSQL`).
2. Arranca la app **sin** correo ni cookie segura, con las mismas claves que usarás abajo:
   ```bash
   export NEXORIX_DB_PASSWORD=x DIDIT_API_KEY=x DIDIT_WORKFLOW_ID=x DIDIT_WEBHOOK_SECRET=whsec_test
   export NEXORIX_DATA_KEY=$(openssl rand -base64 32)
   ./mvnw -DskipTests package && java -jar target/NEXORIX-0.0.1-SNAPSHOT.jar > app.log
   ```
3. En otra terminal:
   ```bash
   cd pruebas-e2e
   export DIDIT_WEBHOOK_SECRET=whsec_test APP_LOG=../app.log
   python3 01_registro_recuperacion.py
   python3 02_notificaciones.py
   ```
Cada script crea usuarios nuevos, así que se puede repetir sin limpiar la base. **No las corras contra producción.**
