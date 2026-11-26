# Conexión de CoordinadITO con Supabase

## La idea en una analogía

Piensa en Supabase como una **bodega compartida** en otra ciudad y en cada PC como una **tienda** que necesita mercancía.

- **La bodega** es la base de datos en Supabase: una sola para las dos PCs.
- **La recepción** de la bodega es el *Session Pooler*: atiende por IPv4 y reparte pocas puertas entre muchos clientes.
- **La flotilla de camiones** de cada tienda es el pool de Payara: unas pocas conexiones abiertas que se reutilizan.
- **La llave** es la contraseña. Se guarda en la caja fuerte de cada tienda (el alias cifrado de Payara), nunca en el plano del proyecto (Git).
- **El código de la tienda** solo conoce el nombre del servicio de camiones: `jdbc/coordinadito`. No sabe en qué ciudad está la bodega.

Por eso el mismo código funciona en las dos PCs. Cada máquina configura su propio pool una vez, y los dos pools apuntan a la misma base.

## Cómo viaja una consulta

```
vista .xhtml → DiagnosticoControl (WAR, CDI)
             → DiagnosticoService (EJB, @Stateless)
             → EntityManager de la unidad CoordinadITOPU (persistence.xml)
             → recurso JNDI jdbc/coordinadito (Payara)
             → pool CoordinadITOPool (máx. 5 conexiones, SSL)
             → aws-0-us-west-2.pooler.supabase.com:5432 (Session Pooler)
             → base postgres del proyecto nwokbcaajmfvfebobyzv
```

## Archivos

| Archivo | Para qué |
|---|---|
| `herramientas/configurar-supabase.ps1` | Configura Payara en cada PC: driver, alias de la contraseña, pool y recurso JNDI. |
| `CoordinadITO-ejb/src/conf/persistence.xml` | Unidad de persistencia `CoordinadITOPU`. Solo nombra `jdbc/coordinadito`; no guarda secretos. |
| `CoordinadITO-ejb/src/java/modelo/bean/DiagnosticoService.java` | Primer EJB: prueba la conexión. Con él, el EAR ya despliega. |
| `CoordinadITO-ejb/src/java/modelo/bean/EstadoConexion.java` | Resultado de la prueba. |
| `CoordinadITO-war/src/java/.../control/DiagnosticoControl.java` | Bean de la vista de diagnóstico. |
| `CoordinadITO-war/web/vista/diagnostico.xhtml` | Página que muestra si conectó y qué tablas hay. Solo para desarrollo. |
| `bd/01-esquema-v1.sql` | Esquema del Sprint 1. |
| `bd/02-migracion-v2.sql` | Tablas y reglas del modelo de datos v2. |
| `bd/03-supabase-rls.sql` | Cierra la API pública de Supabase sobre estas tablas. |
| `bd/04-migracion-v3.sql` | Iteraciones 3 y 4: origen de los datos del alumno, días esperados por trámite y agenda de citas. |
| `bd/05-lectura-avisos.sql` | Qué avisos ya abrió cada alumno (el sobre con número del inicio). |
| `bd/06-sin-administracion.sql` | Retira el rol de administración (sus cuentas pasan a jefatura). Ver `docs/ROLES-SIN-ADMINISTRACION.md`. |
| `bd/95-datos-demo.sql` | Datos ficticios para una demostración (después de 04). |

## Pasos (una vez por PC; la base se prepara una sola vez)

### A. Preparar la base en Supabase (solo una vez)
1. En el panel de Supabase abre **SQL Editor**.
2. Pega y ejecuta, en este orden: `bd/01-esquema-v1.sql`, `bd/02-migracion-v2.sql`, `bd/03-supabase-rls.sql`, `bd/04-migracion-v3.sql`, `bd/05-lectura-avisos.sql`, `bd/06-sin-administracion.sql`.
   Si la base ya tenía 01–05, ejecuta solo `06` (no borra datos).
3. En **Table Editor** deben aparecer 22 tablas.
4. (Opcional, para una demo) Ejecuta `bd/95-datos-demo.sql`: carga personas ficticias, solicitudes en todos los estados,
   grupos, avisos, agenda y auditoría. Solo agrega datos, va en una transacción y se niega a correr dos veces.
   No lo ejecutes en una base con datos reales.

### B. Configurar Payara (en cada PC)
1. Cierra el despliegue en NetBeans, si estaba corriendo.
2. En PowerShell, desde la carpeta del proyecto:
   ```
   powershell -ExecutionPolicy Bypass -File herramientas\configurar-supabase.ps1
   ```
   - Si Payara no está en `C:\Users\<usuario>\Payara_Server`, agrega `-PayaraHome "D:\ruta\Payara_Server"`.
   - Te pide la contraseña del admin de Payara (Enter si no tiene) y la contraseña de la base de Supabase. Ninguna se muestra en pantalla ni queda guardada en el proyecto.
