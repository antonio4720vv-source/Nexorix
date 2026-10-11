#!/bin/sh
# Copia de seguridad de la base de datos. Programala con cron, por ejemplo cada noche:
#   0 3 * * * cd /home/ubuntu/Nexorix && ./deploy/backup.sh
# Guarda las copias fuera de la maquina (otro lugar) para no perderlas si el servidor falla.
set -e
mkdir -p backups
docker compose exec -T db pg_dump -U postgres nexorix | gzip > "backups/nexorix-$(date +%F).sql.gz"
# Conserva las ultimas 14.
ls -1t backups/nexorix-*.sql.gz | tail -n +15 | xargs -r rm --
