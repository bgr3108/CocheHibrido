[CmdletBinding()]
param(
    [switch]$BootstrapLegacy,
    [switch]$NormalizeLegacyModels,
    [string]$EditorialPath,
    [string]$RuntimePath,
    [string]$AssetPath
)

$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($EditorialPath)) { $EditorialPath = Join-Path $scriptRoot "..\catalog\editorial\catalog-editorial.json" }
if ([string]::IsNullOrWhiteSpace($RuntimePath)) { $RuntimePath = Join-Path $scriptRoot "..\catalog\runtime\catalog-runtime.json" }
if ([string]::IsNullOrWhiteSpace($AssetPath)) { $AssetPath = Join-Path $scriptRoot "..\app\src\main\assets\catalog\catalog-runtime.json" }

function New-Object {
    param([hashtable]$Properties)
    $result = [ordered]@{}
    foreach ($entry in $Properties.GetEnumerator()) { $result[$entry.Key] = $entry.Value }
    return [pscustomobject]$result
}

function Get-Sha256 {
    param([byte[]]$Bytes)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { ([System.BitConverter]::ToString($sha.ComputeHash($Bytes))).Replace("-", "").ToLowerInvariant() }
    finally { $sha.Dispose() }
}

function Get-Slug {
    param([string]$Value)
    $normalized = $Value.Normalize([Text.NormalizationForm]::FormD)
    $ascii = -join ($normalized.ToCharArray() | Where-Object {
        [Globalization.CharUnicodeInfo]::GetUnicodeCategory($_) -ne [Globalization.UnicodeCategory]::NonSpacingMark
    })
    $slug = $ascii.ToLowerInvariant() -replace "[^a-z0-9]+", "-"
    return $slug.Trim("-")
}

function Get-ShortHash {
    param([string]$Value)
    $bytes = [Text.Encoding]::UTF8.GetBytes($Value)
    (Get-Sha256 $bytes).Substring(0, 10)
}

function Assert-Id {
    param([string]$Id, [string]$Context)
    if ([string]::IsNullOrWhiteSpace($Id) -or $Id -notmatch "^[a-z0-9]+(?:-[a-z0-9]+)*$") {
        throw "${Context}: id inválido '$Id'"
    }
}

function Assert-PositiveNumber {
    param($Value, [string]$Context)
    if ($null -eq $Value -or -not ($Value -is [ValueType]) -or [double]::IsNaN([double]$Value) -or [double]::IsInfinity([double]$Value) -or [double]$Value -le 0) {
        throw "${Context}: debe ser un número finito mayor que cero"
    }
}

function Get-Property {
    param($Object, [string]$Name)
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    return $property.Value
}

function Get-PowertrainForLegacyType {
    param([string]$Type)
    switch ($Type) {
        "GASOLINA" { return (New-Object @{ kind = "ICE"; primaryFuel = "GASOLINA"; hybridSystem = "NONE" }) }
        "DIESEL" { return (New-Object @{ kind = "ICE"; primaryFuel = "DIESEL"; hybridSystem = "NONE" }) }
        "HIBRIDO" { return (New-Object @{ kind = "HEV"; primaryFuel = $null; hybridSystem = "FULL" }) }
        "HIBRIDO_ENCHUFABLE" { return (New-Object @{ kind = "PHEV"; primaryFuel = $null; hybridSystem = "PLUG_IN" }) }
        "ELECTRICO" { return (New-Object @{ kind = "BEV"; primaryFuel = $null; hybridSystem = "BATTERY_ELECTRIC" }) }
        default { throw "legacy import: tipo de vehículo desconocido '$Type'" }
    }
}

function New-LegacySource {
    param([string]$CatalogFile)
    return @(
        (New-Object @{
            type = "LEGACY_BUNDLED_CATALOG"
            reference = "app/src/main/assets/$CatalogFile"
            url = $null
            accessedAt = "2026-10-05"
            fields = @("brand", "model", "year", "type", "fuelTankLitres", "battery")
        })
    )
}

function New-LegacyVehicle {
    param($Entry, [string]$Category, [string]$CatalogFile)
    $brandId = Get-Slug $Entry.brand
    $modelId = "legacy-$(Get-Slug $Entry.model)-$(Get-ShortHash "$Category|$($Entry.brand)|$($Entry.model)")"
    $legacyIdentity = "$Category|$($Entry.brand)|$($Entry.model)|$($Entry.year)|$($Entry.type)"
    $batteryValue = [double]$Entry.batteryCapacity
    $tankValue = [double]$Entry.fuelTankCapacity
    return New-Object @{
        catalogId = "legacy-$(Get-Slug $Category)-$brandId-$modelId-$($Entry.year)-$(Get-Slug $Entry.type)-$(Get-ShortHash $legacyIdentity)"
        editorialStatus = "ACTIVE"
        provenance = "LEGACY_IMPORT"
        category = $Category
        brandId = $brandId
        modelId = $modelId
        generationId = $null
        variantId = $null
        yearFrom = [int]$Entry.year
        yearTo = [int]$Entry.year
        powertrain = Get-PowertrainForLegacyType $Entry.type
        fuelTankLitres = if ($tankValue -gt 0) { $tankValue } else { $null }
        battery = New-Object @{
            grossKwh = $null
            usableKwh = $null
            declaredKwh = if ($batteryValue -gt 0) { $batteryValue } else { $null }
            declaredCapacityType = if ($batteryValue -gt 0) { "UNKNOWN" } else { $null }
        }
        bodyStyle = $null
        drivetrain = $null
        marketCodes = @("ES")
        aliases = @()
        legacyKeys = @(
            (New-Object @{ category = $Category; brand = [string]$Entry.brand; model = [string]$Entry.model; year = [int]$Entry.year })
        )
        sources = New-LegacySource $CatalogFile
        notes = "Importación fiel del catálogo local pre-1.6; pendiente de investigación editorial."
        editorialRevision = 1
    }
}

