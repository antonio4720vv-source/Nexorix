"""Registro -> identidad (webhook firmado de Didit) -> PIN -> login -> recuperar contrasena."""
import sys, time, uuid
from lib import *

suffix = uuid.uuid4().hex[:6]
user, email, cedula = f"ana{suffix}", f"ana{suffix}@correo.com", str(1000000000 + int(suffix, 16) % 999999999)
pw, pw2 = "Clave#Segura1", "Otra#Clave22"

print("Registro")
c = Client()
s, b = c.call("POST", "/api/users", {"name": "Ana Prueba", "username": user, "email": email, "cedula": cedula, "password": pw})
check("crea la cuenta", s == 200 and b and b.get("username") == user, (s, b))
s, b = Client().call("POST", "/api/users", {"name": "Ana Prueba", "username": user, "email": "otro@correo.com", "cedula": "1", "password": pw})
check("rechaza usuario repetido", s == 400, (s, b))
s, b = Client().call("POST", "/api/users", {"name": "X", "username": "otro" + suffix, "email": email, "cedula": "2", "password": pw})
check("rechaza correo repetido", s == 400, (s, b))
s, b = Client().call("POST", "/api/users", {"name": "X", "username": "otro" + suffix, "email": "z@z.com", "cedula": cedula, "password": pw})
check("rechaza cedula repetida", s == 400, (s, b))
s, b = Client().call("POST", "/api/users", {"name": "X", "username": "debil" + suffix, "email": "d@z.com", "cedula": "3", "password": "abc"})
check("rechaza contrasena debil", s == 400, (s, b))
check("cedula y correo se guardan cifrados", sql(f"select email like 'enc:v1:%' and cedula like 'enc:v1:%' from users where username='{user}'") == "t")

print("Identidad (Didit)")
uid = sql(f"select id from users where username='{user}'")
sql(f"insert into kyc_verifications(user_id,document_type,document_number,status,provider,verification_reference,started_at) values({uid},'CC','{cedula}','IN_PROGRESS','DIDIT','sess-{suffix}',now())")
ev = {"event_id": f"ev-{suffix}-1", "webhook_type": "status.updated", "session_id": f"sess-{suffix}", "status": "Approved"}
s, b = Client().call("POST", "/api/kyc/webhook", {"x": 1}, headers={"X-Signature": "00", "X-Timestamp": str(int(time.time()))})
check("rechaza webhook con firma falsa", s == 401, (s, b))
s, b = didit_webhook(ev)
check("acepta webhook firmado", s == 200, (s, b))
s, b = didit_webhook(ev)
check("webhook repetido no se procesa dos veces", s == 200 and "DUPLICATE" in str(b).upper(), (s, b))
check("usuario queda VERIFIED", sql(f"select kyc_status from users where username='{user}'") == "VERIFIED")

print("Login y PIN")
c = Client()
s, b = c.call("POST", "/api/auth/login", {"username": user, "password": "mala"})
check("contrasena incorrecta = 401", s == 401, (s, b))
s, b = c.call("POST", "/api/auth/login", {"username": user, "password": pw})
check("login pide crear PIN", s == 200 and b.get("next") == "CREATE_PIN", (s, b))
s, b = c.call("POST", "/api/auth/pin/create", {"pin": "123456", "pinConfirm": "123456"})
check("rechaza PIN facil", s == 400, (s, b))
s, b = c.call("POST", "/api/auth/pin/create", {"pin": "483920", "pinConfirm": "483920"})
check("crea PIN y entra", s == 200 and b.get("next") == "DONE", (s, b))
s, b = c.call("GET", "/api/users/me")
check("sesion activa", s == 200 and b.get("username") == user, (s, b))
c2 = Client()
c2.call("POST", "/api/auth/login", {"username": user, "password": pw})
s, b = c2.call("POST", "/api/auth/pin", {"pin": "000111"})
check("PIN incorrecto = 401", s == 401, (s, b))
s, b = c2.call("POST", "/api/auth/pin", {"pin": "483920"})
check("PIN correcto entra", s == 200 and b.get("next") == "DONE", (s, b))

print("Recuperar contrasena")
r = Client()
s, b = r.call("POST", "/api/recovery/start", {"identifier": "noexiste" + suffix})
s2, b2 = Client().call("POST", "/api/recovery/start", {"identifier": user})
check("misma respuesta exista o no la cuenta", s == 200 and s2 == 200 and b == b2, (b, b2))
time.sleep(1)
s, b = r.call("POST", "/api/recovery/start", {"identifier": email})
code = None
for _ in range(10):
    code = last_recovery_code()
    if code:
        break
    time.sleep(0.5)
check("llega un codigo de 6 digitos (log, sin SMTP)", bool(code))
s, b = r.call("POST", "/api/recovery/code", {"code": "000000" if code != "000000" else "111111"})
check("codigo incorrecto = error", s in (400, 401, 403, 422), (s, b))
s, b = r.call("POST", "/api/recovery/code", {"code": code})
check("codigo correcto avanza", s == 200, (s, b))
s, b = r.call("POST", "/api/recovery/reset", {"newPassword": pw2, "newPasswordConfirm": pw2})
check("no deja cambiar sin verificar identidad", s >= 400, (s, b))
# Didit no se llama desde aqui: se deja la solicitud como si ya hubiera empezado la verificacion.
sql(f"update recovery_requests set status='IDENTITY_PENDING', didit_session_id='rec-{suffix}' where id=(select max(id) from recovery_requests where user_id={uid})")
bad = {"event_id": f"ev-{suffix}-2", "webhook_type": "status.updated", "session_id": f"rec-{suffix}", "status": "Approved",
       "decision": {"id_verifications": [{"document_number": "99999"}]}}
didit_webhook(bad)
check("documento ajeno NO aprueba la recuperacion", sql(f"select status from recovery_requests where didit_session_id='rec-{suffix}'") == "IDENTITY_DECLINED")
sql(f"update recovery_requests set status='IDENTITY_PENDING' where didit_session_id='rec-{suffix}'")
good = {"event_id": f"ev-{suffix}-3", "webhook_type": "status.updated", "session_id": f"rec-{suffix}", "status": "Approved",
        "decision": {"id_verifications": [{"document_number": cedula}]}}
didit_webhook(good)
check("su propio documento SI aprueba", sql(f"select status from recovery_requests where didit_session_id='rec-{suffix}'") == "IDENTITY_APPROVED")
s, b = r.call("POST", "/api/recovery/reset", {"newPassword": pw, "newPasswordConfirm": pw})
check("no permite la misma contrasena anterior", s >= 400, (s, b))
s, b = r.call("POST", "/api/recovery/reset", {"newPassword": pw2, "newPasswordConfirm": pw2})
check("cambia la contrasena", s == 200, (s, b))
s, b = Client().call("POST", "/api/auth/login", {"username": user, "password": pw})
check("la contrasena vieja ya no sirve", s == 401, (s, b))
s, b = Client().call("POST", "/api/auth/login", {"username": user, "password": pw2})
check("la nueva si sirve", s == 200, (s, b))

sys.exit(summary())
