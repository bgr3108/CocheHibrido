# Snapshot inicial de cargadores RIPREE

`charger_cache.db` es una caché pública de arranque, no una base de datos de
usuarios. La aplicación la copia únicamente si no existe `charger_cache` y
Room la valida frente al esquema exportado actual antes de abrirla.

Para actualizarla antes de una publicación:

1. Descarga y conserva una exportación RIPREE validada desde
   `https://energia.serviciosmin.gob.es/Ripree/ExportarInstalaciones/GenerarExcel`
   con el cuerpo `{"model":null,"soloConsolidado":true}`.
2. Comprueba cabecera, número de filas, instalaciones, conectores y
   coordenadas con el conjunto descargado.
3. Compila e instala `assembleDebug` y `assembleDebugAndroidTest` en un
   dispositivo de desarrollo. La generación usa el parser y las entidades
   reales de Kilonom, no un segundo parser.
4. Ejecuta `tools/generate_charger_seed.ps1` indicando el CSV y el serial.
5. Comprueba `PRAGMA integrity_check`, el `user_version`, el hash Room,
   recuentos e instalación limpia antes de incluir el asset.

La tarea de Gradle normal nunca descarga RIPREE: el CSV solo se utiliza de
forma manual para regenerar este asset versionado.
