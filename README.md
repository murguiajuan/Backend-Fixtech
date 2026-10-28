# FIXTECH Backend

API REST de la plataforma **FIXTECH** para gestionar solicitudes, ofertas, ejecución de servicios tecnológicos, comisiones, reseñas, reclamos y administración en Piura.

- **Java 21**
- **Spring Boot 4.1.1**
- **Spring Security + JWT + BCrypt**
- **Spring Data JPA + MySQL 8**
- **Apache POI** para reportes XLSX
- **springdoc-openapi** para documentación Swagger
- **JUnit 5 + Mockito + JaCoCo**

## Arquitectura

El backend sigue una arquitectura por capas:

```text
Controller -> Service -> Repository -> MySQL
     |            |
   DTOs       Reglas de negocio
```

Las entidades nunca se exponen directamente: todas las respuestas usan DTOs. Las operaciones críticas son transaccionales y los archivos se guardan fuera de la carpeta pública, con acceso mediante endpoints autorizados.

## Inicio rápido local

### 1. Requisitos

- Java 21
- MySQL 8 o MariaDB de XAMPP
- Maven Wrapper incluido (`./mvnw`)

### 2. Crear la base de datos

El esquema de referencia está en `db/fixtech_db.sql`. **Este script elimina y recrea las tablas**, así que úselo solo en un entorno de desarrollo:

```bash
mysql -h 127.0.0.1 -u root < db/fixtech_db.sql
```

El script crea la base `fixtech_db`, los catálogos, los parámetros y el administrador inicial. Cambie la contraseña del administrador antes de publicar el sistema.

Si ya tiene una base creada antes de la recuperación OTP, aplique la migración una sola vez:

```bash
mysql -h 127.0.0.1 -u root fixtech_db < db/migraciones/001_recuperacion_otp.sql
```

### 3. Configurar el secreto JWT

Opción A, archivo local ignorado por Git:

```bash
cp src/main/resources/application-dev.properties.example \
   src/main/resources/application-dev.properties
# Edite el archivo y coloque un secreto aleatorio
```

Opción B, variable de entorno:

```bash
export FIXTECH_JWT_SECRET="$(openssl rand -hex 32)"
```

### 4. Ejecutar

```bash
./mvnw spring-boot:run
```

La API queda disponible en `http://localhost:8082`. El puerto se puede cambiar con la variable `SERVER_PORT`.

Si MySQL usa credenciales distintas:

```bash
export FIXTECH_DB_URL='jdbc:mysql://localhost:3306/fixtech_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Lima&characterEncoding=UTF-8'
export FIXTECH_DB_USER='root'
export FIXTECH_DB_PASSWORD='su_clave'
```

### 5. Swagger

Con el perfil `dev`:

- Swagger UI: `http://localhost:8082/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8082/v3/api-docs`

En el perfil `prod` la documentación interactiva queda deshabilitada.

## Variables de entorno

| Variable | Uso | Valor por defecto |
|---|---|---|
| `FIXTECH_DB_URL` | URL JDBC de MySQL | `jdbc:mysql://localhost:3306/fixtech_db...` |
| `FIXTECH_DB_USER` | Usuario de base de datos | `root` |
| `FIXTECH_DB_PASSWORD` | Contraseña de base de datos | vacío |
| `FIXTECH_JWT_SECRET` | Secreto HMAC para JWT | vacío; obligatorio fuera de desarrollo |
| `FIXTECH_CORS_ORIGINS` | Orígenes permitidos para la SPA | `http://localhost:4200,http://127.0.0.1:4200` |
| `SERVER_PORT` | Puerto HTTP de la API | `8082` |
| `FIXTECH_ARCHIVOS_DIR` | Directorio privado de archivos | `uploads` |
| `FIXTECH_OPENAPI_HABILITADO` | Habilita Swagger | `true` |
| `FIXTECH_CORREO_HABILITADO` | Habilita envío de correos y el flujo OTP | `false` |
| `FIXTECH_CORREO_REMITENTE` | Remitente de los correos | `no-reply@fixtech.pe` |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | Servidor SMTP | vacío; puerto `587` |
| `FIXTECH_OTP_CODIGO_MINUTOS` | Vigencia del código OTP | `10` |
| `FIXTECH_OTP_TICKET_MINUTOS` | Vigencia del ticket temporal | `10` |
| `FIXTECH_OTP_MAX_INTENTOS` | Intentos por código | `5` |
| `FIXTECH_OTP_REENVIO_SEGUNDOS` | Espera mínima entre reenvíos | `60` |
| `FIXTECH_OTP_MAX_SOLICITUDES_CORREO` | Solicitudes por correo en la ventana | `3` |
| `FIXTECH_OTP_MAX_SOLICITUDES_IP` | Solicitudes por IP en la ventana | `10` |
| `FIXTECH_OTP_VENTANA_MINUTOS` | Ventana de rate limiting | `15` |
| `FIXTECH_OTP_LIMPIEZA_MS` | Frecuencia de limpieza de códigos vencidos | `3600000` |
| `SPRING_PROFILES_ACTIVE` | Perfil `dev` o `prod` | `dev` |

