/*
 * Comportamiento de plantilla-panel.xhtml que PrimeFaces no trae hecho.
 */
var CoordinadITO = CoordinadITO || {};

/**
 * Botón ☰ de la barra superior.
 *   Pantalla ancha: muestra u oculta la barra lateral (y lo recuerda en este navegador).
 *   Teléfono o tableta: abre el menú deslizable (p:sidebar «menuMovil»).
 */
CoordinadITO.menu = function (boton) {
    if (window.matchMedia('(min-width: 992px)').matches) {
        var shell = document.getElementById('shell');
        var colapsada = shell.classList.toggle('sidebar-colapsada');
        boton.setAttribute('aria-expanded', colapsada ? 'false' : 'true');
        try { localStorage.setItem('coordinadito.menuColapsado', colapsada ? '1' : '0'); } catch (e) { /* sin almacenamiento */ }
    } else {
        PF('menuMovil').show();
    }
};

/* Al cargar: respeta la preferencia guardada de la barra lateral. */
document.addEventListener('DOMContentLoaded', function () {
    try {
        if (localStorage.getItem('coordinadito.menuColapsado') === '1') {
            var shell = document.getElementById('shell');
            if (shell) { shell.classList.add('sidebar-colapsada'); }
        }
    } catch (e) { /* sin almacenamiento */ }
});

/* Baja con suavidad hasta una sección de la página (p. ej. los contadores del inicio). */
CoordinadITO.irA = function (id) {
    var destino = document.getElementById(id);
    if (destino) { destino.scrollIntoView({ behavior: 'smooth', block: 'start' }); }
};

/*
 * Borrador de un formulario (HU 1.2, criterio 3): lo que el alumno escribe se guarda en
 * sessionStorage y, si sale y regresa en la misma pestaña, vuelve a aparecer. Se usa
 * sessionStorage y no localStorage a propósito: en una PC compartida (centro de cómputo)
 * el borrador desaparece al cerrar la pestaña. Solo rellena campos vacíos.
 */
CoordinadITO.borrador = function (formId, clave) {
    var form = document.getElementById(formId);
    if (!form) { return; }
    var llave = 'coordinadito.borrador.' + clave;
    var datos = {};
    try { datos = JSON.parse(sessionStorage.getItem(llave) || '{}'); } catch (e) { datos = {}; }
    var campos = function () {
        return form.querySelectorAll('input[type=text]:not([readonly]), input[type=number], input[type=date], textarea, select');
    };
    var widgetDe = function (el) {
        if (!window.PrimeFaces || !PrimeFaces.widgets) { return null; }
        for (var n in PrimeFaces.widgets) {
            var w = PrimeFaces.widgets[n];
            if (w && w.jq && w.jq[0] && w.jq[0].contains(el) && typeof w.selectValue === 'function') { return w; }
        }
        return null;
    };
    Array.prototype.forEach.call(campos(), function (el) {
        var v = datos[el.name];
        if (!el.name || v === undefined || v === '' || el.value) { return; }
        if (el.tagName === 'SELECT') {
            var w = widgetDe(el);
            if (w) { w.selectValue(v); } else { el.value = v; }
        } else {
            el.value = v;
            el.dispatchEvent(new Event('input', { bubbles: false }));   // contador de caracteres
        }
    });
    var guardar = function () {
        var d = {};
        Array.prototype.forEach.call(campos(), function (el) { if (el.name && el.value) { d[el.name] = el.value; } });
        try { sessionStorage.setItem(llave, JSON.stringify(d)); } catch (e) { /* sin almacenamiento: no pasa nada */ }
    };
    form.addEventListener('input', guardar);
    form.addEventListener('change', guardar);
};

CoordinadITO.borrarBorrador = function (clave) {
    try { sessionStorage.removeItem('coordinadito.borrador.' + clave); } catch (e) { /* nada */ }
};
