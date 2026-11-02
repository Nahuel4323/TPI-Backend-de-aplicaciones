# Keycloak local

Requiere Docker Desktop iniciado con contenedores Linux y Docker Compose.
Desde la raiz del repositorio:

```powershell
Copy-Item .env.example .env
```

Completar `KEYCLOAK_ADMIN_PASSWORD` y `KEYCLOAK_DB_PASSWORD` en ese `.env`.
Son contrasenas locales elegidas por cada integrante. No copiar encima de un `.env` existente.
Luego ejecutar:

```powershell
docker compose up -d
docker compose logs -f keycloak
```

Cuando termine el inicio, abrir http://localhost:8180/admin e ingresar con
`KEYCLOAK_ADMIN_USERNAME` y `KEYCLOAK_ADMIN_PASSWORD`. Si cambia `KEYCLOAK_PORT`,
usar ese puerto. El modo `start-dev` es para el entorno local.

Se importa el realm `complejos-deportivos`, con roles `CLIENTE` y `ADMIN`.
El administrador inicial pertenece al realm `master`; no es un usuario de negocio.
El autorregistro esta habilitado con correo unico y rol CLIENTE por defecto. El cliente
publico `postman-local` usa Authorization Code + PKCE S256 y audiencia `backend-api`.
El gateway valida sus tokens para completar el perfil de cliente. Ver la
[guia de Registrar cliente](../../docs/registrar-cliente.md).
La base PostgreSQL de este Compose es exclusiva de Keycloak; no contiene las bases
de users, spaces ni bookings y no publica su puerto en la computadora.

## Persistencia

PostgreSQL conserva la configuracion, usuarios, roles y clientes en el volumen
`keycloak-db-data`. Detener con `docker compose stop` o eliminar los contenedores
con `docker compose down` conserva ese volumen. No ejecutar `docker compose down -v`
si se quieren conservar los datos: elimina los volumenes del entorno.
El volumen no es un respaldo y no se comparte por Git.

Las contrasenas de PostgreSQL y del administrador se aplican al inicializar el
entorno. Editar el `.env` despues no cambia las contrasenas ya guardadas:
deben cambiarse tambien en PostgreSQL o en la consola de Keycloak, respectivamente.

## Configuracion compartida

Se comparten `docker-compose.yml`, `.env.example` y
`infra/keycloak/realms/complejos-deportivos-realm.json` por Git.
Cada integrante tiene su propia base y volumen, y parte del mismo realm inicial.
La importacion al inicio omite un realm que ya existe; los cambios al JSON no
actualizan automaticamente las instalaciones anteriores.

Para aplicar la configuracion de registro a un realm existente sin borrar datos,
usar PowerShell 7 desde la raiz: `pwsh -File infra/keycloak/configurar-registro.ps1`.
El script usa las credenciales administrativas del `.env` raiz y conserva los usuarios.

Los cambios hechos en la consola solo afectan la instalacion local. Para compartirlos,
actualizar el JSON inicial y documentar como aplicarlos a los realms existentes.
No subir exportaciones con usuarios reales, credenciales o secretos de clientes.

Comprobar estado y configuracion sin imprimir contrasenas:

```powershell
docker compose config --quiet
docker compose ps
```

Referencias: [contenedores](https://www.keycloak.org/server/containers) e
[importacion de realms](https://www.keycloak.org/server/importExport).
