[CmdletBinding()]
param(
    [switch]$BootstrapLegacy,
    [switch]$NormalizeLegacyModels,
    [switch]$ConsolidateMotorcycles,
    [switch]$NormalizeDenseCarFamilies,
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

function Set-EditorialProperty {
    param($Object, [string]$Name, $Value)
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        $Object | Add-Member -NotePropertyName $Name -NotePropertyValue $Value
    } else {
        $property.Value = $Value
    }
}

function Get-MotorcycleTechnicalKey {
    param($Vehicle)
    @(
        $Vehicle.category,
        $Vehicle.brandId,
        $Vehicle.modelId,
        $Vehicle.generationId,
        $Vehicle.variantId,
        $Vehicle.powertrain.kind,
        $Vehicle.powertrain.primaryFuel,
        $Vehicle.powertrain.hybridSystem,
        $Vehicle.fuelTankLitres,
        $Vehicle.battery.grossKwh,
        $Vehicle.battery.usableKwh,
        $Vehicle.battery.declaredKwh,
        $Vehicle.battery.declaredCapacityType,
        $Vehicle.bodyStyle,
        $Vehicle.drivetrain,
        (@($Vehicle.marketCodes) -join ",")
    ) -join "|"
}

function Consolidate-MotorcycleRun {
    param([System.Collections.Generic.List[object]]$Run)
    if ($Run.Count -lt 2) { return 0 }

    $canonical = $Run[0]
    $retired = @($Run | Select-Object -Skip 1)
    $canonical.yearFrom = [int]$Run[0].yearFrom
    $canonical.yearTo = [int]$Run[$Run.Count - 1].yearTo
    $keys = @()
    foreach ($entry in $Run) { $keys += @($entry.legacyKeys) }
    $canonical.legacyKeys = @($keys | Sort-Object category,brand,model,year)
    Set-EditorialProperty $canonical "supersededCatalogIds" @($retired | ForEach-Object { $_.catalogId })
    $canonical.editorialRevision = [int]$canonical.editorialRevision + 1
    $canonical.notes = "$($canonical.notes) Consolidación editorial de años técnicamente equivalentes."

    foreach ($entry in $retired) {
        $entry.editorialStatus = "INACTIVE"
        Set-EditorialProperty $entry "replacedByCatalogId" $canonical.catalogId
        $entry.editorialRevision = [int]$entry.editorialRevision + 1
        $entry.notes = "$($entry.notes) Consolidado en $($canonical.catalogId)."
    }
    return $retired.Count
}

function Consolidate-LegacyMotorcycles {
    param($Catalog)

    $activeMotorcycles = @($Catalog.vehicles | Where-Object {
        $_.category -eq "MOTO" -and $_.editorialStatus -eq "ACTIVE" -and $_.provenance -eq "LEGACY_IMPORT"
    })
    $retiredCount = 0
    foreach ($group in ($activeMotorcycles | Group-Object { Get-MotorcycleTechnicalKey $_ })) {
        $ordered = @($group.Group | Sort-Object yearFrom,catalogId)
        $run = [System.Collections.Generic.List[object]]::new()
        $previous = $null
        foreach ($vehicle in $ordered) {
            if ($null -ne $previous -and [int]$vehicle.yearFrom -ne ([int]$previous.yearTo + 1)) {
                $retiredCount += Consolidate-MotorcycleRun $run
                $run = [System.Collections.Generic.List[object]]::new()
            }
            [void]$run.Add($vehicle)
            $previous = $vehicle
        }
        $retiredCount += Consolidate-MotorcycleRun $run
    }
    return $retiredCount
}

