# Nexorix

> Sigue tu dinero, no solamente tus movimientos.

Plataforma de análisis, conciliación y trazabilidad financiera. Java 21 · Spring Boot 4.1.1 · PostgreSQL 18 · Didit (KYC) · Gemini (IA).

## Qué hace

| Módulo | Descripción |
|---|---|
| Registro y acceso | Contraseña fuerte, verificación de identidad con Didit, PIN, bloqueo por intentos, recuperación con correo + identidad |
| Cuentas y movimientos | Cuentas, ingresos, egresos y transferencias entre cuentas propias |
| Trace V2 | Detecta transferencias entre cuentas propias 1→1, 1→N, N→1, con comisiones; confirmar, deshacer e historial |
| Dinero real | Ingresos y gastos reales, sin transferencias internas; las comisiones sí cuentan como gasto |
| Contador | Antes «Importación»: varios extractos PDF o CSV a la vez, en cola, con duplicados, clasificación y cuadre con los totales del banco, más un **historial de auditoría** de cada archivo |
| Dividir gastos | Amigos por nombre de usuario, división en partes iguales y enlace de cobro para que cada amigo reembolse al pagador |
| IA (Gemini) | Lee PDF difíciles o escaneados, mejora la clasificación y el agente **Nexo** responde preguntas con herramientas |
| Reportes | PDF para el contador y CSV para Excel, con la parte real y la parte interna de cada movimiento |
| Compras por WhatsApp | Al registrar un gasto, Nexorix pregunta por WhatsApp **qué compraste**; respondes con una nota de voz y Gemini llena tu tabla personalizada |
| Bancos en vivo (demo) | Webhooks firmados de un agregador tipo Plaid / Prometeo (Nequi, Bancolombia, Davivienda…): clasifica gasto / transferencia propia / a tercero |
| Antifraude | Perfil de comportamiento, regla de **imposibilidad física** (bloqueo + WhatsApp + SMS) y detección de anomalías (solo en la app) |

## Páginas

`/dashboard.html` panel · `/traza.html` Trace · `/contador.html` contador (antes `/importar.html`, que redirige) · `/dividir.html` dividir gastos · `/cobro.html?t=…` cobro · `/reportes.html` reportes · `/compras.html` compras por WhatsApp · `/seguridad.html` bancos en vivo, avisos y simulador. El agente Nexo aparece como botón flotante en todas.

## API principal (nueva en esta versión)

| Ruta | Para qué |
|---|---|
| `GET /api/trace/suggestions` | Sugerencias (1→1, 1→N, N→1, comisión, puntaje, clasificación) |
| `POST /api/trace/groups` | Confirmar `{originIds, destinationIds}` (el servidor vuelve a validar todo) |
| `POST /api/trace/groups/{id}/undo` | Deshacer (queda en el historial) |
| `GET /api/trace/history` · `GET /api/trace/overview` | Historial y conteos |
| `POST /api/agent/chat` · `POST /api/agent/reset` | Agente Nexo |
| `GET /api/reports/resumen.pdf` · `/movimientos.csv` · `/preview` | Reportes, con `?desde=AAAA-MM-DD&hasta=AAAA-MM-DD` |
| `GET/PUT/DELETE /api/compras/whatsapp` · `PUT /api/compras/whatsapp/activo` | Número de WhatsApp (verificación por código) y pausa |
| `GET/POST /api/compras/columnas` · `PUT/DELETE /api/compras/columnas/{id}` | Columnas de la tabla personalizada |
| `GET /api/compras` · `PUT/DELETE /api/compras/{id}` · `GET /api/compras/compras.csv` | Filas de la tabla, edición a mano y CSV |
| `GET/POST /api/whatsapp/webhook` | Webhook de Meta (público, firmado con `X-Hub-Signature-256`) |
| `POST /api/bank/webhook` | Webhook del agregador bancario (público, firmado con `X-Bank-Signature`) |
| `GET/POST /api/bank/links` · `GET /api/bank/events` · `POST /api/bank/events/{id}/release` | Cuentas vinculadas, movimientos del banco y «fui yo» para liberar un bloqueo |
| `POST /api/bank/demo/{SEMILLA\|COMPRA\|COMPRA_INUSUAL\|TRANSFER_PROPIA\|TRANSFER_TERCERO\|VIAJE_IMPOSIBLE}` | Simulador de la demo |
| `GET/PUT /api/security/preferences` · `GET /api/security/profile` · `GET /api/security/notifications` · `POST /api/security/notifications/{id}/read` | Opt-in de WhatsApp, perfil de comportamiento y avisos dentro de la app |
| `GET /api/imports/audit` · `?archivo=ID` · `GET /api/imports/audit.csv` | Historial de auditoría del Contador (JSON y CSV) |
| `GET /api/friends` · `GET /api/friends/search?q=` · `POST /api/friends/requests` · `POST /api/friends/requests/{usuario}/accept` · `DELETE /api/friends/{usuario}` | Amigos |
| `GET/POST /api/split` · `DELETE /api/split/{id}` · `POST /api/split/shares/{id}/settle` | Cuentas compartidas y cobros |
| `GET /api/split/collect/{token}` · `POST /api/split/collect/{token}/paid` | Enlace de cobro y «ya pagué» |

