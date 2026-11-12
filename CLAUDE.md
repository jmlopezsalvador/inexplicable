# Inexplicable — app móvil Android + iOS

App móvil para **Android y iOS** con un único código en Kotlin Multiplatform + Compose Multiplatform.
El usuario habla español: responde y comenta el código en español.

> **Pendiente:** icono (`data/icon.png`, más adelante). La pantalla de ejemplo (contador) sigue en `App.kt` hasta sustituirla.
> Repo: https://github.com/jmlopezsalvador/inexplicable.git (rama `main`).

## Mapa de carpetas
| Carpeta | Para qué sirve |
|---|---|
| `shared/` | Módulo KMP con TODO el código: lógica pura, interfaz Compose, ajustes. `commonMain` (común), `androidMain` / `iosMain` (solo lo del sistema, `expect`/`actual`), `commonTest` (tests). |
| `app/` | Solo la envoltura Android (`MainActivity`, manifiesto, recursos, icono). |
| `iosApp/` | Envoltura iOS en SwiftUI + `project.yml` (XcodeGen genera el `.xcodeproj` en CI; no se versiona). |
| `.github/workflows/ios.yml` | Compila en un Mac de GitHub y publica un `.ipa` sin firmar. Solo `workflow_dispatch`. |
| `docs/` | `ARQUITECTURA.md` (base técnica, fuente de verdad de versiones y reglas) y `SPEC.md` (especificación funcional, se crea al inicializar). |
| `data/` | Material de referencia que aporta el usuario (capturas, icono, recursos). Se puede leer. |
| `data/android_build/` | APK descargados/generados. No se versionan. |
| `data/ios_build/` | `.ipa` descargado de Actions. No se versiona. |
| `ignore/` | **Ignorar por completo** (ver reglas). |

## Reglas para Claude
- **Ignorar por completo la carpeta `ignore/`**: no leerla, no listarla, no buscar dentro, no usarla como referencia. Excluirla
  también de búsquedas (Glob/Grep) y del control de versiones. Es el cajón del usuario para material suelto.
- La base técnica es **`docs/ARQUITECTURA.md`** (versiones, estructura, Gradle, CI de iOS, reglas de código). Si algo choca con él, consultar al usuario.
- **Antes de lanzar el workflow de iOS en GitHub Actions, preguntar siempre al usuario** (cuesta cuota de macOS; se acumulan mejoras).
  Por defecto se lanza **sin tests** (el usuario prueba en su iPhone); solo con tests si él lo pide.
- No inventar funcionalidad: la fuente de verdad es `docs/SPEC.md` y lo que pida el usuario. Ante la duda, preguntar.
- **No hacer commit ni push sin que el usuario lo pida.** No commitear si la compilación falla.
- Datos solo en local (SharedPreferences / UserDefaults y ficheros internos). Sin red ni cuentas salvo que el SPEC lo diga.
- Mantener este `CLAUDE.md` al día: al cambiar de forma relevante la app, el flujo o las decisiones, actualizarlo en la misma tarea.

## Qué hace la app
Paquete `com.inexplicable`. Herramienta de apoyo para un efecto de magia llamado «Inexplicable»: la app finge ser un lector/predictor
de la baraja real y revela 4 predicciones por fases (cartas boca abajo y rojas, negras pares, fallo aparente y excepción), guiada en
secreto por el mago. Modos: «Lector» (pantalla animada, primero) y «RA» (cámara que detecta/sigue el dorso de la baraja con objetos 3D).
Configuración secreta por pulsación larga en una esquina (predicciones, carta extra, tiempos, voz TTS, dorsos); reset oculto arriba a la
izquierda (vuelve al menú principal). Multiidioma (el del móvil), todo local.
**Flujo:** la app arranca en un menú (anillos + EMPEZAR). EMPEZAR inicia un juego nuevo en el modo elegido en la configuración
(`mode`: HUD plano `reader`, Baraja 3D `deck3d` o RA `ar`; solo se aplica al empezar). Cada fase: Analizar → animación → aparece un
«resultado» pulsable (`Stage.Result`) → al tocarlo se abre → se lee la predicción (`Stage.Revealed`) → Continuar. La paleta (3 colores),
`hudLevel` (1-3) y los tiempos son ajustes; la configuración está en secciones plegables (`ConfigScreen.kt`).
**Sonido y voz:** `Sound.kt` (sintetizador común `Synth`, sin ficheros; Android lo reproduce con `AudioTrack`, iOS lo replica en Swift en
`SoundEngine.swift`, hay que mantener ambos iguales) y `Speech.kt` (TTS: `TextToSpeech` en Android; en iOS Kotlin manda órdenes de texto a
`SpeechBridge` en Swift mediante `IosSpeechHooks`/`IosSoundHooks`). Todo lo nativo de iOS que no se pueda hacer en Kotlin se hace así: un
`handler` de texto en `iosMain` que Swift rellena en `iOSApp.init`. En iOS la RA es nativa (`iosApp/ARPrototype.swift`): Kotlin llama a Swift con
`onStartAR`, y la configuración se abre sobre la RA con `ConfigViewController`. Swift lee los ajustes de detección (`det*`, `detManualTap`)
de `UserDefaults` con las mismas claves que escribe `SettingsStore`. La «Baraja 3D» (`DeckScene.kt` + matemática en `Scene3D.kt`: mesa + baraja con proyección propia en `Canvas`, cámara orbital con
arrastrar/pellizcar, anillos, partículas, cubo de resultado y burbuja) es un modo más y sirve para afinar las animaciones sin iPhone;
las mismas animaciones deben llevarse a la RA. El HUD plano está en `Hud.kt`. En Android el modo RA cae al HUD plano. **Detalle y preguntas abiertas en `docs/SPEC.md`.**

