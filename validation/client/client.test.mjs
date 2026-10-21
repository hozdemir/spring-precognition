import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import test from 'node:test';
import { client } from 'laravel-precognition';

// These are actual Spring MVC responses, replayed at the fetch boundary.
// This proves the official core client's wire contract; it is not a browser/network test.
const fixtures = JSON.parse(await readFile(new URL(process.env.PRECOGNITION_FIXTURES ?? '../../target/validation/boot4-laravel-fixtures.json', import.meta.url)));
const valid = { name: 'Ada', email: 'ada@example.test', age: 21 };
const invalid = { name: 'taken', email: 'invalid', age: 12 };

function replay(t, name) {
  const fixture = fixtures[name];
  assert.ok(fixture, `Missing Spring response fixture: ${name}`);
  const requests = [];
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    requests.push({ url, ...options });
    return new Response(fixture.status === 204 ? null : fixture.body, {
      status: fixture.status,
      headers: fixture.headers,
    });
  });
  return requests;
}

test('official client sends selected headers and recognizes Spring 204 success', async t => {
  const requests = replay(t, 'selectedSuccess');
  let succeeded = false;
  const result = await client.post('http://precognition.test/users', valid, {
    only: ['email'],
    onPrecognitionSuccess: () => { succeeded = true; },
  });
  assert.equal(result.status, 204);
  assert.equal(succeeded, true);
  assert.equal(requests[0].headers.Precognition, 'true');
  assert.equal(requests[0].headers['Precognition-Validate-Only'], 'email');
  assert.deepEqual(JSON.parse(requests[0].body), valid);
});

test('official client recognizes full Spring validation when selection is absent', async t => {
  const requests = replay(t, 'fullSuccess');
  const result = await client.post('http://precognition.test/users', valid);
  assert.equal(result.status, 204);
  assert.equal(requests[0].headers['Precognition-Validate-Only'], undefined);
});

test('official client routes Spring selected 422 into validation callback', async t => {
  replay(t, 'selectedInvalid');
  let errors;
  const result = await client.post('http://precognition.test/users', invalid, {
    only: ['email'],
    onValidationError: response => { errors = response.data.errors; return response; },
  });
  assert.equal(result.status, 422);
  assert.deepEqual(Object.keys(errors), ['email']);
  assert.ok(Array.isArray(errors.email));
});

test('official client sends normal submit without Precognition headers', async t => {
  const requests = replay(t, 'normalSuccess');
  const result = await client.post('http://precognition.test/users', valid, { precognitive: false });
  assert.equal(result.status, 201);
  assert.equal(result.data.name, 'Ada');
  assert.equal(requests[0].headers.Precognition, undefined);
});

test('official client accepts normal submit 422 errors', async t => {
  replay(t, 'normalInvalid');
  let errors;
  await client.post('http://precognition.test/users', invalid, {
    precognitive: false,
    onValidationError: response => { errors = response.data.errors; return response; },
  });
  assert.deepEqual(Object.keys(errors).sort(), ['age', 'email', 'name']);
});

test('empty only is a successful no-op, not full validation', async t => {
  const requests = replay(t, 'emptySelection');
  assert.equal(fixtures.emptySelection.status, 204);
  const result = await client.post('http://precognition.test/users', valid, { only: [] });
  assert.equal(result.status, 204);
  assert.equal(requests[0].headers['Precognition-Validate-Only'], '');
});

test('early 401 reaches the official unauthorized callback', async t => {
  replay(t, 'authDenied');
  assert.equal(fixtures.authDenied.status, 401);
  let unauthorized = false;
  let finished = false;
  const response = await client.post('http://precognition.test/users', valid, {
    onUnauthorized: response => { unauthorized = true; return response; },
    onFinish: () => { finished = true; },
  });
  assert.equal(response.status, 401);
  assert.equal(unauthorized, true);
  assert.equal(finished, true);
});