3. Al final debe decir `Command ping-connection-pool executed successfully`.

### C. Probar desde la aplicación
1. En NetBeans: **Clean and Build** del proyecto `CoordinadITO` y **Run** (despliega el EAR completo).
2. Abre `http://localhost:8080/CoordinadITO-war/vista/diagnostico.xhtml`.
3. Debe decir **Conectado a Supabase** y listar las 22 tablas.

## Decisiones y por qué

- **Session Pooler y no conexión directa.** La conexión directa de Supabase solo funciona por IPv6, y muchas redes domésticas y escolares no lo tienen. El Session Pooler funciona por IPv4 y acepta sentencias preparadas, que JDBC y JPA usan todo el tiempo. El *Transaction Pooler* (puerto 6543) no las acepta bien, así que no conviene con JPA.
- **El pool lo crea Payara y no el código.** El código pide `jdbc/coordinadito` y no sabe dónde está la base. Si mañana cambian la contraseña o la región, se vuelve a ejecutar el script y no se toca Java.
- **La contraseña va en un alias de Payara.** En `domain.xml` solo queda `${ALIAS=coordinadito-db}`; el valor real está cifrado en `domain-passwords`, que no está en el repositorio.
- **Máximo 5 conexiones por PC y mínimo 0.** El plan gratuito de Supabase limita las conexiones del pooler. Con dos PCs más el panel de Supabase, 5 por máquina deja margen. Con mínimo 0, Payara no deja conexiones abiertas mientras no se usan.
- **`sslMode=require`.** Supabase exige conexiones cifradas.
- **`stringType=unspecified`.** Permite que JPA guarde texto en las columnas `ENUM` de PostgreSQL (`ROLES`, `ESTADO_SOLICITUD`, `PRIORIDAD`…). Sin esta propiedad, cada INSERT de un enum falla.
- **Validación de conexión (`PostgresConnectionValidation`).** El pooler cierra conexiones que pasan mucho tiempo sin uso. Payara revisa cada conexión antes de entregarla y descarta las muertas.
- **RLS activado.** Supabase publica el esquema `public` por su API REST con una llave que viaja en el navegador. CoordinadITO no usa esa API, así que se cierra. El usuario `postgres` es el dueño de las tablas y RLS no le aplica, por eso la aplicación sigue leyendo y escribiendo.

## Cosas a tener en cuenta

- **Pausa por inactividad.** Un proyecto gratuito de Supabase se pausa tras una semana sin uso. El diagnóstico mostrará un error de conexión; se reactiva desde el panel.
- **Si cambias la contraseña en Supabase**, vuelve a ejecutar el script en las dos PCs.
- **Latencia.** La base está en `us-west-2` (Oregón); cada consulta tarda decenas de milisegundos. Conviene traer los datos en pocas consultas, no una por fila.
- **Antes de producción**, quita `vista/diagnostico.xhtml` o protégela, porque muestra datos del servidor.
- **No subas a Git** ningún archivo con la contraseña. El `.env.example` del repositorio anterior (`Borrador/system-back`) tenía la contraseña real de la base local.

## Si algo falla

| Error | Causa probable |
|---|---|
| `ClassNotFoundException: org.postgresql.ds.PGSimpleDataSource` | El driver no está en `domains/coordinadito/lib` o el dominio no se reinició. |
| `password authentication failed` | Contraseña equivocada en el alias. Vuelve a ejecutar el script. |
| `Tenant or user not found` | El usuario debe ser `postgres.nwokbcaajmfvfebobyzv` (con el sufijo del proyecto) o la región no coincide. |
| `Connection refused` o tiempo agotado | El proyecto de Supabase está pausado, o la red bloquea el puerto 5432. |
| `Max client connections reached` | Demasiados pools abiertos. Baja `-MaxConexiones` o reinicia uno de los Payara. |
| `NameNotFoundException: jdbc/coordinadito` | El script no terminó en esa PC. |

## Alternativa: base PostgreSQL local

Para trabajar sin internet o para una demo, la misma aplicación puede usar un PostgreSQL instalado en la PC:

```
powershell -ExecutionPolicy Bypass -File herramientas\configurar-local.ps1
```

Crea la base `coordinadito`, ejecuta 01–04 y 95 (con `-SinDemo` omite la demo), guarda la contraseña de `postgres`
como alias `coordinadito-db-local` y apunta el pool `CoordinadITOPool` a `localhost` sin volver a desplegar.
Para regresar a Supabase basta con ejecutar otra vez `herramientas\configurar-supabase.ps1`.

