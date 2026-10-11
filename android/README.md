# Nexorix Notificaciones (Android)

Lee las notificaciones de tus apps de banco y se las envía a tu cuenta de Nexorix. Una página web no puede leer
notificaciones de otras apps, por eso existe esta app.

1. En Nexorix (desde este celular): **Panel → Conectar → Permitir recibir notificaciones**. Se abre esta app y guarda
   la dirección y la llave sola (enlace `nexorix://conectar?url=…&token=…`, dirigido solo a esta app).
2. Activa «Nexorix Notificaciones» en *Acceso a notificaciones* (la app te lleva ahí).
3. Si no se abre sola, pega la dirección y la llave a mano y toca **Guardar**.

Solo se envían avisos que mencionan un banco conocido y traen un monto con `$`, junto con el nombre de la app
(«Nequi», «Bancolombia»…). Lo demás nunca sale del celular. La dirección debe ser `https://`.

Compilar: abrir la carpeta `android/` con Android Studio (JDK 17) y ejecutar `app`.
> Este módulo no se compiló en el entorno de desarrollo del servidor; pruébalo en un celular antes de publicar.
