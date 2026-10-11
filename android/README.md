# Nexorix Notificaciones (Android)

Lee las notificaciones de tus apps de banco y se las envía a tu cuenta de Nexorix.
Una página web no puede leer notificaciones de otras apps, por eso existe esta app.

1. En Nexorix: Panel → **Notificaciones del celular** → Activar. Copia la dirección y la llave.
2. Abre esta app, pégalas y toca **Guardar**.
3. Toca **Dar acceso a las notificaciones** y activa «Nexorix Notificaciones».

Solo se envían avisos que mencionan un banco conocido y traen un monto con `$`.
La dirección debe ser `https://` (con `NEXORIX_PUBLIC_URL` / ngrok en pruebas).

Compilar: abrir la carpeta `android/` con Android Studio (JDK 17) y ejecutar `app`.
> Este módulo no se compiló en el entorno de desarrollo del servidor; pruébalo en un celular antes de publicar.
