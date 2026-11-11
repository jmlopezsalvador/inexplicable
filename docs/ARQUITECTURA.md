# Arquitectura multiplataforma (Android + iOS sin Mac)

Guía para montar un proyecto nuevo con la **misma base técnica** que Calculadora (`calcumagic`): un único código
en Kotlin con interfaz Compose que corre en Android y iPhone, y un iPhone `.ipa` compilado en la nube con GitHub
Actions (no hace falta Mac). Aquí solo está la arquitectura y las decisiones; nada de la funcionalidad de la app.

> Para quien lo lea con Claude Code: copia este fichero al proyecto nuevo y pide "monta el proyecto siguiendo
> ARQUITECTURA.md". Sustituye `com.miapp` / `MiApp` por el paquete y el nombre reales.

---

## 1. Idea general

- **Un solo código** en Kotlin Multiplatform (KMP) + Compose Multiplatform: lógica, ajustes e **interfaz** son comunes.
  Solo lo que depende del sistema operativo se escribe dos veces (`expect`/`actual`).
- **Android** se compila en local (Windows/Linux/Mac). **iOS** se compila en un Mac de **GitHub Actions** y sale un
  `.ipa` sin firmar que se instala con Sideloadly/AltStore (basta un Apple ID gratis; caduca a los 7 días).
- Todo el desarrollo y las pruebas se hacen en Android/JVM (rápido); iOS se compila **a mano y de vez en cuando**,
  porque los minutos de macOS cuestan (×10 en repos privados del plan Free).

```
proyecto/
├─ build.gradle.kts            plugins raíz (versiones) sin aplicarlos
├─ settings.gradle.kts         repositorios + include(":shared", ":app")
├─ gradle.properties
├─ shared/                     ← TODO el código de la app
│  ├─ build.gradle.kts
│  └─ src/
│     ├─ commonMain/kotlin/com/miapp/
│     │   ├─ App.kt                   raíz Compose común (@Composable MiAppRoot)
│     │   ├─ <dominio>/               lógica pura (sin Android ni UI)
│     │   └─ ui/                      pantallas, ajustes, Platform.kt (expect)
│     ├─ androidMain/…/ui/Platform.android.kt     actual de Android
│     ├─ iosMain/…/ui/Platform.ios.kt             actual de iOS
│     ├─ iosMain/…/MainViewController.kt          punto de entrada desde Swift
│     └─ commonTest/                  tests con kotlin.test
├─ app/                        ← solo la envoltura de Android (MainActivity, manifiesto, recursos)
├─ iosApp/                     ← solo la envoltura de iOS (SwiftUI + project.yml de XcodeGen)
└─ .github/workflows/ios.yml   ← compilación de iOS en la nube
```

Regla de oro: **si no es estrictamente del sistema, va en `shared/commonMain`**. `app/` e `iosApp/` casi no tienen código.

---

## 2. Versiones (probadas juntas; no mezclar al azar)

| Pieza | Versión |
|---|---|
| Kotlin | 2.0.21 |
| Compose Multiplatform (`org.jetbrains.compose`) | 1.7.1 |
| Plugin Compose del compilador (`org.jetbrains.kotlin.plugin.compose`) | 2.0.21 (la misma que Kotlin) |
| Android Gradle Plugin | 8.7.3 |
| Gradle | 8.9 |
| JDK | 17 (Windows: `JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot`) |
| Android | minSdk 26 · compileSdk/targetSdk 35 |
| Runner de GitHub | `macos-15` (el `macos-14` ya no vale) |
| iOS mínimo | 15.0 |

Bibliotecas multiplataforma (lo de `java.*` **no existe en iOS**, hay que usar estas):
- `com.ionspin.kotlin:bignum:0.3.10` → `BigDecimal`/`BigInteger` en vez de `java.math`.
- `org.jetbrains.kotlinx:kotlinx-datetime:0.6.1` → fecha/hora en vez de `java.time`.
- Tests: `kotlin("test")` en `commonTest` (no JUnit).

---

## 3. Ficheros de Gradle

### `settings.gradle.kts`
```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "MiApp"
include(":shared")
include(":app")
```

### `build.gradle.kts` (raíz): solo declara versiones
```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.compose") version "1.7.1" apply false
}
```

### `gradle.properties`
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
# En Windows/Linux los destinos de iOS no se pueden compilar: se ignoran sin avisos (en el Mac de GitHub Actions sí)
kotlin.native.ignoreDisabledTargets=true
```
La última línea es clave: permite trabajar en Windows sin que Gradle se queje de los destinos de iOS.

### `shared/build.gradle.kts`
```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"   // nombre del framework: en Swift, `import Shared`
            isStatic = true       // estático: se enlaza dentro de la app, sin firmar por separado
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation("com.ionspin.kotlin:bignum:0.3.10")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.9.3")
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}

