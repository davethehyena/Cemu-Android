# Estado del proyecto: Cemu para Android

Documento de traspaso. Cualquier sesión nueva debe leer esto primero.

## Objetivo

Fork propio del port Android de Cemu para mejorarlo hasta ser el mejor emulador
de Wii U en Android. User: `davethehyena` (fork: `davethehyena/Cemu-Android`).

## Dónde está todo

| Qué | Dónde |
|---|---|
| Repo local | `~/Developer/Cemu` |
| Fork (nuestro) | `git@github.com:davethehyena/Cemu-Android.git` (rama `android-port`) |
| Upstream (SSimco) | `https://github.com/SSimco/Cemu` (rama `android-port`) |
| Android Studio | `/Applications/Android Studio.app` |
| SDK Android | `~/Library/Android/sdk` |
| JDK 21 (Temurin) | `~/Library/Java/JavaVirtualMachines/temurin-21.jdk` |
| Clave SSH | `~/.ssh/id_ed25519_github` |

## Entorno del Mac (importante)

MacBook Air 2017: **macOS 12.7.6, Intel i5-5350U 2 núcleos a 1.8GHz, 8GB RAM,
~35GB libres**. Verificado que Gradle 9.3.1 y AGP 9.1.1 funcionan en macOS 12.

Por eso **NO compilamos aquí**: los buildslocales serían de 2,5-5 h y pueden
quedarse sin memoria. Se compila en GitHub Actions. Verificado que Homebrew
está roto en esta máquina (necesita sudo por `/usr/local/share/man/man8`), así
que nada de `brew install`.

## Arquitectura de build

- Build en la nube: `.github/workflows/build-apk.yml`.
- C++ vía NDK 29.0.14206865 + CMake 3.30.5, `arm64-v8a` únicamente.
- Dependencias vía vcpkg (`dependencies/vcpkg`), con **caché de binarios en GitHub
  Packages** por cuenta (ver `.github/actions/set-up-vcpkg-binary-cache-action`).
  El primer build fue en frío; los siguientes reutilizan la caché.
- App Android: Kotlin + Jetpack Compose en `src/android/app/`.
- UI: `src/android/app/src/main/java/info/cemu/cemu/` (141 ficheros Kotlin).
- Capa nativa Android: `src/input/api/Android/`, `src/Common/android/`,
  `src/Cafe/Filesystem/fscDeviceAndroidSAF.cpp`, `src/android/app/src/main/cpp/`.
- Detalle clave: usa SAF (Storage Access Framework) para acceder a los juegos.

## Estado: la cadena de build FUNCIONA y el APK ya está probado en un móvil real

- Builds #4 a #11 completados correctamente en GitHub Actions.
- **El artefacto por defecto es `Cemu-apk-release`.** Ver "El bug del build
  debug" más abajo: era la causa del bajo rendimiento.
- Descargar el artefacto requiere sesión de GitHub iniciada en el navegador; la
  API anónima devuelve 401/403.
- Para diagnosticar builds fallidos hace falta un token de GitHub con
  `Actions: read`. Opciones: pegarlo en `~/.config/gh_token` (chmod 600) y leerlo
  desde ahí con curl, o que el usuario baje el `build-log` a mano.

### Dispositivo de pruebas

Samsung Galaxy S24 Ultra. Snapdragon 8 Gen 3 (SM8650), 11 GB RAM, Adreno 750,
driver Turnip Mesa 26.0.0-devel, Android API 36. Cemu 2.3.0, arm64-v8a.

### El bug del build debug (el hallazgo importante)

Todas las builds que se distributed hasta el commit `681e07d9` eran **debug**. En
un emulador eso es determinante, porque el recompilador de PowerPC (el JIT que
traduce el código de la consola sobre la marcha) se compilaba sin optimización y
con `NDEBUG` sin definir, es decir con todas las aserciones activas. El efecto
observado: tirones y lentitud en escenas con más carga, y compilación de shaders
muy lenta.

El proyecto ya soportaba release sin keystore (`build.gradle.kts:93-97` firma con
la clave de debug si no hay `ANDROID_STORE_FILE`), así que no hacía falta
configurar nada. Se puso `release` como valor por defecto.

`isMinifyEnabled` se dejó en `false` a propósito: la ganancia está en el C++, y R8
con Compose+JNI es riesgo innecesario en runtime. `-dontobfuscate` ya protege los
nombres que usa el JNI.

**Conclusión: antes de culpar a la GPU o al driver, comprobar el tipo de build.**
El usuario llegó a esta conclusión comparando con un emulador de Switch; ver
"Por qué Switch va mejor" en ANALYSIS_NATIVE.md.

## Problemas ya resueltos y verificados en dispositivo

| Problema | Commit | Estado |
|---|---|---|
| Rendimiento bajo y shaders lentos | `681e07d9` | Resuelto, verificado por el usuario |
| Crash de New Super Mario Bros. U | `65ad9db3` | Resuelto, verificado por el usuario |
| Ajustes de CPU inaccesibles | `849e40c` | Funciona, pero no era la causa del calor |
| Full sync at GX2DrawDone inaccesible | `514dd429` | Expuesto |
| Importar archivos de sistema | `21752744`, `b470dcc7` | A la espera de probar |
| Nombre e icono de la app | `fcec29d8` | A la espera de revisar el icono |

Lo que hizo el usuario al informar que un emulador de Switch iba fluido en el
mismo móvil:kehxo que el problema no era el hardware sino el build.

## Cómo trabajar aquí

1. Editar en `~/Developer/Cemu`.
2. `git add` + `commit` + `git push origin android-port`.
3. GitHub Actions compila solo.
4. Descargar el APK: pestaña Actions → run → artifact `Cemu-apk-release` → unzip.
5. Instalar en el móvil. El APK de release tiene identificador
   `info.cemu.cemu` y el debug `info.cemu.cemu.debug`: **conviven como apps
   separadas** y no comparten partidas ni Ajustes.

## Pendiente / bloqueos conocidos

- **El menú de la Wii U y el Mii Maker no se han probado todavía.** Falta
  instalar `otp.bin`, `seeprom.bin`, `mlc01` y `cafeLibs` (ver ANALYSIS_NATIVE.md).
- **Falta la medida de FPS** para saber cuánto se ha ganado con el build release
  y si el problema de "zonas lentas" queda resuelto del todo.
- El icono nuevo se generó con `sips` sin poder revisar el resultado visualmente.
- El log del móvil contiene spam repetido `"Unsupported clear depth as color"`:
  localizar el origen y degradar ese log, porque en un build de depuración puede
  costar I/O por frame.
- Caché de vcpkg poblada: los builds bajan de 60 min a ~20-25 min.

## Hitos siguientes

1. Probar el importador de archivos de sistema y arrancar el menú de la Wii U.
2. Medir FPS en la zona que antes iba a tirones, con el móvil sin cargador.
3. Si el menú no arranca, el siguiente intento es generar `cafeLibs` a partir de
   los archivos de sistema descifrados.

