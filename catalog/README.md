# Catálogo de vehículos local (fase 5)

Esta carpeta contiene el pipeline local del futuro catálogo actualizable de Kilonom. En esta fase no existe ninguna descarga remota: la aplicación usa exclusivamente el seed incluido en `app/src/main/assets/catalog/catalog-runtime.json`.

## Capas

- `editorial/catalog-editorial.json` es la fuente de verdad. Conserva fuentes, notas, estado editorial y claves de compatibilidad (`legacyKeys`).
- `runtime/catalog-runtime.json` es el artefacto compacto generado desde editorial.
- `app/src/main/assets/catalog/catalog-runtime.json` es una copia exacta del runtime que Android empaqueta como seed offline.
- `../tools/generate_vehicle_catalog.ps1` valida y genera ambos runtime de forma determinista.

No se edita el runtime a mano.

## Generación

Desde la raíz del repositorio:

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File tools/generate_vehicle_catalog.ps1
```

La primera generación del catálogo heredado usa `-BootstrapLegacy`. Lee los JSON históricos sin modificarlos, crea un registro editorial por entrada y los conserva como `ACTIVE`. Este modo es de inicialización y no debe usarse para sobrescribir trabajo editorial posterior.

Los registros de ejecución siempre llevan `brand.id`/`brand.displayName` y `model.id`/`model.displayName` explícitos. La aplicación no intenta deducir un modelo base a partir de textos heredados. La normalización inicial de modelos legacy se realiza una sola vez con `-NormalizeLegacyModels` en esta herramienta, que conserva literalmente las `legacyKeys` históricas y separa el modelo base de la variante antes de generar el runtime.

Las motocicletas legacy pueden consolidarse una sola vez con `-ConsolidateMotorcycles`. Solo une años consecutivos con el mismo snapshot técnico completo; conserva el `catalogId` más antiguo del tramo, migra todas sus `legacyKeys`, registra los IDs absorbidos en `supersededCatalogIds` y mantiene los registros sustituidos como `INACTIVE` con `replacedByCatalogId`. El runtime sigue expandiendo cada rango en sus años documentados, por lo que no incorpora años nuevos ni elimina opciones existentes.

El generador rechaza el editorial si encuentra, entre otros problemas: IDs o `legacyKeys` activos duplicados, referencias inexistentes, rangos inválidos, capacidades no positivas, rangos solapados técnicamente iguales, combinaciones de propulsión inválidas o registros `ACTIVE` sin fuentes. Si la validación falla no se escribe un runtime nuevo.

La salida es estable: el orden se fija por ID y `generatedAt` procede del editorial, no del reloj de la máquina. Dos ejecuciones con el mismo editorial producen los mismos bytes y el mismo SHA-256, que el script muestra al terminar.

## Contrato editorial v1

La raíz contiene `schemaVersion`, `catalogVersion`, `generatedAt`, `brands`, `models`, `generations`, `variants` y `vehicles`.

Cada vehículo tiene un `catalogId` estable, minúsculo ASCII con guiones, y referencia las identidades por ID. `yearFrom` es obligatorio y `yearTo` es inclusivo o `null` cuando sigue vigente. Nunca se reutiliza un ID para otro vehículo.

La propulsión se expresa como:

- `kind`: `ICE`, `HEV`, `PHEV` o `BEV`.
- `primaryFuel`: `GASOLINA`, `DIESEL` o `null`.
- `hybridSystem`: `NONE`, `MILD`, `FULL`, `PLUG_IN` o `BATTERY_ELECTRIC`.

El mapeo temporal a Android es explícito: ICE gasolina/diésel → `GASOLINA`/`DIESEL`; HEV → `HIBRIDO`; PHEV → `HIBRIDO_ENCHUFABLE`; BEV → `ELECTRICO`. Un valor desconocido es un error, nunca se convierte silenciosamente en gasolina.

`battery` conserva por separado `grossKwh`, `usableKwh`, `declaredKwh` y `declaredCapacityType` (`UNKNOWN` cuando la fuente no clasifica el valor). La capacidad útil es la única que Android puede usar automáticamente para estimar kWh desde porcentajes de batería. Un dato bruto o desconocido se conserva para trazabilidad, pero no se trata como capacidad operativa. `0` no se usa para ausencia de capacidad: se usa `null`.

`identification` es opcional en editorial y contiene únicamente pistas documentales para resolver variantes funcionalmente distintas: `displacementCc`, `powerKw`, `commercialVersions` y `engineCodes`. Al generar runtime siempre se serializa con listas, incluso vacías. No altera el snapshot del vehículo ni se usa para cálculos. Android puede preguntar por P.1 (cilindrada), P.2 (potencia en kW) o D.2/D.3 (tipo, variante o denominación comercial) solo cuando esas pistas distinguen de forma segura los candidatos; sus fuentes permanecen en editorial.

`fuelTankLitres` es `null` en BEV y positivo en ICE, HEV y PHEV. Las motos siguen el mismo contrato con `category: MOTO`.

## Estados y compatibilidad

El editorial admite `ACTIVE`, `REVIEW` y `DRAFT`. Solo `ACTIVE` pasa a runtime. Los registros heredados publicados se importan como `ACTIVE` con procedencia `LEGACY_IMPORT` para que ninguna selección disponible en 1.5 desaparezca mientras se completa la investigación.

`legacyKeys` describe las claves anteriores `category` + `brand` + `model` + `year`. Sirve para reconciliar datos históricos; no modifica vehículos ya guardados. Los vehículos Room son snapshots personales independientes del catálogo y no se reescriben en esta fase.

## Próximas fases

La futura copia descargada deberá vivir como caché pública recuperable bajo `files/catalog/` y excluirse de backup. La actualización remota añadirá manifest, checksum SHA-256, descarga temporal, validación y sustitución atómica. Nada de ello está implementado todavía.