function Get-ReadyVehicles {
    $official = "OFFICIAL_MANUFACTURER"
    $technical = "TECHNICAL_REFERENCE"
    # Keep the script independent of the PowerShell 5.1 ANSI fallback used for
    # source files without a UTF-8 BOM.
    $leon = "Le$([char]0x00F3)n"
    $leonEHybrid = "$leon e-Hybrid"
    return @(
        (New-Object @{
            catalogId = "car-volkswagen-golf-viii-gte"
            category = "COCHE"; brand = "Volkswagen"; model = "Golf"; generation = "VIII"; variant = "GTE 1.4 TSI"
            yearFrom = 2020; yearTo = 2024; kind = "PHEV"; fuel = "GASOLINA"; hybrid = "PLUG_IN"; tank = 39.5
            gross = $null; usable = $null; declared = 13.0; declaredType = "UNKNOWN"; bodyStyle = "HATCHBACK"; drivetrain = "FWD"
            legacyKeys = @(); sources = @(
                (New-Object @{ type = $official; url = "https://www.volkswagen.es/comunicacion/modelo/golf-gte/"; accessedAt = "2026-10-05"; fields = @("battery.declaredKwh") }),
                (New-Object @{ type = $technical; url = "https://www.km77.com/coches/volkswagen/golf/2020/5-puertas/gte/golf-5p-gte/datos"; accessedAt = "2026-10-05"; fields = @("fuelTankLitres") })
            ); notes = "Capacidad declarada sin clasificación bruto/útil."
        }),
        (New-Object @{
            catalogId = "car-peugeot-3008-p64-plug-in-hybrid-195"
            category = "COCHE"; brand = "Peugeot"; model = "3008"; generation = "P64"; variant = "Plug-in Hybrid 195"
            yearFrom = 2025; yearTo = 2026; kind = "PHEV"; fuel = "GASOLINA"; hybrid = "PLUG_IN"; tank = 55.0
            gross = 21.0; usable = 17.8; declared = $null; declaredType = $null; bodyStyle = "SUV"; drivetrain = "FWD"
            legacyKeys = @(); sources = @(
                (New-Object @{ type = $official; url = "https://www.peugeot.es/content/dam/peugeot/spain/pdf/equipamientos-y-caracteristicas/3008-equipamientos-caracteristicas.pdf"; accessedAt = "2026-10-05"; fields = @("fuelTankLitres", "battery.grossKwh", "battery.usableKwh") })
            ); notes = ""
        }),
        (New-Object @{
            catalogId = "car-ford-kuga-cx482-2-5-phev"
            category = "COCHE"; brand = "Ford"; model = "Kuga"; generation = "CX482-facelift"; variant = "2.5 PHEV"
            yearFrom = 2024; yearTo = $null; kind = "PHEV"; fuel = "GASOLINA"; hybrid = "PLUG_IN"; tank = 45.0
            gross = 14.4; usable = $null; declared = $null; declaredType = $null; bodyStyle = "SUV"; drivetrain = "FWD"
            legacyKeys = @((New-Object @{ category = "COCHE"; brand = "Ford"; model = "Kuga PHEV"; year = 2024 }))
            sources = @(
                (New-Object @{ type = $official; url = "https://www.ford.es/content/dam/guxeu/es/documents/brochures/cars/new-kuga/BRO-ford_new_kuga.pdf"; accessedAt = "2026-10-05"; fields = @("fuelTankLitres", "battery.grossKwh") })
            ); notes = "La capacidad útil publicada por fuentes secundarias no se usa como capacidad operativa hasta completar su trazabilidad editorial."
        }),
        (New-Object @{
            catalogId = "car-hyundai-tucson-nx4-phev"
            category = "COCHE"; brand = "Hyundai"; model = "Tucson"; generation = "NX4"; variant = "1.6 T-GDi PHEV"
            yearFrom = 2021; yearTo = 2024; kind = "PHEV"; fuel = "GASOLINA"; hybrid = "PLUG_IN"; tank = 42.0
            gross = $null; usable = $null; declared = 13.8; declaredType = "UNKNOWN"; bodyStyle = "SUV"; drivetrain = "AWD"
            legacyKeys = @(); sources = @(
                (New-Object @{ type = $official; url = "https://www.hyundai.com/es/es/modelos/nuevo-tucson-phev/rendimiento.html"; accessedAt = "2026-10-05"; fields = @("battery.declaredKwh") }),
                (New-Object @{ type = $technical; url = "https://www.km77.com/coches/hyundai/tucson/2021/estandar/phev/tucson-plug-in-hybrid/datos?nam=1"; accessedAt = "2026-10-05"; fields = @("fuelTankLitres") })
            ); notes = ""
        }),
        (New-Object @{
            catalogId = "car-kia-niro-sg2-phev"
            category = "COCHE"; brand = "Kia"; model = "Niro"; generation = "SG2"; variant = "1.6 GDi PHEV"
            yearFrom = 2022; yearTo = $null; kind = "PHEV"; fuel = "GASOLINA"; hybrid = "PLUG_IN"; tank = 37.0
            gross = $null; usable = $null; declared = 11.1; declaredType = "UNKNOWN"; bodyStyle = "SUV"; drivetrain = "FWD"
            legacyKeys = @(); sources = @(
                (New-Object @{ type = $official; url = "https://www.kia.com/content/dam/kwcms/kme/es/es/assets/contents/catalogos/gama_niro/Niro_julio_22.pdf"; accessedAt = "2026-10-05"; fields = @("fuelTankLitres", "battery.declaredKwh") })
            ); notes = ""
        }),
        (New-Object @{
            catalogId = "car-kia-sorento-mq4-phev"
            category = "COCHE"; brand = "Kia"; model = "Sorento"; generation = "MQ4"; variant = "1.6 T-GDi PHEV"
            yearFrom = 2021; yearTo = 2024; kind = "PHEV"; fuel = "GASOLINA"; hybrid = "PLUG_IN"; tank = 47.0
            gross = $null; usable = $null; declared = 13.8; declaredType = "UNKNOWN"; bodyStyle = "SUV"; drivetrain = "AWD"
            legacyKeys = @(); sources = @(
                (New-Object @{ type = $official; url = "https://press.kia.com/es/es/home/notas-de-prensa/press-releases/2022/todas-las-ventajas-de-la-gama-kia-phev-.html"; accessedAt = "2026-10-05"; fields = @("battery.declaredKwh") }),
                (New-Object @{ type = $technical; url = "https://www.km77.com/coches/kia/sorento/2020/estandar/phev/sorento-hibrido-enchufable/datos?nam=1"; accessedAt = "2026-10-05"; fields = @("fuelTankLitres") })
            ); notes = ""
        }),
        (New-Object @{
            catalogId = "car-seat-leon-kl-facelift-e-hybrid-1-5"
            category = "COCHE"; brand = "SEAT"; model = $leon; generation = "KL-facelift"; variant = "e-Hybrid 1.5"
            yearFrom = 2024; yearTo = $null; kind = "PHEV"; fuel = "GASOLINA"; hybrid = "PLUG_IN"; tank = 40.0
            gross = 25.7; usable = 19.7; declared = $null; declaredType = $null; bodyStyle = "HATCHBACK"; drivetrain = "FWD"
            legacyKeys = @((New-Object @{ category = "COCHE"; brand = "SEAT"; model = $leonEHybrid; year = 2025 }))
            sources = @(
                (New-Object @{ type = $official; url = "https://www.seat.es/sobre-seat/noticias/coches/nuevo-seat-leon-y-leon-sportstourer-style-e-hybrid"; accessedAt = "2026-10-05"; fields = @("battery.grossKwh", "battery.usableKwh") }),
                (New-Object @{ type = $technical; url = "https://www.km77.com/coches/seat/leon/2020/5-puertas/ehybrid/informacion?amp=1"; accessedAt = "2026-10-05"; fields = @("fuelTankLitres") })
            ); notes = ""
        }),
        (New-Object @{
            catalogId = "car-toyota-rav4-xa50-plug-in-hybrid"
            category = "COCHE"; brand = "Toyota"; model = "RAV4"; generation = "XA50"; variant = "Plug-in Hybrid"
            yearFrom = 2021; yearTo = 2025; kind = "PHEV"; fuel = "GASOLINA"; hybrid = "PLUG_IN"; tank = 55.0
            gross = $null; usable = $null; declared = 18.1; declaredType = "UNKNOWN"; bodyStyle = "SUV"; drivetrain = "AWD"
            legacyKeys = @(
                (New-Object @{ category = "COCHE"; brand = "Toyota"; model = "RAV4 Plug-in Hybrid"; year = 2022 }),
                (New-Object @{ category = "COCHE"; brand = "Toyota"; model = "RAV4 GR SPORT PHEV"; year = 2025 })
            )
            sources = @(
                (New-Object @{ type = $official; url = "https://www.toyota.es/world-of-toyota/articles-news-events/que-consume-menos-coche-hibrido-o-hibrido"; accessedAt = "2026-10-05"; fields = @("battery.declaredKwh") }),
                (New-Object @{ type = $technical; url = "https://www.km77.com/coches/toyota/rav4/2019/estandar/plug-in-hybrid/rav4-plug-in-hybrid/datos?nam=1"; accessedAt = "2026-10-05"; fields = @("fuelTankLitres") })
            ); notes = ""
        }),
        (New-Object @{
            catalogId = "car-bmw-x5-g05-lci-xdrive50e"
            category = "COCHE"; brand = "BMW"; model = "X5"; generation = "G05-LCI"; variant = "xDrive50e"
            yearFrom = 2023; yearTo = 2025; kind = "PHEV"; fuel = "GASOLINA"; hybrid = "PLUG_IN"; tank = 69.0
            gross = 29.5; usable = 25.7; declared = $null; declaredType = $null; bodyStyle = "SUV"; drivetrain = "AWD"
            legacyKeys = @(); sources = @(
                (New-Object @{ type = $official; url = "https://www.bmw.es/content/dam/bmw/marketES/bmw_es/brochures-pricelist/SerieX/X5/catalogo-x5.pdf"; accessedAt = "2026-10-05"; fields = @("fuelTankLitres", "battery.grossKwh", "battery.usableKwh") })
            ); notes = ""
        })
    )
}

