const query = new URLSearchParams(location.search);
const nested = query.has('nested');
const baseURL = query.has('cross-origin') ? window.PRECOGNITION_BASE_URL : '';

export const settings = {
  nested, field: nested ? 'address.city' : 'email', path: baseURL + (nested ? '/profiles' : '/users'),
  data: nested ? { name: 'taken', address: { city: 'Istanbul', postcode: 'taken' } }
    : { name: 'Ada', email: 'ada@example.test', age: 21 },
};

// Demo authentication only; the official transport and all backend responses are unchanged.
const nativeFetch = window.fetch.bind(window);
window.fetch = (input, init = {}) => {
  const headers = new Headers(input instanceof Request ? input.headers : undefined);
  new Headers(init.headers).forEach((value, name) => headers.set(name, value));
  if (sessionStorage.getItem('demo-auth') !== 'off') headers.set('Authorization', 'Bearer demo-token');
  return nativeFetch(input, { ...init, headers });
};

export const config = setStatus => ({
  onPrecognitionSuccess: response => { setStatus('valid'); return response; },
  onValidationError: response => { setStatus('invalid'); return response; },
  onUnauthorized: response => { setStatus('unauthorized'); return response; },
});