Las rutas anteriores (`/api/trace/own-transfers`, `/confirm`, `/matches`, `/api/ai/ask`) siguen funcionando.

## Variables de entorno (IntelliJ → Run → Edit Configurations)

| Variable | Obligatoria | Uso |
|---|---|---|
| `NEXORIX_DB_PASSWORD` | Sí | Contraseña de PostgreSQL |
| `NEXORIX_DB_URL`, `NEXORIX_DB_USER` | No | URL JDBC (por defecto `jdbc:postgresql://localhost:5432/nexorix`) y usuario (`postgres`) |
| `BANK_WEBHOOK_SECRET` | Con bancos | Secreto HMAC del webhook bancario. Sin él se rechaza todo |
| `NEXORIX_BANK_DEMO` | No | `false` en producción: apaga el simulador `/api/bank/demo/*` |
| `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER` | No | SMS de alertas críticas. Sin ellos el SMS solo se escribe en el log (`[DEMO SMS]`) |
| `NEXORIX_FRAUD_MAX_SPEED_KMH`, `NEXORIX_FRAUD_MIN_DISTANCE_KM`, `NEXORIX_FRAUD_MIN_HISTORY` | No | Umbrales del antifraude (900 km/h, 150 km, 5 operaciones) |
| `DIDIT_API_KEY`, `DIDIT_WORKFLOW_ID`, `DIDIT_WEBHOOK_SECRET` | Sí | Didit |
| `NEXORIX_PUBLIC_URL` | No | Dirección pública (ngrok) |
| `GEMINI_API_KEY` | No | Activa la IA. Gratis en https://aistudio.google.com/apikey |
| `NEXORIX_AI_MODEL` / `NEXORIX_AI_FAST_MODEL` | No | Modelos de Gemini (por defecto `gemini-3.5-flash`) |
| `NEXORIX_AI_MAX_CONCURRENT` | No | Llamadas simultáneas a la IA (por defecto 4) |
| `NEXORIX_COOKIE_SECURE` | No | `true` en producción con HTTPS |
| `NEXORIX_IP_LIMITS` | No | `false` solo para pruebas de carga |
| `NEXORIX_MAIL_*`, `NEXORIX_RECOVERY_DOCUMENT_CHECK`, `NEXORIX_IMPORT_*` | No | Igual que antes |
| `WHATSAPP_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID` | No | Activan WhatsApp (Meta for Developers → WhatsApp → Configuración de la API) |
| `WHATSAPP_APP_SECRET`, `WHATSAPP_VERIFY_TOKEN` | Con WhatsApp | Firma de los webhooks (Configuración → Básica) y token que inventas para registrar el webhook |
| `WHATSAPP_TEMPLATE_NAME`, `WHATSAPP_TEMPLATE_LANGUAGE` | Recomendada | Plantilla aprobada para escribir primero (ver abajo). Idioma por defecto `es` |
| `WHATSAPP_BUSINESS_PHONE` | No | Número de WhatsApp de Nexorix, para mostrar el enlace `wa.me` con el código |

## Agente Nexo

Gemini con *function calling*. Herramientas de solo lectura sobre los datos de la persona: resumen financiero, cuentas, búsqueda de movimientos, gastos por categoría, resumen mensual, gastos recurrentes, sugerencias e historial de Trace. Las acciones (confirmar una transferencia, descargar un reporte) **solo se proponen como botones**: el agente nunca cambia datos. Límite: 30 mensajes por hora por persona, 6 pasos por mensaje, memoria de los últimos 8 turnos en la sesión.

