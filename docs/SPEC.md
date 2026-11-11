# SPEC — Inexplicable

Especificación funcional. Fuente de verdad de lo que hace la app. Las preguntas abiertas están al final (§10); se resuelven sobre la marcha.
Versión inicial: 2026-11-11 (a partir de la descripción del usuario).

## 1. Qué es
Herramienta de apoyo para un efecto de magia también llamado **«Inexplicable»**. La app finge ser un **lector/predictor de barajas**:
el espectador la apunta a la baraja real y la app «detecta» lo ocurrido y emite las predicciones. En realidad **no detecta nada**:
el mago la guía en secreto (§6). Es un **apoyo a la revelación**; sustituye a las predicciones escritas a mano.

## 2. El efecto (resumen)
La baraja de los espectadores se mezcla de muchas formas (incluida boca arriba/boca abajo). Al final se revelan las predicciones, una por fase.
**Fases iniciales por defecto** (todas editables; la configuración permite **añadir, quitar y reordenar fases y revelaciones**):
1. «**24** cartas cara abajo».
2. «De esas, **13** son rojas».
3. «De las negras, todas son pares».
4. «Excepto el **[carta] de [palo]**» (la excepción).

La **carta extra/excepción se fija de antemano en la configuración**. Los números y textos de cada fase también.

## 3. Alcance y plataformas
- Android + iOS, código único KMP + Compose Multiplatform (ver `ARQUITECTURA.md`). iOS se compila en GitHub Actions.
- Todo en local, sin red ni cuentas. Idioma: el del dispositivo; principal castellano, **multiidioma** desde el diseño.
- Orientación vertical (por defecto).

## 4. Modos de revelación
La app ofrece varios modos; el usuario/mago elige en la configuración secreta.

### 4.1 Modo «Lector» (sin cámara) — **primero a implementar**
- Se apoya el móvil sobre la baraja. La pantalla muestra un «escáner» vistoso (animaciones, texto que se escribe, efectos).
- Avanza **fase a fase** según el guiado oculto (§6). Cada fase muestra su predicción (texto, y opcionalmente voz, §7.3).
- Debe parecer detección: estados «analizando…», progreso, «ping» y revelación.

### 4.2 Modo «RA» (realidad aumentada con cámara) — **objetivo principal, tras el modo Lector**
- Ventana de cámara; el espectador apunta a la baraja. La app detecta el **dorso** de la baraja como marcador.
- Sobre ella se dibujan **objetos 3D y animaciones**: símbolo que «escanea» la baraja y bocadillo/panel que revela
  («Hay 33 cartas boca abajo»…), anclado a la baraja.
- Requisito de robustez: tras detectar el dorso, **seguir el mazo** aunque cambie el ángulo o se pierda parcialmente el dorso
  (no perder la referencia; mantener la posición estimada si el marcador desaparece un instante).
- **Dorsos configurables:** poder precargar varios dorsos (Bicycle rojo/azul, etc.) y elegir cuál se usa (§7.4).
- Ver viabilidad y alternativas en §8.

## 5. Lore / presentación (a definir con el usuario)
La parte visible debe ser **autocontenida y escueta** para ocultar el secreto. Hay que inventar el contexto de la app: qué es el «lector».
**Decidido:** estilo **tecnológico tipo Jarvis (Iron Man)**: HUD, retículas, anillos y trazos luminosos, datos que se «escriben»,
voz de asistente sobria. Pantalla de inicio mínima y mensajes de estado técnicos («Sincronizando con el mazo…», «Analizando orientación de las cartas…»).
Estas frases serán editables y traducibles.

## 6. Control del avance (decisión inicial)
Por ahora **no es oculto**: se apunta el móvil a la baraja, la app hace un efecto de «conexión» con ella y, cuando está lista, activa en el HUD un
botón **«Analizar»/«Leer»**. Al pulsarlo se reproduce la animación y la revelación de la **fase actual**; se pulsa de nuevo para la siguiente.
Más adelante se podrán añadir gestos u otros controles discretos. El reset oculto (esquina superior izquierda) sigue vigente (§7.1).

## 7. Configuración secreta
### 7.1 Acceso
- **Pulsación larga** en una esquina (por definir cuál; **superior derecha** (decidido)) abre el panel de configuración como pop-up.
- Botón oculto **superior izquierdo**: **reset** → deja el efecto preparado de nuevo desde la fase 1.

### 7.2 Contenido del efecto
- Texto/valor de cada predicción por fase (nº de cartas boca abajo, nº de rojas, regla de negras pares, etc.).
- Qué carta es la **excepción / «extra»** (cuarta predicción).
- **Tiempos** entre fases y retardos (desde que aparece hasta que se lee, etc.).

