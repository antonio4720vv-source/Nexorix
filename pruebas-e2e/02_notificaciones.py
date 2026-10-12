"""Avisos del celular: banco por nombre de app, historial, duplicados, antifraude por distancia, claves y cobros."""
import sys, time
from lib import *

c, user, pw, uid = new_user("Ana Prueba")
now_ms = int(time.time() * 1000)
H = 3600 * 1000


def connect():
    s, b = c.call("POST", "/api/notifications/connection")
    assert s == 200, (s, b)
    return b["token"]


def ingest(token, app, title, text, at=None, pkg="com.app"):
    s, b = Client().call("POST", "/api/notifications/ingest",
                         {"packageName": pkg, "appName": app, "title": title, "text": text, "postedAt": at or int(time.time() * 1000)},
                         headers={"Authorization": f"Bearer {token}"} if token else {})
    return s, (b or {}).get("result") if isinstance(b, dict) else b


def accounts():
    s, b = c.call("GET", "/api/accounts")
    return {a["bank"]: a for a in b}


print("Llave del celular")
s, b = c.call("GET", "/api/notifications/connection")
check("al inicio no hay celular conectado", s == 200 and b["connected"] is False, (s, b))
token = connect()
check("la llave empieza por nxr_", token.startswith("nxr_"))
check("en la base solo queda la huella", sql(f"select count(*) from notification_tokens where token_hash='{token}'") == "0")
check("sin llave = 401", ingest(None, "Nequi", "Nequi", "Recibiste $5.000 de Ana")[0] == 401)
check("llave falsa = 401", ingest("nxr_falsa", "Nequi", "Nequi", "Recibiste $5.000 de Ana")[0] == 401)

print("Identifica el banco por el nombre de la app")
s, r = ingest(token, "Nequi", "Nequi", "Recibiste $50.000 de Juan Perez Gomez", at=now_ms - 5 * H)
check("Nequi: ingreso registrado", (s, r) == (200, "REGISTERED"), (s, r))
a = accounts()
check("crea la cuenta Nequi sola (billetera)", "Nequi" in a and a["Nequi"]["type"] == "BILLETERA", a)
s, r = ingest(token, "Bancolombia", "Aviso", "Compra por $25.500 en EXITO CHAPINERO BOGOTA con tarjeta *1234. Saldo disponible: $1.200.000", at=now_ms - 3 * H)
check("Bancolombia: compra registrada", (s, r) == (200, "REGISTERED"), (s, r))
a = accounts()
check("crea la cuenta Bancolombia", "Bancolombia" in a, list(a))
check("el saldo del aviso queda como saldo real", float(a["Bancolombia"]["balance"]) == 1200000.0, a.get("Bancolombia"))
s, r = ingest(token, "Daviplata", "Daviplata", "Enviaste $20.000 a Maria Gomez", at=now_ms - 4 * H)
check("Daviplata: envio registrado", (s, r) == (200, "REGISTERED"), (s, r))
check("cuentas por banco: Nequi, Bancolombia y Daviplata", {"Nequi", "Bancolombia", "Daviplata"} <= set(accounts()))
s, r = ingest(token, "Davivienda", "Davivienda", "Transferencia recibida por $80.000 de Carlos Ruiz", at=now_ms - 2 * H)
check("Davivienda: ingreso registrado", (s, r) == (200, "REGISTERED"), (s, r))

print("Duplicados")
s, r = ingest(token, "Nequi", "Nequi", "Recibiste $50.000 de Juan Perez Gomez", at=now_ms - 5 * H)
check("el mismo aviso otra vez (misma hora) = DUPLICATE", r == "DUPLICATE", (s, r))
s, r = ingest(token, "Nequi", "Nequi", "Recibiste $50.000 de Juan Perez Gomez", at=now_ms - 5 * H + 20000)
check("el banco lo repite 20 s despues = DUPLICATE", r == "DUPLICATE", (s, r))
check("solo hay UN movimiento de esos $50.000", sql(f"select count(*) from bank_events where user_id={uid} and amount=50000 and bank='NEQUI'") == "1")
s, r = ingest(token, "Nequi", "Nequi", "Recibiste $50.001 de Juan Perez Gomez", at=now_ms - 5 * H + 30000)
check("otro monto NO es duplicado", r == "REGISTERED", (s, r))