# Editorial normalization for researched, dense car families.  This intentionally
# lives in the generator: Android consumes only the resulting structured catalog
# and never infers a generation or a model from a legacy display string.
function Add-CatalogIdentity {
    param($Catalog, [string]$Category, [string]$BrandId, [string]$ModelId, [string]$GenerationId, [string]$GenerationName, [string]$VariantId, [string]$VariantName)

    if ($null -eq ($Catalog.models | Where-Object { $_.category -eq $Category -and $_.brandId -eq $BrandId -and $_.id -eq $ModelId } | Select-Object -First 1)) {
        throw "dense car normalization: model inexistente $Category/$BrandId/$ModelId"
    }
    if ($null -ne $GenerationId -and $null -eq ($Catalog.generations | Where-Object {
        $_.category -eq $Category -and $_.brandId -eq $BrandId -and $_.modelId -eq $ModelId -and $_.id -eq $GenerationId
    } | Select-Object -First 1)) {
        $Catalog.generations = @($Catalog.generations) + (New-Object @{ category = $Category; brandId = $BrandId; modelId = $ModelId; id = $GenerationId; displayName = $GenerationName })
    }
    if ($null -ne $VariantId -and $null -eq ($Catalog.variants | Where-Object {
        $_.category -eq $Category -and $_.brandId -eq $BrandId -and $_.modelId -eq $ModelId -and $_.generationId -eq $GenerationId -and $_.id -eq $VariantId
    } | Select-Object -First 1)) {
        $Catalog.variants = @($Catalog.variants) + (New-Object @{ category = $Category; brandId = $BrandId; modelId = $ModelId; generationId = $GenerationId; id = $VariantId; displayName = $VariantName; aliases = @() })
    }
}

function New-DenseCarSource {
    param([string]$Url, [string[]]$Fields, [string]$Type = "TECHNICAL_REFERENCE")
    return New-Object @{ type = $Type; url = $Url; accessedAt = "2026-10-06"; fields = $Fields }
}

function New-DenseCarSpec {
    param(
        [string]$BrandId, [string]$ModelId, [string]$GenerationId, [string]$GenerationName,
        [string]$VariantId, [string]$VariantName, [int]$YearFrom, [Nullable[int]]$YearTo,
        [string]$Kind, [string]$Fuel, [string]$Hybrid, [Nullable[double]]$Tank,
        [Nullable[double]]$Gross, [Nullable[double]]$Usable, [Nullable[double]]$Declared, [string]$DeclaredType,
        [string]$LegacyHybrid, [string]$NewCatalogId, [object[]]$Sources, [string]$Notes
    )
    return New-Object @{
        brandId = $BrandId; modelId = $ModelId; generationId = $GenerationId; generationName = $GenerationName
        variantId = $VariantId; variantName = $VariantName; yearFrom = $YearFrom; yearTo = $YearTo
        kind = $Kind; fuel = $Fuel; hybrid = $Hybrid; tank = $Tank
        gross = $Gross; usable = $Usable; declared = $Declared; declaredType = $DeclaredType
        legacyHybrid = $LegacyHybrid; newCatalogId = $NewCatalogId; sources = @($Sources); notes = $Notes
    }
}

function Test-DenseCarLegacyMatch {
    param($Vehicle, $Spec)
    if ($Vehicle.powertrain.kind -ne $Spec.kind) { return $false }
    if ("$($Vehicle.powertrain.primaryFuel)" -ne "$($Spec.fuel)") { return $false }
    $expectedLegacyHybrid = if ([string]::IsNullOrWhiteSpace($Spec.legacyHybrid)) { $Spec.hybrid } else { $Spec.legacyHybrid }
    if ("$($Vehicle.powertrain.hybridSystem)" -ne "$expectedLegacyHybrid") { return $false }
    # String comparison deliberately treats JSON null and an omitted optional
    # PowerShell value alike, while retaining the exact numeric text from the
    # catalog (we never use zero as an absence sentinel).
    if ("$($Vehicle.fuelTankLitres)" -ne "$($Spec.tank)") { return $false }
    if ("$($Vehicle.battery.grossKwh)" -ne "$($Spec.gross)" -or "$(($Vehicle.battery.usableKwh))" -ne "$($Spec.usable)" -or
        "$(($Vehicle.battery.declaredKwh))" -ne "$($Spec.declared)" -or "$($Vehicle.battery.declaredCapacityType)" -ne "$($Spec.declaredType)") { return $false }
    return @($Vehicle.legacyKeys | ForEach-Object { [int]$_.year } | Where-Object {
        $_ -lt [int]$Spec.yearFrom -or ($null -ne $Spec.yearTo -and $_ -gt [int]$Spec.yearTo)
    }).Count -eq 0
}

