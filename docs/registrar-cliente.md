# US-01: Registrar cliente

La persona crea su identidad en la pantalla de Keycloak. Luego Postman simula al
consumidor del sistema y completa el perfil por el gateway. No se envian ni guardan
contrasenas en users. El correo permanece en Keycloak; users solo comprueba su
presencia cuando el canal elegido es EMAIL.

## Preparacion

1. Seguir [requisitos.md](../requisitos.md), con JDK 25 y Docker Desktop.
2. Levantar Docker desde la raiz: `docker compose up -d`.
3. Si el realm ya existia, ejecutar con PowerShell 7 desde la raiz:

```powershell
pwsh -File infra/keycloak/configurar-registro.ps1
```

El script usa el administrador del `.env` raiz, no imprime sus claves y actualiza
solo el registro, el rol predeterminado y el cliente `postman-local`. No elimina
usuarios ni reinicializa volumenes. Puede ejecutarse nuevamente. Para una instalacion
con volumen nuevo, el JSON del realm ya contiene esta configuracion.

4. Elegir una clave interna larga y aleatoria y poner el mismo valor en
   `GATEWAY_INTERNAL_TOKEN` de `msvc-gateway/.env` y `msvc-users/.env`.
   En la computadora donde se implemento ya se genero y sincronizo.
   No confundirla con la contrasena del usuario ni con un secreto OAuth2.
5. Iniciar users y gateway desde Spring Boot Dashboard. Ambos tienen su configuracion
   en `.vscode/launch.json`, con carpeta propia y UTC. Alternativamente, desde cada carpeta:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.jvmArguments=-Duser.timezone=UTC"
```

Usar [health de users](http://localhost:8081/actuator/health) y
[health del gateway](http://localhost:8080/actuator/health) para comprobar el inicio.

## Obtener el token en Postman y registrarse en Keycloak

Crear una peticion POST a `http://localhost:8080/api/users/clientes`.
En Authorization elegir OAuth 2.0 y configurar Get New Access Token:

| Campo | Valor |
| --- | --- |
| Grant Type | Authorization Code (With PKCE) |
| Authorize using browser | Activado |
| Callback URL | `https://oauth.pstmn.io/v1/browser-callback` |
| Auth URL | `http://localhost:8180/realms/complejos-deportivos/protocol/openid-connect/auth` |
| Access Token URL | `http://localhost:8180/realms/complejos-deportivos/protocol/openid-connect/token` |
| Client ID | `postman-local` |
| Client Secret | Vacio: es un cliente publico |
| Code Challenge Method | SHA-256 / S256 |
| Scope | `openid profile email` |
| Client Authentication | Send client credentials in body, sin client secret |

Tambien esta permitida la callback `https://oauth.pstmn.io/v1/callback` para versiones
de Postman que usan esa URL. Debe coincidir exactamente con la elegida al autorizar.
Si cambia el puerto de Keycloak, ajustar estas URLs y las variables de issuer/JWKS del gateway.

Pulsar Get New Access Token. En la pantalla de Keycloak, elegir **Registrarse** y
completar los datos. El correo es obligatorio, se usa como nombre de usuario y no
puede repetirse. La clave debe tener al menos 12 caracteres, una mayuscula, una
minuscula y un numero. No se puede elegir un rol desde el formulario.
Al regresar a Postman, seleccionar **Use Token** y enviar el access token como Bearer.
No usar el ID token como token de acceso a la API.

Si se importa la coleccion del repositorio, copiar el access token a la variable
local `access_token` de la coleccion. Ejecutar sus peticiones en orden con una
cuenta que todavia no tenga perfil. No guardar el token en Git ni exportar la
coleccion con el valor real. La coleccion contiene ejemplos y aserciones del
contrato OpenAPI; el CLI de Postman no pudo ejecutarse en la computadora de
implementacion por una politica local, por lo que las pruebas HTTP se ejecutaron
con los tests Java y el verificador real de Python.

No se habilita Password Grant en `postman-local`. El entorno local no configura
SMTP: se exige correo, pero no se verifica su titularidad por email. No confundir
presencia de correo con `email_verified=true`.

## Crear el perfil

En Body elegir raw / JSON:

```json
{
  "nombre": "Ana",
  "apellido": "Perez",
  "telefono": "3511234567",
  "preferenciaContacto": "EMAIL"
}
```

El gateway valida firma, vencimiento, issuer, audiencia `backend-api` y rol `CLIENTE`.
Deniega las cuentas con `ADMIN` o `ADMINISTRADOR`, incluso si tambien tienen `CLIENTE`.
Reemplaza encabezados `X-User-*` y `X-Gateway-Token` con datos de confianza; el consumidor
no debe enviarlos. El correo se transporta internamente codificado en Base64URL,
no se almacena en la tabla ni se devuelve en la respuesta.

