# Perfil propio: GET y PATCH /api/users/me

Ambos endpoints se consumen por el Gateway con un access token de Keycloak. El
Gateway valida el JWT y envía su `sub` como `X-User-Id`. No enviar IDs propios en
headers, parámetros ni body. Como se acordó, las llamadas directas a users no pasan
por la autenticación del Gateway; la entrada normal es `http://localhost:8080`.

## Consultar

`GET /api/users/me`, sin body, devuelve UUID local, nombre, apellido, teléfono,
tipoPerfil y preferenciaContacto. El tipo es CLIENTE o ADMINISTRADOR. El administrador
no tiene preferencia de contacto y recibe null en ese campo.

## Actualizar

`PATCH /api/users/me`, con Content-Type `application/json`:

```json
{
  "nombre": "Nombre nuevo",
  "telefono": "3511234567",
  "preferenciaContacto": "TELEFONO"
}
```

Para administrador, enviar solo nombre, apellido y/o teléfono:

```json
{
  "apellido": "Apellido nuevo"
}
```

- Los campos omitidos o null conservan su valor; un body sin cambios devuelve 400.
- Nombre y apellido no admiten blancos y tienen hasta 100 caracteres; se recortan espacios exteriores.
- Teléfono admite hasta 30 caracteres y se recorta. Un string vacío o solo espacios lo elimina.
- La preferencia solo corresponde a CLIENTE: EMAIL o TELEFONO. Si el resultado final
  es TELEFONO, debe quedar un teléfono no vacío, incluso cuando el PATCH solo lo intenta borrar.
- Para borrar un teléfono con canal TELEFONO, cambiar también a EMAIL en la misma petición.
- No se admiten id, keycloakId, rol, activo, email, password, complejoId ni telefonoLaboral.
- La operación actualiza el perfil de negocio en users; no modifica la cuenta ni las credenciales de Keycloak.
- La validación y persistencia se hacen dentro de una transacción. Una operación rechazada no guarda cambios parciales.

Respuesta: 200 con el perfil actualizado. Un GET posterior debe devolver esos datos.

| Caso | HTTP |
| --- | --- |
| Sin token o token inválido en Gateway | 401 |
| Identidad sin perfil local | 404 |
| Perfil inactivo | 403 |
| Datos inválidos, campos no permitidos o contacto incoherente | 400 |

Los errores de users usan `application/problem+json`. Los rechazos de autenticación
en el Gateway pueden responder 401 sin cuerpo. No confundir una cuenta creada solo
en Keycloak con un perfil registrado en PostgreSQL.

## Probar en Postman

Reimportar la colección y entorno de `docs/postman`, seleccionar TPI Local - Pruebas
y usar la carpeta **Perfil propio - PATCH**. Usar cuentas de prueba: las peticiones
válidas modifican sus nombres y teléfonos. Completar localmente `login_email` y
`login_password` para CLIENTE, y `admin_login_email`/`admin_login_password` para ADMIN.
El script obtiene un token nuevo antes de cada petición autenticada; los casos 401
no tienen ese script. Los casos 403/404 usan los tokens de las cuentas de prueba
inactiva/sin perfil configurados en sus variables.

Ejecutar «Cliente actualizado - 200» y luego «Consultar cliente actualizado - 200»
para verificar persistencia. No ejecutar la consulta de verificación antes del PATCH.

## Pruebas automatizadas

```powershell
.\msvc-users\mvnw.cmd -f msvc-users/pom.xml test
.\msvc-gateway\mvnw.cmd -f msvc-gateway/pom.xml test
```

Las pruebas de users usan una base H2 aislada, perfiles de ambos tipos y persistencia
real; prueban actualización parcial, validaciones y rechazo sin cambios guardados.
Las del Gateway prueban JWT, propagación de identidad y sustitución de encabezados
inventados. Estas pruebas no modifican los perfiles del PostgreSQL de desarrollo.