function Add-IdentityDefinitions {
    param($Editorial, $Vehicle, [hashtable]$BrandNames, [hashtable]$ModelNames, [hashtable]$GenerationNames, [hashtable]$VariantNames)

    $brandKey = $Vehicle.brandId
    if (-not $BrandNames.ContainsKey($brandKey)) { $BrandNames[$brandKey] = $Vehicle.brandDisplayName }

    $modelKey = "$($Vehicle.category)|$($Vehicle.brandId)|$($Vehicle.modelId)"
    if (-not $ModelNames.ContainsKey($modelKey)) { $ModelNames[$modelKey] = $Vehicle.modelDisplayName }

    if ($null -ne $Vehicle.generationId) {
        $generationKey = "$modelKey|$($Vehicle.generationId)"
        if (-not $GenerationNames.ContainsKey($generationKey)) { $GenerationNames[$generationKey] = $Vehicle.generationDisplayName }
    }
    if ($null -ne $Vehicle.variantId) {
        $variantKey = "$modelKey|$($Vehicle.generationId)|$($Vehicle.variantId)"
        if (-not $VariantNames.ContainsKey($variantKey)) { $VariantNames[$variantKey] = $Vehicle.variantDisplayName }
    }
}

function New-EditorialFromLegacy {
    param([string]$RootPath)
    $carPath = Join-Path $RootPath "app\src\main\assets\vehicles.json"
    $motorcyclePath = Join-Path $RootPath "app\src\main\assets\motorcycles.json"
    $all = @()
    foreach ($source in @(
        @{ Category = "COCHE"; File = "vehicles.json"; Path = $carPath },
        @{ Category = "MOTO"; File = "motorcycles.json"; Path = $motorcyclePath }
    )) {
        $entries = Get-Content -Raw -Encoding UTF8 $source.Path | ConvertFrom-Json
        foreach ($entry in $entries) { $all += New-LegacyVehicle $entry $source.Category $source.File }
    }

    $brandNames = @{}; $modelNames = @{}; $generationNames = @{}; $variantNames = @{}
    $vehicles = @()
    foreach ($legacy in $all) {
        $legacy | Add-Member -NotePropertyName brandDisplayName -NotePropertyValue $legacy.legacyKeys[0].brand
        $legacy | Add-Member -NotePropertyName modelDisplayName -NotePropertyValue $legacy.legacyKeys[0].model
        $legacy | Add-Member -NotePropertyName generationDisplayName -NotePropertyValue $null
        $legacy | Add-Member -NotePropertyName variantDisplayName -NotePropertyValue $null
        Add-IdentityDefinitions $null $legacy $brandNames $modelNames $generationNames $variantNames
        $legacy.PSObject.Properties.Remove("brandDisplayName")
        $legacy.PSObject.Properties.Remove("modelDisplayName")
        $legacy.PSObject.Properties.Remove("generationDisplayName")
        $legacy.PSObject.Properties.Remove("variantDisplayName")
        $vehicles += $legacy
    }

    foreach ($addition in Get-ReadyVehicles) {
        $brandId = Get-Slug $addition.brand
        $modelId = Get-Slug $addition.model
        $generationId = if ($null -eq $addition.generation) { $null } else { Get-Slug $addition.generation }
        $variantId = if ($null -eq $addition.variant) { $null } else { Get-Slug $addition.variant }
        foreach ($legacyKey in $addition.legacyKeys) {
            $legacy = $vehicles | Where-Object {
                $_.editorialStatus -eq "ACTIVE" -and $_.legacyKeys.Count -eq 1 -and
                $_.legacyKeys[0].category -eq $legacyKey.category -and $_.legacyKeys[0].brand -eq $legacyKey.brand -and
                $_.legacyKeys[0].model -eq $legacyKey.model -and $_.legacyKeys[0].year -eq $legacyKey.year
            }
            if ($null -eq $legacy) { throw "ready addition $($addition.catalogId): legacyKey no encontrado $($legacyKey.brand) $($legacyKey.model) $($legacyKey.year)" }
            $legacy.editorialStatus = "INACTIVE"
            $legacy.notes = "$($legacy.notes) Sustituido en runtime por $($addition.catalogId)."
        }
        $vehicle = New-Object @{
            catalogId = $addition.catalogId; editorialStatus = "ACTIVE"; provenance = "RESEARCH_READY"; category = $addition.category
            brandId = $brandId; modelId = $modelId; generationId = $generationId; variantId = $variantId
            yearFrom = $addition.yearFrom; yearTo = $addition.yearTo
            powertrain = New-Object @{ kind = $addition.kind; primaryFuel = $addition.fuel; hybridSystem = $addition.hybrid }
            fuelTankLitres = $addition.tank
            battery = New-Object @{ grossKwh = $addition.gross; usableKwh = $addition.usable; declaredKwh = $addition.declared; declaredCapacityType = $addition.declaredType }
            bodyStyle = $addition.bodyStyle; drivetrain = $addition.drivetrain; marketCodes = @("ES"); aliases = @()
            legacyKeys = $addition.legacyKeys; sources = $addition.sources; notes = $addition.notes; editorialRevision = 1
        }
        $vehicle | Add-Member -NotePropertyName brandDisplayName -NotePropertyValue $addition.brand
        $vehicle | Add-Member -NotePropertyName modelDisplayName -NotePropertyValue $addition.model
        $vehicle | Add-Member -NotePropertyName generationDisplayName -NotePropertyValue $addition.generation
        $vehicle | Add-Member -NotePropertyName variantDisplayName -NotePropertyValue $addition.variant
        Add-IdentityDefinitions $null $vehicle $brandNames $modelNames $generationNames $variantNames
        $vehicle.PSObject.Properties.Remove("brandDisplayName"); $vehicle.PSObject.Properties.Remove("modelDisplayName")
        $vehicle.PSObject.Properties.Remove("generationDisplayName"); $vehicle.PSObject.Properties.Remove("variantDisplayName")
        $vehicles += $vehicle
    }

    $brands = @($brandNames.Keys | Sort-Object | ForEach-Object { New-Object @{ id = $_; displayName = $brandNames[$_]; aliases = @() } })
    $models = @($modelNames.Keys | Sort-Object | ForEach-Object {
        $parts = $_.Split("|", 3); New-Object @{ category = $parts[0]; brandId = $parts[1]; id = $parts[2]; displayName = $modelNames[$_]; aliases = @() }
    })
    $generations = @($generationNames.Keys | Sort-Object | ForEach-Object {
        $parts = $_.Split("|", 4); New-Object @{ category = $parts[0]; brandId = $parts[1]; modelId = $parts[2]; id = $parts[3]; displayName = $generationNames[$_] }
    })
    $variants = @($variantNames.Keys | Sort-Object | ForEach-Object {
        $parts = $_.Split("|", 5); New-Object @{ category = $parts[0]; brandId = $parts[1]; modelId = $parts[2]; generationId = if ($parts[3] -eq "") { $null } else { $parts[3] }; id = $parts[4]; displayName = $variantNames[$_] ; aliases = @() }
    })

    return New-Object @{
        schemaVersion = 1
        catalogVersion = 1
        generatedAt = "2026-10-05T00:00:00Z"
        brands = $brands
        models = $models
        generations = $generations
        variants = $variants
        vehicles = @($vehicles | Sort-Object catalogId)
    }
}

