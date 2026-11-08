# US-01: Registro público de cliente

`POST http://localhost:8080/api/users/clientes` crea una cuenta con rol CLIENTE en
Keycloak y su perfil de negocio en users. No requiere JWT ni registro previo en la
pantalla de Keycloak. No acepta roles ni identificadores de identidad del consumidor.

## Preparación en Windows / VS Code

1. Preparar los `.env` y levantar PostgreSQL y Keycloak con `docker compose up -d`.
2. Desde la raíz, ejecutar con PowerShell 7:

```powershell
pwsh -File infra/keycloak/configurar-users-registration.ps1
```

El script crea el cliente confidencial `users-registration`, habilita su cuenta de
servicio y asigna `manage-users`, `view-users` y `view-realm` de `realm-management`,
incluyendo sus scope mappings. No concede `realm-admin`. Son permisos administrativos
de usuarios: el secreto se conserva exclusivamente en `msvc-users/.env`, excluido de Git.
La aplicación no usa las credenciales del administrador master. El script puede
repetirse sin rotar el secreto ni borrar datos. También debe ejecutarse en volúmenes nuevos.

3. Iniciar users y gateway desde las configuraciones de VS Code, con JDK 25. Reiniciar
users si estaba abierto antes de configurar la cuenta de servicio.
4. Abrir [Swagger](http://localhost:8080/swagger-ui.html), seleccionar Users y ejecutar
Registrar cliente sin pulsar Authorize. En Postman elegir **No Auth**.

## Solicitud y respuesta

```json
{
  "email": "ana@example.com",
  "password": "UnaClaveLocal123!",
  "nombre": "Ana",
  "apellido": "Perez",
  "telefono": "3511234567",
  "preferenciaContacto": "EMAIL"
}
```

La contraseña debe tener entre 12 y 128 caracteres y cumplir la política de Keycloak
(en el realm del repositorio: mayúscula, minúscula y número). No se recorta ni normaliza.
El correo se normaliza a minúsculas y se usa como username en Keycloak; su titularidad
no se verifica por SMTP en este entorno. TELEFONO requiere teléfono no vacío.

Responde **201 Created** con el UUID local, nombre, apellido, teléfono, preferencia,
estado activo y fecha de alta. No devuelve password ni keycloakId; users no persiste
la contraseña ni el correo. La contraseña solo se utiliza para la solicitud a Keycloak.
El DTO oculta credenciales en `toString()` y marca password como write-only.

| Caso | Resultado |
| --- | --- |
| Registro nuevo sin token | 201 |
| Correo ya existente en Keycloak | 409; no modifica la cuenta existente |
| Campos inválidos, rol/keycloakId en JSON o contraseña rechazada | 400 |
| Keycloak inaccesible o cuenta de servicio sin configurar/permisos | 503 |
| Falla el guardado del perfil | Rollback local y eliminación compensatoria de la cuenta nueva |
| Falla la compensación | 503 con indicación de reconciliación y correlación en logs |

Los errores usan `application/problem+json`, con `title`, `status` y `detail`; las
validaciones añaden `errors`. No se devuelve el cuerpo de error del proveedor ni secretos.
Si se envía voluntariamente un Bearer inválido al Gateway, Spring Security puede
responder 401 incluso en esta ruta pública: para registrarse, usar No Auth.

## Consistencia y recuperación

Users crea primero una cuenta deshabilitada, asigna CLIENTE y comprueba que no herede
ADMIN. Luego guarda perfil y cliente en una transacción local, habilita la cuenta y
confirma la transacción. Si falla el guardado, la habilitación o el commit, intenta
eliminar exclusivamente la cuenta creada por esa operación.

No es una transacción distribuida. Un cierre abrupto del proceso o una caída durante
la compensación puede requerir reconciliación manual. Una respuesta perdida al crear
la cuenta se intenta resolver consultando el username y el atributo `registrationId`
de esa misma operación; nunca se adopta una cuenta preexistente por compartir correo.
Si no se puede resolver, se registra la correlación y se responde 503.

Para reconciliar, el operador debe localizar la cuenta por el ID o `registrationId`
del log y comprobar si existe un perfil con ese `keycloak_id` en users_db. Si no hay
perfil y la cuenta es de esa operación fallida, deshabilitarla/eliminarla desde la
consola y reintentar el registro. Si hay perfil, revisar su consistencia antes de
habilitar o eliminar nada. Un 409 por sí solo nunca autoriza borrar una cuenta.
No hay recuperación automática persistente ante cierre del proceso.

## Acceso y login posterior

Users sigue escuchando en `127.0.0.1:8081` y no tiene un filtro de clave interna.
La autenticación JWT y los permisos por rol se aplican en el Gateway, en `8080`.
Las llamadas directas a users no pasan por esos controles. El registro no usa
`X-User-*` para crear la identidad y no permite crear administradores.

Después del 201, iniciar sesión normalmente en Keycloak con el correo y contraseña
registrados. `postman-local` mantiene Authorization Code + PKCE S256, callback
`https://oauth.pstmn.io/v1/browser-callback`, scope `openid profile email` y audiencia
`backend-api`. No habilitar Password Grant para el login del cliente.

## Verificación

Desde la raíz:

```powershell
.\msvc-users\mvnw.cmd -f msvc-users/pom.xml test
.\msvc-gateway\mvnw.cmd -f msvc-gateway/pom.xml test
pwsh -File infra/keycloak/verificar-registro-publico.ps1
```

Las pruebas Java de users ejecutan validación, JPA H2, transacciones y solicitudes
HTTP contra un stand-in local de Keycloak, incluyendo duplicados y compensaciones.
La última prueba requiere servicios reales iniciados: crea una cuenta/perfil
aleatorios, comprueba 201/409/400, rol, contraseña y vínculo PostgreSQL, y elimina
exclusivamente esos datos temporales. Ejecutarla desde la raíz con Docker disponible.
El antiguo `scripts/verificar-registro.py` corresponde al flujo anterior y no es
el verificador de este contrato.

Contrato: [OpenAPI de users](http://localhost:8080/docs/users).
Referencia de integración: [Keycloak Admin REST API](https://www.keycloak.org/docs-api/latest/rest-api/index.html).
