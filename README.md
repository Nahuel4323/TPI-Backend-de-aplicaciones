# TPI Backend — Gestión de Complejos Deportivos

Trabajo Práctico Integrador de la materia **Backend de Aplicaciones** de Ingeniería en Sistemas de Información — UTN Facultad Regional Córdoba.

## Descripción del proyecto

El proyecto consiste en desarrollar un sistema backend basado en una arquitectura de microservicios para gestionar complejos deportivos, sus canchas y las reservas realizadas por los clientes.

El sistema permitirá administrar la disponibilidad de las canchas, registrar reservas, gestionar pagos de señas, aplicar bonificaciones y realizar reprogramaciones ante condiciones climáticas desfavorables o tareas de mantenimiento.

## Arquitectura

El sistema estará compuesto por tres microservicios principales:

### 1. msvc-users
Responsable de gestionar los perfiles de clientes y administradores, así como su vinculación con Keycloak.

### 2. msvc-spaces
Responsable de gestionar los complejos deportivos, canchas, deportes, horarios, tarifas y ocupaciones.

También controlará la disponibilidad de los turnos, evitando reservas superpuestas.

### 3. msvc-bookings
Responsable de gestionar el ciclo de vida de las reservas:

- Solicitud y retención de turnos.
- Registro de pagos y confirmación.
- Cancelaciones.
- Evaluación de condiciones climáticas.
- Reprogramaciones.
- Bonificaciones por reservas frecuentes.

### Componentes adicionales

- **API Gateway:** punto de entrada al backend, enrutamiento y control de acceso.
- **Keycloak:** autenticación, autorización y administración de identidades.
- **Common Library:** librería JAR reutilizable entre microservicios.
- **API climática externa:** consulta de pronósticos para evaluar reservas.

### Diagrama general de arquitectura

<img src="docs/img/arquitectura-general.png" alt="Arquitectura general del sistema" width="700">

## Tecnologías

- Java 25
- Spring Boot
- Spring Security
- Spring Cloud Gateway
- Spring Data JPA / Hibernate
- Spring RestClient
- Keycloak
- PostgreSQL
- Maven
- Docker y Docker Compose
- Swagger / OpenAPI
- Git y GitHub

## Estructura del repositorio

Se utiliza un único repositorio para todos los microservicios (monorepo). Cada proyecto tiene su propio `pom.xml`; no hay un POM agregador en la raíz.

```text
backend-complejos-deportivos/
├── .github/
├── .vscode/
│   ├── launch.json
│   └── settings.json
├── common-lib/
├── msvc-gateway/
├── msvc-users/
├── msvc-spaces/
├── msvc-bookings/
├── infra/
│   ├── keycloak/
│   │   ├── README.md
│   │   ├── configurar-registro.ps1
│   │   └── realms/
│   │       └── complejos-deportivos-realm.json
│   └── postgres/
│       ├── README.md
│       └── init/
│           └── 01-create-databases.sh
├── docs/
│   ├── postman/
│   │   └── tpi-endpoints-implementados.postman_collection.json
│   ├── registrar-cliente.md
│   └── img/
│       ├── arquitectura-general.png
│       ├── c4-contexto-c1.png
│       └── c4-contenedores-c2.png
├── scripts/
│   └── verificar-registro.py
├── .env.example
├── .gitattributes
├── .gitignore
├── docker-compose.yml
├── requisitos.md
└── README.md
```

Los cuatro servicios incluyen `pom.xml`, Maven Wrapper, `src/main`, `src/test` y `.env.example`. `common-lib` es una biblioteca JAR con su propio POM. Los `.env` locales y las carpetas generadas `target/` se omiten del árbol y no se versionan.

Docker Compose incluye Keycloak con su propia base PostgreSQL y otro PostgreSQL para los microservicios, ambos con volumenes persistentes. Ver las [instrucciones de Keycloak local](infra/keycloak/README.md) y [PostgreSQL de los microservicios](infra/postgres/README.md).

## Configuración local

Para preparar una computadora nueva, seguir [requisitos.md](requisitos.md): incluye instalaciones, credenciales, ejecución con Spring Boot Dashboard o Maven y solución de problemas frecuentes.

Se requiere JDK 25. Los puertos predeterminados son: gateway `8080`, users `8081`, spaces `8082` y bookings `8083`.

