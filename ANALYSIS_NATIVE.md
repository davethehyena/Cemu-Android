# Análisis del port Android de Cemu — hallazgos

Análisis por lectura de código (no medido en dispositivo). Commit base: `2d049e3`.

## 1. El número de hilos no es configurable — el problema más claro

En móvil, elegir bien cuántos hilos usar la GPU emulada determina el rendimiento.
El emulador decide solo, y la decisión es cuestionable en Android.

**La cadena:**

- `src/config/ActiveSettings.cpp:65-79` — `GetCPUMode()`: si el modo es `Auto`,
  elige `MulticoreRecompiler` cuando `GetPhysicalCoreCount() >= 4`.
- `src/util/helpers/helpers.cpp:235-272` — `GetPhysicalCoreCount()`: en Windows
  consulta la topología real de la CPU. **En cualquier otro sistema operativo,
  incluido Android, devuelve directamente
  `std::thread::hardware_concurrency()`**.
- `src/android/app/src/main/java/info/cemu/cemu/` — **no existe ningún ajuste de
  hilos en la app**. Una búsqueda de "thread" en los 141 ficheros Kotlin no da
  ni una opción.

**Por qué importa en Android:**

`hardware_concurrency()` devuelve el número total de núcleos lógicos del SoC. En
un Snapdragon 8 Gen 2 eso son 8, contando los tres núcleos *little*. No sabe
nada de big.LITTLE, ni de frequencies, ni de *core hotplug*. Consecuencias:

- Se activan 8 hilos de emulación de GPU y compiten con los hilos de compilación
  de shaders por los núcleos pequeños, que son mucho más lentos. El resultado
  típico es más calor, menos clocks y frame times peores.
- No hay forma de ajustarlo. El usuario de Cemu en escritorio sí puede; aquí no.

**Dos mejoras concretas y separables:**

a) **Exponer el ajuste.** Añadir una opción de "núcleos de CPU" en los ajustes
   de la app, plumbed hasta `ActiveSettings`. Es trabajo de plumbing, no de
   lógica nueva.

b) **Detectar los núcleos grandes en Android.** Leer
   `/sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_max_freq` y contar los que
   superan un umbral (típicamente >2 GHz), o leer la jerarquía de
   `/sys/devices/system/cpu/cpu*/cpufreq/related_cpus`. Implementar
   `GetPhysicalCoreCount()` para la rama Android en vez de devolver
   `hardware_concurrency()` en crudo.

**Honestidad sobre el estado:** esto es lectura de código. Puede que en la
práctica `hardware_concurrency()` ya devuelva un número razonable, o que el
cuello de botella esté en otro sitio. Hay que medirlo antes de tocar nada. Es un
primer candidato *porque es barato de medir* (contar hilos usados frente al
frame time), no porque esté demostrado que sea el problema.

## 2. Lo que YA está bien (no tocar)

Vale la pena dejarlo por escrito, porque son las cosas que otros emuladores
móviles no tienen y es fácil romperlas sin querer:

- **Caché de shaders compilados en disco.** `RendererShaderVk.cpp:439-441`
  guarda el SPIR-V precompilado bajo
  `ActiveSettings::GetCachePath("shaderCache/precompiled/")`. La ruta se fija en
  `src/android/app/src/main/java/info/cemu/cemu/CemuApplication.kt:138` a
  `internalCemuUserFolder` — almacenamiento interno, persistente y escribible.
  **La caché funciona en Android.** Sin esto habría tirones constantes al
  arrancar cada juego.
- **Compilación asíncrona de pipelines.** `VulkanPipelineStableCache.cpp` y
  `VulkanPipelineCompiler.cpp` compilan en segundo plano.
- **Renderer Vulkan real**, no una emulación de OpenGL:
  `src/Cafe/HW/Latte/Renderer/Vulkan/`.

## 3. Sospecha: el acceso a ficheros vía SAF

El emulador lee las claves del juego (`.rpx`, `title.tmd`, etc.) a través del
Storage Access Framework: `src/Cafe/Filesystem/fscDeviceAndroidSAF.cpp` y
`src/Common/android/FilesystemAndroid.cpp`. SAF es una API de *contenedores*, no
de ficheros: cada lectura pasa por el sistema. Si el emulador hace muchas
lecturas pequeñas (miles al arrancar), la latencia se acumula.

No verificado. Para medirlo:comparar tiempo de arranque con el juego en
almacenamiento interno frente a almacenamiento externo/SAF.

## 4. Menos promising, pero anotado

- El renderer Metal (macOS) tiene cachés más elaboradas que Vulkan
  (`MetalOutputShaderCache`, `MetalPipelineCache`). Alguna idea podría
 trasladarse, pero Vulkan ya tiene lo esencial.
- `NativeEmulation.cpp:167-183` crea la superficie Android vía `SurfaceTexture`.
  Path normal; el *frame pacing* merece revisión cuando haya un dispositivo real
  con el que medir.

## Correccion: la resolucion de render ya es 1280x720

La hipotesis inicial de que Cemu renderizaba por encima de la resolucion nativa
era **incorrecta**. `LatteThread.cpp:53-54` fija el framebuffer emulado a 1280x720
de forma fija, que es la resolucion nativa de Wii U:

    LatteHandleOSScreen_getOrCreateScreenTex(..., 1280, 720, 1280);

La separacion entre resolucion emulada y ventana ya existe
(`LatteRenderTarget_getScreenImageArea`, lineas 830-864): la imagen de 1280x720
se escala alPresentarla. Limitar la resolucion de render no aportaria nada, asi
que no se ha implementado ningun ajuste de este tipo.

## La palanca real de calor: hilos de CPU emulada

