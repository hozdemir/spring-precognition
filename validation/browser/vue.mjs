import { createApp, h, ref } from 'vue';
import { useForm } from 'laravel-precognition-vue';
import { settings, config } from './shared.mjs';

createApp({ setup() {
  const status = ref('ready');
  const form = useForm('post', settings.path, settings.data, config(value => { status.value = value; }));
  form.setValidationTimeout(50);
  return () => h('form', { onSubmit: event => {
    event.preventDefault();
    form.submit().then(response => { status.value = `saved:${response.status}`; })
      .catch(() => { status.value = 'submit-error'; });
  } }, [
    h('label', [settings.field, h('input', {
      'data-testid': 'field', value: settings.nested ? form.address.city : form.email,
      onInput: event => {
        if (settings.nested) form.address.city = event.target.value;
        else form.email = event.target.value;
      },
    })]),
    h('p', { 'data-testid': 'error' }, form.errors[settings.field] ?? ''),
    h('p', { 'data-testid': 'status' }, status.value),
    h('button', { type: 'button', onClick: () => form.validate(settings.field) }, 'Validate'),
    h('button', { type: 'submit', disabled: form.processing }, 'Submit'),
  ]);
} }).mount('#root');