android {
    namespace = "com.miapp.shared"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
```

### `app/build.gradle.kts` (envoltura Android)
Plugins: `com.android.application`, `org.jetbrains.kotlin.android`, `org.jetbrains.kotlin.plugin.compose`.
`applicationId = "com.miapp"`, `minSdk = 26`, `targetSdk = 35`, Java/JVM 17, `buildFeatures { compose = true }`.
Dependencias: `implementation(project(":shared"))`, BOM de Compose `androidx.compose:compose-bom:2024.10.01`,
`androidx.activity:activity-compose:1.9.3`.

### `.gitignore`
```
local.properties
build/
.gradle/
*.iml
.idea/
# iOS: el proyecto de Xcode se genera con XcodeGen en CI
*.xcodeproj/
xcuserdata/
DerivedData/
.kotlin/
```

---

## 4. Patrón `expect`/`actual` (lo único que se escribe dos veces)

En `commonMain/.../ui/Platform.kt` se declara lo que cada sistema implementa:
```kotlin
expect fun platformManufacturer(): String          // "samsung", "Apple"…
expect fun platformLocaleTag(): String             // "es-ES"
@Composable expect fun SystemBarsEffect(dark: Boolean)   // barras de estado/navegación
```
- **Android** (`androidMain`): `Build.MANUFACTURER`, `java.util.Locale.getDefault().toLanguageTag()`,
  `enableEdgeToEdge(...)` desde un `LaunchedEffect`.
- **iOS** (`iosMain`): `"Apple"`, `NSLocale.currentLocale.localeIdentifier`, `SystemBarsEffect` vacío (lo gestiona
  SwiftUI con `.preferredColorScheme`).

### Almacenamiento de ajustes: interfaz común, dos implementaciones
En `commonMain` una interfaz mínima, y la clase de ajustes que la usa (no sabe de plataforma):
```kotlin
interface KeyValueStore {
    fun getString(key: String): String?
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getLong(key: String, default: Long): Long
    fun putString(key: String, value: String)
    fun putBoolean(key: String, value: Boolean)
    fun putLong(key: String, value: Long)
}
class SettingsStore(private val prefs: KeyValueStore) { fun load(): AppSettings; fun save(s: AppSettings) }
```
- Android: `SharedPrefsStore(context)` (envuelve `SharedPreferences`).
- iOS: `UserDefaultsStore()` (envuelve `NSUserDefaults.standardUserDefaults`; ojo, `getBoolean/getLong` deben mirar
  `objectForKey(key) == null` para devolver el valor por defecto).

Este patrón (interfaz en común + implementación por plataforma inyectada desde el punto de entrada) sirve para
cualquier otra cosa del sistema: sensores, vibración, portapapeles, red…

---

## 5. Puntos de entrada

**Raíz común** (`commonMain/.../App.kt`): carga ajustes, los guarda al cambiar y pinta la app.
```kotlin
@Composable
fun MiAppRoot(store: SettingsStore) {
    var settings by remember { mutableStateOf(store.load()) }
    MaterialTheme {
        MiApp(settings = settings, onSettingsChange = { settings = it; store.save(it) })
    }
}
```

**Android** (`app/.../MainActivity.kt`): 
```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()               // pantalla completa; SystemBarsEffect ajusta los iconos después
        super.onCreate(savedInstanceState)
        val store = SettingsStore(SharedPrefsStore(applicationContext))
        setContent { MiAppRoot(store) }
    }
}
```
Manifiesto: `screenOrientation="portrait"` + `configChanges` si la app es solo vertical (así no se recrea al girar).
Para conservar el estado si Android recrea la actividad se usa `rememberSaveable` con un `listSaver`.

**iOS** (`shared/src/iosMain/.../MainViewController.kt`):
```kotlin
fun MainViewController(): UIViewController = ComposeUIViewController {
    MiAppRoot(SettingsStore(UserDefaultsStore()))
}
```

**iOS, envoltura SwiftUI** (`iosApp/iosApp/`):
```swift
// iOSApp.swift
import SwiftUI
@main struct iOSApp: App { var body: some Scene { WindowGroup { ContentView() } } }