### 7.3 Voz
- Voz sintética del dispositivo (TTS): decir «estoy analizando…», efecto «ping», y leer la predicción.
- Configurable: activar/desactivar, **voz**, **cadencia/velocidad**, tono. Mezcla texto + voz configurable.
- Idioma de la voz: el del móvil por defecto.

### 7.4 RA y dorsos
- Lista de dorsos precargados + posibilidad de **añadir uno propio** (foto del dorso) si es viable.
- Elegir estilo del símbolo/animación 3D y del bocadillo.

### 7.5 Persistencia
- Todo se guarda en local (SharedPreferences / UserDefaults). Idioma = el del sistema; textos por idioma.
- La configuración crece «sobre la marcha»: este apartado se mantiene al día.

## 8. Viabilidad de la RA (hipótesis a **verificar con una prueba de concepto**)
Hay una prueba de concepto antigua del usuario con un framework de RA y dorsos personalizados; hay que comprobar si sigue vigente.
Opciones técnicas (no comprobadas aún; confirmar versiones y limitaciones actuales antes de decidir):
| Opción | Idea | Ojo |
|---|---|---|
| **ARCore Augmented Images** (Android) + **ARKit image tracking** (iOS) | Nativo de cada sistema; detección y seguimiento continuo de una imagen de referencia | El dorso de baraja suele tener patrón repetitivo y poco contraste: puede puntuar mal como imagen de referencia. Dos implementaciones nativas (`expect`/`actual`). |
| **Vuforia** u otro SDK comercial multiplataforma | Marcadores de imagen con buen tracking | Licencia, tamaño, integración con Compose Multiplatform |
| **OpenCV** (features ORB/AKAZE + homografía) + render propio | Control total, funciona con cualquier dorso | Más trabajo; seguimiento 3D más difícil |
| **Visión + SLAM** (anclar a la posición estimada cuando se pierde el dorso) | Mantiene el ancla aunque se pierda el marcador | Combinar con una de las anteriores |
Nota de arquitectura: la RA necesita código **nativo por plataforma** (cámara, ARCore/ARKit, render 3D) integrado vía `expect`/`actual`
o vistas embebidas en Compose. Hay que decidir el motor 3D (SceneView/Filament en Android, RealityKit/SceneKit en iOS) en una fase de investigación.
**Recomendación (a validar en RA-0):** para ser agnóstico al dorso, **no usar el dorso como patrón** sino **detectar el mazo como objeto**
(detector 2D de objeto/ML, o segmentación) y **anclarlo con el seguimiento del mundo de ARCore/ARKit** (SLAM): el ancla 3D se fija sobre
el plano de la mesa (o en el aire si está en la mano) y se mantiene estable aunque cambie el ángulo y no se vea el dorso, que es justo la
preocupación de perder la referencia. Como alternativa/refuerzo, selección de dorsos precargados con *image tracking*.
Riesgo: un mazo sostenido en la mano no da plano; se comparará en el prototipo.
**Entregable de la fase RA-0:** prototipo mínimo que detecta un dorso y dibuja algo anclado, en Android, con medición de robustez (ángulos, luz, oclusión por la mano).

## 9. Hoja de ruta (propuesta)
1. **Fase 0 — Inicialización** (hecha): renombrado a Inexplicable, SPEC, compilación.
2. **Fase 1 — Modo Lector (hecha en lo básico, 2026-11-11: HUD Jarvis, máquina de fases con tests, textos es/en; faltan textos editables):** pantalla inicial, escáner animado, máquina de estados de fases, textos de predicciones, textos multiidioma, tests de la lógica.
3. **Fase 2 — Configuración secreta:** pulsación larga, panel, persistencia, reset oculto, tiempos.
4. **Fase 3 — Voz (TTS):** `expect`/`actual` para Android/iOS, ajustes de voz.
5. **Fase 4 — RA-0:** prueba de concepto de detección/seguimiento del dorso (Android primero).
6. **Fase 5 — RA completa:** animaciones 3D, bocadillo, dorsos configurables, iOS.
7. **Fase 6 — Pulido:** icono, ajustes, instrucciones, `.ipa` en Actions (siempre preguntando antes de lanzarlo).

## 10. Preguntas abiertas
1. «Par» en la fase 3: ¿cómo cuentan As, J, Q, K? (relevante solo si la app valida algo; ahora es texto configurable).
2. Mecanismo definitivo de dorsos: ¿detección del mazo como objeto (agnóstica) o lista de dorsos? Se decide tras RA-0.
3. ~~Esquina de la configuración~~: superior derecha (decidido).
4. ~~Idiomas iniciales~~: castellano e inglés (decidido).
5. ¿Modo Lector y RA se eligen en la configuración o hay botón visible?
6. Pruebas de RA: ¿se dispone de un móvil Android físico? (ver emulador y webcam en la conversación).
