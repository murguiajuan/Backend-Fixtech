# Reporte de pruebas y cierre técnico — FIXTECH

**Proyecto:** FIXTECH — Plataforma web de soporte y mantenimiento tecnológico
**Componente:** Backend REST
**Fecha de cierre:** 25 de octubre de 2026
**Responsable:** Equipo FIXTECH

## 1. Alcance

Este reporte documenta la verificación del backend correspondiente a los requerimientos
funcionales **RF-01 a RF-56** y a los requerimientos no funcionales aplicables al cierre
técnico. Incluye pruebas automatizadas, cobertura, documentación OpenAPI, prueba de carga,
análisis de seguridad, copias de seguridad y despliegue con contenedores.

El frontend, las pruebas multinavegador, las pruebas de usabilidad y el monitoreo de
disponibilidad no forman parte de este repositorio y se registran como pendientes
cuando corresponda.

## 2. Entorno de ejecución

| Elemento | Versión / detalle |
|---|---|
| Sistema operativo | Ubuntu 24.04, kernel 6.17, 12 vCPU, 7.1 GiB RAM |
| Java | OpenJDK 21.0.12.1 |
| Maven | 3.10.0 (wrapper incluido) |
| Spring Boot | 4.1.1 |
| Base de datos | MariaDB 10.4.32 de XAMPP |
| Cliente MySQL | 8.0.45 |
| Documentación API | springdoc-openapi 3.2.0 |
| Cobertura | JaCoCo 0.8.12 |
| Carga | Apache JMeter 5.6.3 |
| Seguridad | OWASP ZAP 2.16.1 |
| Navegador / API cliente | `curl`; Swagger UI verificado por HTTP |

## 3. Resumen ejecutivo

| Verificación | Resultado | Estado |
|---|---|---|
| Pruebas automatizadas | 337 pruebas, 0 fallos, 0 errores | Cumple |
| Cobertura de servicios | 94.00 % de líneas; 76.84 % de ramas | Cumple |
| Cobertura global | 87.08 % de líneas; 70.97 % de ramas | Informativo |
| Umbral JaCoCo (servicios ≥ 70 %) | Superado | Cumple |
| Documentación OpenAPI | 79 rutas, esquema JWT `bearerAuth` | Cumple |
| Swagger en desarrollo | UI y JSON responden 200 | Cumple |
| Swagger en producción | Responde 404 | Cumple |
| Carga RNF-02 | p95 = 103 ms con 100 usuarios; 0 errores | Cumple |
| OWASP ZAP baseline | 0 altas, 0 medias, 0 bajas | Cumple |
| Copia de seguridad y restauración | Respaldo válido y restauración comprobada | Cumple |
| Docker Compose | Configuración válida; imagen no construida por permisos del daemon | Parcial |
| SonarQube | Configuración lista; sin servidor disponible | Pendiente |
| Git | Repositorio inicializado en `main` con commit inicial | Cumple |

## 4. Pruebas automatizadas

### 4.1 Ejecución

```bash
./mvnw clean verify
```

Resultado:

```text
Tests run: 337, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
All coverage checks have been met.
```

- 40 clases de prueba.
- Tiempo total del build: 29.4 s.
- Reportes: `target/surefire-reports/`.
- Cobertura HTML: `target/site/jacoco/index.html`.
- Cobertura XML para SonarQube: `target/site/jacoco/jacoco.xml`.

### 4.2 Cobertura

| Ámbito | Líneas cubiertas | Líneas totales | Cobertura de líneas | Cobertura de ramas |
|---|---:|---:|---:|---:|
| Paquete `com.fixtech.service` | 2 805 | 2 984 | **94.00 %** | 76.84 % |
| Proyecto completo | 3 491 | 4 009 | **87.08 %** | 70.97 % |

El `pom.xml` falla el build si la cobertura de líneas del paquete de servicios baja del
70 %, conforme a RNF-22.

### 4.3 Cobertura funcional

Los 337 casos automatizados cubren, entre otros:

- Registro, login, refresco de token, bloqueo por intentos fallidos y recuperación.
- Autorización por rol y por propiedad del recurso.
- Solicitudes, imágenes, ofertas, contraofertas y aceptación transaccional.
- Ejecución del servicio, PIN, avances, constancias y ajustes de precio.
- Comisiones con `BigDecimal`, redondeo `HALF_UP`, pagos y bloqueo por deuda.
- Reseñas, reclamos, notificaciones y auditoría.
- Parámetros del sistema, catálogos y reportes XLSX.
- Validación de archivos JPG/PNG, tamaño, MIME y contenido real.
- Tareas programadas de vencimiento, conformidad y deuda.

