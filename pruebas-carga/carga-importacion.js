// ============================================================
// NEXORIX - Prueba de carga: muchas personas subiendo extractos
// ============================================================
// Cada usuario virtual:
//   1. Crea una cuenta de Nexorix (queda con la sesion iniciada)
//   2. Crea una cuenta bancaria
//   3. Sube 3 extractos CSV A LA VEZ
//   4. Espera a que se lean (cola en segundo plano)
//   5. Confirma la importacion y abre el panel
//
// Uso (PowerShell, con Nexorix corriendo):
//   winget install k6 --source winget
//   k6 run pruebas-carga\carga-importacion.js
//   k6 run -e BASE=https://tu-url.ngrok-free.dev -e USERS=100 pruebas-carga\carga-importacion.js
//
// ANTES: arranca Nexorix con la variable NEXORIX_IP_LIMITS=false. Si no, el limite
// de registros por IP (10 cada 30 min) frena la prueba, que crea muchas cuentas
// desde un mismo computador. Quitala al terminar.
//
// IMPORTANTE: crea usuarios de prueba en la base de datos. Usalo solo en
// tu computador o en un ambiente de pruebas, nunca en produccion.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { FormData } from 'https://jslib.k6.io/formdata/0.0.2/index.js';

const BASE = __ENV.BASE || 'http://localhost:8080';
const USERS = Number(__ENV.USERS || 50);

export const options = {
    scenarios: {
        subir_extractos: {
            executor: 'ramping-vus',
            stages: [
                { duration: '30s', target: Math.ceil(USERS / 2) },
                { duration: '1m', target: USERS },
                { duration: '1m', target: USERS },
                { duration: '20s', target: 0 },
            ],
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.02'],             // menos de 2% de errores
        'http_req_duration{tipo:subida}': ['p(95)<3000'],
        tiempo_hasta_listo: ['p(95)<60000'],        // archivo leido en menos de 60 s
    },
};

const readyTime = new Trend('tiempo_hasta_listo', true);
const imported = new Counter('movimientos_importados');

function csv(seed) {
    let rows = 'fecha;descripcion;valor\n';
    for (let i = 1; i <= 25; i++) {
        const day = String((i % 28) + 1).padStart(2, '0');
        const amount = (seed * 1000 + i * 137) % 900000 + 1000;
        rows += `2026-08-${day};${i % 3 === 0 ? 'Salario' : 'Compra Exito'} ${seed}-${i};${i % 3 === 0 ? '' : '-'}${amount}\n`;
    }
    return rows;
}

export default function () {
    const id = `${__VU}${__ITER}${Date.now() % 100000}`;
    const json = { headers: { 'Content-Type': 'application/json' } };

    // 1. Registro (deja la sesion iniciada)
    let res = http.post(`${BASE}/api/users`, JSON.stringify({
        name: `Carga ${id}`,
        username: `carga${id}`.slice(0, 30),
        email: `carga${id}@prueba.nexorix.co`,
        cedula: `9${id}`.slice(0, 15),
        password: 'Carga#2026Prueba',
    }), json);
    if (!check(res, { 'registro 200': r => r.status === 200 })) return;

    // 2. Cuenta bancaria
    res = http.post(`${BASE}/api/accounts?name=Cuenta%20${id}&type=AHORROS&bank=Nequi&balance=0`);
    if (!check(res, { 'cuenta 200': r => r.status === 200 })) return;
    const accountId = res.json('id');

    // 3. Tres extractos a la vez
    const form = new FormData();
    form.append('accountId', String(accountId));
    for (let f = 1; f <= 3; f++) {
        form.append('files', http.file(csv(Number(id) + f), `extracto-${f}.csv`, 'text/csv'));
    }
    const started = Date.now();
    res = http.post(`${BASE}/api/imports`, form.body(), {
        headers: { 'Content-Type': 'multipart/form-data; boundary=' + form.boundary },
        tags: { tipo: 'subida' },
    });
    if (!check(res, { 'subida 200': r => r.status === 200 })) return;
    const ids = res.json().filter(s => s.batchId).map(s => s.batchId);

    // 4. Esperar a que se lean
    let statuses = [];
    for (let i = 0; i < 60; i++) {
        sleep(1);
        statuses = http.get(`${BASE}/api/imports?ids=${ids.join(',')}`, { tags: { tipo: 'estado' } }).json();
        if (statuses.every(s => s.status !== 'QUEUED' && s.status !== 'PROCESSING')) break;
    }
    readyTime.add(Date.now() - started);
    check(statuses, { 'todos listos': s => s.every(x => x.status === 'PREVIEW') });

    // 5. Confirmar e ir al panel
    for (const s of statuses.filter(x => x.status === 'PREVIEW')) {
        const preview = http.get(`${BASE}/api/imports/${s.batchId}`).json();
        const rowIds = preview.rows.filter(r => r.selected).map(r => r.id);
        const ok = http.post(`${BASE}/api/imports/${s.batchId}/confirm`, JSON.stringify({ rowIds }), json);
        if (check(ok, { 'confirmar 200': r => r.status === 200 })) imported.add(ok.json('imported'));
    }
    check(http.get(`${BASE}/api/dashboard`), { 'panel 200': r => r.status === 200 });
    sleep(1);
}
