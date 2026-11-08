const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, 'autenticar-admin.js'), 'utf8');

function ejecutar({ credentials = true, code = 200, data = { access_token: 'token-prueba' }, networkError = false, invalidJson = false } = {}) {
    const local = new Map([['admin_access_token', 'token-viejo']]);
    const environment = new Map(credentials ? [['admin_login_email', 'admin@example.com'], ['admin_login_password', 'UnaClave+Con&Caracteres123!']] : []);
    let sent, skipped = false, error;
    const pm = {
        variables: { get: key => local.get(key), set: (key, value) => local.set(key, value) },
        environment: { get: key => environment.get(key) },
        execution: { skipRequest: () => { skipped = true; } },
        sendRequest: (request, callback) => {
            sent = request;
            callback(networkError ? new Error('No imprimir datos del proveedor') : null,
                { code, json: () => { if (invalidJson) throw new Error('JSON inválido'); return data; } });
        }
    };
    try { vm.runInNewContext(source, { pm }); } catch (ex) { error = ex; }
    return { local, environment, sent, skipped, error };
}

test('obtiene token temporal y envía las credenciales como formulario sin concatenarlas', () => {
    const result = ejecutar();
    assert.equal(result.error, undefined);
    assert.equal(result.skipped, false);
    assert.equal(result.local.get('admin_access_token'), 'token-prueba');
    assert.equal(result.environment.has('admin_access_token'), false);
    assert.equal(result.sent.method, 'POST');
    assert.equal(result.sent.body.mode, 'urlencoded');
    assert.equal(result.sent.body.urlencoded.find(x => x.key === 'password').value, 'UnaClave+Con&Caracteres123!');
    assert.equal(result.sent.body.urlencoded.find(x => x.key === 'client_id').value, 'postman-auto');
});

for (const [name, options] of [
    ['faltan credenciales', { credentials: false }],
    ['credenciales rechazadas', { code: 401 }],
    ['Keycloak caído', { networkError: true }],
    ['JSON inválido', { invalidJson: true }],
    ['token ausente', { data: {} }],
    ['JSON nulo', { data: null }],
    ['token vacío', { data: { access_token: ' ' } }]
]) {
    test(name + ': cancela la petición y no reutiliza el token anterior', () => {
        const result = ejecutar(options);
        assert.equal(result.skipped, true);
        assert.equal(result.local.get('admin_access_token'), '');
        assert.ok(result.error);
        assert.equal(result.error.message.includes('UnaClave'), false);
        if (!options.credentials && options.credentials === false) assert.equal(result.sent, undefined);
    });
}

test('colección incorpora exactamente el script en las peticiones ADMIN y conserva pruebas negativas', () => {
    const collection = JSON.parse(fs.readFileSync(path.join(__dirname, 'tpi-endpoints-implementados.postman_collection.json'), 'utf8'));
    const folder = collection.item.find(item => item.name === 'Registrar administrador');
    let authenticated = 0;
    for (const item of folder.item) {
        const script = item.event.find(event => event.listen === 'prerequest');
        if (item.request.auth?.bearer?.some(x => x.value === '{{admin_access_token}}')) {
            assert.equal(script.script.exec.join('\n').trim(), source.trim());
            authenticated++;
        } else assert.equal(script, undefined);
    }
    assert.equal(authenticated, 2);
});
