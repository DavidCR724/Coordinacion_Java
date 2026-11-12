"""
Genera el tema PrimeFaces "coordinadito" a partir del tema Saga que trae PrimeFaces.

Uso (desde la raíz del repositorio):
    python herramientas/generar-tema.py CoordinadITO-war/web/WEB-INF/lib/primefaces-16.0.2.jar

Escribe CoordinadITO-war/web/resources/primefaces-coordinadito/theme.css.
PrimeFaces lo encuentra porque web.xml declara primefaces.THEME = coordinadito
(busca el recurso theme.css en la biblioteca "primefaces-coordinadito").
Volver a ejecutarlo al actualizar PrimeFaces.
"""
import re
import sys
import zipfile
from pathlib import Path

# Colores de Saga -> paleta del ITO (la misma que usaba estilos.css).
REEMPLAZOS = [
    (r"#2196f3", "#2b6cb0"),                                   # primario  -> azul ITO
    (r"rgb\(12\.7384615385,\s*136\.9384615385,\s*235\.6615384615\)", "#245a93"),  # hover
    (r"rgb\(11\.3230769231,\s*121\.7230769231,\s*209\.4769230769\)", "#1e4b7a"),  # activo
    (r"rgb\(166\.2,\s*213,\s*250\.2\)", "#b9d0e8"),            # anillo de foco
    (r"#e3f2fd", "#edf3f9"),                                   # fondo resaltado
    (r"rgba\(33,150,243,", "rgba(43,108,176,"),                # transparencias del primario
    (r"#495057", "#12233f"),                                   # texto -> azul marino
    (r"#6c757d", "#637083"),                                   # texto secundario
    (r"#dee2e6", "#e2dfd8"),                                   # bordes
    (r"#e9ecef", "#ece8e1"),                                   # superficies hover
    (r"#f8f9fa", "#f9f8f5"),                                   # fondo suave
    (r"#ced4da", "#d6d2c9"),                                   # borde de campos
]
FUENTE_SAGA = re.compile(r"-apple-system,\s*BlinkMacSystemFont,\s*\"?Segoe UI\"?,\s*Roboto")
FUENTE_ITO = '"Geist", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto'


def main(jar):
    with zipfile.ZipFile(jar) as z:
        css = z.read("META-INF/resources/primefaces-saga-blue/theme.css").decode("utf-8-sig")
    for patron, nuevo in REEMPLAZOS:
        css = re.sub(patron, nuevo, css, flags=re.IGNORECASE)
    css = FUENTE_SAGA.sub(FUENTE_ITO, css)
    destino = Path("CoordinadITO-war/web/resources/primefaces-coordinadito/theme.css")
    destino.parent.mkdir(parents=True, exist_ok=True)
    cabecera = ("/* Tema CoordinadITO: Saga de " + Path(jar).name +
                " recoloreado con la paleta del ITO. Generado por herramientas/generar-tema.py; no editar a mano. */\n")
    destino.write_text(cabecera + css, encoding="utf-8")
    print("Escrito", destino, len(css), "bytes")


if __name__ == "__main__":
    main(sys.argv[1])
