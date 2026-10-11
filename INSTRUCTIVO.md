# Instructivo: Nexorix real, instalable en celular y computador

## 0. Cómo entran los datos (todo es real, no hay demos)

| Vía | Costo | Qué hace |
|---|---|---|
| Notificaciones de tus apps de banco (app Android de `android/`) | Gratis | Identifica el banco por el nombre de la app, crea la cuenta y registra cada ingreso, gasto o transferencia, con antifraude y pregunta por WhatsApp |
| Extractos PDF/CSV en el Contador | Gratis | Importa meses completos (la cuenta se crea ahí mismo eligiendo el banco) |
| Conexión directa al banco (agregador: Belvo, Prometeo, Plaid) | **De pago** | El webhook `/api/bank/webhook` ya está listo; falta contratar y poner `BANK_WEBHOOK_SECRET` |

Ya no se escriben cuentas, saldos ni movimientos a mano: todo viene de avisos o extractos reales. Una cuenta nueva empieza en cero
y se corrige sola cuando un aviso del banco trae el «saldo disponible». El botón **Conectar** muestra «conexión directa en
mantenimiento» y ofrece el permiso de notificaciones.

## 1. Variables de entorno (IntelliJ → Run → Edit Configurations → Environment variables)

Plantilla completa en `.env.example`. **Nunca pegues llaves en chats ni las subas a git.**

| Variable | Obligatoria | Valor |
|---|---|---|
| `NEXORIX_DB_URL`, `NEXORIX_DB_USER`, `NEXORIX_DB_PASSWORD` | Sí | PostgreSQL. Usa una contraseña larga y aleatoria |
| `NEXORIX_PUBLIC_URL` | Sí | `https://tu-dominio` (sin `/` al final) |
| `NEXORIX_COOKIE_SECURE` | Sí en producción | `true` |
| `NEXORIX_DATA_KEY` | **Sí en producción** | `openssl rand -base64 32`. Cifra cédula, correo y celular. **Guárdala aparte; si se pierde, esos datos no se recuperan.** Con `NEXORIX_COOKIE_SECURE=true` la app **no arranca** sin ella |
| `DIDIT_API_KEY`, `DIDIT_WORKFLOW_ID`, `DIDIT_WEBHOOK_SECRET` | Sí | Paso 3 |
| `GEMINI_API_KEY`, `NEXORIX_AI_MODEL`, `NEXORIX_AI_FAST_MODEL` | Recomendada | Paso 4 (pon el mismo modelo en las dos) |
| `NEXORIX_MAIL_HOST/PORT/USERNAME/PASSWORD/FROM` | Recomendada | Paso 5 |
| `WHATSAPP_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_APP_SECRET`, `WHATSAPP_VERIFY_TOKEN`, `WHATSAPP_BUSINESS_PHONE`, `WHATSAPP_TEMPLATE_NAME`, `WHATSAPP_ALERT_TEMPLATE_NAME` | Para WhatsApp | Paso 6 |
| `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER` | Opcional | SMS de alertas críticas |
| `BANK_WEBHOOK_SECRET` | Solo con agregador | `openssl rand -hex 32` |

Después de cambiar variables, detén y vuelve a correr la app.

## 2. Servidor con HTTPS (obligatorio para celular, Didit y Meta)

- **Pruebas**: ngrok (`ngrok http 8080`). El dominio gratis cambia o se cae si cierras ngrok; cada cambio obliga a actualizar
  `NEXORIX_PUBLIC_URL` y los webhooks.
- **Producción**: un dominio fijo con un hosting que ejecute Java 21 + PostgreSQL. Si hay un proxy delante, comprueba que
  la IP del cliente llegue bien (`X-Forwarded-For`); si no, la defensa por IP vería a todos como una sola persona.
- Haz copias de seguridad automáticas de PostgreSQL.

## 3. Didit (identidad)

1. <https://business.didit.me> → crea un *workflow* (documento + rostro) → copia **API key** y **Workflow ID**.
2. *Webhooks*: URL `https://tu-dominio/api/kyc/webhook` → copia el **secreto de firma**.

## 4. Gemini

<https://aistudio.google.com/apikey> → crear llave. Activa la lectura de PDF difíciles, las categorías con IA, las notas de voz de WhatsApp y el agente Nexo.

## 5. Correo (recuperar contraseña)

Gmail: verificación en dos pasos → «Contraseñas de aplicaciones». `NEXORIX_MAIL_HOST=smtp.gmail.com`, `PORT=587`,
`USERNAME` y `FROM` = tu correo, `PASSWORD` = la contraseña de aplicación.

## 6. WhatsApp (Meta Cloud API)

1. <https://developers.facebook.com> → app tipo **Empresa** → producto **WhatsApp**.
2. *Configuración de la API*: **ID del número** → `WHATSAPP_PHONE_NUMBER_ID`; **token** → `WHATSAPP_TOKEN`.
   El token temporal dura horas: para producción crea un **usuario del sistema** (Administrador comercial) con permiso
   `whatsapp_business_messaging` y genera un token permanente.
3. *Configuración → Básica*: **secreto de la app** → `WHATSAPP_APP_SECRET`.
4. Inventa un texto largo → `WHATSAPP_VERIFY_TOKEN`. En *WhatsApp → Configuración → Webhook*: URL
   `https://tu-dominio/api/whatsapp/webhook`, ese texto como token de verificación, y suscríbete a **messages**.
