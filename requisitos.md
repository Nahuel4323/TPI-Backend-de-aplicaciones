# Requisitos y ejecución del entorno local

Guía para preparar una computadora nueva y empezar a desarrollar. Los comandos
están escritos para PowerShell en Windows y se ejecutan desde la raíz del
repositorio, salvo que se indique otra carpeta.

## 1. Instalar las herramientas

| Herramienta | Uso |
| --- | --- |
| [Git](https://git-scm.com/downloads) | Clonar el repositorio y compartir cambios. |
| [JDK 25, Eclipse Temurin](https://adoptium.net/temurin/releases/?version=25) | Compilar y ejecutar los microservicios. Instalar el JDK, no solamente el JRE. |
| [Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install/) | Ejecutar PostgreSQL y Keycloak. Incluye Docker Compose. Seguir sus requisitos de Windows y WSL 2, si se usa ese backend. |
| [Visual Studio Code](https://code.visualstudio.com/) | Editor recomendado para trabajar con el equipo. |

No hace falta instalar PostgreSQL, Keycloak ni pgAdmin en Windows. Las bases y
Keycloak se ejecutan en contenedores. pgAdmin es opcional para explorar datos.
Los cuatro servicios incluyen Maven Wrapper, por lo que no necesitan Maven global.
`common-lib` no incluye Wrapper y por ahora no es dependencia de los servicios;
para compilarla por separado se puede usar Maven instalado o, desde la raíz,
`.\msvc-users\mvnw.cmd -f common-lib/pom.xml install`.

En VS Code, instalar desde Extensiones (`Ctrl+Shift+X`):

- [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack).
- [Spring Boot Extension Pack](https://marketplace.visualstudio.com/items?itemName=vmware.vscode-boot-dev-pack).
- Recomendado: [Spring Boot Dashboard](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-spring-boot-dashboard), incluido en el paquete de Spring. Permite iniciar, detener y depurar los servicios desde la barra lateral.

## 2. Comprobar Java y Docker

Abrir Docker Desktop y esperar a que el motor esté listo, usando contenedores Linux.
En una terminal nueva ejecutar:

```powershell
git --version
java -version
javac -version
docker --version
docker compose version
docker info
```

Java y javac deben mostrar versión 25. Si hay varias versiones instaladas,
configurar `JAVA_HOME` con la carpeta del JDK 25 y colocar `%JAVA_HOME%\bin`
antes de otras versiones en el `Path`. Cerrar todas las ventanas de VS Code
y volver a abrirlo después de cambiar las variables de entorno de Windows.

Para cambiar solo en la terminal actual, reemplazar la ruta por la instalación real:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\CARPETA-DEL-JDK-25"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

## 3. Clonar y abrir el repositorio

Clonar usando la URL que comparta el equipo:

```powershell
git clone <URL_DEL_REPOSITORIO>
cd backend-complejos-deportivos
code .
```

Reemplazar `<URL_DEL_REPOSITORIO>` antes de ejecutar. Abrir la carpeta completa
`backend-complejos-deportivos` en VS Code, porque las configuraciones de ejecución
usan esa carpeta como raíz. Esperar a que las extensiones importen los proyectos Maven.

## 4. Preparar los archivos .env

En una copia nueva, desde la raíz:

```powershell
Copy-Item .env.example .env
Copy-Item msvc-users/.env.example msvc-users/.env
Copy-Item msvc-spaces/.env.example msvc-spaces/.env
Copy-Item msvc-bookings/.env.example msvc-bookings/.env
Copy-Item msvc-gateway/.env.example msvc-gateway/.env
```

Hacer esto solo para archivos que todavía no existan: no sobrescribir credenciales
locales ya configuradas. Los `.env.example` son plantillas; los `.env` reales no
se comparten por Git.

En el `.env` de la raíz, completar:

| Variable | Qué configurar |
| --- | --- |
| `KEYCLOAK_PORT` | Dejar `8180`, salvo conflicto de puerto. |
| `KEYCLOAK_ADMIN_USERNAME` | Dejar `admin` o elegir el administrador inicial. |
| `KEYCLOAK_ADMIN_PASSWORD` | Elegir la contraseña inicial de la consola de Keycloak. |
| `KEYCLOAK_DB_PASSWORD` | Elegir la contraseña para la conexión de Keycloak a su base. |
| `APP_DB_PORT` | Dejar `5433`, separado del PostgreSQL que pueda estar instalado en Windows. |
| `APP_DB_ADMIN_PASSWORD` | Elegir la contraseña del administrador PostgreSQL `app_admin`. |
| `USERS_DB_PASSWORD` | Elegir la contraseña del usuario PostgreSQL `users`. |
| `SPACES_DB_PASSWORD` | Elegir la contraseña del usuario PostgreSQL `spaces`. |
| `BOOKINGS_DB_PASSWORD` | Elegir la contraseña del usuario PostgreSQL `bookings`. |

Cada integrante puede elegir sus propias contraseñas locales. Para evitar diferencias
de interpretación entre Docker Compose y Java properties, usar claves largas de letras
y números en este entorno local, sin comillas ni espacios. No usar el usuario de
administración de PostgreSQL para las conexiones de los microservicios.

En los `.env` de cada servicio, conservar los valores de las plantillas y completar:

| Servicio | DB_USERNAME | DB_URL | DB_PASSWORD debe coincidir con |
| --- | --- | --- | --- |
| users | `users` | `jdbc:postgresql://localhost:5433/users_db` | `USERS_DB_PASSWORD` de la raíz |
| spaces | `spaces` | `jdbc:postgresql://localhost:5433/spaces_db` | `SPACES_DB_PASSWORD` de la raíz |
| bookings | `bookings` | `jdbc:postgresql://localhost:5433/bookings_db` | `BOOKINGS_DB_PASSWORD` de la raíz |

El `.env` del gateway ya incluye las direcciones locales de los tres servicios y no
necesita credenciales PostgreSQL. Si se cambia `APP_DB_PORT`, actualizar también
el puerto de las tres `DB_URL`.

Completar ademas `GATEWAY_INTERNAL_TOKEN` con la misma clave aleatoria larga en
`msvc-gateway/.env` y `msvc-users/.env`. Protege el canal interno: no es la contrasena
de un usuario y no debe enviarse desde Postman. Los demas servicios no la necesitan
para su estado actual. Para aplicar la configuracion a un Keycloak ya existente,
instalar [PowerShell 7](https://learn.microsoft.com/powershell/scripting/install/installing-powershell-on-windows)
y ejecutar `pwsh -File infra/keycloak/configurar-registro.ps1` despues de iniciar Docker.

## 5. Levantar la infraestructura

Desde la raíz, con Docker Desktop abierto:

```powershell
docker compose config --quiet
docker compose up -d
docker compose ps
```

El primer comando valida la configuración sin imprimir las contraseñas. La primera
ejecución puede tardar por la descarga de imágenes. Se levantan:

| Servicio Docker | Función | Acceso desde Windows |
| --- | --- | --- |
| `app-db` | PostgreSQL con users_db, spaces_db y bookings_db | `localhost:5433` |
| `keycloak-db` | PostgreSQL exclusivo de Keycloak | Solo red interna de Docker |
| `keycloak` | Servidor de identidades | `http://localhost:8180` |

Esperar a que ambas bases estén `healthy`. Keycloak puede figurar `Up` antes de
terminar de arrancar. Para revisar su inicio:

```powershell
docker compose logs --tail 100 keycloak
docker compose logs --tail 100 app-db
```

Las bases y usuarios se crean automáticamente al inicializar un volumen vacío.
Para comprobarlo:

```powershell
docker compose exec app-db psql -U app_admin -d postgres -c "\l"
```

Deben aparecer `users_db`, `spaces_db` y `bookings_db`, además de las bases internas
de PostgreSQL. Las tablas de negocio se crean al iniciar los microservicios que
tengan entidades `@Entity`, mediante `spring.jpa.hibernate.ddl-auto=update`.
Por ahora es normal no tener tablas si aún no se implementaron entidades.

## 6. Probar Keycloak

Abrir [la consola de administración](http://localhost:8180/admin) e ingresar con
`KEYCLOAK_ADMIN_USERNAME` y `KEYCLOAK_ADMIN_PASSWORD` del `.env` raíz.
Seleccionar el realm `complejos-deportivos` y comprobar los roles `CLIENTE` y `ADMIN`.

El administrador inicial pertenece a `master`; no es un usuario cliente del sistema.
Cada integrante tiene sus propios datos locales y recibe el mismo realm inicial.
Los cambios posteriores en la consola no se sincronizan entre computadoras, y la
importación al inicio omite un realm existente. El registro publico otorga CLIENTE;
`postman-local` permite registrarse e iniciar sesion con Authorization Code + PKCE.
Seguir [Registrar cliente](docs/registrar-cliente.md) para obtener el token y completar
el perfil pasando por el gateway. El correo y la contrasena permanecen en Keycloak.

## 7. Ejecutar los microservicios

| Aplicación | Puerto |
| --- | --- |
| Gateway | 8080 |
| Users | 8081 |
| Spaces | 8082 |
| Bookings | 8083 |

### Opción recomendada: Spring Boot Dashboard

1. Abrir la carpeta raíz del repositorio en VS Code y mantener Docker funcionando.
2. Ejecutar `Java: Configure Java Runtime` desde `Ctrl+Shift+P` y verificar que los
   proyectos usen JDK 25.
3. Abrir Spring Boot Dashboard y ejecutar Users, Spaces y Bookings con sus botones
   de inicio. Seleccionar sus configuraciones de `launch.json` si se solicita.
4. Comprobar que cada consola muestre `Started ...Application` sin errores.

El archivo `.vscode/launch.json` compartido configura para esos tres servicios su
carpeta de trabajo, su `.env` y `-Duser.timezone=UTC`. Conservar esos valores para
evitar errores de conexión y zona horaria. Si Dashboard no usa la configuración,
seleccionarla en **Ejecutar y depurar** y ejecutarla desde allí.

El gateway tambien tiene una configuracion en el `launch.json` compartido y puede
iniciarse desde Dashboard.

### Alternativa: terminal y Maven Wrapper

Abrir una terminal por aplicación. Desde la raíz, por ejemplo para users:

```powershell
cd msvc-users
.\mvnw.cmd -version
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.jvmArguments=-Duser.timezone=UTC"
```

Maven debe informar Java 25. En las otras terminales, repetir desde la raíz usando
`cd msvc-spaces`, `cd msvc-bookings` o `cd msvc-gateway`. No ejecutar varios servicios
en la misma terminal ni iniciar dos veces una aplicación en el mismo puerto.
La primera ejecución descarga dependencias y necesita conexión a Internet.

Los servicios leen su `.env` desde la carpeta de ejecución; no hace falta activar
un perfil adicional. Las variables de entorno ya definidas en la terminal tienen
prioridad sobre esos archivos.

## 8. Comprobar que funcionan

Con los servicios de negocio iniciados, abrir:

- [Users health](http://localhost:8081/actuator/health).
- [Spaces health](http://localhost:8082/actuator/health).
- [Bookings health](http://localhost:8083/actuator/health).

La respuesta esperada es `{"status":"UP"}`. Revisar también en los logs que la URL
JDBC corresponda a `localhost:5433` y a la base de cada servicio.

El gateway valida los tokens de Keycloak. `POST /api/users/clientes` requiere rol
CLIENTE sin ADMIN y permite completar el perfil. Otras rutas quedan denegadas hasta
implementar su politica. El POST directo al puerto 8081 devuelve 403: el registro
debe pasar por el gateway en el puerto 8080. Un 404 en la raiz puede ser normal;
no indica un fallo de PostgreSQL.

## 9. Detener y volver a trabajar

Detener las aplicaciones desde Dashboard o con `Ctrl+C` en cada terminal.
Desde la raíz se puede detener la infraestructura con:

```powershell
docker compose stop
```

Para volver a levantarla, abrir Docker Desktop y ejecutar `docker compose up -d`.
Los volúmenes conservan los datos. `docker compose down` también los conserva;
**no usar `docker compose down -v`**, porque elimina los volúmenes y sus datos,
incluida la configuración de Keycloak.

Cambiar las contraseñas en el `.env` después del primer arranque no modifica las
que ya guarda PostgreSQL o Keycloak. Es necesario actualizar las cuentas existentes
y las configuraciones de conexión correspondientes.

## Problemas frecuentes

| Mensaje o situación | Qué revisar |
| --- | --- |
| `docker` no se reconoce | Instalación de Docker Desktop; abrir una terminal nueva. |
| No se puede conectar al motor Docker | Iniciar Docker Desktop y esperar al motor; revisar su diagnóstico de WSL si corresponde. |
| `release version 25 not supported` | Maven está usando otro JDK: comprobar `.\mvnw.cmd -version`, `JAVA_HOME` y el runtime del IDE. |
| `invalid value for parameter TimeZone` | Usar `-Duser.timezone=UTC` en Maven o `vmArgs` en `launch.json`. |
| Error de contraseña PostgreSQL | Comparar la clave del `.env` raíz con `DB_PASSWORD` del servicio; recordar que un volumen existente conserva la clave inicial. |
| Conexión rechazada | Revisar `docker compose ps`, el estado `healthy`, `localhost:5433` y el puerto publicado. |
| Puerto ocupado | Detener otra ejecución del mismo servicio o ajustar el puerto y los destinos correspondientes. |
| No encuentra DB_USERNAME o DB_PASSWORD | Completar el `.env` del servicio y ejecutarlo desde su carpeta. |

Antes de un commit, revisar `git status`: compartir `.env.example`, nunca `.env`
con credenciales. No hace falta un `pom.xml` raíz: cada servicio se ejecuta desde
su carpeta. Las reglas de ramas y revisión están en el [README](README.md).

## Documentacion interactiva

Iniciar gateway y msvc-users y abrir http://localhost:8080/swagger-ui.html. Seleccionar Users. Para probar operaciones, pulsar Authorize y pegar el access token obtenido desde Keycloak/Postman, sin el prefijo Bearer. Ver docs/registrar-cliente.md para el registro y el inicio de sesion. Los contratos de Spaces y Bookings se incorporaran cuando se implementen.
