# US-05: Registrar administrador

`POST http://localhost:8080/api/users/administradores` crea una cuenta nueva con rol
Keycloak `ADMIN` y un perfil de negocio en `perfil_usuario` y `administrador`.
El Gateway requiere un access token válido con rol ADMIN. Sin token responde 401;
un CLIENTE o usuario sin ADMIN recibe 403.

La historia US-05 separa el alta de la asignación a complejo: esa operación pertenece
a US-07. Este DTO no acepta `complejoId`, `rol` ni `keycloakId`. No se convierte ni
se adopta automáticamente una cuenta existente por coincidir su correo.

## Preparación

Usa la misma cuenta de servicio configurada para Registrar cliente:

```powershell
docker compose up -d
pwsh -File infra/keycloak/configurar-users-registration.ps1
```

Iniciar users y gateway desde VS Code. La autenticación y los roles se controlan
en el Gateway; users escucha en loopback y, por decisión del proyecto, no tiene
filtro de clave interna ni validación JWT propia. Una llamada directa a 8081
no pasa por la autorización del Gateway. Usar siempre 8080 como entrada normal.

## Primer administrador sin un endpoint público de bootstrap

El administrador de la consola master de Keycloak no es un administrador de negocio.
Para crear al primero, preparar temporalmente en `msvc-users/.env`:

```properties
BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_EMAIL=admin.inicial@example.com
BOOTSTRAP_ADMIN_PASSWORD=ElegirUnaClaveLocal123!
BOOTSTRAP_ADMIN_NOMBRE=Administrador
BOOTSTRAP_ADMIN_APELLIDO=Inicial
BOOTSTRAP_ADMIN_TELEFONO_LABORAL=3511234567
```

Elegir datos y una contraseña propios; el valor mostrado es solo un ejemplo. Iniciar
users. El componente de arranque crea cuenta y perfil sin hacer una llamada pública
sin autorización. Si ya existe ese administrador con cuenta ADMIN válida y perfil
activo, no lo duplica ni cambia su contraseña. Si ya hay otro administrador, rechaza
el bootstrap y exige el endpoint autenticado. Nunca promueve una cuenta CLIENTE existente.

Después del primer arranque exitoso, poner `BOOTSTRAP_ADMIN_ENABLED=false`, quitar las
credenciales `BOOTSTRAP_ADMIN_*` del `.env` y reiniciar users. La cuenta de servicio
`KEYCLOAK_REGISTRATION_CLIENT_SECRET` sí debe conservarse para los registros normales.
Un fallo de bootstrap impide completar el arranque y deja un mensaje de diagnóstico;
no pegar claves en argumentos de terminal ni en Git.

## Registrar los siguientes administradores

Iniciar sesión con el administrador de negocio mediante Authorization Code + PKCE
del cliente `postman-local`, como se describe en [Registrar cliente](registrar-cliente.md).
Usar su **access token**, no el ID token ni el token del administrador del realm master.

En Postman elegir Bearer Token; en Swagger, Authorize. Enviar:

Para que Postman obtenga el token automáticamente antes de cada petición, usar el
script y las variables de [autenticación automática local](postman/README.md).

```json
{
  "email": "admin.nuevo@example.com",
  "password": "UnaClaveLocal123!",
  "nombre": "Ana",
  "apellido": "Perez",
  "telefono": "3511234567",
  "telefonoLaboral": "3517654321"
}
```

Responde 201 con UUID local, nombre, apellido, teléfonos normalizados, activo y
fechaAlta. Los teléfonos son opcionales y admiten hasta 30 caracteres. Email,
password, nombre y apellido son obligatorios. Password cumple la política de Keycloak,
tiene entre 12 y 128 caracteres, es write-only y no se persiste en users.

| Caso | Resultado |
| --- | --- |
| ADMIN y datos válidos | 201 |
| Sin token o token inválido en Gateway | 401 |
| CLIENTE sin ADMIN en Gateway | 403 |
| Correo ya usado por cliente o administrador | 409, sin modificar esa cuenta |
| Datos inválidos o campos desconocidos | 400 |
| Keycloak no disponible o roles incompatibles | 503 |
| Falla del perfil o habilitación | Rollback local y eliminación compensatoria de la cuenta nueva |

El alta crea la cuenta deshabilitada, retira las asignaciones de roles predeterminados
(incluido el compuesto que hereda CLIENTE), asigna ADMIN y comprueba sus roles efectivos.
Solo admite ADMIN sin CLIENTE ni ADMINISTRADOR. El discriminador JPA `ADMINISTRADOR`
no es un rol Keycloak. No modifica los roles predeterminados globales ni otras cuentas.

La compensación y reconciliación tienen los mismos límites que en Registrar cliente:
no hay transacción distribuida ni recuperación persistente automática ante cierre
abrupto. Si falla la compensación, responde 503 y registra el ID a reconciliar sin
imprimir secretos. Ver el procedimiento en [Registrar cliente](registrar-cliente.md).

## Ver la persistencia

Desde la raíz:

```powershell
docker compose exec app-db psql -U app_admin -d users_db
```

```sql
SELECT p.id, p.keycloak_id, p.nombre, p.apellido, p.activo,
       a.telefono_laboral, a.fecha_alta
FROM perfil_usuario p
JOIN administrador a ON a.usuario_id = p.id
ORDER BY a.fecha_alta DESC;
```

## Pruebas

Las pruebas de users ejercitan ambos registros sobre H2 y un Keycloak simulado por
HTTP: roles, eliminación de herencia CLIENTE, duplicados, validaciones, compensación
y bootstrap idempotente. Las del Gateway comprueban ADMIN → 201, CLIENTE → 403,
sin token/token inválido → 401 y limpieza de encabezados.

```powershell
.\msvc-users\mvnw.cmd -f msvc-users/pom.xml test
.\msvc-gateway\mvnw.cmd -f msvc-gateway/pom.xml test
pwsh -File infra/keycloak/verificar-registro-administrador.ps1
```

La última prueba requiere Docker, Keycloak y users/Gateway iniciados. Usa un cliente
efímero para emitir tokens reales con y sin ADMIN, comprueba 401/403/201/409/400 y el
vínculo en PostgreSQL, y elimina exclusivamente los datos temporales que crea.