function Normalize-SearchText {
    param([string]$Value)
    (Get-Slug $Value) -replace "-", ""
}

function Get-ComparisonSlug {
    param([string]$Value)
    $characters = [System.Collections.Generic.List[char]]::new()
    foreach ($character in $Value.Normalize([Text.NormalizationForm]::FormD).ToCharArray()) {
        if ([Globalization.CharUnicodeInfo]::GetUnicodeCategory($character) -ne [Globalization.UnicodeCategory]::NonSpacingMark) {
            [void]$characters.Add($character)
        }
    }
    ((-join $characters).ToLowerInvariant() -replace "[^a-z0-9]+", "-").Trim("-")
}

# One-time import normalisation. It belongs to the editorial tool rather than
# Android runtime code: future catalog entries must arrive already structured.
function Get-LegacyCarModelStructure {
    param([string]$BrandId, [string]$SourceModel)

    if ($BrandId -eq "mercedes-benz" -and $SourceModel -match "^B\s+\d+") {
        return @{ model = "Clase B"; variant = $SourceModel }
    }
    $families = @{
        "audi" = @("A3 Sportback", "A4 Avant", "TT")
        "bmw" = @("iX1", "X3", "X5")
        "byd" = @("Atto 3")
        "citroen" = @("ë-Berlingo", "Berlingo", "C-Elysée", "C3 Aircross", "C4")
        "cupra" = @("Formentor", "León")
        "dacia" = @("Sandero Stepway", "Duster", "Jogger")
        "fiat" = @("500")
        "ford" = @("Focus", "Kuga", "Puma")
        "hyundai" = @("Ioniq 5", "i20", "ix35", "Kona", "Tucson")
        "jeep" = @("Compass")
        "kia" = @("EV6", "Niro", "Sorento", "Sportage")
        "lexus" = @("LBX")
        "lynk-co" = @("01")
        "mazda" = @("CX-30")
        "mercedes-benz" = @("Clase A", "Clase B", "CLK", "GLC")
        "mg" = @("MG4", "ZS")
        "mitsubishi" = @("ASX")
        "nissan" = @("Qashqai", "Juke", "Pulsar")
        "omoda" = @("7")
        "opel" = @("Corsa", "Astra")
        "peugeot" = @("3008", "208")
        "renault" = @("Megane", "Austral", "Captur", "Clio")
        "seat" = @("León", "Ateca", "Ibiza")
        "skoda" = @("Fabia")
        "suzuki" = @("Swift")
        "tesla" = @("Model 3")
        "toyota" = @("Yaris Cross", "GR Yaris", "Yaris", "Corolla", "RAV4")
        "volkswagen" = @("ID.4", "Golf", "Polo", "Tiguan")
        "volvo" = @("EX30", "XC60")
    }
    $sourceSlug = Get-ComparisonSlug $SourceModel
    $base = $null
    foreach ($candidate in @($families[$BrandId] | Sort-Object { $_.Length } -Descending)) {
        $familySlug = Get-ComparisonSlug $candidate
        if ($sourceSlug -eq $familySlug -or $sourceSlug.StartsWith("$familySlug-", [StringComparison]::Ordinal)) {
            $base = $candidate
            break
        }
    }
    if ($null -eq $base) { throw "legacy normalisation: no base model for $BrandId / $SourceModel" }
    $variant = $SourceModel.Substring($base.Length).Trim()
    return @{ model = $base; variant = if ([string]::IsNullOrWhiteSpace($variant)) { $null } else { $variant } }
}