// ContentView.swift
import SwiftUI
import Shared
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController { MainViewControllerKt.MainViewController() }
    func updateUIViewController(_ vc: UIViewController, context: Context) {}
}
struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)          // sin esto salen franjas blancas arriba y abajo
            .background(Color.black)        // el color de fondo de tu app
            .preferredColorScheme(.dark)    // barra de estado clara/oscura según tu fondo
    }
}
```
Nombre del fichero `MainViewController.kt` → clase Swift `MainViewControllerKt` (sufijo `Kt`).

---

## 6. iOS sin Mac: XcodeGen + GitHub Actions

### Por qué XcodeGen
El `.xcodeproj` no se versiona (es un fichero ilegible y propenso a conflictos). Se describe en `iosApp/project.yml` y
el Mac de CI lo genera con `xcodegen generate`.

### `iosApp/project.yml`
```yaml
name: iosApp
options:
  bundleIdPrefix: com.miapp
  deploymentTarget: { iOS: "15.0" }
  createIntermediateGroups: true
targets:
  iosApp:
    type: application
    platform: iOS
    sources:
      - path: iosApp
    info:
      path: iosApp/Info.plist
      properties:
        CFBundleDisplayName: MiApp
        UILaunchScreen: {}
        UIRequiresFullScreen: true
        UISupportedInterfaceOrientations: [UIInterfaceOrientationPortrait]
        UISupportedInterfaceOrientations~ipad: [UIInterfaceOrientationPortrait]
        CADisableMinimumFrameDurationOnPhone: true
    settings:
      base:
        PRODUCT_BUNDLE_IDENTIFIER: com.miapp
        PRODUCT_NAME: MiApp
        ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon
        MARKETING_VERSION: "1.0"
        CURRENT_PROJECT_VERSION: "1"
        SWIFT_VERSION: "5.0"
        TARGETED_DEVICE_FAMILY: "1"
        ENABLE_USER_SCRIPT_SANDBOXING: NO
        FRAMEWORK_SEARCH_PATHS: "$(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)"
        OTHER_LDFLAGS: "$(inherited) -framework Shared"
    preBuildScripts:
      - name: Compilar Kotlin (Gradle)
        basedOnDependencyAnalysis: false
        script: |
          cd "$SRCROOT/.."
          ./gradlew :shared:embedAndSignAppleFrameworkForXcode
```
Cómo encaja: Xcode, antes de compilar el Swift, ejecuta Gradle (`embedAndSignAppleFrameworkForXcode`), que compila
el módulo `shared` a un framework estático `Shared` y lo deja en `shared/build/xcode-frameworks/…`; Xcode lo enlaza.
`ENABLE_USER_SCRIPT_SANDBOXING: NO` es obligatorio para que el script pueda llamar a Gradle.

### Icono
`iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/` con un PNG de 1024×1024 y su `Contents.json`
(un solo tamaño "universal" basta) + `Assets.xcassets/Contents.json`.

### `.github/workflows/ios.yml`
```yaml
name: iOS
on:
  workflow_dispatch:          # solo a mano: cada ejecución gasta minutos de macOS
    inputs:
      tests:
        description: "Pasar también los tests en el simulador (gasta más minutos)"
        type: boolean
        default: false
jobs:
  build:
    runs-on: macos-15
    timeout-minutes: 60
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 17 }
      # Gradle configura también el módulo Android: necesita licencias del SDK
      - uses: android-actions/setup-android@v3
        with:
          packages: ""        # sin esto falla buscando el paquete "tools", que ya no existe
      - uses: gradle/actions/setup-gradle@v4     # caché de Gradle: las siguientes ejecuciones son más rápidas
      - name: Tests de la lógica en iOS
        if: ${{ inputs.tests }}
        run: ./gradlew :shared:iosSimulatorArm64Test --no-daemon
      - name: Instalar XcodeGen
        run: brew install xcodegen
      - name: Generar el proyecto de Xcode
        working-directory: iosApp
        run: xcodegen generate
      - name: Compilar para iPhone (sin firmar) y empaquetar .ipa
        working-directory: iosApp
        run: |
          xcodebuild build \
            -project iosApp.xcodeproj -scheme iosApp -configuration Release \
            -sdk iphoneos -destination 'generic/platform=iOS' \
            -derivedDataPath build/device \
            CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY=""
          mkdir -p build/ipa/Payload
          cp -R build/device/Build/Products/Release-iphoneos/MiApp.app build/ipa/Payload/
          (cd build/ipa && zip -qry ../MiApp.ipa Payload)
      - uses: actions/upload-artifact@v4
        with:
          name: MiApp-ios-sin-firmar
          path: iosApp/build/MiApp.ipa
          if-no-files-found: error