`OSSchedulerBegin` (coreinit_Thread.cpp:1470-1488) solo admite 1 o 3:

    cemu_assert_debug(numCPUEmulationThreads == 1 || numCPUEmulationThreads == 3);

- 1 = `SinglecoreRecompiler`, 3 = `MulticoreRecompiler`.
- `CafeSystem.cpp:894` elige entre `OSSchedulerBegin(3)` y `OSSchedulerBegin(1)`.

El motor no admite un numero arbitrario de hilos, asi que la opcion util es
forzar 1 en vez de 3.

`CemuConfig::cpu_mode` ya existia pero estaba **muerto**: declarado en
`CemuConfig.h:442` y sin cargar, sin guardar y sin consumir (todo comentado).
Se ha revivido:

- `CemuConfig.cpp`: se carga y se guarda.
- `ActiveSettings::GetCPUMode()`: el ajuste global tiene prioridad sobre el perfil.
- Expuesto en Ajustes > General como "CPU mode".
- `GetPhysicalCoreCount()` detecta nucleos grandes en Android via `cpufreq`.

## Por que un emulador de Switch va mucho mejor en el mismo movil

No es que Switch emule "nativamente". Es cuestion de arquitecturas:

| Consola | CPU | Telefono | Traduccion |
|---|---|---|---|
| Wii U | PowerPC, **big-endian** | ARM, **little-endian** | Carisima |
| Switch | ARM (Cortex-A57) | ARM (Cortex) | Barata |

Cemu nunca corre nativo en ningun equipo: un PC es x86-64 y la Wii U es
PowerPC, asi que la traduccion es obligatoria por diseno del hardware, no por un
fallo de la implementacion. Aparte, los emuladores de Switch sustituyen mucho
codigo del sistema por HLE.

Lo que si es cierto, y es la lesson de este port: **no se puede eliminar la
traduccion, pero se puede abaratar**, que es justo lo que hizo RPCS3 con su
"descubrimiento" de la CPU de PS3 (abril 2026, 5-7% mas de FPS generando codigo
nativo mas eficiente). En Cemu las palancas son:

1. **Mejor codigo generado** por el recompilador (`PpcRecompiler`).
2. **HLE** para sustituir funciones conocidas del sistema.
3. **Traduccion de shaders** por la ruta Vulkan.

**Regla practica: ante bajo rendimiento, comprobar primero el tipo de build.**
Durante mucho tiempo se culpó a la GPU y al driver de Turnip, y la causa real era
que se estaban distribuyendo compilaciones de depuracion.

## Los shaders precompilados no existen en la ruta Vulkan

`GetPrecompiledShadersOption()` devuelve siempre `PrecompiledShaderOption::Auto`
ignorando la configuracion, y la opcion solo se consulta en la ruta OpenGL. En
Vulkan no hay soporte de shaders precompilados: existe una cache de pipelines en
tiempo de ejecucion y `async_compile` esta activo. Por eso la primera vez que se
entra en una zona nueva hay coste de compilacion.

Consecuencia practica: distinguir si una zona va lenta solo la primera vez
(compilacion) o tambien al repetir (cuello de botella real de CPU o GPU).

## Archivos de sistema: que hace falta para el menu de la Wii U

El menu (00050010), Mii Maker y los Ajustes del sistema son titulos de
*aplicacion*, no modulos de Cafe OS, asi que el HLE no puede sustituirlos:

    rpl.cpp:1976-1981
    // if no CafeLibs RPL is present then try to load as a HLE module
    if (!fs::exists(cafeLibsFilePath, ec))
        newDependency->rplHLEModule = RPLLoader_GetHLECafeOSModule(moduleName);

`RPLLoader_GetHLECafeOSModule` solo busca en `GetCOSModules()` (linea 1926), o
sea un puñado de modulos del sistema. Por eso `cafeLibs` es **imprescindible**
para el menu: sin el, el arranque se queda en "please wait".

Archivos necesarios en la carpeta de datos del emulador:

- `otp.bin` (1024 bytes) y `seeprom.bin` (512 bytes). Si no estan, el log avisa
  con `No otp.bin found` / `No seeprom.bin found`.
- `cafeLibs/`: librerias del sistema descifradas.
- `mlc01/`: la memoria de la tarjeta, donde vive `sys/title/00050010`
  (`TitleList.cpp:290` la escanea).
- `keys.txt` es opcional: se crea solo y solo hace falta para discos WUD/WUX.

Este fork **no usa carpeta `nand`**: no existe en el codigo. La SLC se genera a
partir de lo anterior.

La **region se lee del seeprom** (`ncrypto.cpp:786`, offset 0xA4), asi que un
seeprom EUR configura la consola en europea sin tocar codigo. Por eso el boton de
region que estaba planeado dejo de ser necesario. `CemuConfig.cpp:314` tiene
ademas la escritura de `console_region` comentada y el campo esta muerto:
`GetPlatformRegion()` (`CafeSystem.cpp:1038`) deduce la region del titulo en
juego con USA como valor por defecto.

## Trampa de Android: dos almacenes

El `userDataPath` de Cemu es el almacenamiento **externo**
(`getExternalFilesDir`, via `Context.internalFolder()` en
`CemuApplication.kt:151`), no `context.filesDir` que es el interno. Escribir en
el sitio equivocado no da ningun error, solo que el emulador no encuentra nada.
Por eso el importador de archivos de sistema usa `internalFolder()`.

Relacionado: desde Android 11 el explorador de archivos del movil y OpenMTP no
tienen acceso de escritura a `Android/data`, asi que copiar los archivos a mano
no es viable. La solucion fue el boton "Import system files", que usa
`java.util.zip` en Kotlin leyendo el `content://` URI de SAF. Se evito minizip a
proposito: no esta enlazado en el target de Android y habria exigido tocar CMake.
