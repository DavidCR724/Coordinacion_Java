# CoordinadITO · Correo y acceso con código

## La idea en una analogía

Piensa en la **caseta del estacionamiento** del Tec:

- Llegas y dices quién eres (tu correo institucional).
- La caseta no te pide una contraseña: te **manda una ficha a tu buzón** (el código de 6 dígitos). Solo quien puede abrir ese buzón la recibe, y eso demuestra que el correo es tuyo.
- La ficha **vale 10 minutos, sirve una sola vez** y, si alguien intenta adivinarla 5 veces, se invalida.
- Con la ficha entras por **la misma puerta** que con la cuenta institucional (OIDC): mismas reglas de padrón, primer acceso, cuenta desactivada y auditoría.

El **cartero** es una sola pieza (`CorreoService`): lo usa la caseta para mandar fichas y lo usa el sistema para mandar los avisos de cambio de estado (HU 5.1).

Y la **llave del cartero** (la contraseña de Gmail) nunca está escrita en el código ni en `domain.xml`: Payara la guarda cifrada como alias, igual que la contraseña de la base.

## Cómo se conecta (una vez por PC)

1. En tu cuenta de Google activa la **verificación en dos pasos** y crea una **contraseña de aplicación** en <https://myaccount.google.com/apppasswords> (nombre: CoordinadITO). Son 16 letras.
2. En PowerShell, desde la carpeta del proyecto:

   ```powershell
   powershell -ExecutionPolicy Bypass -File herramientas\configurar-correo.ps1
   ```

   Te pide tu Gmail y la contraseña de aplicación (no se muestra), **te manda un correo de prueba** a ti mismo y, solo si Google lo acepta, crea:
   - el alias cifrado `coordinadito-correo`;
   - la sesión de correo `mail/coordinadito` → `smtp.gmail.com:587` con STARTTLS.
3. Abre el login: aparece **«Enviarme un código»**. No hace falta volver a desplegar.

Para quitarlo: `configurar-correo.ps1 -Quitar` (el login vuelve a ocultar el acceso con código).

> **Límite honesto:** una cuenta personal de Gmail manda unos **500 correos al día**. Para la demo y el piloto alcanza de sobra. En producción convendría la cuenta institucional del Tec o un servicio de envío (SendGrid, Amazon SES…): solo cambia la sesión `mail/coordinadito`, no el código.

## Flujo del acceso con código

| Paso | Qué ve la persona | Qué hace el servidor |
|---|---|---|
| 1 | Escribe su correo y pulsa «Enviarme un código» | `CodigoAccesoService.solicitar`: valida el dominio, revisa límites, genera 6 dígitos con `SecureRandom`, guarda **solo la huella** (SHA-256 con sal) y manda el correo |
| — | «Si tu correo puede entrar a CoordinadITO, te enviamos un código» | La respuesta es **igual** tenga o no cuenta: el formulario no sirve para averiguar qué correos existen |
| 2 | Escribe el código (acepta «123 456») | `AccesoService.ingresarConCodigo` → `CodigoAccesoService.comprobar` (en su propia transacción, para que el intento cuente aunque falle) → `ingresar(...)`, el mismo camino que OIDC |
| 3 | Entra a su inicio (o a primer acceso, donde escribe su nombre) | Auditoría: `CODIGO_ACCESO`, `ACCESO` o `ACCESO_FALLIDO` con el motivo |

### Reglas de seguridad

| Regla | Valor | Por qué |
|---|---|---|
| Vigencia | 10 minutos | Suficiente para abrir el correo; corto para que una ficha olvidada no sirva después |
| Intentos por código | 5 | 5 intentos de 1 000 000 combinaciones: adivinar es prácticamente imposible |
| Solo vale el último código | — | Pedir otro invalida los anteriores |
| Reenvío | 1 por minuto y 5 por hora por correo | Evita llenar el buzón de alguien (o gastar el límite de Gmail) |
| Por red | 60 cada 15 min por IP | Holgado a propósito: en el Tec muchas PCs salen a internet con la misma IP |
| A quién se le manda | Cuentas activas y estudiantes (número de control) | A un correo sin cuenta de personal no se le manda nada, pero la pantalla responde igual |
| Comparación | En tiempo constante (`MessageDigest.isEqual`) | No filtra por tiempos de respuesta cuántos dígitos acertó |
| Si el correo no sale | «No pudimos enviar el correo…» y **no** queda el código guardado | La transacción se deshace |

