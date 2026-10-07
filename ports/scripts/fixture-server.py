"""Loopback-only deterministic fixtures for native iOS protocol tests; no paid services."""
import base64
import hashlib
import io
import json
import math
import struct
import time
import wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

audio = io.BytesIO()
with wave.open(audio, 'wb') as wav:
    wav.setnchannels(1)
    wav.setsampwidth(2)
    wav.setframerate(48000)
    wav.writeframes(b''.join(struct.pack('<h', int(math.sin(n / 48000 * math.tau * 440) * 1000)) for n in range(48000 * 4)))
PNG = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNoSDjwHwAFpAKgaAA9lwAAAABJRU5ErkJggg==')

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def reply(self, code, body=b'', mime='application/json', extra=None):
        if isinstance(body, dict):
            body = json.dumps(body).encode()
        self.send_response(code)
        self.send_header('Content-Type', mime)
        self.send_header('Content-Length', str(len(body)))
        for key, value in (extra or {}).items():
            self.send_header(key, value)
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_PROPFIND(self):
        expected = 'Basic ' + base64.b64encode(b'user:fixture-secret').decode()
        if self.headers.get('Authorization') != expected or self.headers.get('Depth') != '1':
            return self.reply(401)
        self.reply(207, b'<d:multistatus xmlns:d="DAV:"><d:response><d:href>/audio.wav</d:href></d:response></d:multistatus>', 'application/xml')

    def do_GET(self):
        parsed = urlparse(self.path)
        query = parse_qs(parsed.query)
        if parsed.path == '/health':
            return self.reply(200, {'ok': True})
        if parsed.path == '/cover.png':
            return self.reply(200, PNG, 'image/png')
        if parsed.path == '/redirect':
            return self.reply(302, extra={'Location': '/health'})
        if parsed.path == '/bad':
            return self.reply(401)
        if parsed.path == '/rest/download.view':
            salt = query.get('s', [''])[0]
            token = hashlib.md5(('fixture-secret' + salt).encode()).hexdigest()
            if query.get('u') != ['user'] or query.get('t') != [token] or query.get('id') != ['中文?#']:
                return self.reply(401)
            return self.reply(200, audio.getvalue(), 'audio/wav')
        if parsed.path == '/slow.wav':
            time.sleep(5)
            return self.reply(200, audio.getvalue(), 'audio/wav')
        self.reply(404)

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))))
        if self.path == '/Users/AuthenticateByName':
            if body != {'Username': 'user', 'Pw': 'fixture-secret'}:
                return self.reply(401)
            return self.reply(200, {'AccessToken': 'fixture-token', 'User': {'Id': 'fixture-user'}})
        valid = body.get('model') == 'fixture-model' and body.get('messages', [{}])[0].get('content') == 'fixture-prompt'
        if self.path == '/chat/completions' and self.headers.get('Authorization') == 'Bearer fixture-key' and valid:
            return self.reply(200, {'choices': [{'message': {'content': 'Fixture recommendation'}}]})
        if self.path == '/messages' and self.headers.get('x-api-key') == 'fixture-key' and self.headers.get('anthropic-version') == '2023-06-01' and valid:
            return self.reply(200, {'content': [{'type': 'text', 'text': 'Fixture explanation'}]})
        self.reply(401)

if __name__ == '__main__':
    ThreadingHTTPServer(('127.0.0.1', 8765), Handler).serve_forever()
