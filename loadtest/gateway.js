// k6 load test for the LLM Gateway's /v1/messages endpoint.
//
// This is a runnable, documented artifact — NOT a CI gate (load tests are slow
// and flaky in CI, so `mvnw verify` never runs it). It drives the gateway with a
// mix of UNIQUE prompts (cache misses -> a provider call + usage accounting) and
// REPEATED prompts (cache hits, served without a provider call), so a single run
// exercises the proxy, the response cache, and cost accounting together.
//
// Point it at a gateway running in demo mode (WireMock/stub provider, $0) so it
// costs nothing to run. Usage:
//
//   k6 run loadtest/gateway.js
//   k6 run -e BASE_URL=https://gateway.example.com -e API_KEY=sk-gw-... \
//          -e VUS=25 -e DURATION=1m -e HIT_RATIO=0.5 loadtest/gateway.js
//
// Environment variables (all optional):
//   BASE_URL   gateway base URL              (default http://localhost:8080)
//   API_KEY    client x-api-key              (default demo-key)
//   MODEL      model id (must be priced)     (default claude-3-5-sonnet-20241022)
//   VUS        concurrent virtual users      (default 10)
//   DURATION   test duration                 (default 30s)
//   HIT_RATIO  fraction of repeated prompts  (default 0.5)

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY || 'demo-key';
const MODEL = __ENV.MODEL || 'claude-3-5-sonnet-20241022';
const HIT_RATIO = Number(__ENV.HIT_RATIO || '0.5');

// A small fixed pool of prompts that repeat across iterations to produce cache
// hits; everything else is unique and misses.
const REPEATED_PROMPTS = [
  'What is the capital of France?',
  'Summarize the theory of relativity in one sentence.',
  'Write a haiku about databases.',
  'Explain rate limiting to a five year old.',
];

const cacheMissRequests = new Counter('gateway_unique_prompts');
const cacheHitRequests = new Counter('gateway_repeated_prompts');

export const options = {
  vus: Number(__ENV.VUS || '10'),
  duration: __ENV.DURATION || '30s',
  thresholds: {
    // Informational targets for a demo-mode run; tune for your environment.
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<750'],
  },
};

function prompt() {
  if (Math.random() < HIT_RATIO) {
    cacheHitRequests.add(1);
    return REPEATED_PROMPTS[Math.floor(Math.random() * REPEATED_PROMPTS.length)];
  }
  cacheMissRequests.add(1);
  return `unique-${uuidv4()}`;
}

export default function () {
  const body = JSON.stringify({
    model: MODEL,
    messages: [{ role: 'user', content: prompt() }],
    max_tokens: 64,
  });

  const res = http.post(`${BASE_URL}/v1/messages`, body, {
    headers: { 'Content-Type': 'application/json', 'x-api-key': API_KEY },
  });

  check(res, {
    'status is 200': (r) => r.status === 200,
    'has a completion body': (r) => r.body && r.body.length > 0,
  });
}