## Contador: trazabilidad

Cada archivo que se sube deja una cadena de eventos en `import_audit_log`, con el nombre y la huella SHA-256 del archivo: `RECIBIDO` (formato, tamaño, cuenta) → `EN_COLA` → `PROCESANDO` → `LEIDO` (método reglas/IA, movimientos nuevos/repetidos/con problemas, si cuadra con el banco, segundos) → `CATEGORIAS_IA` → `CONFIRMADO` (cuántos movimientos y sus ids) o `CANCELADO` / `DESCARTADO` (vista previa borrada a las 24 h) / `ERROR` / `RECHAZADO` (repetido, formato no válido, cola llena).

- Es **solo de escritura** y sobrevive a la limpieza horaria de lotes (no depende de ellos).
- Cada evento se guarda en su propia transacción y `CONFIRMADO` solo después de que la confirmación se grabó: si se deshace, no queda registrado.
- Nunca guarda el contenido del archivo, la contraseña del PDF ni descripciones de movimientos.
- Cada persona solo ve el suyo (pestaña Contador o `GET /api/imports/audit.csv`).

## Dividir gastos

1. Los amigos se buscan por nombre de usuario (mínimo 3 letras, con límite de búsquedas; solo se muestra usuario y nombre). La amistad **requiere aceptación**: nadie puede cobrarle a quien no lo aceptó.
2. El pagador escribe el total y elige amigos. El total se divide en partes iguales entre el pagador y los amigos, calculado en centavos: los amigos pagan todos lo mismo y el pagador absorbe los centavos sobrantes (`amigos × parte + parte del pagador = total`, siempre).
3. Cada amigo recibe un cobro (en su pestaña «Yo debo» y con un enlace `/cobro.html?t=…` que el pagador copia o manda por WhatsApp). El enlace tiene un token aleatorio **y** exige sesión: solo lo abren el pagador y quien debe.
4. Estados: `PENDIENTE` → el amigo pulsa «Ya pagué» → `REPORTADO` → el pagador confirma «Ya me pagó» → `SALDADO`. Nexorix no mueve dinero: el reembolso se hace por fuera y aquí se lleva la cuenta.

## Compras por WhatsApp

1. La persona escribe su número en `/compras.html` y manda desde su WhatsApp el código `NEXORIX 123456` que ve en pantalla. Nexorix **solo escribe a números verificados**, así nadie puede poner el número de otro.
2. Cada gasto (egreso) registrado a mano dispara la pregunta *"Registraste un gasto de $ 25.000 (Éxito). ¿Qué compraste?"*. Las transferencias entre cuentas propias y los extractos importados no preguntan nada. Máximo 20 preguntas al día por persona.
3. La persona responde con una **nota de voz** (o con texto). Gemini la escucha, la transcribe y llena las columnas que ella eligió (por defecto: producto, cantidad, tienda, para quién, motivo; se pueden renombrar, borrar o agregar hasta 15). Nexorix le confirma por WhatsApp lo que guardó.
4. Si responde citando la pregunta, la respuesta va a esa compra; si no, a la pregunta más reciente sin responder (de los últimos 7 días).

**Configuración en Meta**

- Webhook: `{NEXORIX_PUBLIC_URL}/api/whatsapp/webhook`, token de verificación = `WHATSAPP_VERIFY_TOKEN`, suscrito al campo `messages`.
- Plantilla (categoría *Utility*), con dos variables: `¿En qué gastaste {{1}} en {{2}}? Respóndeme con una nota de voz 🎙️`. Pon su nombre en `WHATSAPP_TEMPLATE_NAME`. Sin plantilla, Nexorix manda texto libre, que WhatsApp solo entrega si la persona escribió en las últimas 24 horas (sirve para probar).
- Sin `GEMINI_API_KEY` las notas de voz no se pueden entender; las respuestas de texto se guardan tal cual en la primera columna.

## Bancos en vivo y antifraude (demo)

```
Banco/agregador ──webhook firmado──▶ BankWebhookController ──▶ BankSyncService
                                                                   │ 1. BankMovementClassifier  (gasto / interna / tercero / ingreso)
                                                                   │ 2. FraudEngine             (lee UserBehaviorLog)
                                                                   │      ├─ CRITICAL → bloquea + app + WhatsApp + SMS   (ignora el opt-in)
                                                                   │      └─ ANOMALY  → aplica + aviso SOLO dentro de la app
                                                                   │ 3. TransactionService.saveFromBank + aprende el comportamiento
                                                                   └ 4. si gasto/tercero y whatsapp_notifications_enabled → «¿En qué gastaste $X en Comercio?»
```

