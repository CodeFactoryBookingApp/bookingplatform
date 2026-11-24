"""Simulador minimo de Supabase Auth (GoTrue) para las pruebas de API de bookingplatform.

Copia extendida del stub de QA: ademas de lo original, atiende GET/PUT /admin/users/{id} y
GET /admin/users (los usa el cambio de rol de HU-022), guarda app_metadata por usuario y
emite el claim amr con la hora de autenticacion (RoleTokenSessionPolicy lo exige tras un cambio).
La clave RSA se genera en memoria en cada arranque (no se escribe en disco).

Publica JWKS (RS256), firma JWT como Supabase y simula /admin/users, /token,
/logout, /resend, /recover y /verify. Endpoints de control bajo /_ctl:
  POST /_ctl/behavior  {...}  reemplaza el comportamiento (p. ej. {"logout_status":429})
  POST /_ctl/mint      {claims...} -> {"token": "..."}  (claims extra/omitidos con null)
  POST /_ctl/confirm   {"email":...} -> {"token_hash": ...}
  GET  /_ctl/log       peticiones recibidas;  POST /_ctl/reset_log
Uso: python gotrue_stub.py <puerto> <issuer_base_url>
(requiere el paquete cryptography)
"""
import base64, json, sys, threading, time, uuid
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from cryptography.hazmat.primitives.asymmetric import rsa, padding
from cryptography.hazmat.primitives import hashes

PORT = int(sys.argv[1])
BASE = sys.argv[2].rstrip('/')
ISSUER = BASE + '/auth/v1'
KID = 'reval-key-1'
KEY = rsa.generate_private_key(public_exponent=65537, key_size=2048)
PUB = KEY.public_key().public_numbers()
GOOD_PASSWORD = 'ClaveSegura2026!'

def b64u(b):
    return base64.urlsafe_b64encode(b).rstrip(b'=').decode()