```
Duración orientativa: 15–25 min por ejecución (la primera, la más lenta).

### Instalar el `.ipa` en el iPhone
1. Descargar el artefacto (web de Actions o `gh run download <id> -n MiApp-ios-sin-firmar`).
2. Firmarlo e instalarlo con **Sideloadly** (o AltStore) con un Apple ID. Cuenta gratuita → caduca a los 7 días.
3. En el iPhone: Ajustes → Privacidad y seguridad → **Modo desarrollador** (activar y reiniciar) y confiar en el perfil.

---

## 7. Flujo de trabajo

1. Desarrollar y probar en **Android/JVM** en local:
   `./gradlew.bat :shared:testDebugUnitTest assembleDebug` (APK en `app/build/outputs/apk/debug/`).
2. Commit y subida a la rama (`ios` en este proyecto, aparte de `main`).
3. **Preguntar antes de lanzar Actions** y acumular mejoras: `gh workflow run ios.yml --ref <rama>`
   (el CLI `gh` en Windows: `C:\Program Files\GitHub CLI\gh.exe`; hay que hacer `gh auth login` una vez).
4. Descargar el `.ipa` e instalarlo con Sideloadly.

---

## 8. Reglas de código para que compile en iOS

El código de `commonMain` debe ser Kotlin puro + Compose Multiplatform. **Android compila aunque uses algo de la
JVM, pero iOS fallará**, y el fallo solo se ve en Actions (cuesta 20 min). Por eso:

- Nada de `java.*` ni `android.*` en `commonMain`: usar `bignum`, `kotlinx-datetime`, `kotlin.math.*`
  (`kotlin.math.PI`, no `Math.PI`), `String.format` no existe → formatear a mano.
- Nada de `R.string`/recursos de Android en común; los textos van en el código (o `compose.components.resources`).
- Cuidado con APIs de `Locale`, `SimpleDateFormat`, `Thread`, `synchronized`… son JVM.
- Dibujar iconos con `Canvas`/`Path` de Compose en vez de `Icons` de material-icons-extended (no incluido).
- Texto/medidas: `rememberTextMeasurer` para ajustar tamaños de fuente al ancho.
- Tener los **tests de la lógica en `commonTest`**: se ejecutan en JVM (Android) en local y, si se quiere, en el
  simulador de iOS con la opción `tests` del workflow.
- Mantener la lógica (dominio) **pura y sin UI**: así se testea sin emulador y se comporta igual en las dos plataformas.
- Los dos sistemas pueden diferir en detalles (fuentes, redondeos, barras del sistema). El aspecto final conviene
  verlo en un móvil real de cada sistema.

---

## 9. Errores que ya nos han pasado (y su arreglo)

| Síntoma | Causa / solución |
|---|---|
| Actions: `The configuration 'macos-14-arm64-us-default' is not supported` | Usar `runs-on: macos-15`. |
| `setup-android`: `Failed to find package 'tools'` | Poner `packages: ""`. |
| Solo en iOS: `Unresolved reference 'Math'` | Estaba `Math.PI` en `commonMain`; usar `kotlin.math.PI`. |
| `androidx.activity` no resuelve en `androidMain` | Añadir `androidMain.dependencies { implementation("androidx.activity:activity-compose:1.9.3") }`. |
| Franjas blancas arriba y abajo en iOS | `ContentView`: `.ignoresSafeArea(.all)` + `.background(color)`. |
| iOS pide "Modo desarrollador" al abrir | Activarlo en Ajustes → Privacidad y seguridad. |
| `gh run download` falla con "file exists" | Renombrar/borrar el `.ipa` anterior antes. |
| El demonio de Gradle se cae al arrancar una vez | Repetir el comando. |
| Se coló `.kotlin/errors` en git | Añadir `.kotlin/` al `.gitignore`. |

---

## 10. Checklist para el proyecto nuevo

- [ ] Crear `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore` (§3) y el wrapper de Gradle 8.9.
- [ ] Módulo `shared` con KMP + Compose (§3) y `app` como envoltura de Android (§5).
- [ ] `Platform.kt` con `expect` y sus `actual` en `androidMain` / `iosMain` (§4).
- [ ] `KeyValueStore` + `SettingsStore` + `MiAppRoot` (§4, §5).
- [ ] `iosApp/` con SwiftUI, `project.yml`, icono 1024 (§5, §6).
- [ ] `.github/workflows/ios.yml` (§6) y rama `ios`.
- [ ] Probar en Android (`assembleDebug` + tests) antes de gastar minutos de macOS.
- [ ] Primera compilación de iOS: esperar errores de "solo-iOS" (§8, §9) y corregirlos.
- [ ] `CLAUDE.md` del proyecto nuevo con: estructura, comandos, la regla "preguntar antes de lanzar Actions".