**Clasificación** (`banking/BankMovementClassifier`): tarjeta o datáfono (`CARD_PURCHASE`, `POS`, `ONLINE`) = **gasto**; `TRANSFER` hacia otra cuenta vinculada del mismo usuario o con su mismo documento = **transferencia interna** (nunca dispara el flujo de gasto); `TRANSFER` hacia otra persona = **transferencia a terceros**; dinero que entra = ingreso.

**Opt-in de WhatsApp**: `users.whatsapp_notifications_enabled` (por defecto `false`, se cambia en `/seguridad.html` o `PUT /api/security/preferences`). Con `false`, los gastos que llegan del banco no generan ningún WhatsApp. Con `true`, un gasto o una transferencia a terceros dispara de forma asíncrona (después del commit, otro hilo) *«¿En qué gastaste $ 48.900 en Éxito?»*; la respuesta (nota de voz o texto) llena tu tabla de compras y completa la descripción del movimiento (`Éxito - arroz y leche`). Las alertas críticas **ignoran** este flag. El flag controla el flujo de bancos; los gastos escritos a mano siguen preguntando si tienes un WhatsApp verificado.

**Perfil de comportamiento** (`user_behavior_log`, solo se agrega): cada compra o transferencia a tercero aceptada guarda comercio, categoría, destinatario, monto y lugar (coordenadas, o ciudad/país → `GeoLocator`). De ahí salen los comercios y categorías habituales, los destinatarios frecuentes y los lugares comunes (`GET /api/security/profile`). Los movimientos bloqueados no se aprenden hasta que la persona confirma «fui yo».

**Reglas**

| Regla | Condición | Acción |
|---|---|---|
| 🔴 Imposibilidad física | distancia ≥ 150 km respecto a la última operación con ubicación **y** velocidad > 900 km/h (ej. Bogotá 10:00 → Miami 11:00 ≈ 2.430 km en 1 h) | El movimiento **no se aplica** (sin transacción ni cambio de saldo, `bank_events.status = BLOCKED`), aviso crítico en la app + WhatsApp + SMS, aunque el opt-in esté apagado. «Fui yo» lo libera |
| 🟡 Comercio inusual | comercio nunca visto, con ≥ 5 operaciones de historial | Se aplica; aviso `WARNING` solo dentro de la app |
| 🟡 Destinatario nuevo | primera transferencia a ese destinatario | Se aplica; aviso `WARNING` solo dentro de la app |

Detalles: la distancia es haversine en Java (no exige extensiones de PostgreSQL); los eventos son idempotentes por `eventId`; si no hay claves de WhatsApp/Twilio, los mensajes críticos se escriben en el log como `[DEMO WHATSAPP]` / `[DEMO SMS]` y la campanita marca `WHATSAPP(demo),SMS(demo)`; si un canal falla, el otro sale igual. «Bloquear» en esta demo significa no registrar el movimiento (Nexorix no mueve dinero). La ubicación por IP es un punto de extensión: el agregador manda ciudad/país (o coordenadas) en `location`; para geolocalizar IPs reales conecta MaxMind/ipinfo en `GeoLocator`.

**Webhook** (`POST /api/bank/webhook`, cabecera `X-Bank-Signature: sha256=HMAC_SHA256(BANK_WEBHOOK_SECRET, cuerpo exacto)`):

```jsonc
{
  "eventId": "evt-0001",              // único: reintentos no duplican
  "bank": "NEQUI",
  "accountRef": "nequi-demo-001",     // external_ref de bank_links
  "direction": "DEBIT",               // DEBIT | CREDIT
  "amount": 52000, "currency": "COP",
  "channel": "CARD_PURCHASE",         // CARD_PURCHASE | POS | ONLINE | TRANSFER
  "merchant": "Éxito",
  "counterparty": { "name": "Carlos Pérez", "accountRef": "nequi-3001112233", "document": "1020304050" },
  "location": { "city": "Bogotá", "country": "CO", "latitude": null, "longitude": null, "ip": "190.24.10.5" },
  "occurredAt": "2026-10-08T10:00:00-05:00"
}
```

Probar el bloqueo sin la interfaz (necesitas el usuario `demo` del seed y `BANK_WEBHOOK_SECRET`):