function Normalize-LegacyModelStructure {
    param($Catalog)

    $modelLookup = @{}; foreach ($item in $Catalog.models) { $modelLookup["$($item.category)|$($item.brandId)|$($item.id)"] = $item }
    $generationLookup = @{}; foreach ($item in $Catalog.generations) { $generationLookup["$($item.category)|$($item.brandId)|$($item.modelId)|$($item.id)"] = $item }
    $variantLookup = @{}; foreach ($item in $Catalog.variants) { $variantLookup["$($item.category)|$($item.brandId)|$($item.modelId)|$($item.generationId)|$($item.id)"] = $item }
    $modelNames = @{}; $generationNames = @{}; $variantNames = @{}

    foreach ($vehicle in $Catalog.vehicles) {
        $oldModelId = $vehicle.modelId
        $oldGenerationId = $vehicle.generationId
        $oldVariantId = $vehicle.variantId
        $oldModel = $modelLookup["$($vehicle.category)|$($vehicle.brandId)|$oldModelId"]
        if ($null -eq $oldModel) { throw "legacy normalisation: missing source model for $($vehicle.catalogId)" }

        $modelDisplayName = $oldModel.displayName
        $variantDisplayName = if ($null -eq $oldVariantId) { $null } else { $variantLookup["$($vehicle.category)|$($vehicle.brandId)|$oldModelId|$oldGenerationId|$oldVariantId"].displayName }
        if ($vehicle.provenance -eq "LEGACY_IMPORT" -and $vehicle.category -eq "COCHE" -and $oldModelId.StartsWith("legacy-", [StringComparison]::Ordinal)) {
            $structure = Get-LegacyCarModelStructure $vehicle.brandId $oldModel.displayName
            $modelDisplayName = $structure.model
            $vehicle.modelId = Get-Slug $modelDisplayName
            $variantDisplayName = $structure.variant
            $vehicle.variantId = if ($null -eq $variantDisplayName) { $null } else { Get-Slug $variantDisplayName }
        }

        $modelKey = "$($vehicle.category)|$($vehicle.brandId)|$($vehicle.modelId)"
        if ($modelNames.ContainsKey($modelKey) -and $modelNames[$modelKey] -ne $modelDisplayName) { throw "legacy normalisation: conflicting model displayName for $modelKey" }
        $modelNames[$modelKey] = $modelDisplayName
        if ($null -ne $vehicle.generationId) {
            $generation = $generationLookup["$($vehicle.category)|$($vehicle.brandId)|$oldModelId|$oldGenerationId"]
            if ($null -eq $generation) { throw "legacy normalisation: missing generation for $($vehicle.catalogId)" }
            $generationNames["$modelKey|$($vehicle.generationId)"] = $generation.displayName
        }
        if ($null -ne $vehicle.variantId) {
            $variantNames["$modelKey|$($vehicle.generationId)|$($vehicle.variantId)"] = $variantDisplayName
        }
    }

    $Catalog.models = @($modelNames.Keys | Sort-Object | ForEach-Object {
        $parts = $_.Split("|", 3); New-Object @{ category = $parts[0]; brandId = $parts[1]; id = $parts[2]; displayName = $modelNames[$_]; aliases = @() }
    })
    $Catalog.generations = @($generationNames.Keys | Sort-Object | ForEach-Object {
        $parts = $_.Split("|", 4); New-Object @{ category = $parts[0]; brandId = $parts[1]; modelId = $parts[2]; id = $parts[3]; displayName = $generationNames[$_] }
    })
    $Catalog.variants = @($variantNames.Keys | Sort-Object | ForEach-Object {
        $parts = $_.Split("|", 5); New-Object @{ category = $parts[0]; brandId = $parts[1]; modelId = $parts[2]; generationId = if ($parts[3] -eq "") { $null } else { $parts[3] }; id = $parts[4]; displayName = $variantNames[$_]; aliases = @() }
    })
}