## Reglas de código para que compile en iOS
- En `commonMain`: nada de `java.*` ni `android.*`. Usar `kotlinx-datetime` (0.6.1), `kotlin.math.*` y, si hace falta aritmética
  grande, `bignum` (`com.ionspin.kotlin:bignum:0.3.10`). Sin `String.format`, `Locale`, `SimpleDateFormat`, `Thread`, `synchronized`.
- Sin recursos de Android (`R.string`) en común; textos en el código.
- Iconos dibujados con `Canvas`/`Path` de Compose (no `material-icons-extended`).
- Lógica de dominio pura y sin UI, con tests en `commonTest` (y reloj inyectable si depende del tiempo).
- Lo del sistema (sensores, vibración, galería, portapapeles…): `expect` en `commonMain/.../ui/Platform.kt` + `actual` en `androidMain`/`iosMain`.
- `Platform.ios.kt` lleva `@file:OptIn(ExperimentalForeignApi::class)` (cinterop de Kotlin/Native).
- El fallo de iOS solo se ve en Actions (~20 min): revisar con cuidado antes de lanzarlo.
- Con scripts: en esta máquina los heredocs de Bash con contenido complejo fallan; escribe los `.py` con la herramienta Write y ejecútalos.

## Compilar y probar (Windows)
- Kotlin 2.0.21, Compose Multiplatform 1.7.1, AGP 8.7.3, Gradle 8.9, JDK 17, minSdk 26, target/compileSdk 35, iOS mínimo 15.0.
- `JAVA_HOME` apunta al JDK 17: `C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot`.
- SDK de Android en `C:\Users\jmlop\AppData\Local\Android\Sdk`: crear `local.properties` con `sdk.dir=...` (ignorado por git).
- `./gradlew.bat :shared:testDebugUnitTest assembleDebug` → APK en `app/build/outputs/apk/debug/app-debug.apk`.
  Copiar el APK a `data/android_build/` si se quiere conservar.
- Emulador AVD `calcu` (Android 15), reutilizable. Poner la zona horaria `Europe/Madrid` (arranca en GMT). Gboard puede salir en negro (es del emulador).
- `gh` en Windows: `C:\Program Files\GitHub CLI\gh.exe` (hace falta `gh auth login` una vez).
- En Windows los destinos de iOS se ignoran (`kotlin.native.ignoreDisabledTargets=true`); Android compila y testea igual.

## Flujo de trabajo
1. Desarrollar y probar en Android/JVM en local (emulador).
2. Commit y push cuando el usuario lo pida.
3. **Preguntar antes de lanzar Actions** y acumular mejoras: `gh workflow run ios.yml --ref main` (opción `-f tests=true` solo si se pide).
4. Descargar el artefacto a `data/ios_build/`: `gh run download <id> -n <Nombre>-ios-sin-firmar -D data/ios_build`.
5. Instalar el `.ipa` con Sideloadly/AltStore (Apple ID gratis; caduca a los 7 días; activar Modo desarrollador en el iPhone).

## Lecciones de iOS ya aprendidas
- `gradlew` debe estar en git con permiso de ejecución (`git update-index --chmod=+x gradlew`).
- En iOS, `UIDevice.proximityState` se lee por sondeo; apagar y reactivar la monitorización deja el estado pegado. Mantenerla activa.
- Barra de estado e indicador de inicio se ocultan desde SwiftUI (`ContentView.swift`); el bloqueo automático con `idleTimerDisabled`.

## Instrucciones para el usuario (si la app las necesita)
Si la app lleva una página de «Instrucciones» de cara al usuario final, mantenerla al día en la misma tarea en que se cambie la función.