```bash
send() { # uso: send '<json>'
  SIG=$(printf '%s' "$1" | openssl dgst -sha256 -hmac "$BANK_WEBHOOK_SECRET" | sed 's/^.* //')
  curl -s -X POST http://localhost:8080/api/bank/webhook -H 'Content-Type: application/json' -H "X-Bank-Signature: sha256=$SIG" -d "$1"; echo; }
send '{"eventId":"t1","accountRef":"nequi-demo-001","direction":"DEBIT","amount":52000,"channel":"CARD_PURCHASE","merchant":"Éxito","location":{"city":"Bogotá","country":"CO"},"occurredAt":"'$(date -u -d '-1 hour' +%FT%TZ)'"}'
send '{"eventId":"t2","accountRef":"nequi-demo-001","direction":"DEBIT","amount":3400000,"channel":"CARD_PURCHASE","merchant":"Best Buy","location":{"city":"Miami","country":"US"},"occurredAt":"'$(date -u +%FT%TZ)'"}'
# → {"status":"BLOCKED","risk":"CRITICAL","message":"Imposibilidad física: Bogotá, CO -> Miami, US (2430 km) en 1.0 h"}
```

## Guía de ejecución local

Nexorix es **un solo proyecto**: el backend (Spring Boot) también sirve el frontend (HTML/JS estático en `src/main/resources/static`). No hay un servidor de frontend aparte ni `npm install`.