print("Antifraude por distancia (compras con tarjeta)")
s, r = ingest(token, "Bancolombia", "Aviso", "Compra por $60.000 en TIENDA MEDELLIN con tarjeta *1234", at=now_ms - 2 * H)
check("Bogota -> Medellin en 1 h es normal (se registra)", r == "REGISTERED", (s, r))
s, r = ingest(token, "Bancolombia", "Aviso", "Compra por $3.400.000 en BEST BUY MIAMI con tarjeta *1234", at=now_ms - 1 * H)
check("Medellin -> Miami en 1 h: se registra el aviso (queda bloqueado)", r == "REGISTERED", (s, r))
bl = sql(f"select status || '|' || risk_level from bank_events where user_id={uid} and amount=3400000")
check("el movimiento queda BLOQUEADO / CRITICAL", bl == "BLOCKED|CRITICAL", bl)
check("no entra a las transacciones ni cambia el saldo", sql(f"select count(*) from financial_transactions t join accounts a on a.id=t.account_id where a.user_id={uid} and t.amount=3400000") == "0")
check("genera una alerta critica en la app", sql(f"select count(*) from app_notifications where user_id={uid} and severity='CRITICAL'") == "1")
ev = sql(f"select id from bank_events where user_id={uid} and amount=3400000")
s, b = c.call("POST", f"/api/bank/events/{ev}/release")
check("'Fui yo' libera el movimiento", s == 200 and b["status"] == "RELEASED", (s, b))
check("ahora si queda como transaccion", sql(f"select count(*) from financial_transactions t join accounts a on a.id=t.account_id where a.user_id={uid} and t.amount=3400000") == "1")

print("Historial de TODOS los avisos")
s, r = ingest(token, "Nequi", "Nequi", "Tu código de seguridad es 482910. No lo compartas")
check("una clave: se ignora", r == "IGNORED", (s, r))
s, r = ingest(token, "Bancolombia", "Bancolombia", "Actualiza tu app para ver las novedades")
check("aviso del banco sin plata: se ignora", r == "IGNORED", (s, r))
s, r = ingest(token, "Davivienda", "Davivienda", "Operación por $120.000 en proceso")
check("parece movimiento pero no se entiende: UNRECOGNIZED", r == "UNRECOGNIZED", (s, r))
s, r = ingest(token, "WhatsApp", "Mamá", "Hola, hoy salgo de $viaje", pkg="com.whatsapp")
check("un chat no se guarda", r == "IGNORED", (s, r))
s, log = c.call("GET", "/api/notifications/log")
check("el historial responde", s == 200 and isinstance(log, list) and len(log) >= 10, (s, len(log) if isinstance(log, list) else log))
results = {x["result"] for x in log}
check("trae registrados, duplicados, bloqueados, informativos, claves y no entendidos",
      {"REGISTERED", "DUPLICATE", "BLOCKED", "INFO", "SENSITIVE", "UNRECOGNIZED"} <= results, results)
banks = {x["bank"] for x in log if x["bank"]}
check("cada aviso dice de que banco es", {"Nequi", "Bancolombia", "Daviplata", "Davivienda"} <= banks, banks)
check("la clave NO guarda texto", all(not x["text"] for x in log if x["result"] == "SENSITIVE"))
check("lo no entendido guarda el texto para revisarlo", any(x["text"] and "$120.000" in x["text"] for x in log if x["result"] == "UNRECOGNIZED"))
check("el chat no aparece", not any(x["app"] == "WhatsApp" for x in log))
check("el texto no entendido va cifrado en la base", sql("select count(*) from notification_logs where snippet like 'enc:v1:%'") != "0")

print("Cobros de Dividir gastos: se saldan por el nombre")
cj, juser, _, juid = new_user("Juan Perez", "juan")
cm, muser, _, muid = new_user("Maria Lopez", "maria")
s, b = c.call("POST", "/api/friends/requests", {"username": juser})
check("Ana invita a Juan", s == 200, (s, b))
cj.call("POST", f"/api/friends/requests/{user}/accept")
s, b = c.call("POST", "/api/friends/requests", {"username": muser})
cm.call("POST", f"/api/friends/requests/{user}/accept")
s, b = c.call("POST", "/api/split", {"title": "Cena", "total": 90000, "participants": [juser, muser]})
check("crea la cuenta compartida (cada uno $30.000)", s == 200, (s, b))
def shares_by_name():
    st, ov = c.call("GET", "/api/split")
    return {sh["name"]: sh["status"] for e in ov["owedToMe"] for sh in e["shares"]}


check("antes del pago las dos partes estan pendientes", set(shares_by_name().values()) == {"PENDIENTE"}, shares_by_name())
s, r = ingest(token, "Nequi", "Nequi", "Recibiste $30.000 de Camila Torres", at=int(time.time() * 1000) - 600000)
check("un pago de $30.000 de OTRO nombre no salda nada", set(shares_by_name().values()) == {"PENDIENTE"}, shares_by_name())
s, r = ingest(token, "Nequi", "Nequi", "Recibiste $30.000 de Juan Perez Gomez")
check("llega un pago de $30.000 de 'Juan Perez Gomez'", r == "REGISTERED", (s, r))
st = shares_by_name()
check("saldo la parte de Juan (por su nombre)", st.get("Juan Perez") == "SALDADO", st)
check("la parte de Maria sigue pendiente", st.get("Maria Lopez") == "PENDIENTE", st)
s, notes = c.call("GET", "/api/bank/events")
check("avisa en la app 'Te pagaron una division'", sql(f"select count(*) from app_notifications where user_id={uid} and title like 'Te pagaron%'") == "1")

sys.exit(summary())