function Set-DenseCarRecord {
    param($Vehicle, $Spec, $LegacyKeys)
    $Vehicle.editorialStatus = "ACTIVE"
    $Vehicle.provenance = "RESEARCH_READY"
    $Vehicle.category = "COCHE"
    $Vehicle.brandId = $Spec.brandId; $Vehicle.modelId = $Spec.modelId
    $Vehicle.generationId = $Spec.generationId; $Vehicle.variantId = $Spec.variantId
    $Vehicle.yearFrom = [int]$Spec.yearFrom; $Vehicle.yearTo = $Spec.yearTo
    $Vehicle.powertrain = New-Object @{ kind = $Spec.kind; primaryFuel = if ([string]::IsNullOrWhiteSpace($Spec.fuel)) { $null } else { $Spec.fuel }; hybridSystem = $Spec.hybrid }
    $Vehicle.fuelTankLitres = $Spec.tank
    $Vehicle.battery = New-Object @{ grossKwh = $Spec.gross; usableKwh = $Spec.usable; declaredKwh = $Spec.declared; declaredCapacityType = if ([string]::IsNullOrWhiteSpace($Spec.declaredType)) { $null } else { $Spec.declaredType } }
    $Vehicle.bodyStyle = if ($Spec.brandId -eq "citroen" -and $Spec.modelId -eq "berlingo") { "MPV" } elseif ($Spec.brandId -eq "nissan") { "SUV" } else { "HATCHBACK" }
    $Vehicle.drivetrain = "FWD"
    $Vehicle.marketCodes = @("ES")
    $Vehicle.aliases = @()
    $Vehicle.legacyKeys = @($LegacyKeys | Sort-Object category,brand,model,year)
    $Vehicle.sources = @($Spec.sources)
    $Vehicle.notes = $Spec.notes
    $Vehicle.editorialRevision = [int]$Vehicle.editorialRevision + 1
    Set-EditorialProperty $Vehicle "denseCarNormalizationVersion" 1
}