Nunca versione `.env`, `application-dev.properties`, contraseñas, tokens ni secretos JWT.

## Docker

El proyecto incluye una imagen multietapa y MySQL 8.4 con el esquema inicial.

```bash
cp .env.example .env
# Edite .env y reemplace todos los valores CAMBIE_...
docker compose up --build -d
```

Si su instalación usa Docker Compose v1:

```bash
docker-compose up --build -d
```

Servicios:

- API: `http://localhost:8080`
- MySQL: `localhost:3307` (evita el puerto 3306 de XAMPP)
- Swagger: `http://localhost:8080/swagger-ui.html` con perfil `dev`

En Docker el contenedor escucha internamente en el puerto `8080`; el puerto del host se define con `APP_PORT`. El puerto `8082` corresponde a la ejecución local con Maven.

Para producción:

```bash
SPRING_PROFILES_ACTIVE=prod docker compose up --build -d
```

El esquema SQL solo se importa al crear el volumen de MySQL por primera vez. Para recrear la base desde cero:

```bash
docker compose down -v
docker compose up --build -d
```

Los datos se conservan en los volúmenes `mysql_data` y `uploads_data`.

## Pruebas y cobertura

```bash
./mvnw clean verify
```

El build ejecuta las pruebas y falla si la cobertura de líneas del paquete `com.fixtech.service` baja del **70 %**. El reporte queda en:

```text
target/site/jacoco/index.html
```

Resultados de referencia del cierre técnico:

- 337 pruebas automatizadas, 0 fallos.
- Cobertura de servicios: **94.00 %** de líneas y 76.84 % de ramas.
- Cobertura global: **87.08 %** de líneas y 70.97 % de ramas.
- E2E del Punto 8: **180/180** comprobaciones.
- El informe completo está en [`docs/calidad/REPORTE_PRUEBAS.md`](docs/calidad/REPORTE_PRUEBAS.md).

## Calidad estática y seguridad

### SonarQube

Con un servidor SonarQube disponible:

```bash
./mvnw clean verify
sonar-scanner \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.login="$SONAR_TOKEN"
```

La configuración está en `sonar-project.properties` y consume el reporte JaCoCo.

### JMeter

El plan de carga está en `qa/jmeter/fixtech-catalogo.jmx` y prueba un endpoint público con acceso a base de datos:

```bash
jmeter -n \
  -t qa/jmeter/fixtech-catalogo.jmx \
  -Jhost=127.0.0.1 \
  -Jport=8082 \
  -Jthreads=100 \
  -Jduration=60 \
  -l qa/jmeter/resultados/resultados.jtl \
  -e -o qa/jmeter/resultados/html
```

Resultado de referencia: 139 200 peticiones con 100 usuarios concurrentes durante 60 s,
0 errores, p95 de **103 ms** y p99 de 136 ms. El detalle está en
[`docs/calidad/evidencias/jmeter-carga-100-resumen.txt`](docs/calidad/evidencias/jmeter-carga-100-resumen.txt).

### OWASP ZAP

```bash
./scripts/qa/zap-baseline.sh http://127.0.0.1:8082
```

El script usa ZAP local o el contenedor oficial si está disponible. Los reportes se guardan en `qa/zap/`, que está excluido de Git. El baseline del cierre reportó **0 alertas altas, 0 medias y 0 bajas**; la evidencia está en [`docs/calidad/evidencias/zap-baseline-20261025.html`](docs/calidad/evidencias/zap-baseline-20261025.html).

## Copias de seguridad

Crear un respaldo comprimido:

```bash
FIXTECH_DB_USER=root \
FIXTECH_DB_PASSWORD='su_clave' \
./scripts/backup.sh
```

Restaurar, con confirmación explícita:

