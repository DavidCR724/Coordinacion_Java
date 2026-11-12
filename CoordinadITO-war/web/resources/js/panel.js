/*
 * Comportamiento de plantilla-panel.xhtml que PrimeFaces no trae hecho.
 * (El menú móvil ya es un p:sidebar y no necesita código aquí.)
 */
var CoordinadITO = CoordinadITO || {};

/** Contrae o expande la barra lateral de escritorio. */
CoordinadITO.alternarBarraLateral = function (boton) {
    var shell = document.getElementById('shell');
    if (!shell) { return; }
    var colapsada = shell.classList.toggle('sidebar-colapsada');
    boton.setAttribute('aria-expanded', colapsada ? 'false' : 'true');
};

/** Selector "Ver como": abre la pantalla de inicio con el rol elegido (?rol=...). */
CoordinadITO.verComo = function (contexto, rol) {
    window.location.href = contexto + '/vista/inicio.xhtml?rol=' + encodeURIComponent(rol);
};
