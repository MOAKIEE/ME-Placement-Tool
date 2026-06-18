param(
    [string]$RecipeDir = "src/main/resources/data/meplacementtool/recipe"
)

$ErrorActionPreference = "Stop"

$expectedRecipes = @(
    "key_of_spectrum",
    "me_cable_placement_tool",
    "me_placement_tool",
    "multiblock_placement_tool",
    "prism_core"
)

$missing = @()
foreach ($recipeName in $expectedRecipes) {
    $path = Join-Path $RecipeDir "$recipeName.json"
    if (-not (Test-Path -LiteralPath $path)) {
        $missing += $recipeName
    }
}

if ($missing.Count -gt 0) {
    throw "Missing recipe files: $($missing -join ', ')"
}

$legacyIngredients = @()
Get-ChildItem -LiteralPath $RecipeDir -Filter "*.json" | ForEach-Object {
    $json = Get-Content -LiteralPath $_.FullName -Raw | ConvertFrom-Json
    if ($json.type -ne "minecraft:crafting_shaped") {
        return
    }

    foreach ($property in $json.key.PSObject.Properties) {
        if ($property.Value -isnot [string]) {
            $legacyIngredients += "$($_.Name): key '$($property.Name)' must be a 26.1 string ingredient"
        }
    }
}

if ($legacyIngredients.Count -gt 0) {
    throw "Legacy recipe ingredient format found:`n$($legacyIngredients -join "`n")"
}

Write-Host "Verified ME Placement Tool recipe resources."