5. **Plantillas** (WhatsApp solo deja escribir primero con una plantilla aprobada; categoría **Utilidad**, idioma español):
   - **Compra** → nombre en `WHATSAPP_TEMPLATE_NAME`. Texto exacto, con **dos variables**:
     `🛒 Nexorix: ¿En qué gastaste {{1}} en {{2}}? Respóndeme con una nota de voz 🎙️` (ejemplos: `$ 48.900` y `Éxito`).
   - **Alerta** → nombre en `WHATSAPP_ALERT_TEMPLATE_NAME`. **Una** variable, que no puede ir al inicio ni al final:
     `🚨 Nexorix – Alerta de seguridad: {{1}} Abre la app para revisarlo.` (ejemplo: `Bloqueamos $ 3.400.000 en Best Buy (Miami, US).`)
   - Si el número de variables no coincide, Meta rechaza el envío. Espera la aprobación antes de probar.
6. **Número**: el de prueba de Meta (+1 555…) solo escribe a máximo 5 destinatarios que registres. Para el público necesitas tu
   propio número verificado en tu cuenta de WhatsApp Business → `WHATSAPP_BUSINESS_PHONE` (solo dígitos, con indicativo).
7. **Cómo se conecta una persona**: en Compras escribe su número, recibe un código y lo envía por WhatsApp al número del negocio.
   Desde ese momento cada compra le pregunta *«¿en qué gastaste…?»* y responde con una nota de voz.
8. **Probar**: agrega una cuenta, registra un gasto (o recibe un aviso del banco) y revisa que llegue el mensaje y que la
   respuesta llene la tabla en Compras. Si no llega: revisa que la plantilla esté **aprobada**, los nombres de las variables de
   entorno, y los registros del servidor (`WhatsApp respondio HTTP…` dice el motivo).

## 7. Instalar Nexorix como app (celular y computador)

Nexorix es una **PWA**: se instala desde el navegador, sin tienda, con su icono, en su propia ventana y con un aviso sin conexión.
La dirección tiene que ser `https://` y fija.

| Dispositivo | Cómo |
|---|---|
| Android (Chrome) | Abre la dirección → franja «Instalar Nexorix» (o menú ⋮ → *Instalar app*) |
| Computador (Chrome o Edge) | Icono de instalar en la barra de direcciones, o *Ajustes → Instalar la app* dentro de Nexorix |
| iPhone / iPad (Safari) | Compartir → *Añadir a pantalla de inicio* (iOS no deja instalar con un botón) |

Para que cualquiera la encuentre en tiendas:
- **Google Play y Microsoft Store**: <https://www.pwabuilder.com> empaqueta la PWA (Android: Trusted Web Activity). Te entrega un
  archivo `assetlinks.json`; ponlo en `src/main/resources/static/.well-known/assetlinks.json`. Play exige cuenta de desarrollador (pago único).
- **App Store**: requiere un envoltorio (por ejemplo Capacitor), Mac y cuenta Apple Developer (pago anual). No incluido.

## 8. App Android de notificaciones

1. Abre `android/` en Android Studio (JDK 17) → *Build → Build APK*. Para que cualquiera la descargue, copia el APK a
   `src/main/resources/static/nexorix-notificaciones.apk`: el botón «Descargar la app de Nexorix para Android» aparece solo si ese archivo existe.
   (Para Google Play necesitas una cuenta de desarrollador, pago único.)
2. En Nexorix, desde el **celular Android**: **Panel → Conectar → Permitir recibir notificaciones**. Nexorix abre la app, que
   guarda la dirección y la llave sola y te lleva a *Acceso a notificaciones*: activa «Nexorix Notificaciones». Si no se abre,
   usa «Conéctala a mano» (misma pantalla) y pega la dirección y la llave en la app.
3. Ajustes → Apps → Nexorix Notificaciones → Batería: **Sin restricciones** (si no, Android la cierra).
4. Android da el permiso para **todas** las apps, pero la app solo envía avisos que mencionan un banco conocido y traen un monto.
   Junto con el aviso envía el **nombre de la app** («Nequi», «Bancolombia»…), y con él el servidor sabe de qué banco es.
5. iPhone no permite leer notificaciones de otras apps.

## 9. Lista antes de publicar

- [ ] Llaves rotadas si alguna se compartió; todas solo en variables de entorno.
- [ ] `NEXORIX_COOKIE_SECURE=true` y `NEXORIX_DATA_KEY` guardada en un lugar seguro y aparte.
- [ ] Registro con una cédula real: Didit aprueba y llega el webhook.
- [ ] Plantillas de WhatsApp **aprobadas** y probadas con un gasto real.
- [ ] Una notificación real de **cada banco** que uses queda registrada. Los textos de los bancos cambian: si alguno no se reconoce,
      pasa el texto de ejemplo (sin datos personales) y se ajusta el lector.
- [ ] Probado en un Android, un iPhone y un computador.
- [ ] Copias de seguridad de PostgreSQL.
- [ ] Política de privacidad y términos publicados (manejas datos financieros y de identidad; Ley 1581 de 2012, Colombia).
