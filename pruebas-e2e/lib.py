"""Utilidades para probar Nexorix de punta a punta contra una app real (PostgreSQL incluido).

Variables: BASE (http://localhost:8080), PG (comando psql), WEBHOOK_SECRET (DIDIT_WEBHOOK_SECRET), APP_LOG (ruta del log).
"""
import hashlib, hmac, http.cookiejar, json, os, re, subprocess, time, urllib.error, urllib.request

BASE = os.environ.get("BASE", "http://localhost:8080")
WEBHOOK_SECRET = os.environ.get("DIDIT_WEBHOOK_SECRET", "whsec_test")
APP_LOG = os.environ.get("APP_LOG", "app.log")
PSQL = os.environ.get("PSQL", "psql -h localhost -U postgres -t -A nexorix -c")

passed, failed = 0, []


def check(name, condition, detail=""):
    global passed
    if condition:
        passed += 1
        print(f"  OK   {name}")
    else:
        failed.append(name)
        print(f"  FALLA {name} {detail}")


def summary():
    print(f"\n{passed} correctas, {len(failed)} con fallas")
    for f in failed:
        print("  -", f)
    return 0 if not failed else 1


class Client:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def call(self, method, path, body=None, headers=None, raw=None):
        data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
        req = urllib.request.Request(BASE + path, data=data, method=method)
        if data is not None:
            req.add_header("Content-Type", "application/json")
        for k, v in (headers or {}).items():
            req.add_header(k, v)
        try:
            with self.opener.open(req, timeout=30) as r:
                status, text = r.status, r.read().decode()
        except urllib.error.HTTPError as e:
            status, text = e.code, e.read().decode()
        try:
            return status, json.loads(text) if text else None
        except ValueError:
            return status, text


def sql(query):
    out = subprocess.run(PSQL.split() + [query], capture_output=True, text=True)
    return out.stdout.strip()


def didit_webhook(payload):
    raw = json.dumps(payload).encode()
    ts = str(int(time.time()))
    sig = hmac.new(WEBHOOK_SECRET.encode(), raw, hashlib.sha256).hexdigest()
    return Client().call("POST", "/api/kyc/webhook", raw=raw, headers={"X-Signature": sig, "X-Timestamp": ts})


def last_recovery_code():
    text = open(APP_LOG, errors="ignore").read()
    codes = re.findall(r"CODIGO DE RECUPERACION \(solo desarrollo\) para [^:]+: (\d{6})", text)
    return codes[-1] if codes else None


def new_user(name="Ana Prueba", prefix="ana"):
    """Crea una persona verificada con PIN y sesion iniciada. Devuelve (cliente, usuario, contrasena, id)."""
    import uuid
    suffix = uuid.uuid4().hex[:6]
    user, pw = f"{prefix}{suffix}", "Clave#Segura1"
    cedula = str(2000000000 + int(suffix, 16))
    c = Client()
    s, b = c.call("POST", "/api/users", {"name": name, "username": user, "email": f"{user}@correo.com", "cedula": cedula, "password": pw})
    assert s == 200, (s, b)
    uid = sql(f"select id from users where username='{user}'")
    sql(f"insert into kyc_verifications(user_id,document_type,document_number,status,provider,verification_reference,started_at) values({uid},'CC','{cedula}','IN_PROGRESS','DIDIT','s-{suffix}',now())")
    didit_webhook({"event_id": f"e-{suffix}", "webhook_type": "status.updated", "session_id": f"s-{suffix}", "status": "Approved"})
    c = Client()
    c.call("POST", "/api/auth/login", {"username": user, "password": pw})
    s, b = c.call("POST", "/api/auth/pin/create", {"pin": "483920", "pinConfirm": "483920"})
    assert s == 200, (s, b)
    return c, user, pw, uid
