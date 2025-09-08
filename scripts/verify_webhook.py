#!/usr/bin/env python3
"""
Webhook signature verification example for K-line alerts.
Usage: python3 scripts/verify_webhook.py
Then POST to http://localhost:8080/api/alerts/subscriptions with webhookUrl=http://localhost:8000
"""

import hmac
import hashlib
import json
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

class WebhookHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        # Read body
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length).decode('utf-8')
        
        # Get signature header
        signature_header = self.headers.get('X-Kline-Signature', '')
        print(f"\n=== Webhook Received ===")
        print(f"Headers: {dict(self.headers)}")
        print(f"Body: {body}")
        
        # Verify signature (you need to know the secret used in subscription)
        secret = "my-secret"  # This should match webhookSecret in your subscription
        expected_sig = self._compute_signature(body, secret)
        
        if signature_header.startswith('sha256='):
            received_sig = signature_header[7:]  # Remove 'sha256=' prefix
            is_valid = hmac.compare_digest(expected_sig, received_sig)
            print(f"Expected signature: {expected_sig}")
            print(f"Received signature: {received_sig}")
            print(f"Signature valid: {is_valid}")
        else:
            print(f"No valid signature header found")
        
        # Parse and display alert data
        try:
            alert_data = json.loads(body)
            print(f"Alert ID: {alert_data.get('id')}")
            print(f"Symbol: {alert_data.get('symbol')}")
            print(f"Type: {alert_data.get('type')}")
            print(f"Strength: {alert_data.get('strength')}")
            print(f"Timestamp: {alert_data.get('ts')}")
        except json.JSONDecodeError:
            print("Invalid JSON body")
        
        # Respond with 200 OK
        self.send_response(200)
        self.send_header('Content-type', 'application/json')
        self.end_headers()
        self.wfile.write(b'{"status":"received"}')
    
    def _compute_signature(self, payload, secret):
        """Compute HMAC-SHA256 signature for payload with secret"""
        return hmac.new(
            secret.encode('utf-8'),
            payload.encode('utf-8'),
            hashlib.sha256
        ).hexdigest()
    
    def log_message(self, format, *args):
        # Suppress default logging
        pass

if __name__ == '__main__':
    server = HTTPServer(('localhost', 8000), WebhookHandler)
    print("Webhook receiver running on http://localhost:8000")
    print("Use this URL in your subscription: http://localhost:8000")
    print("Press Ctrl+C to stop")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down...")
        server.shutdown()
