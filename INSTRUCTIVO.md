# Instructivo: dejar Nexorix real y funcionando en celulares

## 0. Qué es real hoy y qué no

| Cómo entran los datos | Real | Costo | Estado |
|---|---|---|---|
| Notificaciones de tus apps de banco (app Android de `android/`) | Sí | Gratis | Listo |
| Extractos PDF/CSV en el Contador | Sí | Gratis | Listo |
| Escribir movimientos a mano | Sí | Gratis | Listo |
| Conexión directa al banco (saldos y movimientos solos) | Sí | **De pago** | No incluido |

Los agregadores bancarios que cubren Colombia ([Belvo](https://belvo.com/plans-and-pricing/), Prometeo) solo
regalan un *sandbox* con datos de mentira; para datos reales hay que contratar, pasar una reunión y una
certificación. No existe un plan gratis de producción que yo haya podido confirmar, por eso no se integró.
Cuando quieras pagar uno, dime cuál y se agrega.

## 1. Servidor con HTTPS (obligatorio para celulares)

El celular, la app Android y los webhooks de Didit y Meta exigen una dirección `https://` pública.

- **Pruebas**: `ngrok http 8080` o Cloudflare Tunnel. Copia la dirección `https://...` que te dan.
- **Producción**: cualquier hosting que ejecute Java 21 (Render, Fly.io, Railway, un VPS). Revisa sus precios
  vigentes: los planes gratis suelen dormir el servidor, lo que no sirve para recibir avisos.
- **PostgreSQL 18**: el de tu hosting, o uno gestionado (Neon, Supabase). Define
  `SPRING_DATASOURCE_URL` (`jdbc:postgresql://HOST:5432/BASE`), `SPRING_DATASOURCE_USERNAME` y `NEXORIX_DB_PASSWORD`.

Variables de este paso:

| Variable | Valor |
|---|---|
| `NEXORIX_PUBLIC_URL` | `https://tu-dominio` (sin `/` al final) |
| `NEXORIX_COOKIE_SECURE` | `true` |
| `NEXORIX_DB_PASSWORD` | contraseña de PostgreSQL |

## 2. Didit (verificación de identidad) — obligatorio

1. Crea cuenta en <https://business.didit.me> y un **workflow** de verificación (documento + rostro).
2. Copia la **API key** → `DIDIT_API_KEY` y el **Workflow ID** → `DIDIT_WORKFLOW_ID`.
3. En *Webhooks* pon la URL `https://tu-dominio/api/kyc/webhook` y copia el **secreto de firma** → `DIDIT_WEBHOOK_SECRET`.

## 3. Gemini (IA) — recomendado

1. <https://aistudio.google.com/apikey> → *Create API key* → `GEMINI_API_KEY`.
2. Activa lectura de PDF difíciles, categorías con IA y el agente Nexo. Sin la llave, esas funciones se apagan.

## 4. Correo (recuperar contraseña) — recomendado

Con Gmail: activa la verificación en dos pasos y crea una **contraseña de aplicación** (Cuenta de Google → Seguridad).

| Variable | Valor |
|---|---|
| `NEXORIX_MAIL_HOST` | `smtp.gmail.com` |
| `NEXORIX_MAIL_PORT` | `587` |
| `NEXORIX_MAIL_USERNAME` | tu correo |
| `NEXORIX_MAIL_PASSWORD` | la contraseña de aplicación |
| `NEXORIX_MAIL_FROM` | tu correo |

## 5. WhatsApp Cloud API (compras por voz) — opcional

1. <https://developers.facebook.com> → *Crear app* → tipo **Empresa** → agrega el producto **WhatsApp**.
2. En *Configuración de la API* copia el **token** → `WHATSAPP_TOKEN` y el **ID del número** → `WHATSAPP_PHONE_NUMBER_ID`.
3. *Configuración → Básica*: copia el **secreto de la app** → `WHATSAPP_APP_SECRET`.
4. Inventa un texto → `WHATSAPP_VERIFY_TOKEN`. En *WhatsApp → Configuración → Webhook* pon
   `https://tu-dominio/api/whatsapp/webhook`, ese mismo texto como token de verificación, y suscríbete a **messages**.
5. Crea una **plantilla** de mensaje (categoría utilidad), espera su aprobación y pon su nombre en
   `WHATSAPP_TEMPLATE_NAME` (y el idioma en `WHATSAPP_TEMPLATE_LANGUAGE`, por defecto `es`).
6. Opcional: `WHATSAPP_BUSINESS_PHONE` con el número, para mostrar el enlace `wa.me`.

> El token temporal de Meta vence en horas; para producción crea un **usuario del sistema** con token permanente.

## 6. App Android de notificaciones

1. Abre la carpeta `android/` con Android Studio (JDK 17) y *Build → Build APK*. Instala el APK en el celular
   (permite «instalar apps desconocidas»). Para publicarla en Play Store necesitas una cuenta de desarrollador (pago único).
2. En Nexorix: **Panel → Notificaciones del celular → Activar**. Copia la dirección y la llave (se muestra una sola vez).
3. En la app pega ambas y toca **Guardar**; luego **Dar acceso a las notificaciones** y activa Nexorix.
4. Haz una compra o recibe una transferencia pequeña y revisa que aparezca en el Panel.

Android puede cerrar la app para ahorrar batería: en *Ajustes → Apps → Nexorix Notificaciones → Batería* elige
**Sin restricciones**. iPhone no permite leer notificaciones de otras apps; allí usa el Contador o el registro manual.

## 7. Instalar Nexorix como app en el celular

- **Android (Chrome)**: abre `https://tu-dominio/login.html` → menú ⋮ → *Instalar app*.
- **iPhone (Safari)**: abre la dirección → botón Compartir → *Añadir a pantalla de inicio*.

## 8. Lista antes de publicar

- [ ] Todo lo de los pasos 1 y 2 configurado; la app arranca sin errores.
- [ ] Registro con una cédula real: Didit aprueba y llega el webhook.
- [ ] Una notificación real de **cada banco** que uses queda registrada (los textos de los bancos cambian; si alguno no se
      reconoce, dime el texto de ejemplo, sin datos personales, y se ajusta el lector).
- [ ] Probado en un Android y un iPhone reales.
- [ ] Copias de seguridad de PostgreSQL activadas.
- [ ] Política de privacidad y términos publicados: manejas datos financieros y de identidad (Ley 1581 de 2012, Colombia).