```bash
CONFIRMAR_RESTAURACION=si \
FIXTECH_DB_USER=root \
FIXTECH_DB_PASSWORD='su_clave' \
./scripts/restore.sh backups/fixtech_db_20261025_180000.sql.gz
```

Las copias se guardan en `backups/`, excluido de Git. Programe una tarea diaria en el servidor y pruebe la restauración periódicamente.

## Endpoints principales

La lista completa y los esquemas de petición/respuesta están en Swagger.

| Módulo | Rutas |
|---|---|
| Autenticación | `POST /api/auth/register/cliente`, `/register/tecnico`, `/login`, `/refresh`; `GET /api/auth/me` (perfil autenticado) |
| Recuperación OTP | `POST /api/auth/recuperacion/solicitar`, `/verificar`, `/restablecer`, `/acceder` |
| Perfil | `PUT /api/perfil/tecnico`, `POST /api/perfil/foto`; `GET /api/tecnicos/{id}/foto` (foto autorizada) |
| Catálogos públicos | `GET /api/catalogos/tipos-equipo`, `/tipos-servicio`, `/especialidades`, `/distritos` |
| Solicitudes | `/api/solicitudes`, `/api/solicitudes/mias`, `/api/solicitudes/disponibles` |
| Ofertas y contraofertas | `/api/solicitudes/{id}/ofertas`, `/api/ofertas/{id}/...` |
| Servicios | `/api/servicios/mios`, `/api/servicios/{id}/...` |
| Comisiones | `/api/comisiones/mi-saldo`, `/api/comisiones/pagos` |
| Reseñas y reclamos | `/api/servicios/{id}/resenas`, `/api/servicios/{id}/reclamos` |
| Notificaciones | `/api/notificaciones` |
| Administración | `/api/admin/tecnicos`, `/usuarios`, `/parametros`, `/catalogos`, `/dashboard`, `/reportes`, `/auditoria` |

## Recuperación de contraseña

El flujo usa un código OTP de seis dígitos enviado al correo registrado:

1. `POST /api/auth/recuperacion/solicitar` responde `202` con un mensaje genérico, exista o no el correo.
2. `POST /api/auth/recuperacion/verificar` valida el código y devuelve un ticket temporal de un solo uso.
3. Con ese ticket, `POST /api/auth/recuperacion/restablecer` cambia la contraseña o `POST /api/auth/recuperacion/acceder` entrega tokens JWT para iniciar sesión sin cambiarla.

El código vence a los 10 minutos, admite 5 intentos, se puede reenviar cada 60 segundos y tiene un máximo de 3 solicitudes por correo cada 15 minutos. Requiere `FIXTECH_CORREO_HABILITADO=true` y SMTP configurado; de lo contrario responde `503`. Los códigos se guardan con BCrypt y los tickets con SHA-256, nunca en texto plano. El job `RecuperacionLimpiezaJob` elimina los registros antiguos.

## Seguridad

- Contraseñas con BCrypt, nunca en texto plano.
- JWT de acceso corto y token de refresco.
- Autorización por rol y por propiedad del recurso.
- Bloqueo temporal tras intentos fallidos.
- Validación de MIME, extensión, tamaño y contenido real de imágenes.
- Nombres de archivo generados con UUID y almacenamiento privado.
- Consultas parametrizadas mediante JPA.
- Cabeceras de seguridad: CSP, HSTS, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy` y `Permissions-Policy`.
- Auditoría de acciones críticas con usuario, entidad, detalle, fecha e IP.
- Swagger se deshabilita en el perfil de producción.

## Estructura

```text
src/main/java/com/fixtech
  config/       Seguridad, OpenAPI, propiedades y utilidades
  controller/   Endpoints REST
  dto/          Requests y responses inmutables
  entity/       Modelo JPA
  enums/        Estados y reglas del dominio
  exception/    Manejo global de errores
  mapper/       Conversión entidad -> DTO
  repository/   Consultas Spring Data JPA
  security/     JWT y filtros
  service/      Reglas de negocio y transacciones
src/main/resources
  application.properties
  application-prod.properties
db/             Esquema de referencia fixtech_db.sql
scripts/        Backup, restauración y ZAP
qa/             Planes JMeter y ZAP
docs/           Reporte de pruebas y evidencias
```

## Trabajo futuro

Quedan fuera del alcance del MVP: pasarela de pagos con retención automática, geolocalización en tiempo real, chat por WebSocket, notificaciones push/WhatsApp, aplicación móvil nativa, comprobantes electrónicos y recomendación inteligente de técnicos.