function Test-EditorialCatalog {
    param($Catalog)
    if ($Catalog.schemaVersion -ne 1) { throw "catalog: schemaVersion no soportada '$($Catalog.schemaVersion)'" }
    if ($Catalog.catalogVersion -lt 1) { throw "catalog: catalogVersion debe ser positivo" }
    if ([string]::IsNullOrWhiteSpace($Catalog.generatedAt)) { throw "catalog: generatedAt obligatorio" }

    $brandIds = @{}; foreach ($item in $Catalog.brands) {
        Assert-Id $item.id "brand $($item.displayName)"
        if ([string]::IsNullOrWhiteSpace($item.displayName)) { throw "brand $($item.id): displayName obligatorio" }
        if ($brandIds[$item.id]) { throw "brand: id duplicado $($item.id)" }
        $brandIds[$item.id] = $true
    }
    $modelIds = @{}; foreach ($item in $Catalog.models) {
        Assert-Id $item.id "model $($item.displayName)"
        if ([string]::IsNullOrWhiteSpace($item.displayName)) { throw "model $($item.id): displayName obligatorio" }
        $key = "$($item.category)|$($item.brandId)|$($item.id)"
        if ($modelIds[$key]) { throw "model: id duplicado $key" }
        if (-not $brandIds[$item.brandId]) { throw "model ${key}: brandId inexistente" }
        $modelIds[$key] = $true
    }
    $generationIds = @{}; foreach ($item in $Catalog.generations) { Assert-Id $item.id "generation $($item.displayName)"; $modelKey = "$($item.category)|$($item.brandId)|$($item.modelId)"; if (-not $modelIds[$modelKey]) { throw "generation $($item.id): model inexistente" }; $key = "$modelKey|$($item.id)"; if ($generationIds[$key]) { throw "generation: id duplicado $key" }; $generationIds[$key] = $true }
    $variantIds = @{}; foreach ($item in $Catalog.variants) { Assert-Id $item.id "variant $($item.displayName)"; $modelKey = "$($item.category)|$($item.brandId)|$($item.modelId)"; if (-not $modelIds[$modelKey]) { throw "variant $($item.id): model inexistente" }; if ($null -ne $item.generationId -and -not $generationIds["$modelKey|$($item.generationId)"]) { throw "variant $($item.id): generation inexistente" }; $key = "$modelKey|$($item.generationId)|$($item.id)"; if ($variantIds[$key]) { throw "variant: id duplicado $key" }; $variantIds[$key] = $true }

    $allowedCategory = @("COCHE", "MOTO"); $allowedKind = @("ICE", "HEV", "PHEV", "BEV"); $allowedFuel = @("GASOLINA", "DIESEL"); $allowedHybrid = @("NONE", "MILD", "FULL", "PLUG_IN", "BATTERY_ELECTRIC"); $allowedStatus = @("DRAFT", "REVIEW", "ACTIVE", "INACTIVE"); $allowedProvenance = @("LEGACY_IMPORT", "RESEARCH_READY")
    $catalogIds = @{}; $legacyKeys = @{}; $technicalRanges = @{}
    foreach ($vehicle in $Catalog.vehicles) {
        $context = "vehicle $($vehicle.catalogId)"
        Assert-Id $vehicle.catalogId $context
        if ($catalogIds[$vehicle.catalogId]) { throw "${context}: catalogId duplicado" }; $catalogIds[$vehicle.catalogId] = $true
        if ($vehicle.category -notin $allowedCategory) { throw "$context.category: enum desconocido '$($vehicle.category)'" }
        if ($vehicle.editorialStatus -notin $allowedStatus) { throw "$context.editorialStatus: enum desconocido '$($vehicle.editorialStatus)'" }
        if ($vehicle.provenance -notin $allowedProvenance) { throw "$context.provenance: enum desconocido '$($vehicle.provenance)'" }
        $modelKey = "$($vehicle.category)|$($vehicle.brandId)|$($vehicle.modelId)"; if (-not $modelIds[$modelKey]) { throw "${context}: modelId inexistente" }
        if ($null -ne $vehicle.generationId -and -not $generationIds["$modelKey|$($vehicle.generationId)"]) { throw "${context}: generationId inexistente" }
        if ($null -ne $vehicle.variantId -and -not $variantIds["$modelKey|$($vehicle.generationId)|$($vehicle.variantId)"]) { throw "${context}: variantId inexistente" }
        if ([int]$vehicle.yearFrom -lt 1886) { throw "$context.yearFrom: año inválido" }
        if ($null -ne $vehicle.yearTo -and [int]$vehicle.yearTo -lt [int]$vehicle.yearFrom) { throw "$context.yearTo: anterior a yearFrom" }
        if ($vehicle.powertrain.kind -notin $allowedKind) { throw "$context.powertrain.kind: enum desconocido" }
        if ($null -ne $vehicle.powertrain.primaryFuel -and $vehicle.powertrain.primaryFuel -notin $allowedFuel) { throw "$context.powertrain.primaryFuel: enum desconocido" }
        if ($null -ne $vehicle.powertrain.hybridSystem -and $vehicle.powertrain.hybridSystem -notin $allowedHybrid) { throw "$context.powertrain.hybridSystem: enum desconocido" }
        $battery = $vehicle.battery
        foreach ($name in @("grossKwh", "usableKwh", "declaredKwh")) { $value = Get-Property $battery $name; if ($null -ne $value) { Assert-PositiveNumber $value "$context.battery.$name" } }
        if ($null -ne $battery.usableKwh -and $null -ne $battery.grossKwh -and [double]$battery.usableKwh -gt [double]$battery.grossKwh) { throw "$context.battery: usableKwh no puede superar grossKwh" }
        if ($null -ne $battery.declaredKwh -and $battery.declaredCapacityType -ne "UNKNOWN") { throw "$context.battery: declaredKwh requiere declaredCapacityType UNKNOWN" }
        if ($null -eq $battery.declaredKwh -and $null -ne $battery.declaredCapacityType) { throw "$context.battery: declaredCapacityType requiere declaredKwh" }
        $hasBattery = $null -ne $battery.grossKwh -or $null -ne $battery.usableKwh -or $null -ne $battery.declaredKwh
        $hasTank = $null -ne $vehicle.fuelTankLitres
        if ($hasTank) { Assert-PositiveNumber $vehicle.fuelTankLitres "$context.fuelTankLitres" }
        switch ($vehicle.powertrain.kind) {
            "BEV" { if ($hasTank) { throw "${context}: BEV no puede tener depósito" }; if (-not $hasBattery) { throw "${context}: BEV requiere batería" } }
            "PHEV" { if (-not $hasTank) { throw "${context}: PHEV requiere depósito" }; if (-not $hasBattery) { throw "${context}: PHEV requiere batería" } }
            "ICE" { if (-not $hasTank) { throw "${context}: ICE requiere depósito" }; if ($hasBattery) { throw "${context}: ICE no admite batería de tracción en schema v1" } }
            "HEV" { if (-not $hasTank) { throw "${context}: HEV requiere depósito" }; if ($hasBattery) { throw "${context}: HEV no admite batería de tracción en schema v1" } }
        }
        if ($vehicle.editorialStatus -eq "ACTIVE" -and ($null -eq $vehicle.sources -or $vehicle.sources.Count -eq 0)) { throw "${context}: ACTIVE requiere sources" }
        foreach ($alias in $vehicle.aliases) { if ([string]::IsNullOrWhiteSpace($alias)) { throw "$context.aliases: alias vacío" } }
        foreach ($legacy in $vehicle.legacyKeys) {
            $legacyKey = "$($legacy.category)|$($legacy.brand)|$($legacy.model)|$($legacy.year)"
            if ($vehicle.editorialStatus -eq "ACTIVE" -and $legacyKeys.ContainsKey($legacyKey)) { throw "$context.legacyKeys: duplicada con $($legacyKeys[$legacyKey])" }
            if ($vehicle.editorialStatus -eq "ACTIVE") { $legacyKeys[$legacyKey] = $vehicle.catalogId }
        }
        $rangeKey = "$($vehicle.category)|$($vehicle.brandId)|$($vehicle.modelId)|$($vehicle.generationId)|$($vehicle.variantId)|$($vehicle.bodyStyle)|$($vehicle.drivetrain)|$($vehicle.powertrain.kind)|$($vehicle.powertrain.primaryFuel)"
        if ($vehicle.editorialStatus -eq "ACTIVE") {
            $from = [int]$vehicle.yearFrom; $to = if ($null -eq $vehicle.yearTo) { 9999 } else { [int]$vehicle.yearTo }
            if ($technicalRanges.ContainsKey($rangeKey)) { foreach ($other in $technicalRanges[$rangeKey]) { if ($from -le $other.to -and $to -ge $other.from) { throw "${context}: rango solapado con $($other.id)" } } }
            if (-not $technicalRanges.ContainsKey($rangeKey)) { $technicalRanges[$rangeKey] = @() }
            $technicalRanges[$rangeKey] += (New-Object @{ id = $vehicle.catalogId; from = $from; to = $to })
        }
    }
}

