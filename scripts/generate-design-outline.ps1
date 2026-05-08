param(
    [string]$TemplatePath = '',
    [string]$OutlinePath = '',
    [string]$OutputPath = ''
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$designDir = Join-Path $repoRoot 'docs\Design'

if ([string]::IsNullOrWhiteSpace($OutlinePath)) {
    $OutlinePath = Join-Path $designDir 'unified-account-outline.txt'
}

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $designDir 'unified-account-platform-outline-v0.1.docx'
}

if ([string]::IsNullOrWhiteSpace($TemplatePath)) {
    $candidates = Get-ChildItem -LiteralPath $designDir -Filter '*.docx' |
        Where-Object { $_.Name -notlike '__*' -and $_.FullName -ne $OutputPath } |
        Sort-Object Length, LastWriteTime -Descending

    if (-not $candidates) {
        throw "No template docx found in $designDir"
    }

    $TemplatePath = $candidates[0].FullName
}

if (-not (Test-Path -LiteralPath $TemplatePath)) {
    throw "Template not found: $TemplatePath"
}

if (-not (Test-Path -LiteralPath $OutlinePath)) {
    throw "Outline file not found: $OutlinePath"
}

$word = $null
$doc = $null

try {
    Copy-Item -LiteralPath $TemplatePath -Destination $OutputPath -Force

    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $word.DisplayAlerts = 0

    $doc = $word.Documents.Open($OutputPath)
    $doc.Content.Delete()

    $selection = $word.Selection
    $wdStyleNormal = -1
    $wdStyleHeading1 = -2
    $wdStyleHeading2 = -3
    $wdStyleHeading3 = -4
    $wdStyleTitle = -63
    $wdAlignParagraphLeft = 0
    $wdAlignParagraphCenter = 1

    $styleMap = @{
        'NORMAL' = $wdStyleNormal
        'H1' = $wdStyleHeading1
        'H2' = $wdStyleHeading2
        'H3' = $wdStyleHeading3
        'TITLE' = $wdStyleTitle
    }

    $lines = Get-Content -LiteralPath $OutlinePath -Encoding UTF8
    foreach ($line in $lines) {
        $parts = $line -split '\|', 3
        if ($parts.Count -lt 3) {
            continue
        }

        $styleKey = $parts[0].Trim().ToUpperInvariant()
        $alignKey = $parts[1].Trim().ToUpperInvariant()
        $text = $parts[2]

        $styleId = $wdStyleNormal
        if ($styleMap.ContainsKey($styleKey)) {
            $styleId = $styleMap[$styleKey]
        }

        $selection.Style = $doc.Styles.Item($styleId)
        $alignment = if ($alignKey -eq 'CENTER') {
            $wdAlignParagraphCenter
        } else {
            $wdAlignParagraphLeft
        }
        $selection.ParagraphFormat.Alignment = $alignment

        if ([string]::IsNullOrEmpty($text)) {
            $selection.TypeParagraph()
            continue
        }

        $selection.TypeText($text)
        $selection.TypeParagraph()
    }

    $doc.Save()
    $doc.Close()
    $word.Quit()

    Get-Item -LiteralPath $OutputPath | Select-Object FullName, Length, LastWriteTime
} finally {
    if ($doc -ne $null) {
        try { $doc.Close() } catch {}
    }
    if ($word -ne $null) {
        try { $word.Quit() } catch {}
    }
}