def int_b64(n):
    return b64u(n.to_bytes((n.bit_length() + 7) // 8, 'big'))

def sign(claims):
    header = {'alg': 'RS256', 'typ': 'JWT', 'kid': KID}
    si = b64u(json.dumps(header).encode()) + '.' + b64u(json.dumps(claims).encode())
    sig = KEY.sign(si.encode(), padding.PKCS1v15(), hashes.SHA256())
    return si + '.' + b64u(sig)

LOCK = threading.Lock()
USERS = {}        # email -> {id, password, confirmed, role}
HASHES = {}       # token_hash -> email
BEHAVIOR = {}
LOG = []

def meta(user):
    m = {'provider': 'email', 'role': user.get('role', 'CLIENT')}
    m.update(user.get('app_metadata') or {})
    return m

def base_claims(user):
    now = int(time.time())
    return {'iss': ISSUER, 'sub': user['id'], 'aud': 'authenticated', 'exp': now + 900, 'iat': now,
            'email': user['email'], 'role': 'authenticated', 'app_metadata': meta(user),
            'amr': [{'method': 'password', 'timestamp': now}]}

def admin_view(u):
    return {'id': u['id'], 'email': u['email'], 'app_metadata': meta(u),
            'confirmed_at': '2026-01-01T00:00:00Z' if u.get('confirmed') else None,
            'banned_until': None, 'deleted_at': None}

def user_for(email, create_confirmed=True):
    u = USERS.get(email)
    if u is None and create_confirmed:
        u = {'id': str(uuid.uuid5(uuid.NAMESPACE_DNS, email)), 'email': email, 'password': GOOD_PASSWORD,
             'confirmed': True, 'role': 'CLIENT'}
        USERS[email] = u
    return u

class H(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'
    def log_message(self, *a):
        pass

    def _body(self):
        if 'chunked' in (self.headers.get('Transfer-Encoding') or '').lower():
            out = b''
            while True:
                size = int(self.rfile.readline().strip().split(b';')[0], 16)
                if size == 0:
                    self.rfile.readline()
                    return out
                out += self.rfile.read(size)
                self.rfile.readline()
        n = int(self.headers.get('Content-Length') or 0)
        return self.rfile.read(n) if n else b''

    def _send(self, status, obj=None, raw=None):
        data = raw if raw is not None else (b'' if obj is None else json.dumps(obj).encode())
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        if data:
            self.wfile.write(data)

    def _record(self, status, body):
        txt = body.decode('utf-8', 'replace')
        with LOCK:
            LOG.append('%s %s %s -> %s | bytes del cuerpo: %d | %s' % (
                datetime.now().strftime('%H:%M:%S.%f')[:-3], self.command, self.path, status, len(body), txt[:300]))

    def _override(self, name):
        st = BEHAVIOR.get(name + '_status')
        if st:
            return st, BEHAVIOR.get(name + '_body', {'code': st, 'msg': 'stub forced %s' % st})
        return None

    def do_GET(self):
        p = self.path.split('?')[0]
        if p == '/auth/v1/.well-known/jwks.json':
            return self._send(200, {'keys': [{'kty': 'RSA', 'kid': KID, 'use': 'sig', 'alg': 'RS256',
                                              'n': int_b64(PUB.n), 'e': int_b64(PUB.e)}]})
        if p.startswith('/auth/v1/admin/users/'):
            uid = p.rsplit('/', 1)[1]
            with LOCK:
                u = next((x for x in USERS.values() if x['id'] == uid), None)
            if u is None:
                return self._send(404, {'code': 404, 'error_code': 'user_not_found', 'msg': 'User not found'})
            return self._send(200, admin_view(u))
        if p == '/auth/v1/admin/users':
            from urllib.parse import urlparse, parse_qs
            q = parse_qs(urlparse(self.path).query)
            page, per = int(q.get('page', ['1'])[0]), int(q.get('per_page', ['50'])[0])
            with LOCK:
                users = [admin_view(x) for x in USERS.values()]
            return self._send(200, {'users': users[(page - 1) * per:page * per]})
        if p == '/_ctl/log':
            with LOCK:
                return self._send(200, raw=('\n'.join(LOG) + '\n').encode())
        self._send(404, {'msg': 'not found'})

    def do_PUT(self):
        body = self._body()
        p = self.path.split('?')[0]
        if p.startswith('/auth/v1/admin/users/'):
            uid = p.rsplit('/', 1)[1]
            j = json.loads(body or b'{}')
            with LOCK:
                u = next((x for x in USERS.values() if x['id'] == uid), None)
                if u is None:
                    self._record(404, body)
                    return self._send(404, {'code': 404, 'error_code': 'user_not_found', 'msg': 'User not found'})
                md = dict(u.get('app_metadata') or {})
                md.update(j.get('app_metadata') or {})
                u['app_metadata'] = md
                if md.get('role'):
                    u['role'] = md['role']
            self._record(200, body)
            return self._send(200, admin_view(u))
        self._send(404, {'msg': 'not found'})

    def do_DELETE(self):
        body = self._body()
        p = self.path.split('?')[0]
        if p.startswith('/auth/v1/admin/users/'):
            uid = p.rsplit('/', 1)[1]
            with LOCK:
                for e, u in list(USERS.items()):
                    if u['id'] == uid:
                        del USERS[e]
            self._record(200, body)
            return self._send(200, {})
        self._send(404, {'msg': 'not found'})

    def do_POST(self):
        body = self._body()
        p = self.path.split('?')[0]
        try:
            j = json.loads(body or b'{}')
        except Exception:
            j = {}
        if p.startswith('/_ctl/'):
            return self._ctl(p, j)
        if BEHAVIOR.get('require_apikey') and self.headers.get('apikey') in (None, ''):
            self._record(401, body)
            return self._send(401, {'message': 'No API key found in request'})
        if p == '/auth/v1/admin/users':
            ov = self._override('admin')
            if ov:
                self._record(ov[0], body); return self._send(ov[0], ov[1])
            d = BEHAVIOR.get('admin_delay')
            if d:
                time.sleep(d)
            email = j.get('email')
            with LOCK:
                if email in USERS and not BEHAVIOR.get('allow_duplicates', True):
                    self._record(422, body)
                    return self._send(422, {'code': 422, 'error_code': 'email_exists',
                                            'msg': 'A user with this email address has already been registered'})
                uid = BEHAVIOR.get('admin_id') or str(uuid.uuid4())
                USERS[email] = {'id': uid, 'email': email, 'password': j.get('password'), 'confirmed': False,
                                'role': (j.get('app_metadata') or {}).get('role', 'CLIENT')}
            self._record(200, body)
            return self._send(200, {'id': uid, 'email': email, 'aud': 'authenticated', 'role': 'authenticated'})
        if p == '/auth/v1/token':
            ov = self._override('token')
            if ov:
                self._record(ov[0], body); return self._send(ov[0], ov[1])
            email, pw = j.get('email'), j.get('password')
            with LOCK:
                u = user_for(email, create_confirmed=(pw == GOOD_PASSWORD))
            if u is None or u['password'] != pw:
                self._record(400, body)
                return self._send(400, {'code': 400, 'error_code': 'invalid_credentials', 'msg': 'Invalid login credentials'})
            if not u['confirmed']:
                self._record(400, body)
                return self._send(400, {'code': 400, 'error_code': 'email_not_confirmed', 'msg': 'Email not confirmed'})
            self._record(200, body)
            return self._send(200, {'access_token': sign(base_claims(u)), 'refresh_token': uuid.uuid4().hex,
                                    'token_type': 'bearer', 'expires_in': BEHAVIOR.get('expires_in', 900),
                                    'user': {'id': u['id'], 'email': u['email']}})
        if p == '/auth/v1/logout':
            ov = self._override('logout')
            self._record(ov[0] if ov else 204, body)
            if ov:
                return self._send(ov[0], raw=b'')
            return self._send(204)
        if p in ('/auth/v1/resend', '/auth/v1/recover'):
            ov = self._override(p.rsplit('/', 1)[1])
            if ov:
                self._record(ov[0], body); return self._send(ov[0], ov[1])
            self._record(200, body)
            return self._send(200, {})
        if p == '/auth/v1/verify':
            ov = self._override('verify')
            if ov:
                self._record(ov[0], body); return self._send(ov[0], ov[1])
            th = j.get('token_hash')
            with LOCK:
                email = HASHES.pop(th, None)
                u = USERS.get(email) if email else None
            if u is None:
                self._record(403, body)
                return self._send(403, {'code': 403, 'error_code': 'otp_expired',
                                        'msg': 'Email link is invalid or has expired'})
            u['confirmed'] = True
            if j.get('password'):
                u['password'] = j['password']
            self._record(200, body)
            return self._send(200, {'access_token': sign(base_claims(u)), 'token_type': 'bearer', 'expires_in': 900,
                                    'user': {'id': u['id'], 'email': u['email']}})
        self._record(404, body)
        self._send(404, {'msg': 'not found'})

    def _ctl(self, p, j):
        global BEHAVIOR
        if p == '/_ctl/behavior':
            BEHAVIOR = j
            return self._send(200, BEHAVIOR)
        if p == '/_ctl/reset_log':
            with LOCK:
                LOG.clear()
            return self._send(200, {})
        if p == '/_ctl/mint':
            email = j.pop('_email', 'qa.token@example.com')
            with LOCK:
                u = user_for(email)
            claims = base_claims(u)
            for k, v in j.items():
                if v is None:
                    claims.pop(k, None)
                else:
                    claims[k] = v
            return self._send(200, {'token': sign(claims), 'claims': claims})
        if p == '/_ctl/confirm':
            th = uuid.uuid4().hex
            with LOCK:
                HASHES[th] = j['email']
            return self._send(200, {'token_hash': th})
        if p == '/_ctl/user':
            with LOCK:
                USERS[j['email']] = {'id': j.get('id') or str(uuid.uuid4()), 'email': j['email'],
                                     'password': j.get('password', GOOD_PASSWORD),
                                     'confirmed': j.get('confirmed', True), 'role': j.get('role', 'CLIENT')}
                return self._send(200, USERS[j['email']])
        self._send(404, {})

ThreadingHTTPServer(('127.0.0.1', PORT), H).serve_forever()
