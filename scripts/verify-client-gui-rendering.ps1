param(
    [string]$SourceRoot = "src/main/java/com/moakiee/meplacementtool/client",
    [string]$TextureRoot = "src/main/resources/assets/meplacementtool/textures/gui"
)

$ErrorActionPreference = "Stop"

function Read-Source($name) {
    Get-Content -LiteralPath (Join-Path $SourceRoot $name) -Raw
}

function Require-Match($name, $source, $pattern) {
    if ($source -notmatch $pattern) {
        throw "Client GUI rendering regression check failed: $name"
    }
}

function Require-NoMatch($name, $source, $pattern) {
    if ($source -match $pattern) {
        throw "Client GUI rendering regression check failed: $name"
    }
}

$radialRenderer = Read-Source "RadialMenuRenderer.java"
$radialScreen = Read-Source "RadialMenuScreen.java"
$dualRadialScreen = Read-Source "DualLayerRadialMenuScreen.java"
$cableScreen = Read-Source "CableToolScreen.java"

Require-Match `
    "radial slices submit non-culled vertex order" `
    $radialRenderer `
    "x1In, y1In\)\.setColor\(this\.color\);\s*vertexConsumer\.addVertexWith2DPose\(this\.pose, x2In, y2In\)"

Require-Match `
    "single radial click recomputes selection from the click position" `
    $radialScreen `
    "mouseClicked[\s\S]*findHoveredSlot\(\(int\) event\.x\(\), \(int\) event\.y\(\)"

Require-Match `
    "dual radial click recomputes selection from the click position" `
    $dualRadialScreen `
    "mouseClicked[\s\S]*findHoveredSelection\(\(int\) event\.x\(\), \(int\) event\.y\(\)"

Require-Match `
    "single radial advances the GUI stratum before text and item icons" `
    $radialScreen `
    "RadialMenuRenderer\.drawDivider[\s\S]*graphics\.nextStratum\(\);[\s\S]*graphics\.centeredText"

Require-Match `
    "dual radial advances the GUI stratum before text and item icons" `
    $dualRadialScreen `
    "RadialMenuRenderer\.drawDivider[\s\S]*graphics\.nextStratum\(\);[\s\S]*graphics\.centeredText"

Require-Match `
    "cable custom chrome is drawn from the background layer" `
    $cableScreen `
    "extractBackground[\s\S]*drawColorBar"

Require-NoMatch `
    "cable contents no longer draw custom chrome before vanilla slots" `
    $cableScreen `
    "extractContents[^{]*\{[^}]*drawColorBar"

Require-Match `
    "cable clicks recompute hover state from the click position" `
    $cableScreen `
    "mouseClicked[\s\S]*updateHoverState\(\(int\) mouseX, \(int\) mouseY\)"

Require-NoMatch `
    "cable texture blits use explicit 26.1 GUI_TEXTURED pipeline" `
    $cableScreen `
    "GuiGraphicsExtractor\.blit\((?!RenderPipelines\.GUI_TEXTURED)"

Add-Type -AssemblyName System.Drawing
$expectedSizes = @{
    "cable_tool.png" = @(256, 256)
    "color_menu.png" = @(112, 49)
    "color_frame.png" = @(11, 11)
    "color_unselected.png" = @(11, 11)
    "expand_button.png" = @(12, 12)
    "button_normal.png" = @(12, 12)
    "button_pressed.png" = @(12, 11)
}

foreach ($entry in $expectedSizes.GetEnumerator()) {
    $path = Join-Path $TextureRoot $entry.Key
    $image = [System.Drawing.Image]::FromFile((Resolve-Path -LiteralPath $path))
    try {
        if ($image.Width -ne $entry.Value[0] -or $image.Height -ne $entry.Value[1]) {
            throw "Client GUI rendering regression check failed: $($entry.Key) expected $($entry.Value[0])x$($entry.Value[1]), got $($image.Width)x$($image.Height)"
        }
    } finally {
        $image.Dispose()
    }
}

Write-Host "Verified client GUI rendering migration invariants."