Además, el cierre del Punto 8 ejecutó una prueba extremo a extremo con **180/180
comprobaciones correctas** sobre los flujos principales de cliente, técnico y administrador.

## 5. Documentación de la API (RNF-05)

Con el perfil `dev`:

| Recurso | Resultado |
|---|---|
| `GET /v3/api-docs` | 200; 67 585 bytes |
| `GET /swagger-ui.html` | 302 hacia `/swagger-ui/index.html` |
| `GET /swagger-ui/index.html` | 200 |
| Título | FIXTECH API |
| Versión | 1.0.0 |
| Rutas documentadas | 79 |
| Seguridad | `bearerAuth` (HTTP Bearer JWT) |
| Etiquetas de dominio | 19, en español |
| Render en navegador | Chromium headless, sin errores de carga |

La configuración está en `src/main/java/com/fixtech/config/OpenApiConfig.java`. Un
`OpenApiCustomizer` traduce las etiquetas técnicas de los controladores a nombres de
dominio en español. El botón **Authorize** de Swagger UI permite enviar el token emitido
por `/api/auth/login`.

Con el perfil `prod`, `application-prod.properties` desactiva springdoc:

```text
GET /v3/api-docs     -> 404
GET /swagger-ui.html -> 404
GET /actuator/health -> 200, sin detalles internos
```

Evidencia: `docs/calidad/evidencias/openapi-resumen.txt`.

## 6. Prueba de carga (RNF-02)

**Herramienta:** Apache JMeter 5.6.3
**Plan:** `qa/jmeter/fixtech-catalogo.jmx`
**Objetivo:** `GET /api/catalogos/distritos`, endpoint público con acceso a base de datos.
**Configuración:** 100 usuarios concurrentes, rampa de 10 s, duración 60 s.

| Métrica | Valor |
|---|---:|
| Muestras | 139 200 |
| Peticiones exitosas | 139 200 |
| Errores | 0 |
| Código HTTP | 200 en el 100 % |
| Rendimiento | 2 314.8 peticiones/s |
| Media | 39.34 ms |
| Mediana | 36 ms |
| p90 | 86 ms |
| **p95** | **103 ms** |
| p99 | 136 ms |
| Máximo | 320 ms |
| Muestras ≤ 3 000 ms | 100.00 % |

**Criterio:** el 95 % de las operaciones debe responder en 3 segundos o menos con hasta
100 usuarios concurrentes. **Cumple**, con un margen amplio: el p95 fue de 103 ms.

También se ejecutó una prueba de humo con 1 usuario durante 10 s: 1 738 peticiones,
0 errores, media de 5.2 ms.

**Limitación:** la prueba se concentra en un endpoint de catálogo. No reemplaza una
prueba de carga de los flujos transaccionales completos (login, publicación de solicitud,
oferta, aceptación y cierre), que requiere un conjunto de datos y usuarios de prueba.

Evidencia: `docs/calidad/evidencias/jmeter-carga-100-resumen.txt`.

## 7. Seguridad (RNF-13 a RNF-18, RNF-20)

### 7.1 OWASP ZAP baseline

Se ejecutó un escaneo baseline sobre `http://127.0.0.1:18081/api/catalogos/distritos`
con ZAP 2.16.1.

| Riesgo | Alertas |
|---|---:|
| Alto | 0 |
| Medio | 0 |
| Bajo | 0 |
| Informativo | 1 (`User Agent Fuzzer`) |

La única alerta informativa corresponde a la variación de la respuesta según el
`User-Agent`; no representa una vulnerabilidad explotable en este contexto.

**Limitación:** el escaneo se hizo sin autenticación y sobre la superficie pública.
Los endpoints autenticados requieren un escaneo adicional con un token válido.

Evidencia: `docs/calidad/evidencias/zap-baseline-20261025.html`.

### 7.2 Controles verificados