Users crea las filas `perfil_usuario` y `cliente` en una transaccion y responde
**201 Created** con el UUID local, datos normalizados y fecha de alta.
El UUID local no es el identificador de Keycloak.

| Prueba | Resultado esperado |
| --- | --- |
| Cuenta CLIENTE nueva y datos validos | 201 |
| Repetir la misma cuenta | 409 |
| Cuenta que ya tiene perfil ADMINISTRADOR local | 409 |
| Nombre/apellido vacio, canal invalido o TELEFONO sin telefono | 400 |
| Enviar `rol`, `keycloakId`, `email` o `password` en el JSON | 400 |
| EMAIL sin correo en el token | 400 |
| Sin token o token alterado/vencido/audiencia incorrecta | 401 |
| Token ADMIN, ambos roles o sin CLIENTE | 403 |
| POST directo a localhost:8081, sin clave interna | 403 |

Si el perfil falla, la cuenta de Keycloak sigue existiendo: corregir los datos o
la disponibilidad de users y reintentar con esa misma cuenta. No registrarse otra vez
ni eliminar la identidad para reintentar. La operacion no implementa transacciones
distribuidas ni escribe en Keycloak desde users.

## Limites de acceso y administradores

Users y gateway escuchan en 127.0.0.1. Users permite health sin clave; el resto del
canal requiere la clave interna. Users no usa Spring Security ni valida JWT:
solo verifica la clave del canal interno antes de aceptar identidad. Quien conoce
esa clave puede suplantar al gateway; mantenerla privada y fuera de Postman/Git.
Un despliegue fuera de la computadora necesita aislamiento de red y TLS/mTLS para
ese canal. No publicar users directamente en Internet.

Otras rutas del gateway quedan denegadas hasta implementar la politica de su US.
No crear administradores con el registro publico. Para cuentas administrativas
controladas, el operador debe retirar el rol compuesto `default-roles-complejos-deportivos`
o su herencia de CLIENTE en esa cuenta antes de asignar ADMIN y verificar sus roles
efectivos. La validacion JWT local no consulta la sesion en cada peticion: los tokens
anteriores pueden seguir siendo validos hasta vencer. Para una conversion de rol,
esperar a su vencimiento antes de considerar completado el cambio; el alta controlada
de administradores y una politica de revocacion inmediata quedan para su propia US.

## Archivos y pruebas

- Contrato: [OpenAPI generado](http://localhost:8080/docs/users), derivado de controladores y DTO de users.
- Coleccion importable: [registrar-cliente.postman_collection.json](postman/registrar-cliente.postman_collection.json).
- Tests users: validaciones, persistencia, duplicados, perfil administrativo y acceso directo; usan H2 aislado.
- Tests gateway: roles y sustitucion de encabezados por HTTP, con decoder de prueba y destino local controlado.
- La firma y audiencia se comprueban tambien en las pruebas con Keycloak real, no con el decoder simulado.

Desde la raiz:

```powershell
.\msvc-users\mvnw.cmd -f msvc-users/pom.xml test
.\msvc-gateway\mvnw.cmd -f msvc-gateway/pom.xml test
```

La comprobacion real opcional `python scripts/verificar-registro.py` requiere Python 3,
users/gateway iniciados, Docker y acceso administrativo local. Usa los formularios
reales de Keycloak con PKCE; crea cuentas y un cliente temporales con identificadores
aleatorios y elimina exclusivamente esos perfiles/cuentas al terminar. No imprime
tokens ni contrasenas. No forma parte del arranque cotidiano.

Referencias: [registro y administracion de Keycloak](https://www.keycloak.org/docs/latest/server_admin/),
[validacion JWT reactiva](https://docs.spring.io/spring-security/reference/reactive/oauth2/resource-server/jwt.html).

## Swagger UI e inicio de sesión

Con gateway y users iniciados, abrir http://localhost:8080/swagger-ui.html y elegir Users.
El contrato se genera desde controladores y DTO de users y se publica por el gateway en /docs/users.
Para probar, obtener el access token en Postman, pulsar Authorize en Swagger y pegar solo el token, sin el prefijo Bearer. Luego usar Try it out y Execute. No pegar la clave interna del gateway.

Una cuenta existente puede iniciar sesión desde Get New Access Token. Si Keycloak reutiliza la sesión y vuelve automáticamente a Postman, agregar ?prompt=login al final de Auth URL para pedir las credenciales. Registrarse en Keycloak no crea el perfil: enviar POST una sola vez; repetirlo devuelve 409.

La UI central tiene por ahora Users. Agregar Spaces y Bookings cuando publiquen sus contratos, con una ruta GET dedicada y una entrada springdoc.swagger-ui.urls por servicio.
