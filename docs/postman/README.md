# Pruebas locales en Postman

La colección local de la aplicación está en
`postman/collections/TPI Backend Complejos Deportivos - endpoints implementados/`.
Las peticiones de perfil están bajo **msvc-users → A traves del API Gateway →
Consultar Usuario Propio / Actualizar Usuario Propio**. Al usar esta colección de
archivos locales no hace falta reimportar el JSON para ver sus cambios. Se conserva
también la exportación JSON del directorio como alternativa importable.

Las nuevas peticiones locales de perfil tienen URL, autorización, body y el código
HTTP esperado en el nombre; no añaden scripts de aserciones automáticas. Conservan
únicamente el script de login cuando corresponde. Los casos sin token o token
inválido no hacen login. Para CLIENTE usan las variables locales ya existentes
`cliente_login_email`/`cliente_login_password`, con `login_email`/`login_password`
como alternativa. No se modifican las credenciales guardadas en tu entorno.

Importar `tpi-endpoints-implementados.postman_collection.json` y
`tpi-local-pruebas.postman_environment.json`. Seleccionar el entorno TPI Local - Pruebas.

## Login automático de administrador

Las peticiones «Administrador nuevo - 201» y «Administrador duplicado - 409» ya
incluyen `autenticar-admin.js` como Pre-request Script y usan Bearer
`{{admin_access_token}}`. No hace falta pegar ni renovar manualmente el token:
el script obtiene uno nuevo antes de cada petición y lo guarda solo durante esa
petición. Las pruebas negativas sin token y con CLIENTE conservan su comportamiento.

En tu equipo el cliente Keycloak de pruebas `postman-auto` se configura con:

```powershell
pwsh -File infra/keycloak/configurar-postman-auto.ps1
```

Ejecutarlo después de levantar Keycloak, también en instalaciones con realm existente.
El JSON del realm contiene la definición para nuevas instalaciones. Este cliente usa
Direct Access Grants para automatizar el login por correo y contraseña **en pruebas
locales**; `postman-local` conserva Authorization Code + PKCE para el flujo normal.
No tiene cuenta de servicio ni permisos administrativos propios: los permisos
provienen del usuario autenticado.

Completar solo en los valores locales de Postman:

| Variable | Valor |
| --- | --- |
| `keycloak_url` | `http://localhost:8180` |
| `keycloak_realm` | `complejos-deportivos` |
| `keycloak_auto_client_id` | `postman-auto` |
| `admin_login_email` | Correo del administrador de negocio existente |
| `admin_login_password` | Contraseña de ese administrador |

No usar el administrador de la consola master. Para crear el primer administrador
de negocio, seguir [Registrar administrador](../registrar-administrador.md).
Las credenciales de login son distintas de `admin_registration_email/password`,
que corresponden a la cuenta nueva que se quiere crear en el body.

Los archivos versionados tienen las credenciales vacías. El tipo secret solo oculta
el valor en pantalla: no exportar ni compartir un entorno rellenado. El script no
imprime ni persiste tokens. Si falla el login, cancela la petición y descarta el token
anterior. Una cuenta con MFA, contraseña temporal o acciones pendientes necesita
completar su login interactivo; no se eliminan esos controles desde el script.

Para otra petición administrativa, copiar `autenticar-admin.js` a Scripts → Pre-request
y elegir Bearer Token `{{admin_access_token}}` en Authorization.

## Actualizar el perfil propio

La carpeta **Perfil propio - PATCH** contiene 15 peticiones, incluyendo el GET de
verificación posterior. Completar `login_email` y `login_password` con una cuenta
CLIENTE de prueba que tenga perfil en PostgreSQL; el script `autenticar-usuario.js`
obtiene `access_token` temporalmente. Para ADMIN usa `admin_login_*` y el script
administrativo. Estas credenciales son de la cuenta que se modifica, no de otra
cuenta que se quiera administrar. Ver [reglas, bodies y errores](../perfil-propio.md).
Los casos sin token e inválido no ejecutan autenticación automática. Los casos
403 y 404 necesitan las cuentas/variables indicadas en sus nombres.

## Consultar el perfil propio

La carpeta **Perfil propio - GET** incluye consultas de cliente y administrador,
401 sin token/token inválido, 404 de cuenta sin perfil y 403 de perfil inactivo.
Usa `GET {{gateway_url}}/api/users/me`, sin body ni encabezados de identidad propios.
Para 200 se necesita un perfil en PostgreSQL: una cuenta creada solo en Keycloak
sirve como fixture del 404, no del 200. Las cuentas administrativas de prueba que
solo existen en Keycloak también devolverán 404. Las variables `sin_perfil_access_token`
y `perfil_inactivo_access_token` se completan localmente para los casos negativos.
El handler de `ResponseStatusException` en users devuelve `application/problem+json`
para los errores de perfil. Para ejecutar esos casos en Postman se necesitan las
cuentas de prueba indicadas y users reiniciado con el código actualizado.

## Verificar el script

```powershell
node --test docs/postman/autenticar-admin.test.cjs
pwsh -File docs/postman/verificar-autenticacion.ps1
```

La primera prueba comprueba el comportamiento del script y su incorporación a la
colección. La segunda ejecuta ese mismo script en Postman CLI contra Keycloak real,
con una cuenta administrativa temporal que se elimina al terminar. No necesita
levantar users ni gateway; solo Keycloak y el cliente postman-auto configurado.

Referencias: [scripts de solicitudes en Postman](https://learning.postman.com/docs/tests-and-scripts/write-scripts/postman-sandbox-reference/pm-send-request/)
y [administración de Keycloak](https://www.keycloak.org/docs/latest/server_admin/).
