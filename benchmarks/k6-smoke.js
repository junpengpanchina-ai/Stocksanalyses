import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
  vus: __ENV.VUS ? parseInt(__ENV.VUS) : 5,
  duration: __ENV.DURATION || '1m',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<800'],
  },
};

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const res1 = http.get(`${BASE}/actuator/health`);
  check(res1, { 'health 200': (r) => r.status === 200 });

  const res2 = http.get(`${BASE}/api/candles?symbol=TEST&interval=1d`);
  check(res2, { 'candles 200': (r) => r.status === 200 });

  const res3 = http.get(`${BASE}/api/signals?symbol=TEST`);
  check(res3, { 'signals 200': (r) => r.status === 200 });

  sleep(1);
}