function Get-DisplayLookup {
    param($Catalog)
    $brands = @{}; foreach ($item in $Catalog.brands) { $brands[$item.id] = $item }
    $models = @{}; foreach ($item in $Catalog.models) { $models["$($item.category)|$($item.brandId)|$($item.id)"] = $item }
    $generations = @{}; foreach ($item in $Catalog.generations) { $generations["$($item.category)|$($item.brandId)|$($item.modelId)|$($item.id)"] = $item }
    $variants = @{}; foreach ($item in $Catalog.variants) { $variants["$($item.category)|$($item.brandId)|$($item.modelId)|$($item.generationId)|$($item.id)"] = $item }
    return @{ brands = $brands; models = $models; generations = $generations; variants = $variants }
}

function New-RuntimeCatalog {
    param($Editorial)
    $lookup = Get-DisplayLookup $Editorial
    $vehicles = @()
    foreach ($entry in ($Editorial.vehicles | Where-Object { $_.editorialStatus -eq "ACTIVE" } | Sort-Object catalogId)) {
        $brand = $lookup.brands[$entry.brandId]
        $model = $lookup.models["$($entry.category)|$($entry.brandId)|$($entry.modelId)"]
        $generation = if ($null -eq $entry.generationId) { $null } else { $lookup.generations["$($entry.category)|$($entry.brandId)|$($entry.modelId)|$($entry.generationId)"] }
        $variant = if ($null -eq $entry.variantId) { $null } else { $lookup.variants["$($entry.category)|$($entry.brandId)|$($entry.modelId)|$($entry.generationId)|$($entry.variantId)"] }
        $vehicles += New-Object @{
            catalogId = $entry.catalogId; category = $entry.category
            brand = New-Object @{ id = $brand.id; displayName = $brand.displayName; aliases = @($brand.aliases) }
            model = New-Object @{ id = $model.id; displayName = $model.displayName; aliases = @($model.aliases) }
            generation = if ($null -eq $generation) { $null } else { New-Object @{ id = $generation.id; displayName = $generation.displayName } }
            variant = if ($null -eq $variant) { $null } else { New-Object @{ id = $variant.id; displayName = $variant.displayName; aliases = @($variant.aliases) } }
            yearFrom = $entry.yearFrom; yearTo = $entry.yearTo; powertrain = $entry.powertrain
            fuelTankLitres = $entry.fuelTankLitres; battery = $entry.battery
            bodyStyle = $entry.bodyStyle; drivetrain = $entry.drivetrain; marketCodes = @($entry.marketCodes); aliases = @($entry.aliases); legacyKeys = @($entry.legacyKeys)
        }
    }
    return New-Object @{ schemaVersion = $Editorial.schemaVersion; catalogVersion = $Editorial.catalogVersion; generatedAt = $Editorial.generatedAt; vehicles = $vehicles }
}

