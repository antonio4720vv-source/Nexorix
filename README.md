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

## Páginas

`/dashboard.html` panel · `/traza.html` Trace · `/contador.html` contador (antes `/importar.html`, que redirige) · `/dividir.html` dividir gastos · `/cobro.html?t=…` cobro · `/reportes.html` reportes · `/compras.html` compras por WhatsApp. El agente Nexo aparece como botón flotante en todas.

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
| `GET /api/imports/audit` · `?archivo=ID` · `GET /api/imports/audit.csv` | Historial de auditoría del Contador (JSON y CSV) |
| `GET /api/friends` · `GET /api/friends/search?q=` · `POST /api/friends/requests` · `POST /api/friends/requests/{usuario}/accept` · `DELETE /api/friends/{usuario}` | Amigos |
| `GET/POST /api/split` · `DELETE /api/split/{id}` · `POST /api/split/shares/{id}/settle` | Cuentas compartidas y cobros |
| `GET /api/split/collect/{token}` · `POST /api/split/collect/{token}/paid` | Enlace de cobro y «ya pagué» |

Las rutas anteriores (`/api/trace/own-transfers`, `/confirm`, `/matches`, `/api/ai/ask`) siguen funcionando.

## Variables de entorno (IntelliJ → Run → Edit Configurations)

| Variable | Obligatoria | Uso |
|---|---|---|
| `NEXORIX_DB_PASSWORD` | Sí | Contraseña de PostgreSQL |
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
- Plantilla (categoría *Utility*), con dos variables: `Registraste un gasto de {{1}} ({{2}}). ¿Qué compraste? Respóndeme con una nota de voz 🎙️`. Pon su nombre en `WHATSAPP_TEMPLATE_NAME`. Sin plantilla, Nexorix manda texto libre, que WhatsApp solo entrega si la persona escribió en las últimas 24 horas (sirve para probar).
- Sin `GEMINI_API_KEY` las notas de voz no se pueden entender; las respuestas de texto se guardan tal cual en la primera columna.

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
