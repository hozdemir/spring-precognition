import React, { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { useForm } from 'laravel-precognition-react';
import { settings, config } from './shared.mjs';

const h = React.createElement;
function App() {
  const [status, setStatus] = useState('ready');
  const form = useForm('post', settings.path, settings.data, config(setStatus));
  form.setValidationTimeout(50);
  const value = settings.nested ? form.data.address.city : form.data.email;
  return h('form', { onSubmit: event => {
    event.preventDefault();
    form.submit().then(response => setStatus(`saved:${response.status}`)).catch(() => setStatus('submit-error'));
  } },
  h('label', null, settings.field, h('input', {
    'data-testid': 'field', value,
    onChange: event => form.setData(settings.field, event.target.value),
  })),
  h('p', { 'data-testid': 'error' }, form.errors[settings.field] ?? ''),
  h('p', { 'data-testid': 'status' }, status),
  h('button', { type: 'button', onClick: () => form.validate(settings.field) }, 'Validate'),
  h('button', { type: 'submit', disabled: form.processing }, 'Submit'));
}
createRoot(document.getElementById('root')).render(h(App));