Docker Compose publica PostgreSQL de los microservicios en `localhost:5433` y crea `users_db`, `spaces_db` y `bookings_db` al inicializar su volumen por primera vez. No requiere instalar PostgreSQL ni pgAdmin. Cada servicio carga su propio archivo `.env` desde su carpeta de ejecución. Los archivos `.env` están excluidos de Git; las plantillas `.env.example` se comparten en el repositorio.

Para una nueva copia del repositorio, ejecutar desde la carpeta de cada servicio:

```powershell
Copy-Item .env.example .env
```

Preparar tambien el `.env` raiz siguiendo la [guia de PostgreSQL](infra/postgres/README.md). Los usuarios de los servicios son `users`, `spaces` y `bookings`. Copiar `USERS_DB_PASSWORD`, `SPACES_DB_PASSWORD` y `BOOKINGS_DB_PASSWORD` del `.env` raiz al `DB_PASSWORD` del servicio correspondiente. El gateway no necesita credenciales de base de datos. Con Docker Desktop iniciado, levantar la infraestructura desde la raíz:

```powershell
docker compose config --quiet
docker compose up -d
docker compose ps
```

PostgreSQL se publica en `localhost:5433` y la consola de Keycloak en [localhost:8180/admin](http://localhost:8180/admin). Luego iniciar cada servicio desde su carpeta, en terminales separadas y con JDK 25 activo:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.jvmArguments=-Duser.timezone=UTC"
```

Como alternativa, instalar Spring Boot Extension Pack y usar Spring Boot Dashboard en VS Code. El archivo `.vscode/launch.json` configura los cuatro servicios con su propia carpeta de trabajo, su `.env` y `-Duser.timezone=UTC`. Abrir el repositorio completo como carpeta raíz. La autenticación y los permisos por rol se centralizan en el Gateway; users no requiere una clave interna compartida.

La carga se configura mediante `spring.config.import=optional:file:./.env[.properties]`. El archivo se interpreta como Java properties: usar `CLAVE=valor`, sin `export` ni comillas alrededor de los valores. Para valores que contengan barras invertidas, escaparlas como `\\`. Cada servicio puede usar credenciales diferentes. Las variables de entorno de la terminal tienen prioridad sobre el `.env`. En el IDE, establecer la carpeta de cada servicio como directorio de trabajo.

La variable opcional `DB_URL` permite reemplazar la URL JDBC completa de cada servicio; `SERVER_PORT` permite cambiar su puerto. Para desarrollo local, Hibernate usa `ddl-auto=update`: crea y actualiza tablas a partir de las clases `@Entity` al iniciar cada microservicio. Los cambios complejos de esquema pueden requerir SQL o migraciones; revisar esta opcion antes de desplegar fuera del entorno local.

El gateway reenvía `/api/users/**`, `/api/spaces/**` y `/api/bookings/**` conservando la ruta completa. Sus destinos se pueden cambiar con `USERS_SERVICE_URL`, `SPACES_SERVICE_URL` y `BOOKINGS_SERVICE_URL`. Si cambia el puerto de un servicio, también debe cambiarse su destino en el gateway.

`POST /api/users/clientes` es público: recibe email, password y datos personales, crea una cuenta CLIENTE en Keycloak y guarda el perfil vinculado en users. Antes de iniciar users, ejecutar `pwsh -File infra/keycloak/configurar-users-registration.ps1` para configurar su cuenta de servicio y guardar el secreto en su `.env` local. La contraseña no se persiste en users ni se devuelve. El Gateway valida JWT y roles para las operaciones protegidas; el registro no requiere token. Ambas aplicaciones escuchan en loopback. Ver la [guia completa de Registrar cliente](docs/registrar-cliente.md), el [contrato OpenAPI](http://localhost:8080/docs/users) y la [coleccion Postman](docs/postman/tpi-endpoints-implementados.postman_collection.json).

## Metodología de trabajo

El equipo utilizará una metodología basada en **Kanban**, organizando las historias de usuario, tareas, responsables y estados de desarrollo mediante Notion.

### Estrategia de ramas

Se utilizará una estrategia de trabajo basada en **ramas individuales permanentes**, donde cada integrante tendrá su propia rama de desarrollo.

- `main`: rama principal que contendrá las funcionalidades terminadas, revisadas e integradas.
- `dev/nombre-integrante`: rama personal donde cada integrante desarrollará las funcionalidades que tenga asignadas.

Las ramas personales se mantendrán durante todo el proyecto, evitando la necesidad de crear y eliminar ramas por cada funcionalidad.

### Reglas de trabajo en las ramas

Cada integrante trabajará sobre **una única funcionalidad a la vez** en su rama personal.

1. Antes de comenzar una funcionalidad, deberá sincronizar su rama con la última versión de `main`.
2. Desarrollará la funcionalidad asignada, realizando los commits necesarios.
3. Una vez finalizada y probada, creará un Pull Request para integrar los cambios en `main`.
4. La funcionalidad deberá ser revisada y aprobada por otro integrante antes de realizar el merge.
5. **No comenzará otra funcionalidad hasta que la anterior haya sido integrada en `main`.**
6. Después de cada integración, actualizará su rama personal con los últimos cambios de `main` antes de comenzar una nueva tarea.

Las ramas personales serán permanentes durante todo el proyecto y no se eliminarán después de cada integración.

### Organización de las funcionalidades

Las historias de usuario estarán registradas en el tablero Kanban de Notion.

Cada funcionalidad tendrá un responsable asignado, quien se encargará de su implementación, pruebas y documentación.

Los estados utilizados serán:

- **Pendiente:** funcionalidad todavía no iniciada.
- **Lista para empezar:** funcionalidad disponible para comenzar.
- **En curso:** funcionalidad en desarrollo.
- **En revisión:** funcionalidad terminada, pendiente de revisión e integración.
- **Terminada:** funcionalidad integrada y verificada.

## Documentación

Guías disponibles: [requisitos y ejecución](requisitos.md), [Keycloak local](infra/keycloak/README.md) y [PostgreSQL de los microservicios](infra/postgres/README.md).

La documentación funcional y técnica del proyecto incluirá:

- Modelo de dominio y reglas de negocio.
- Diagramas de bases de datos.
- Diagramas de arquitectura C4 (C1 y C2).
- Documentación de APIs mediante Swagger/OpenAPI.
- Instrucciones de instalación y ejecución.

### Diagramas C4

#### C1 — Diagrama de Contexto
<img src="docs/img/c4-contexto-c1.png" alt="Arquitectura general del sistema" width="700">


#### C2 — Diagrama de Contenedores
<img src="docs/img/c4-contenedores-c2.png" alt="Arquitectura general del sistema" width="700">

## Estado del proyecto

**Entorno local preparado para comenzar el desarrollo de funcionalidades.**

Se crearon los tres microservicios de negocio, el gateway y la biblioteca compartida con Java 25. Docker Compose levanta Keycloak y dos instancias PostgreSQL con volúmenes persistentes; las bases y usuarios de negocio se inicializan automáticamente. Cada servicio usa su propio `.env`, y Hibernate crea o actualiza tablas a partir de las entidades al arrancar durante el desarrollo local.

Se comprobó el arranque de los contenedores y la conexión de los servicios de negocio en el entorno del equipo. La configuración de VS Code permite ejecutar users, spaces y bookings con sus credenciales y zona horaria UTC.

Registrar cliente incluye alta pública de cuenta en Keycloak y perfil local, validaciones, control de duplicados y eliminación compensatoria de la cuenta nueva si falla el perfil. [Registrar administrador](docs/registrar-administrador.md) permite crear cuenta ADMIN y perfil administrativo, con permisos en el Gateway y bootstrap inicial explícito e idempotente; la asignación a complejo pertenece a US-07. Un fallo de compensación o cierre abrupto requiere reconciliación según las guías. El login posterior mantiene Authorization Code + PKCE. Quedan pendientes las demás US y la integración de la biblioteca compartida cuando haya código común. Los roles de acceso son `CLIENTE` y `ADMIN`; el discriminador JPA `ADMINISTRADOR` identifica el subtipo local, no un rol adicional de Keycloak.

## Organización

**Universidad:** Universidad Tecnológica Nacional — Facultad Regional Córdoba

**Carrera:** Ingeniería en Sistemas de Información

**Materia:** Backend de Aplicaciones

**Proyecto:** Trabajo Práctico Integrador

Documentación interactiva central: [Swagger UI](http://localhost:8080/swagger-ui.html). Iniciar gateway y users. Seleccionar Users; Registrar cliente se ejecuta sin Authorize. Para operaciones protegidas, usar un access token obtenido en Keycloak/Postman. Spaces y Bookings se agregarán cuando publiquen sus contratos.