function ConvertTo-CanonicalObject {
    param($Value)
    if ($null -eq $Value) { return $null }
    # Strings expose adapted PowerShell properties such as Length. Handle them before
    # PSCustomObject so a one-item string array remains ["ES"], not [{"Length":2}].
    if ($Value -is [string]) { return $Value }
    if ($Value -is [System.Collections.IDictionary]) {
        $ordered = [ordered]@{}
        foreach ($key in @($Value.Keys | Sort-Object)) { $ordered[$key] = ConvertTo-CanonicalObject $Value[$key] }
        return [pscustomobject]$ordered
    }
    if ($Value -is [pscustomobject]) {
        $ordered = [ordered]@{}
        foreach ($property in @($Value.PSObject.Properties | Sort-Object Name)) {
            $ordered[$property.Name] = ConvertTo-CanonicalObject $property.Value
        }
        return [pscustomobject]$ordered
    }
    if ($Value -is [System.Collections.IEnumerable] -and -not ($Value -is [string])) {
        # The unary comma preserves empty and single-item arrays when PowerShell
        # returns from this recursive function.
        $items = @($Value | ForEach-Object { ConvertTo-CanonicalObject $_ })
        return ,$items
    }
    return $Value
}

function Write-DeterministicJson {
    param($Object, [string]$Path)
    $directory = Split-Path -Parent $Path
    if (-not (Test-Path $directory)) { New-Item -ItemType Directory -Force -Path $directory | Out-Null }
    $json = (ConvertTo-CanonicalObject $Object) | ConvertTo-Json -Depth 100 -Compress
    [IO.File]::WriteAllText($Path, $json + "`n", [Text.UTF8Encoding]::new($false))
}

function Write-EditorialJson {
    param($Object, [string]$Path)
    $directory = Split-Path -Parent $Path
    if (-not (Test-Path $directory)) { New-Item -ItemType Directory -Force -Path $directory | Out-Null }
    # Editorial is intentionally readable for data maintenance. Runtime is the
    # compact deterministic artifact whose bytes are checksummed.
    $json = $Object | ConvertTo-Json -Depth 100
    [IO.File]::WriteAllText($Path, $json + "`n", [Text.UTF8Encoding]::new($false))
}

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if ($BootstrapLegacy) {
    $editorial = New-EditorialFromLegacy $root
    Write-EditorialJson $editorial $EditorialPath
} elseif (-not (Test-Path $EditorialPath)) {
    throw "No existe el catálogo editorial: $EditorialPath. Ejecuta primero con -BootstrapLegacy."
}

$editorial = Get-Content -Raw -Encoding UTF8 $EditorialPath | ConvertFrom-Json
if ($NormalizeLegacyModels) {
    Normalize-LegacyModelStructure $editorial
    Write-EditorialJson $editorial $EditorialPath
}
Test-EditorialCatalog $editorial
$runtime = New-RuntimeCatalog $editorial
Write-DeterministicJson $runtime $RuntimePath
Write-DeterministicJson $runtime $AssetPath
$bytes = [IO.File]::ReadAllBytes($RuntimePath)
$activeCount = @($runtime.vehicles).Count
Write-Output "Editorial validado: $(@($editorial.vehicles).Count) registros"
Write-Output "Runtime generado: $activeCount registros"
Write-Output "SHA-256: $(Get-Sha256 $bytes)"
Write-Output "Bytes: $($bytes.Length)"