- Contraseñas almacenadas con BCrypt; nunca en texto plano.
- JWT de acceso corto y token de refresco.
- Autorización por rol y por propiedad del recurso.
- Bloqueo temporal tras intentos fallidos.
- Consultas parametrizadas mediante JPA.
- Validación de MIME, extensión, tamaño y contenido real de imágenes.
- Nombres de archivo con UUID y almacenamiento privado.
- Auditoría de acciones administrativas y críticas.
- Swagger deshabilitado en producción.
- Secretos fuera de Git: `.env` y `application-dev.properties` están excluidos; se
  incluye `application-dev.properties.example` y `.env.example` como plantillas.

Cabeceras observadas en las respuestas:

```text
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: no-referrer
Permissions-Policy: geolocation=(), microphone=(), camera=()
Content-Security-Policy: default-src 'self'; base-uri 'self'; frame-ancestors 'none';
  object-src 'none'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline';
  img-src 'self' data:; font-src 'self' data:; connect-src 'self'; form-action 'self'
```

HSTS se emite solo sobre HTTPS; las pruebas locales se hicieron por HTTP, por lo que su
ausencia es la esperada. La política CSP usa `'unsafe-inline'` para permitir el
funcionamiento de Swagger UI; en un despliegue con frontend propio conviene restringirla.

Evidencia: `docs/calidad/evidencias/cabeceras-seguridad.txt`.

## 8. Copias de seguridad (RNF-12)

Se probó el ciclo completo con `scripts/backup.sh` y `scripts/restore.sh`:

1. Respaldo comprimido de `fixtech_db` con `mysqldump` y `gzip`.
2. Verificación de integridad con `gzip -t`.
3. Restauración en la base temporal `fixtech_restore_test`.
4. Comprobación de datos: 1 usuario, 10 distritos y 13 parámetros.
5. Eliminación de la base temporal.

El respaldo utiliza `--single-transaction`, `--quick`, `--triggers`, `--skip-events` y
`--no-tablespaces`. Se omitieron rutinas y eventos porque el servidor MariaDB de XAMPP
no los soporta con el cliente MySQL 8; el esquema de FIXTECH no depende de ellos.

El aviso `column statistics not supported by the server` es informativo y no afecta la
copia. Para producción se recomienda programar el respaldo diario y probar la
restauración periódicamente.

## 9. Docker y despliegue (RNF-25)

Archivos entregados:

- `Dockerfile` multietapa (compilación con JDK 21, ejecución con JRE 21, usuario no root).
- `compose.yaml` con MySQL 8.4, API, health checks, volúmenes persistentes y red interna.
- `.env.example` con todas las variables y valores de ejemplo.
- `.dockerignore` para excluir secretos, artefactos y documentación.
- `db/fixtech_db.sql` como esquema inicial, idéntico al SQL fuente.

Validación realizada:

```bash
MYSQL_ROOT_PASSWORD=test_root \
MYSQL_PASSWORD=test_app \
FIXTECH_JWT_SECRET=test_secret_1234567890 \
docker-compose -f compose.yaml config -q
```

Resultado: **configuración válida**.

**Limitación:** el daemon de Docker no es accesible para el usuario actual
(`permission denied while trying to connect to the Docker API at unix:///var/run/docker.sock`).
Por eso no se pudo construir la imagen ni levantar los contenedores. La configuración
quedó validada estáticamente y debe ejecutarse en un equipo con Docker habilitado:

```bash
cp .env.example .env
# Editar .env y cambiar todos los valores CAMBIE_...
docker compose up --build -d
```

## 10. Versionado (RNF-24)

El proyecto se inicializó como repositorio Git en la rama `main`, con un commit inicial
que excluye secretos, `target/`, `.idea/`, respaldos, logs y resultados crudos de QA.

Archivos ignorados relevantes:

```text
.env
src/main/resources/application-dev.properties
uploads/
backups/
qa/jmeter/resultados/
qa/zap/
```

Las plantillas `.env.example` y `application-dev.properties.example` sí se versionan.

## 11. Requerimientos no funcionales

