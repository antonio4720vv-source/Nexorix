# Nexorix 24/7 gratis (Oracle Cloud Always Free + Docker)

Todo el despliegue ya está preparado en el repo (`Dockerfile`, `docker-compose.yml`, `deploy/`). Tú solo creas el servidor,
el nombre de internet y llenas el archivo `.env`. Después de esto ya no necesitas IntelliJ ni ngrok.

> Lo que sigue lo escribí sin poder ejecutarlo en un servidor real: si un paso falla, copia el mensaje de error y se corrige.
> Los detalles de Oracle (tamaños, disponibilidad) cambian: revisa su página «Always Free» antes de empezar.

## 1. Servidor gratis en Oracle Cloud
1. Crea tu cuenta en <https://www.oracle.com/cloud/free/>. Pide una tarjeta solo para verificar la identidad.
2. **Compute → Instances → Create instance**:
   - Imagen: **Ubuntu 22.04 o 24.04**.
   - Forma (*Shape*): **VM.Standard.A1.Flex** (Ampere ARM, dentro del «Always Free»). Con 2 CPU y 12 GB sobra.
     Si dice «Out of capacity», prueba otro dominio de disponibilidad o vuelve a intentar más tarde.
   - *Add SSH keys*: genera o sube tu llave y **descarga la privada**.
   - Deja marcada **Assign a public IPv4 address**.
3. Cuando esté lista, copia su **IP pública**.
4. **Abre los puertos 80 y 443**: en la instancia → *Subnet* → *Security List* → *Add Ingress Rules*:
   origen `0.0.0.0/0`, protocolo TCP, puertos `80` y `443` (una regla por puerto).
5. Oracle también bloquea dentro de la máquina. Conéctate (`ssh -i TU_LLAVE ubuntu@IP`) y ejecuta:
   ```bash
   sudo iptables -I INPUT -p tcp --dport 80 -j ACCEPT
   sudo iptables -I INPUT -p tcp --dport 443 -j ACCEPT
   sudo netfilter-persistent save
   ```

## 2. Nombre de internet gratis
1. Entra a <https://www.duckdns.org> (con Google o GitHub), crea un subdominio (por ejemplo `minexorix`) y pon la **IP pública** del paso 1.
2. Tu dirección será `minexorix.duckdns.org`. (Si más adelante compras un dominio, apuntas un registro A a la misma IP y cambias `DOMINIO`.)

## 3. Instalar y arrancar
En el servidor:
```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER && exit        # vuelve a entrar por ssh
git clone -b claude/sharp-davinci-the2sr https://github.com/antonio4720vv-source/Nexorix.git
cd Nexorix
cp .env.example .env
nano .env                                    # rellena (ver abajo)
docker compose up -d --build                 # la primera vez tarda varios minutos
docker compose logs -f app                   # espera "Started NexorixApplication"
docker compose exec -T db psql -U postgres -d nexorix < db/01-bank-fraud-schema.sql
```
En `.env` rellena como mínimo: `DOMINIO`, `NEXORIX_DB_PASSWORD` (larga y aleatoria), `NEXORIX_DATA_KEY` (`openssl rand -base64 32`;
**guárdala también fuera del servidor**), `DIDIT_*`, `GEMINI_API_KEY`, `NEXORIX_MAIL_*` y `WHATSAPP_*` (ver `INSTRUCTIVO.md`).
`NEXORIX_PUBLIC_URL` y `NEXORIX_COOKIE_SECURE` los pone solo `docker-compose.yml`.

Abre `https://TU-DOMINIO/login.html`: Caddy consigue el certificado HTTPS solo (el dominio tiene que apuntar ya a la IP).

## 4. Cambia esto una sola vez
- Webhook de Didit → `https://TU-DOMINIO/api/kyc/webhook`.
- Webhook de Meta → `https://TU-DOMINIO/api/whatsapp/webhook` (con tu `WHATSAPP_VERIFY_TOKEN`).
- App Android: en el panel, **Conectar → Permitir recibir notificaciones** otra vez para que tome la dirección nueva.

## 5. Mantenimiento
- **Actualizar**: `cd Nexorix && git pull && docker compose up -d --build`.
- **Copias de seguridad**: `./deploy/backup.sh` guarda la base en `backups/`. Prográmala (`crontab -e`):
  `0 3 * * * cd /home/ubuntu/Nexorix && ./deploy/backup.sh`, y copia esa carpeta a otro lugar (tu PC, un bucket): si Oracle
  reclama o falla la máquina, solo lo guardado fuera del servidor se salva.
- **Ver errores**: `docker compose logs --tail 200 app`.
- **Reiniciar**: `docker compose restart app`.
- Los contenedores tienen `restart: always`: si el servidor se reinicia, todo vuelve a levantar solo.

## Cosas a saber
- **Oracle puede reclamar** máquinas «Always Free» que casi no se usan o por cambios de sus condiciones. Por eso las copias fuera del servidor.
- **El repo es público**: nunca subas el archivo `.env` (ya está en `.gitignore`). Si quieres, hazlo privado en GitHub; para clonarlo
  en el servidor tendrás que usar un token de acceso.
- **Alternativa**: Render tiene plan gratis, pero duerme el servidor tras ~15 min sin visitas y su base gratis caduca; no sirve
  para recibir avisos todo el día.
