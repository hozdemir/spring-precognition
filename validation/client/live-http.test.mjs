import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { client } from 'laravel-precognition';

import { startBackend } from './backend.mjs';

// Starts Tomcat and uses the unmodified official fetch transport.
const valid = { name: 'Ada', email: 'ada@example.test', age: 21 };
const emailOnly = { name: 'taken', email: 'ada@example.test', age: 12 };
const invalid = { name: 'taken', email: 'invalid', age: 12 };
const auth = { Authorization: 'Bearer demo-token' };
let backend;
let baseURL;
before(async () => {
  backend = await startBackend();
  baseURL = backend.baseURL;
  client.withBaseURL(baseURL).withTimeout(5000);
}, { timeout: 30000 });
after(async () => { await backend?.close(); });

async function counters() {
  const response = await fetch(`${baseURL}/demo/counters`);
  assert.equal(response.status, 200);
  return response.json();
}

function delta(before, after) {
  return { controllerCalls: after.controllerCalls - before.controllerCalls,
    nameValidationCalls: after.nameValidationCalls - before.nameValidationCalls };
}

test('official client validates a selected field through real Tomcat HTTP', async () => {
  const before = await counters();
  let success = false;
  const response = await client.post('/users', emailOnly, {
    headers: auth, only: ['email'], onPrecognitionSuccess: () => { success = true; },
  });
  assert.equal(response.status, 204);
  assert.equal(success, true);
  assert.deepEqual(delta(before, await counters()), { controllerCalls: 0, nameValidationCalls: 0 });
});

test('official client receives selected 422 through real Tomcat HTTP', async () => {
  const before = await counters();
  let errors;
  await client.post('/users', invalid, {
    headers: auth, only: ['email'],
    onValidationError: response => { errors = response.data.errors; return response; },
  });
  assert.deepEqual(Object.keys(errors), ['email']);
  assert.deepEqual(delta(before, await counters()), { controllerCalls: 0, nameValidationCalls: 0 });
});

test('official client submits normally through real Tomcat HTTP', async () => {
  const before = await counters();
  const response = await client.post('/users', valid, { headers: auth, precognitive: false });
  assert.equal(response.status, 201);
  assert.equal(response.data.name, 'Ada');
  assert.deepEqual(delta(before, await counters()), { controllerCalls: 1, nameValidationCalls: 1 });
});

test('official client receives full submit validation through real Tomcat HTTP', async () => {
  const before = await counters();
  let errors;
  await client.post('/users', invalid, {
    headers: auth, precognitive: false,
    onValidationError: response => { errors = response.data.errors; return response; },
  });
  assert.deepEqual(Object.keys(errors).sort(), ['age', 'email', 'name']);
  assert.deepEqual(delta(before, await counters()), { controllerCalls: 0, nameValidationCalls: 1 });
});

test('parallel official-client HTTP requests preserve validation scope', async () => {
  const before = await counters();
  const requests = Array.from({ length: 40 }, (_, i) => client.post('/users', emailOnly, {
    headers: auth, precognitive: i % 2 === 0, only: i % 2 === 0 ? ['email'] : undefined,
    fingerprint: null,
    onValidationError: response => response,
  }));
  const responses = await Promise.all(requests);
  responses.forEach((response, i) => assert.equal(response.status, i % 2 === 0 ? 204 : 422));
  assert.deepEqual(delta(before, await counters()), { controllerCalls: 0, nameValidationCalls: 20 });
});

test('empty only validates no fields over real HTTP', async () => {
  const before = await counters();
  const response = await client.post('/users', invalid, { headers: auth, only: [] });
  assert.equal(response.status, 204);
  assert.deepEqual(delta(before, await counters()), { controllerCalls: 0, nameValidationCalls: 0 });
});

test('early 401 invokes the official unauthorized callback over real HTTP', async () => {
  const before = await counters();
  let unauthorized = false;
  const response = await client.post('/users', valid, {
    onUnauthorized: response => { unauthorized = true; return response; },
  });
  assert.equal(response.status, 401);
  assert.equal(unauthorized, true);
  assert.deepEqual(delta(before, await counters()), { controllerCalls: 0, nameValidationCalls: 0 });
});

test('nested selected leaf skips root and sibling validators over real HTTP', async () => {
  const before = await counters();
  const response = await client.post('/profiles', {
    name: 'taken', address: { city: 'Istanbul', postcode: 'taken' },
  }, { headers: auth, only: ['address.city'] });
  assert.equal(response.status, 204);
  assert.deepEqual(delta(before, await counters()), { controllerCalls: 0, nameValidationCalls: 0 });
});

test('nested selected error preserves the dot path over real HTTP', async () => {
  const before = await counters();
  let errors;
  await client.post('/profiles', {
    name: 'taken', address: { city: '', postcode: 'taken' },
  }, { headers: auth, only: ['address.city'],
    onValidationError: response => { errors = response.data.errors; return response; },
  });
  assert.deepEqual(Object.keys(errors), ['address.city']);
  assert.deepEqual(delta(before, await counters()), { controllerCalls: 0, nameValidationCalls: 0 });
});
