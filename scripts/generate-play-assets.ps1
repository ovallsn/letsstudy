Add-Type -AssemblyName System.Drawing
$root = Split-Path -Parent $PSScriptRoot
$output = Join-Path $root 'play-listing'
$screens = Join-Path $output 'phone-screenshots'
New-Item -ItemType Directory -Path $screens -Force | Out-Null

function Save-Png($bitmap, $path) {
    $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
}

$icon = [System.Drawing.Image]::FromFile((Join-Path $root 'docs/letsstudy-icon.png'))
$storeIcon = [System.Drawing.Bitmap]::new(512, 512)
$graphics = [System.Drawing.Graphics]::FromImage($storeIcon)
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$graphics.DrawImage($icon, 0, 0, 512, 512)
$graphics.Dispose()
Save-Png $storeIcon (Join-Path $output 'store-icon.png')

$feature = [System.Drawing.Bitmap]::new(1024, 500)
$graphics = [System.Drawing.Graphics]::FromImage($feature)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.Clear([System.Drawing.Color]::FromArgb(32, 38, 58))
$graphics.DrawImage($icon, 62, 104, 292, 292)
$white = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::White)
$light = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(222, 225, 241))
$accent = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(139, 131, 232))
$graphics.FillRectangle($accent, 395, 108, 72, 9)
$title = [System.Drawing.Font]::new('Segoe UI', 66, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$subtitle = [System.Drawing.Font]::new('Segoe UI', 31, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$graphics.DrawString('LetsStudy', $title, $white, 390, 146)
$graphics.DrawString('Learn something new.', $subtitle, $light, 394, 273)
$graphics.DrawString("Feel ready for what's next.", $subtitle, $light, 394, 321)
$graphics.Dispose()
$title.Dispose(); $subtitle.Dispose(); $white.Dispose(); $light.Dispose(); $accent.Dispose()
Save-Png $feature (Join-Path $output 'feature-graphic.png')
$icon.Dispose()

$names = @('age-screen', 'youth-home', 'youth-answer', 'teen-home', 'home', 'job-preparation', 'material-import', 'community-board')
foreach ($name in $names) {
    $source = [System.Drawing.Image]::FromFile((Join-Path $root "docs/screenshots/$name.png"))
    $canvas = [System.Drawing.Bitmap]::new(1242, 2208, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $graphics = [System.Drawing.Graphics]::FromImage($canvas)
    $graphics.Clear([System.Drawing.Color]::FromArgb(247, 248, 251))
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $targetWidth = [int][Math]::Round($source.Width * 2208 / $source.Height)
    $graphics.DrawImage($source, [int]((1242 - $targetWidth) / 2), 0, $targetWidth, 2208)
    $graphics.Dispose()
    $source.Dispose()
    Save-Png $canvas (Join-Path $screens "$name.png")
}
