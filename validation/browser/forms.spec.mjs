import { test, expect } from '@playwright/test';

async function counters(page) {
  const response = await page.request.get('/demo/counters');
  expect(response.status()).toBe(200);
  return response.json();
}
function delta(before, after) {
  return { controllerCalls: after.controllerCalls - before.controllerCalls,
    nameValidationCalls: after.nameValidationCalls - before.nameValidationCalls };
}
async function validate(page, expectedStatus, selection) {
  const received = page.waitForResponse(response => response.request().method() === 'POST'
    && response.request().headers()['precognition'] === 'true');
  await page.getByRole('button', { name: 'Validate', exact: true }).click();
  const response = await received;
  expect(response.status()).toBe(expectedStatus);
  expect(response.request().headers()['precognition-validate-only']).toBe(selection);
  return response;
}

for (const framework of ['react', 'vue']) {
  test(`${framework}: field error renders and clears after correction`, async ({ page }) => {
    await page.goto(`/${framework}.html`);
    const before = await counters(page);
    await page.getByTestId('field').fill('invalid');
    await validate(page, 422, 'email');
    await expect(page.getByTestId('error')).not.toBeEmpty();
    await expect(page.getByTestId('status')).toHaveText('invalid');
    await page.getByTestId('field').fill('fixed@example.test');
    await validate(page, 204, 'email');
    await expect(page.getByTestId('error')).toBeEmpty();
    await expect(page.getByTestId('status')).toHaveText('valid');
    expect(delta(before, await counters(page))).toEqual({ controllerCalls: 0, nameValidationCalls: 0 });
  });

  test(`${framework}: normal submit invokes the controller once`, async ({ page }) => {
    await page.goto(`/${framework}.html`);
    const before = await counters(page);
    const received = page.waitForResponse(response => response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Submit', exact: true }).click();
    const response = await received;
    expect(response.status()).toBe(201);
    expect(response.request().headers()['precognition']).toBeUndefined();
    await expect(page.getByTestId('status')).toHaveText('saved:201');
    expect(delta(before, await counters(page))).toEqual({ controllerCalls: 1, nameValidationCalls: 1 });
  });

  test(`${framework}: nested field preserves its error path and validation scope`, async ({ page }) => {
    await page.goto(`/${framework}.html?nested=1`);
    const before = await counters(page);
    await page.getByTestId('field').fill('');
    await validate(page, 422, 'address.city');
    await expect(page.getByTestId('error')).not.toBeEmpty();
    await page.getByTestId('field').fill('Ankara');
    await validate(page, 204, 'address.city');
    await expect(page.getByTestId('error')).toBeEmpty();
    expect(delta(before, await counters(page))).toEqual({ controllerCalls: 0, nameValidationCalls: 0 });
  });

  test(`${framework}: cross-origin validation exposes protocol headers to the actual browser`, async ({ page }) => {
    await page.goto(`/${framework}.html?cross-origin=1`);
    const before = await counters(page);
    await page.getByTestId('field').fill('invalid');
    let response = await validate(page, 422, 'email');
    expect(new URL(response.url()).origin).not.toBe('http://127.0.0.1:4173');
    expect(response.headers()['access-control-allow-origin']).toBe('http://127.0.0.1:4173');
    await expect(page.getByTestId('error')).not.toBeEmpty();
    await page.getByTestId('field').fill('cross-origin@example.test');
    await validate(page, 204, 'email');
    await expect(page.getByTestId('status')).toHaveText('valid');
    await expect(page.getByTestId('error')).toBeEmpty();
    await page.evaluate(() => sessionStorage.setItem('demo-auth', 'off'));
    await page.getByTestId('field').fill('cross-origin-unauthorized@example.test');
    await validate(page, 401, 'email');
    await expect(page.getByTestId('status')).toHaveText('unauthorized');
    expect(delta(before, await counters(page))).toEqual({ controllerCalls: 0, nameValidationCalls: 0 });
  });

  test(`${framework}: early authentication rejection invokes onUnauthorized`, async ({ page }) => {
    await page.goto(`/${framework}.html`);
    await page.evaluate(() => sessionStorage.setItem('demo-auth', 'off'));
    const before = await counters(page);
    // Official helpers skip validate(name) when the value equals its initial value.
    await page.getByTestId('field').fill('unauthorized@example.test');
    const response = await validate(page, 401, 'email');
    expect(response.request().headers()['authorization']).toBeUndefined();
    expect(response.headers()['precognition']).toBe('true');
    expect(response.headers()['precognition-success']).toBeUndefined();
    await expect(page.getByTestId('status')).toHaveText('unauthorized');
    expect(delta(before, await counters(page))).toEqual({ controllerCalls: 0, nameValidationCalls: 0 });
  });
}