| RNF | Verificación | Resultado |
|---|---|---|
| RNF-01 | `BigDecimal`, `HALF_UP`, 2 decimales; pruebas unitarias | Cumple |
| RNF-02 | JMeter, 100 usuarios, p95 ≤ 3 s | Cumple (p95 103 ms) |
| RNF-03 | Compresión/redimensión de imágenes y paginación | Implementado y probado |
| RNF-04 | Navegadores | Fuera del backend; pendiente frontend |
| RNF-05 | OpenAPI/Swagger | Cumple |
| RNF-06 a RNF-09 | Interfaz, usabilidad, accesibilidad | Fuera del backend; pendiente frontend |
| RNF-10 | Disponibilidad 99 % y monitoreo | Requiere operación y monitoreo |
| RNF-11 | Transacciones ACID | Implementado y probado |
| RNF-12 | Backup y restauración | Cumple |
| RNF-13 | BCrypt | Cumple |
| RNF-14 | JWT y HTTPS | JWT cumple; HTTPS debe terminarse en el proxy |
| RNF-15 | Autorización por rol y propiedad | Cumple |
| RNF-16 | Validación de archivos | Cumple |
| RNF-17 | OWASP Top 10 / ZAP | Cumple en superficie pública |
| RNF-18 | Límite de intentos fallidos | Cumple |
| RNF-19 | Ley 29733 y consentimiento | Implementado en registro; revisar política legal |
| RNF-20 | Auditoría | Cumple |
| RNF-21 | Arquitectura por capas | Cumple |
| RNF-22 | Cobertura de servicios ≥ 70 % | Cumple (94.00 %) |
| RNF-23 | SonarQube sin vulnerabilidades críticas | Pendiente: no hay servidor SonarQube |
| RNF-24 | Git y flujo por ramas | Repositorio inicial en `main` |
| RNF-25 | Docker, perfiles y variables de entorno | Configuración validada; falta ejecución real |
| RNF-26 | Parámetros configurables sin recompilar | Cumple |

## 12. Limitaciones y acciones pendientes

1. **SonarQube:** no hay servidor disponible. La configuración está en
   `sonar-project.properties`; al conectar un servidor ejecutar:
   `./mvnw clean verify` y `sonar-scanner -Dsonar.host.url=... -Dsonar.login=...`.
2. **Docker:** el daemon no es accesible para el usuario actual. Validar
   `docker compose up --build -d` en un equipo con permisos y ejecutar un smoke test
   contra los contenedores.
3. **ZAP autenticado:** repetir el escaneo con un token de cliente, técnico y
   administrador para cubrir los endpoints privados.
4. **Carga transaccional:** ampliar el plan JMeter a los flujos completos con datos
   de prueba y usuarios autenticados.
5. **HTTPS:** desplegar detrás de un proxy inverso con TLS; HSTS ya está configurado
   para conexiones seguras.
6. **Contraseña inicial:** `db/fixtech_db.sql` contiene un administrador de desarrollo.
   Cambiar su contraseña inmediatamente después del primer despliegue.
7. **Frontend:** ejecutar las pruebas de RNF-04, RNF-06 a RNF-09 y RNF-19 en la
   interfaz web.

## 13. Evidencias

| Evidencia | Ubicación |
|---|---|
| Resultados de pruebas | `target/surefire-reports/` |
| Cobertura JaCoCo | `target/site/jacoco/index.html` |
| OpenAPI / Swagger | `docs/calidad/evidencias/openapi-resumen.txt` |
| Captura de Swagger UI | `docs/calidad/evidencias/swagger-ui.png` |
| Carga JMeter | `docs/calidad/evidencias/jmeter-carga-100-resumen.txt` |
| Seguridad ZAP | `docs/calidad/evidencias/zap-baseline-20261025.html` |
| Cabeceras de seguridad | `docs/calidad/evidencias/cabeceras-seguridad.txt` |
| Base limpia | `docs/calidad/evidencias/base-limpia.txt` |
| Esquema de base de datos | `db/fixtech_db.sql` |
| Configuración de calidad | `sonar-project.properties`, `pom.xml` |
| Planes de QA | `qa/jmeter/`, `scripts/qa/zap-baseline.sh` |

## 14. Conclusión

El backend FIXTECH queda funcionalmente completo (RF-01 a RF-56), con 337 pruebas
automatizadas sin fallos, cobertura de servicios del 94.00 %, API documentada, carga
verificada con 100 usuarios y sin alertas de severidad alta, media o baja en el análisis
ZAP de la superficie pública. La entrega incluye Docker, backups, perfiles y
documentación operativa.

Quedan como acciones externas la ejecución real de los contenedores, el análisis
SonarQube, el escaneo autenticado y las pruebas de interfaz. Ninguna de ellas bloquea
el cierre del backend, pero deben completarse antes de una puesta en producción.
