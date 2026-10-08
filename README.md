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
| Importación | Varios extractos PDF o CSV a la vez, en cola, con duplicados, clasificación y cuadre con los totales del banco |
| IA (Gemini) | Lee PDF difíciles o escaneados, mejora la clasificación y el agente **Nexo** responde preguntas con herramientas |
| Reportes | PDF para el contador y CSV para Excel, con la parte real y la parte interna de cada movimiento |

## Páginas

`/dashboard.html` panel · `/traza.html` Trace · `/importar.html` importar · `/reportes.html` reportes. El agente Nexo aparece como botón flotante en todas.

## API principal (nueva en esta versión)

| Ruta | Para qué |
|---|---|
| `GET /api/trace/suggestions` | Sugerencias (1→1, 1→N, N→1, comisión, puntaje, clasificación) |
| `POST /api/trace/groups` | Confirmar `{originIds, destinationIds}` (el servidor vuelve a validar todo) |
| `POST /api/trace/groups/{id}/undo` | Deshacer (queda en el historial) |
| `GET /api/trace/history` · `GET /api/trace/overview` | Historial y conteos |
| `POST /api/agent/chat` · `POST /api/agent/reset` | Agente Nexo |
| `GET /api/reports/resumen.pdf` · `/movimientos.csv` · `/preview` | Reportes, con `?desde=AAAA-MM-DD&hasta=AAAA-MM-DD` |

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

## Agente Nexo

Gemini con *function calling*. Herramientas de solo lectura sobre los datos de la persona: resumen financiero, cuentas, búsqueda de movimientos, gastos por categoría, resumen mensual, gastos recurrentes, sugerencias e historial de Trace. Las acciones (confirmar una transferencia, descargar un reporte) **solo se proponen como botones**: el agente nunca cambia datos. Límite: 30 mensajes por hora por persona, 6 pasos por mensaje, memoria de los últimos 8 turnos en la sesión.

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

Los extractos no se guardan (solo su huella SHA-256 y los movimientos confirmados). Con la IA activa, los PDF difíciles y los datos que consulta el agente se envían a la API de Gemini. **En el nivel gratuito, Google puede usar ese contenido para mejorar sus productos**: para datos reales de usuarios usa el nivel pagado y descríbelo en la política de tratamiento de datos (Ley 1581 de 2012).
