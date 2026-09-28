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

## Estado: la cadena de build FUNCIONA

- **Build #2 (commit `a9be619`): `Build Cemu` → SUCCESS.** El emulador se
  compila entero en la nube.
- Falló después el paso `Merge logs` (bug mío), lo que arrastró a `Upload APK`
  porque no tenía `if: always()`. Corregido en `e4b2fc9`.
- Build #3 launched desde `e4b2fc9`: debe dejar el APK en el artefacto
  `Cemu-apk-debug`.

## Cómo trabajar aquí

1. Editar en `~/Developer/Cemu`.
2. `git add` + `commit` + `git push origin android-port`.
3. GitHub Actions compila solo.
4. Descargar el APK: pestaña Actions → run → artifact `Cemu-apk-debug` → unzip.
   (Requiere sesión de GitHub; no se puede bajar por API sin token.)
5. Instalar en el móvil por USB: `adb install -r <apk>`.

## Pendiente / bloqueos conocidos

- **API de GitHub sin token**: los logs y artefactos dan 401/403 al descarga
  anónima. Para diagnosticar builds fallidos hace falta un token de GitHub con
  permiso `Actions: read`. Opciones: pegarlo en `~/.config/gh_token` (chmod 600)
  y leerlo desde ahí con curl, o que el usuario baje el `build-log` a mano.
- **Nunca se ha probado el APK en un móvil real.** No se sabe si arranca ni si
  carga juegos. Es el siguiente hitoUnknown.
- Caché de vcpkg ya poblada: los builds deberían bajar de 60 min a ~15-20 min.

## Hitos siguientes

1. Conseguir el APK e instalarlo en el móvil. Ver si arranca.
2. Cargar un juego de verdad (los juegos van en la SD/teléfono; Cemu no incluye
   las claves, el usuario pone su propio juego desde el dispositivo).
3. Profiling y optimización real.