## Piezas nuevas o cambiadas

| Pieza | Capa | Qué hace |
|---|---|---|
| `bd/07-codigo-acceso.sql` | Base | Tabla `CODIGO_ACCESO` (correo, huella, sal, vence, usado, intentos, IP) |
| `MigracionesService` | EJB | **Pone la base al día al desplegar** (ver abajo) |
| `CorreoService` | EJB | Único punto de envío: `enviar(para, asunto, texto)` y el temporizador de avisos. Autenticación explícita con el usuario y la contraseña del alias |
| `CodigoAccesoService` | EJB | Genera, envía y comprueba códigos |
| `CodigoAcceso` + `CodigoAccesoRepository` | Modelo / datos | Entidad y consultas (último código, enviados por correo y por IP) |
| `AccesoService.ingresarConCodigo` | EJB | Comprueba el código y entra por `ingresar` |
| `Accion.CODIGO_ACCESO` | Modelo | Nuevo valor en la auditoría (la columna es texto: no requiere migración) |
| `SesionControl` + `login.xhtml` | WAR | Dos pasos: correo → código (`inputmode=numeric`, `autocomplete=one-time-code`, para que el celular sugiera el código) |
| `herramientas/configurar-correo.ps1` | Herramienta | Prueba la contraseña y crea el alias y la sesión de correo |

## Migraciones automáticas al desplegar

**Problema que resolvía:** tu base local tenía hasta la 04 y el código ya usaba la 05 y la 06; Payara mostraba «no existe la relación aviso_lectura» y varias pantallas salían vacías aunque **todas ya leían de la base**.

**Solución:** `MigracionesService` (`@Singleton @Startup`) revisa al arrancar si cada migración posterior a la 04 ya está, por una **marca** (una tabla o columna que esa migración crea), y si falta la ejecuta:

| Script | Marca |
|---|---|
| 05-lectura-avisos | tabla `aviso_lectura` |
| 06-sin-administracion | columna `carga_padron.id_responsable` |
| 07-codigo-acceso | tabla `codigo_acceso` |

- Los scripts son **los mismos archivos de `bd/`**: `CoordinadITO-ejb/build.xml` (`-post-compile`) los copia dentro del JAR del EJB. Una sola copia del SQL.
- Si la base no tiene ni el esquema 01–04, no toca nada (eso sigue siendo trabajo de `configurar-local.ps1`).
- Cada script trae su `BEGIN/COMMIT`: si uno falla no queda a medias, y el servicio se detiene ahí.
- Lo que hizo se ve en **vista/diagnostico** («Migraciones al desplegar»). Se apaga con la propiedad del sistema `coordinadito.bd.migrar=false`.

Para agregar una migración futura: el script en `bd/` y una línea con su marca en `MigracionesService.MIGRACIONES`.

## Cómo se probó

| Recorrido | Resultado |
|---|---|
| Base con solo 01–04 (como la de tu PC): al desplegar se aplicaron 05, 06 y 07; al reiniciar aparecen «al día» y no se repiten | correcto |
| Acceso con código contra un SMTP falso que **exige usuario y contraseña** (como Gmail): dominio, aviso genérico, correo con huella y no código, incorrecto, correcto con espacio, auditoría, no reutilizable, sin enumeración, primer acceso, reenvío limitado, 5 intentos, vencido, jefatura, «usar otro correo», avisos del temporizador | 21 / 21 |
| SMTP caído: avisa y no guarda el código | 2 / 2 |
| La contraseña llegó desde `${ALIAS=…}` dentro de la sesión de correo | AUTH aceptado |
