param(
    [string]$SourcePath = "src/main/java/com/moakiee/meplacementtool/client/CablePreviewRenderer.java"
)

$ErrorActionPreference = "Stop"

$source = Get-Content -LiteralPath $SourcePath -Raw

$requirements = @(
    @{
        Name = "uses ExtractBlockOutlineRenderStateEvent for block-hit previews"
        Pattern = "ExtractBlockOutlineRenderStateEvent"
    },
    @{
        Name = "registers the block-outline preview listener"
        Pattern = "CablePreviewRenderer::extractBlockOutline"
    },
    @{
        Name = "adds a custom block-outline renderer"
        Pattern = "addCustomRenderer"
    },
    @{
        Name = "world-stage renderer skips block hits"
        Pattern = "if \(blockHit != null\) \{\s*return;"
    }
)

$missing = @()
foreach ($requirement in $requirements) {
    if ($source -notmatch $requirement.Pattern) {
        $missing += $requirement.Name
    }
}

if ($missing.Count -gt 0) {
    throw "Cable preview rendering regression check failed:`n$($missing -join "`n")"
}

Write-Host "Verified cable preview rendering event split."