function Normalize-DenseCarFamilies {
    param($Catalog)

    $alreadyNormalized = @($Catalog.vehicles | Where-Object { (Get-Property $_ "denseCarNormalizationVersion") -eq 1 })
    $needsNullRepair = @($alreadyNormalized | Where-Object { $_.battery.declaredCapacityType -eq "" }).Count -gt 0
    if ($alreadyNormalized.Count -gt 0 -and -not $needsNullRepair) { return 0 }

    $km77CorsaC = "https://www.km77.com/coches/opel/corsa/2001/5-puertas/elegance/corsa-5p-elegance-14-16v-aut/datos"
    $km77CorsaD = "https://www.km77.com/coches/opel/corsa/2006/5-puertas/enjoy/corsa-5p-enjoy-12/datos"
    $km77CorsaE = "https://www.km77.com/coches/opel/corsa/2011/5-puertas/selective/corsa-5p-selective-14-100-cv-startstop/datos"
    $km77CorsaF = "https://www.km77.com/coches/opel/corsa/2020/5-puertas/datos"
    $km77CorsaElectric = "https://www.km77.com/coches/opel/corsa/2020/5-puertas/corsa-e/corsa-e/datos"
    $opelCorsaMy26 = "https://www.opel.es/hub/datasheet/corsa-my26-es.html"
    $km77BerlingoI = "https://www.km77.com/coches/citroen/berlingo/2003/estandar/xtr/berlingo-hdi-92-xtr/datos"
    $km77BerlingoB9 = "https://www.km77.com/coches/citroen/berlingo/2008/estandar/xtr/nuevo-berlingo-xtr-16-hdi-90/datos"
    $km77BerlingoK9 = "https://www.km77.com/coches/citroen/berlingo/2019/talla-m/datos?market%5B%5D=discontinued"
    $citroenEBerlingo50 = "https://www.citroen.es/content/dam/citroen/spain/pdf/catalogos/C_BERLINGO_MULTI.pdf"
    $citroenEBerlingo54 = "https://www.citroen.es/vehiculos-citroen/e-berlingo.html"
    $km77QashqaiJ10 = "https://www.km77.com/coches/nissan/qashqai/2007/datos?hp=1"
    $km77QashqaiJ11 = "https://www.km77.com/coches/nissan/qashqai/2014/estandar/n-tec/qashqai-12i-dig-t-115cv-stopstart-4x2-n-tec/datos"
    $qashqaiJ11DieselAwd = "https://www.autohints.com/es/nissan-qashqai-ii-j11-facelift-2017-1.7-dci-150-hp-4x4-version-28140"
    $nissanQashqaiJ12 = "https://www.nissan.es/content/dam/Nissan/es/brochures/E-Catalogo_Nissan_Qashqai_ES.pdf"

    $specs = @(
        (New-DenseCarSpec "opel" "corsa" "c" "C" "gasolina" "Gasolina" 2001 2006 "ICE" "GASOLINA" "NONE" 44 $null $null $null $null $null $null @((New-DenseCarSource $km77CorsaC @("fuelTankLitres"))) "Corsa C: gasolina con depósito de 44 L."),
        (New-DenseCarSpec "opel" "corsa" "c" "C" "diesel" "Diésel" 2001 2006 "ICE" "DIESEL" "NONE" 44 $null $null $null $null $null $null @((New-DenseCarSource $km77CorsaC @("fuelTankLitres"))) "Corsa C: diésel con depósito de 44 L."),
        (New-DenseCarSpec "opel" "corsa" "d" "D" "gasolina" "Gasolina" 2006 2014 "ICE" "GASOLINA" "NONE" 45 $null $null $null $null $null $null @((New-DenseCarSource $km77CorsaD @("fuelTankLitres"))) "Corsa D: gasolina con depósito de 45 L."),
        (New-DenseCarSpec "opel" "corsa" "d" "D" "diesel" "Diésel" 2006 2014 "ICE" "DIESEL" "NONE" 45 $null $null $null $null $null $null @((New-DenseCarSource $km77CorsaD @("fuelTankLitres"))) "Corsa D: diésel con depósito de 45 L."),
        (New-DenseCarSpec "opel" "corsa" "e" "E" "gasolina" "Gasolina" 2015 2019 "ICE" "GASOLINA" "NONE" 45 $null $null $null $null $null $null @((New-DenseCarSource $km77CorsaE @("fuelTankLitres"))) "Corsa E: gasolina con depósito de 45 L."),
        (New-DenseCarSpec "opel" "corsa" "e" "E" "diesel" "Diésel" 2015 2019 "ICE" "DIESEL" "NONE" 45 $null $null $null $null $null $null @((New-DenseCarSource $km77CorsaE @("fuelTankLitres"))) "Corsa E: diésel con depósito de 45 L."),
        (New-DenseCarSpec "opel" "corsa" "f" "F" "gasolina" "Gasolina" 2019 2026 "ICE" "GASOLINA" "NONE" 44 $null $null $null $null $null $null @((New-DenseCarSource $opelCorsaMy26 @("fuelTankLitres" ) "OFFICIAL_MANUFACTURER"),(New-DenseCarSource $km77CorsaF @("yearFrom","fuelTankLitres"))) "Corsa F: gasolina con depósito de 44 L."),
        (New-DenseCarSpec "opel" "corsa" "f" "F" "diesel" "Diésel" 2019 2023 "ICE" "DIESEL" "NONE" 41 $null $null $null $null $null $null @((New-DenseCarSource $km77CorsaF @("yearFrom","yearTo","fuelTankLitres"))) "Corsa F: diésel con depósito de 41 L."),
        (New-DenseCarSpec "opel" "corsa" "f" "F" "hybrid" "Hybrid" 2024 2026 "HEV" $null "MILD" 44 $null $null $null $null "FULL" $null @((New-DenseCarSource $opelCorsaMy26 @("yearFrom","fuelTankLitres") "OFFICIAL_MANUFACTURER")) "Corsa F: sistema mild-hybrid de 48 V, separado del híbrido e-POWER de Nissan."),
        (New-DenseCarSpec "opel" "corsa" "f" "F" "electric-50" "Electric 50 kWh" 2020 2023 "BEV" $null "BATTERY_ELECTRIC" $null 50 46 $null $null $null "car-opel-corsa-f-electric-50" @((New-DenseCarSource $km77CorsaElectric @("yearFrom","yearTo","battery.grossKwh","battery.usableKwh"))) "Corsa-e: 50 kWh brutos y 46 kWh utilizables."),
        (New-DenseCarSpec "opel" "corsa" "f" "F" "electric-51" "Electric 51 kWh" 2024 2026 "BEV" $null "BATTERY_ELECTRIC" $null $null $null 51 "UNKNOWN" $null "car-opel-corsa-f-electric-51" @((New-DenseCarSource $opelCorsaMy26 @("yearFrom","battery.declaredKwh") "OFFICIAL_MANUFACTURER")) "Opel publica 51 kWh sin especificar capacidad bruta o útil; no se usa para cálculos automáticos."),

        (New-DenseCarSpec "citroen" "berlingo" "m49" "M49" "gasolina" "Gasolina" 1996 2007 "ICE" "GASOLINA" "NONE" 55 $null $null $null $null $null $null @((New-DenseCarSource $km77BerlingoI @("fuelTankLitres"))) "Berlingo de primera generación: gasolina con depósito de 55 L."),
        (New-DenseCarSpec "citroen" "berlingo" "m49" "M49" "diesel" "Diésel" 1996 2007 "ICE" "DIESEL" "NONE" 55 $null $null $null $null $null $null @((New-DenseCarSource $km77BerlingoI @("fuelTankLitres"))) "Berlingo de primera generación: diésel con depósito de 55 L."),
        (New-DenseCarSpec "citroen" "berlingo" "b9" "B9" "gasolina" "Gasolina" 2008 2021 "ICE" "GASOLINA" "NONE" 60 $null $null $null $null $null $null @((New-DenseCarSource $km77BerlingoB9 @("yearFrom","fuelTankLitres")),(New-DenseCarSource "https://www.coches.net/fichas_tecnicas/citroen/berlingo/industriales/4-puertas/talla_m_puretech_110_ss_live_110cv_gasolina/86136/799230120180604/" @("yearFrom","yearTo","fuelTankLitres"))) "Berlingo térmica gasolina: depósito de 60 L documentado hasta 2021."),
        (New-DenseCarSpec "citroen" "berlingo" "b9" "B9" "diesel" "Diésel" 2008 2017 "ICE" "DIESEL" "NONE" 60 $null $null $null $null $null $null @((New-DenseCarSource $km77BerlingoB9 @("yearFrom","fuelTankLitres"))) "Berlingo B9 diésel: depósito de 60 L."),
        (New-DenseCarSpec "citroen" "berlingo" "b9" "B9" "electric" "Electric" 2013 2018 "BEV" $null "BATTERY_ELECTRIC" $null $null $null 22.5 "UNKNOWN" $null $null @((New-DenseCarSource "https://www.km77.com/coches/citroen/berlingo/2015/datos" @("yearFrom","yearTo","battery.declaredKwh"))) "E-Berlingo Multispace: capacidad publicada de 22,5 kWh sin clasificación bruto/útil."),
        (New-DenseCarSpec "citroen" "berlingo" "k9" "K9" "gasolina" "Gasolina" 2022 2022 "ICE" "GASOLINA" "NONE" 50 $null $null $null $null $null $null @((New-DenseCarSource $km77BerlingoK9 @("fuelTankLitres"))) "Berlingo K9 gasolina: solo se conserva el año legado 2022 con depósito de 50 L; no se amplía sin fuente concluyente."),
        (New-DenseCarSpec "citroen" "berlingo" "k9" "K9" "diesel" "Diésel" 2018 2025 "ICE" "DIESEL" "NONE" 50 $null $null $null $null $null $null @((New-DenseCarSource "https://www.autobild.es/coches/citroen/berlingo/berlingo-5-2018/talla-m-bluehdi-130-feel-7p/ficha-tecnica" @("yearFrom","fuelTankLitres")),(New-DenseCarSource $km77BerlingoK9 @("yearTo","fuelTankLitres"))) "Berlingo K9 diésel: depósito de 50 L."),
        (New-DenseCarSpec "citroen" "berlingo" "k9" "K9" "electric-50" "Electric 50 kWh" 2021 2024 "BEV" $null "BATTERY_ELECTRIC" $null $null $null 50 "UNKNOWN" $null "car-citroen-berlingo-k9-electric-50" @((New-DenseCarSource $citroenEBerlingo50 @("yearFrom","yearTo","battery.declaredKwh") "OFFICIAL_MANUFACTURER")) "ë-Berlingo: Citroën publica 50 kWh sin clasificación bruto/útil."),
        (New-DenseCarSpec "citroen" "berlingo" "k9-facelift" "K9 facelift" "electric-54" "Electric 54 kWh" 2025 2026 "BEV" $null "BATTERY_ELECTRIC" $null $null $null 54 "UNKNOWN" $null "car-citroen-berlingo-k9-electric-54" @((New-DenseCarSource $citroenEBerlingo54 @("yearFrom","battery.declaredKwh") "OFFICIAL_MANUFACTURER")) "ë-Berlingo actual: Citroën publica 54 kWh sin clasificación bruto/útil."),

        (New-DenseCarSpec "nissan" "qashqai" "j10" "J10" "gasolina" "Gasolina" 2007 2013 "ICE" "GASOLINA" "NONE" 65 $null $null $null $null $null $null @((New-DenseCarSource $km77QashqaiJ10 @("yearFrom","yearTo","fuelTankLitres"))) "Qashqai J10: gasolina con depósito de 65 L."),
        (New-DenseCarSpec "nissan" "qashqai" "j10" "J10" "diesel" "Diésel" 2007 2013 "ICE" "DIESEL" "NONE" 65 $null $null $null $null $null $null @((New-DenseCarSource $km77QashqaiJ10 @("yearFrom","yearTo","fuelTankLitres"))) "Qashqai J10: diésel con depósito de 65 L."),
        (New-DenseCarSpec "nissan" "qashqai" "j11" "J11" "gasolina" "Gasolina" 2014 2020 "ICE" "GASOLINA" "NONE" 55 $null $null $null $null $null $null @((New-DenseCarSource $km77QashqaiJ11 @("yearFrom","fuelTankLitres"))) "Qashqai J11 gasolina con depósito de 55 L."),
        (New-DenseCarSpec "nissan" "qashqai" "j11" "J11" "diesel-4x2" "Diésel 4x2" 2014 2018 "ICE" "DIESEL" "NONE" 55 $null $null $null $null $null $null @((New-DenseCarSource $km77QashqaiJ11 @("yearFrom","fuelTankLitres"))) "Qashqai J11 diésel 4x2 con depósito de 55 L."),
        (New-DenseCarSpec "nissan" "qashqai" "j11" "J11" "diesel-65l" "Diésel 65 L" 2019 2020 "ICE" "DIESEL" "NONE" 65 $null $null $null $null $null $null @((New-DenseCarSource $qashqaiJ11DieselAwd @("yearFrom","yearTo","fuelTankLitres"))) "Qashqai J11 1.7 dCi: depósito de 65 L; se mantiene separado por capacidad."),
        (New-DenseCarSpec "nissan" "qashqai" "j12" "J12" "mild-hybrid" "Mild Hybrid" 2021 2026 "HEV" $null "MILD" 55 $null $null $null $null "FULL" $null @((New-DenseCarSource $nissanQashqaiJ12 @("yearFrom","fuelTankLitres") "OFFICIAL_MANUFACTURER")) "Qashqai J12 Mild Hybrid 12 V; se mantiene distinto de e-POWER."),
        (New-DenseCarSpec "nissan" "qashqai" "j12" "J12" "e-power" "e-POWER" 2022 2026 "HEV" $null "FULL" 55 $null $null $null $null $null $null @((New-DenseCarSource $nissanQashqaiJ12 @("yearFrom","fuelTankLitres") "OFFICIAL_MANUFACTURER")) "Qashqai J12 e-POWER: híbrido de serie, separado del Mild Hybrid." )
    )

    $families = @("opel|corsa", "citroen|berlingo", "nissan|qashqai")
    $original = @($Catalog.vehicles | Where-Object {
        $_.category -eq "COCHE" -and $_.editorialStatus -eq "ACTIVE" -and $families -contains "$($_.brandId)|$($_.modelId)"
    })
    $retiredCount = 0

    foreach ($spec in $specs) {
        Add-CatalogIdentity $Catalog "COCHE" $spec.brandId $spec.modelId $spec.generationId $spec.generationName $spec.variantId $spec.variantName
        $matches = @($original | Where-Object {
            $_.editorialStatus -eq "ACTIVE" -and $_.brandId -eq $spec.brandId -and $_.modelId -eq $spec.modelId -and (Test-DenseCarLegacyMatch $_ $spec)
        } | Sort-Object yearFrom,catalogId)
        if ($spec.brandId -eq "nissan" -and $spec.modelId -eq "qashqai" -and $spec.hybrid -eq "MILD") {
            $matches = @($matches | Where-Object { $_.catalogId -like "*mhev*" })
        }
        # The pre-normalized e-POWER legacy record was imported with an
        # inconsistent optional-fuel representation. Its literal legacy key is
        # unambiguous, so retain it while correcting the structured powertrain.
        if ($matches.Count -eq 0 -and $spec.brandId -eq "nissan" -and $spec.modelId -eq "qashqai" -and $spec.kind -eq "HEV" -and $spec.hybrid -eq "FULL" -and [int]$spec.yearFrom -eq 2022) {
            $matches = @($original | Where-Object {
                $_.editorialStatus -eq "ACTIVE" -and $_.catalogId -like "*qashqai-e-power*"
            } | Sort-Object yearFrom,catalogId)
        }
        # A failed validation never publishes runtime/seed files, but it can
        # have written the readable editorial file. This lets a subsequent
        # invocation repair such a partial normalization deterministically.
        if ($matches.Count -eq 0) {
            $matches = @($original | Where-Object {
                $_.editorialStatus -eq "ACTIVE" -and (Get-Property $_ "denseCarNormalizationVersion") -eq 1 -and
                    $_.brandId -eq $spec.brandId -and $_.modelId -eq $spec.modelId -and $_.generationId -eq $spec.generationId -and $_.variantId -eq $spec.variantId
            } | Sort-Object yearFrom,catalogId)
        }
        $canonical = if ($matches.Count -gt 0) { $matches[0] } else { $null }
        if ($null -eq $canonical) {
            if ([string]::IsNullOrWhiteSpace($spec.newCatalogId)) { throw "dense car normalization: falta catalogId nuevo para $($spec.brandId)/$($spec.modelId)/$($spec.variantId) [kind=$($spec.kind), fuel=$($spec.fuel), hybrid=$($spec.hybrid), from=$($spec.yearFrom)]" }
            $canonical = New-Object @{ catalogId = $spec.newCatalogId; editorialStatus = "ACTIVE"; provenance = "RESEARCH_READY"; category = "COCHE"; brandId = $spec.brandId; modelId = $spec.modelId; generationId = $spec.generationId; variantId = $spec.variantId; yearFrom = $spec.yearFrom; yearTo = $spec.yearTo; powertrain = $null; fuelTankLitres = $null; battery = $null; bodyStyle = $null; drivetrain = $null; marketCodes = @("ES"); aliases = @(); legacyKeys = @(); sources = @(); notes = ""; editorialRevision = 0 }
            $Catalog.vehicles = @($Catalog.vehicles) + $canonical
        }

        Set-DenseCarRecord $canonical $spec @($matches | ForEach-Object { $_.legacyKeys })
        $retired = @($matches | Where-Object { $_.catalogId -ne $canonical.catalogId })
        if ($retired.Count -gt 0) { Set-EditorialProperty $canonical "supersededCatalogIds" @($retired | ForEach-Object { $_.catalogId }) }
        foreach ($entry in $retired) {
            $entry.editorialStatus = "INACTIVE"
            Set-EditorialProperty $entry "replacedByCatalogId" $canonical.catalogId
            $entry.editorialRevision = [int]$entry.editorialRevision + 1
            $entry.notes = "$($entry.notes) Consolidado en $($canonical.catalogId) mediante normalización editorial investigada."
            $retiredCount++
        }
    }

    $unresolved = @($original | Where-Object { $_.editorialStatus -eq "ACTIVE" -and (Get-Property $_ "denseCarNormalizationVersion") -ne 1 })
    if ($unresolved.Count -gt 0) { throw "dense car normalization: registros legacy sin rango: $($unresolved.catalogId -join ', ')" }
    return $retiredCount
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
    $catalogIds = @{}; $vehiclesById = @{}; $legacyKeys = @{}; $technicalRanges = @{}
    foreach ($vehicle in $Catalog.vehicles) {
        $context = "vehicle $($vehicle.catalogId)"
        Assert-Id $vehicle.catalogId $context
        if ($catalogIds[$vehicle.catalogId]) { throw "${context}: catalogId duplicado" }; $catalogIds[$vehicle.catalogId] = $true; $vehiclesById[$vehicle.catalogId] = $vehicle
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
    foreach ($vehicle in $Catalog.vehicles) {
        $context = "vehicle $($vehicle.catalogId)"
        $supersededIds = Get-Property $vehicle "supersededCatalogIds"
        if ($null -ne $supersededIds) {
            foreach ($supersededId in @($supersededIds)) {
                Assert-Id $supersededId "$context.supersededCatalogIds"
                if (-not $catalogIds[$supersededId]) { throw "$context.supersededCatalogIds: catalogId inexistente '$supersededId'" }
            }
        }
        $replacementId = Get-Property $vehicle "replacedByCatalogId"
        if ($null -ne $replacementId) {
            Assert-Id $replacementId "$context.replacedByCatalogId"
            $replacement = $vehiclesById[$replacementId]
            if ($null -eq $replacement -or $replacement.editorialStatus -ne "ACTIVE") { throw "$context.replacedByCatalogId: debe referenciar un ACTIVE" }
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
$editorialDirty = $false
$retiredMotorcycles = $null
$retiredCars = $null
if ($NormalizeLegacyModels) {
    Normalize-LegacyModelStructure $editorial
    $editorialDirty = $true
}
if ($ConsolidateMotorcycles) {
    $retiredMotorcycles = Consolidate-LegacyMotorcycles $editorial
    $editorialDirty = $true
}
if ($NormalizeDenseCarFamilies) {
    $retiredCars = Normalize-DenseCarFamilies $editorial
    $editorialDirty = $true
}
Test-EditorialCatalog $editorial
if ($editorialDirty) {
    Write-EditorialJson $editorial $EditorialPath
}
if ($null -ne $retiredMotorcycles) {
    Write-Output "Motocicletas consolidadas: $retiredMotorcycles registros absorbidos"
}
if ($null -ne $retiredCars) {
    Write-Output "Coches normalizados: $retiredCars registros absorbidos"
}
$runtime = New-RuntimeCatalog $editorial
Write-DeterministicJson $runtime $RuntimePath
Write-DeterministicJson $runtime $AssetPath
$bytes = [IO.File]::ReadAllBytes($RuntimePath)
$activeCount = @($runtime.vehicles).Count
Write-Output "Editorial validado: $(@($editorial.vehicles).Count) registros"
Write-Output "Runtime generado: $activeCount registros"
Write-Output "SHA-256: $(Get-Sha256 $bytes)"
Write-Output "Bytes: $($bytes.Length)"