**Requisitos:** JDK 21, PostgreSQL 14+ (probado en 16), `curl` y `openssl`. Maven no hace falta: el repo trae `mvnw`. Para recibir webhooks reales de Meta/Twilio desde internet, [ngrok](https://ngrok.com).

1. **Base de datos** (una vez):
   ```bash
   createdb -U postgres nexorix          # o:  docker run -d --name nexorix-db -e POSTGRES_PASSWORD=cambia-esta-clave -p 5432:5432 -e POSTGRES_DB=nexorix postgres:16
   ```
2. **Variables de entorno:** `cp .env.example .env`, edítalo (ver siguiente sección) y cárgalo: `set -a; source .env; set +a` (Windows/IntelliJ: ver el encabezado de `.env.example`). Mínimo: `NEXORIX_DB_PASSWORD`, `DIDIT_API_KEY`, `DIDIT_WORKFLOW_ID` (cualquier texto si usarás el usuario demo) y `BANK_WEBHOOK_SECRET`.
3. **Arrancar el backend (y con él el frontend):**
   ```bash
   ./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
   ```
   Escucha en **http://localhost:8080** (puerto por defecto de Spring; cámbialo con `SERVER_PORT=9090`). Al primer arranque Hibernate (`ddl-auto=update`) crea las tablas.
4. **Esquema y datos de prueba:** con la app ya arrancada una vez, ejecuta los scripts de la sección *PostgreSQL manual* (`db/01-…sql`, `db/02-…sql`).
5. **Entrar:** http://localhost:8080/login.html → usuario `demo`, contraseña `Demo1234!`, PIN `123456` → menú **Seguridad** (`/seguridad.html`) → «Cargar hábitos» → «Bogotá → Miami en 1 h».
6. **Pruebas:** `./mvnw test` (todas pasan salvo `NexorixApplicationTests`, que necesita la base de datos y las variables del paso 2).
7. **Webhooks reales (opcional):** `ngrok http 8080`, pon la URL en `NEXORIX_PUBLIC_URL` y úsala en Meta (`{URL}/api/whatsapp/webhook`) y en el agregador bancario (`{URL}/api/bank/webhook`).

## Claves y variables de entorno (`.env`)

Plantilla completa en [`.env.example`](.env.example). **`.env` está en `.gitignore`: nunca lo subas.** Sin las claves opcionales todo funciona en modo demo (los mensajes se escriben en el log).

| Variable | ¿Necesaria para la demo? | Qué es |
|---|---|---|
| `NEXORIX_DB_URL` / `NEXORIX_DB_USER` / `NEXORIX_DB_PASSWORD` | Sí | PostgreSQL: `jdbc:postgresql://localhost:5432/nexorix`, `postgres`, la clave que le pusiste |
| `DIDIT_API_KEY`, `DIDIT_WORKFLOW_ID` | Sí (valor de relleno con el seed) | Verificación de identidad real |
| `BANK_WEBHOOK_SECRET` | Sí | Firma del webhook bancario |
| `WHATSAPP_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_APP_SECRET`, `WHATSAPP_VERIFY_TOKEN` | No | WhatsApp real |
| `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER` | No | SMS real |
| `GEMINI_API_KEY` | No | Entender notas de voz y PDF |

**Cómo obtener cada una (gratis / sandbox).** Las pantallas de los proveedores cambian; si algo no coincide, busca el nombre del campo.

- **`BANK_WEBHOOK_SECRET`** — la inventas: `openssl rand -hex 32`. En un agregador real (Plaid, Prometeo…) pegas este mismo valor como secreto de firma del webhook; en la demo lo usas para firmar con `openssl` como en el ejemplo de arriba.
- **PostgreSQL** — local (`apt install postgresql`, Homebrew, instalador de Windows) o Docker (comando del paso 1). La contraseña es la que definas.
- **WhatsApp Cloud API (Meta)** — gratis en modo prueba:
  1. https://developers.facebook.com → *Mis apps* → *Crear app* → tipo **Empresa** (*Business*) → agrega el producto **WhatsApp**.
  2. *WhatsApp → Configuración de la API*: Meta te da un **número de prueba**, el **Phone number ID** (`WHATSAPP_PHONE_NUMBER_ID`) y un **token temporal de 24 h** (`WHATSAPP_TOKEN`; para uno permanente crea un *usuario del sistema* en Business Settings con el permiso `whatsapp_business_messaging`).
  3. En el mismo panel, en *Para* agrega tu celular como destinatario de prueba (máx. 5) y confirma el código que te llega por WhatsApp.
  4. *Configuración de la app → Básica → Clave secreta de la app* → `WHATSAPP_APP_SECRET`.
  5. *WhatsApp → Configuración → Webhook*: URL `{NEXORIX_PUBLIC_URL}/api/whatsapp/webhook` (ngrok), **token de verificación** = el texto que inventes en `WHATSAPP_VERIFY_TOKEN`, y suscríbete al campo `messages`.
  6. Con el número de prueba solo puedes escribir primero con plantillas; el mensaje `hello_world` viene creado. Para el texto libre de la demo, escribe tú primero al número de prueba (se abre la ventana de 24 h) o crea una plantilla *Utility* (ver «Compras por WhatsApp»).
- **SMS con Twilio** — cuenta de prueba gratuita con crédito:
  1. Regístrate en https://www.twilio.com/try-twilio y verifica tu correo y tu celular.
  2. En la *Console* (página principal) copia **Account SID** → `TWILIO_ACCOUNT_SID` y **Auth Token** → `TWILIO_AUTH_TOKEN`.
  3. *Phone Numbers → Manage → Buy a number* (con el crédito de prueba) o el número que te asigne el asistente → `TWILIO_FROM_NUMBER` en formato `+1…`.
  4. En cuenta de prueba solo puedes enviar a números verificados: *Phone Numbers → Verified Caller IDs → Add*. Habilita Colombia en *Messaging → Settings → Geo permissions*. Los SMS de prueba llevan un prefijo «Sent from your Twilio trial account».
- **Gemini (IA, opcional)** — https://aistudio.google.com/apikey (nivel gratuito).
- **Didit** — https://business.didit.me, modo *Sandbox*: *API key*, *Workflow ID* y *Webhook secret*. Con el usuario `demo` del seed no se usa.

## PostgreSQL: configuración manual

Los scripts están en [`db/`](db/) y son **idempotentes** (se pueden repetir). Orden:

1. **Tablas base:** arranca el backend una vez (`./mvnw spring-boot:run`). Hibernate crea `users`, `accounts`, `financial_transactions`, `whatsapp_links`… (y ya trae también las tablas nuevas). En producción cámbialo a `spring.jpa.hibernate.ddl-auto=validate` y gestiona el esquema solo con SQL.
2. **Esquema nuevo** — `psql -U postgres -d nexorix -f db/01-bank-fraud-schema.sql`:

   | Objeto | Para qué |
   |---|---|
   | `users.whatsapp_notifications_enabled boolean NOT NULL DEFAULT false` | Opt-in de WhatsApp (`false` = no se escribe por gastos comunes) |
   | `users.security_phone varchar(20)` | Celular de alertas críticas (WhatsApp + SMS). Si es nulo, se usa el WhatsApp verificado |
   | `bank_links` (`user_id`→`users`, `account_id`→`accounts` único, `external_ref` único) | Cuenta de Nexorix ↔ cuenta del banco |
   | `bank_events` (`event_id` único, FKs a `users` y `accounts`, `CHECK` en `kind`, `direction`, `status`, `amount > 0`) | Cada webhook: idempotencia, estado `APPLIED/BLOCKED/RELEASED`, riesgo y lugar |
   | `user_behavior_log` (FK a `users`, índices por usuario+fecha, +comercio, +destinatario) | `UserBehaviorLog`: comercios, categorías, destinatarios y ubicaciones habituales |
   | `app_notifications` (FK a `users`) | Campanita de la app y canales por los que salió cada alerta |

   **Extensiones:** ninguna es obligatoria. La distancia se calcula en Java (haversine). `cube` + `earthdistance` (contrib de PostgreSQL, vienen con el paquete `postgresql-contrib`; se activan con `CREATE EXTENSION cube; CREATE EXTENSION earthdistance;`) solo sirven si quieres consultar distancias desde SQL, por ejemplo `earth_distance(ll_to_earth(4.711,-74.0721), ll_to_earth(25.7617,-80.1918))/1000` ≈ 2.430 km. El seed usa `pgcrypto` (viene en `contrib`) solo para generar los hashes BCrypt.
3. **Datos de prueba** — `psql -U postgres -d nexorix -f db/02-demo-seed.sql`: usuario `demo` (`Demo1234!` / PIN `123456`, identidad ya `VERIFIED`, opt-in de WhatsApp `true`, celular `573001234567`), cuentas Nequi, Bancolombia y Davivienda con saldo, sus `bank_links` (`nequi-demo-001`, `bancolombia-demo-001`, `davivienda-demo-001`), un WhatsApp verificado y 7 filas de comportamiento en Bogotá (Éxito, Rappi, Starbucks, un destinatario). Cambia el celular `573001234567` por el tuyo (con indicativo, solo dígitos) si usas WhatsApp/SMS reales:
   ```sql
   UPDATE users SET security_phone = '57300XXXXXXX' WHERE username = 'demo';
   UPDATE whatsapp_links SET phone = '57300XXXXXXX' WHERE user_id = (SELECT id FROM users WHERE username = 'demo');
   ```
4. **Comprobar:** `SELECT bank, external_ref FROM bank_links;` debe listar 3 filas y `SELECT count(*) FROM user_behavior_log;` al menos 7. Para repetir la demo desde cero: `DELETE FROM app_notifications; DELETE FROM bank_events;` y borra de `financial_transactions` las filas con `source = 'BANK'` (los saldos de `accounts` no se revierten solos).

## Seguridad

- Sesión con cookie HttpOnly + SameSite=Lax, 15 minutos de inactividad, sin JWT a propósito (ver abajo).
- Solicitudes que cambian datos solo desde la misma página (Origin / Sec-Fetch-Site); el webhook de Didit está exento y va firmado.
- Límites por IP en inicio de sesión, PIN, registro y recuperación, además del bloqueo por usuario.
- Cabeceras: CSP, frame-deny, Referrer-Policy, Permissions-Policy, HSTS en HTTPS.
- Reportes con `Cache-Control: no-store`, documento enmascarado y CSV protegido contra inyección de fórmulas.

**¿Por qué no JWT?** Nexorix es una aplicación web del mismo dominio. Una cookie de sesión HttpOnly no la puede leer JavaScript, y se invalida al cerrar sesión. Un JWT guardado en el navegador queda expuesto si hay un XSS y no se puede revocar sin infraestructura extra. JWT tiene sentido cuando haya una app móvil nativa o una API para terceros.

## Pruebas

- Automáticas: Run 'All Tests'. (`NexorixApplicationTests` necesita la base de datos.)
- Carga: `pruebas-carga/carga-importacion.js` (k6), con `NEXORIX_IP_LIMITS=false`.

## Privacidad

Los extractos no se guardan (solo su huella SHA-256 y los movimientos confirmados). Las notas de voz tampoco: se descargan de Meta, se envían a Gemini para transcribirlas y solo queda el texto. Con la IA activa, los PDF difíciles y los datos que consulta el agente se envían a la API de Gemini. **En el nivel gratuito, Google puede usar ese contenido para mejorar sus productos**: para datos reales de usuarios usa el nivel pagado y descríbelo en la política de tratamiento de datos (Ley 1581 de 2012).
