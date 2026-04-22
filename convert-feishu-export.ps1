param(
    [string]$DepartmentInputPath = "docs/departments.xlsx",
    [string]$UserInputPath = "docs/users.xlsx",
    [string]$DepartmentWorksheetName,
    [string]$UserWorksheetName,
    [string]$DepartmentOutputPath = "docs/feishu-import/departments/demo.json",
    [string]$UserOutputPath = "docs/feishu-import/users/demo.json"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$ScriptRoot = Split-Path -Parent $PSCommandPath
$DepartmentPathColumn = "B"
$UserRealNameColumn = "A"
$UserMobileColumn = "B"
$UserEmployeeNoColumn = "C"
$UserDepartmentColumn = "E"

function Resolve-LocalPath {
    param([Parameter(Mandatory = $true)][string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }

    return [System.IO.Path]::GetFullPath((Join-Path $ScriptRoot $Path))
}

function New-Utf8NoBomEncoding {
    return New-Object System.Text.UTF8Encoding($false)
}

function Write-Utf8NoBomFile {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Content
    )

    $directory = Split-Path -Parent $Path
    if ($directory) {
        [System.IO.Directory]::CreateDirectory($directory) | Out-Null
    }
    [System.IO.File]::WriteAllText($Path, $Content, (New-Utf8NoBomEncoding))
}

function Read-ZipEntryText {
    param(
        [Parameter(Mandatory = $true)][System.IO.Compression.ZipArchive]$Archive,
        [Parameter(Mandatory = $true)][string]$EntryName
    )

    $entry = $Archive.GetEntry($EntryName)
    if (-not $entry) {
        throw "Missing Excel entry: $EntryName"
    }

    $stream = $entry.Open()
    $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
    try {
        return $reader.ReadToEnd()
    }
    finally {
        $reader.Dispose()
        $stream.Dispose()
    }
}

function Get-SharedStrings {
    param([Parameter(Mandatory = $true)][System.IO.Compression.ZipArchive]$Archive)

    $entry = $Archive.GetEntry("xl/sharedStrings.xml")
    if (-not $entry) {
        return @()
    }

    [xml]$sharedStringsXml = Read-ZipEntryText -Archive $Archive -EntryName "xl/sharedStrings.xml"
    $result = @()
    foreach ($stringNode in $sharedStringsXml.SelectNodes("//*[local-name()='si']")) {
        $textNodes = $stringNode.SelectNodes(".//*[local-name()='t']")
        $result += (($textNodes | ForEach-Object { $_.InnerText }) -join "")
    }
    return $result
}

function Get-WorkbookSheets {
    param([Parameter(Mandatory = $true)][System.IO.Compression.ZipArchive]$Archive)

    [xml]$workbookXml = Read-ZipEntryText -Archive $Archive -EntryName "xl/workbook.xml"
    [xml]$relationXml = Read-ZipEntryText -Archive $Archive -EntryName "xl/_rels/workbook.xml.rels"

    $relationById = @{}
    foreach ($relationNode in $relationXml.SelectNodes("//*[local-name()='Relationship']")) {
        $target = [string]$relationNode.Target
        if ($target.StartsWith('/')) {
            $target = $target.TrimStart('/')
        }
        if (-not $target.StartsWith("xl/")) {
            $target = "xl/$target"
        }
        $relationById[$relationNode.Id] = $target
    }

    $sheets = @()
    foreach ($sheetNode in $workbookXml.SelectNodes("//*[local-name()='sheet']")) {
        $relationshipId = $sheetNode.GetAttribute("id", "http://schemas.openxmlformats.org/officeDocument/2006/relationships")
        if (-not $relationById.ContainsKey($relationshipId)) {
            continue
        }
        $sheets += [pscustomobject]@{
            Name   = [string]$sheetNode.name
            Target = [string]$relationById[$relationshipId]
        }
    }
    return $sheets
}

function Get-CellColumnLetters {
    param([Parameter(Mandatory = $true)][string]$CellReference)

    return ($CellReference -replace "\d", "")
}

function Get-CellValue {
    param(
        [Parameter(Mandatory = $true)][System.Xml.XmlElement]$CellNode,
        [Parameter(Mandatory = $true)][object[]]$SharedStrings
    )

    $cellType = $CellNode.GetAttribute("t")
    if ($cellType -eq "inlineStr") {
        return (($CellNode.SelectNodes(".//*[local-name()='t']") | ForEach-Object { $_.InnerText }) -join "")
    }

    $valueNode = $CellNode.SelectSingleNode("./*[local-name()='v']")
    if (-not $valueNode) {
        return ""
    }

    $value = $valueNode.InnerText
    if ($cellType -eq "s") {
        return $SharedStrings[[int]$value]
    }
    if ($cellType -eq "b") {
        return [string]([int]$value)
    }
    return [string]$value
}

function Get-WorksheetRows {
    param(
        [Parameter(Mandatory = $true)][System.IO.Compression.ZipArchive]$Archive,
        [Parameter(Mandatory = $true)][string]$WorksheetEntryName,
        [Parameter(Mandatory = $true)][object[]]$SharedStrings
    )

    [xml]$worksheetXml = Read-ZipEntryText -Archive $Archive -EntryName $WorksheetEntryName
    $rows = @()
    foreach ($rowNode in $worksheetXml.SelectNodes("//*[local-name()='sheetData']/*[local-name()='row']")) {
        $cells = [ordered]@{}
        foreach ($cellNode in $rowNode.SelectNodes("./*[local-name()='c']")) {
            $reference = $cellNode.GetAttribute("r")
            $column = Get-CellColumnLetters -CellReference $reference
            $cells[$column] = Get-CellValue -CellNode $cellNode -SharedStrings $SharedStrings
        }
        $rows += [pscustomobject]@{
            RowNumber = [int]$rowNode.GetAttribute("r")
            Cells     = $cells
        }
    }
    return $rows
}

function Get-WorksheetData {
    param(
        [Parameter(Mandatory = $true)][string]$InputPath,
        [string]$WorksheetName
    )

    Add-Type -AssemblyName System.IO.Compression.FileSystem

    $archive = [System.IO.Compression.ZipFile]::OpenRead($InputPath)
    try {
        $sharedStrings = @(Get-SharedStrings -Archive $archive)
        $workbookSheets = @(Get-WorkbookSheets -Archive $archive)
        if (@($workbookSheets).Count -eq 0) {
            throw "No readable worksheet found in $InputPath"
        }

        $worksheet = if ($WorksheetName) {
            $workbookSheets | Where-Object { $_.Name -eq $WorksheetName } | Select-Object -First 1
        }
        else {
            $workbookSheets | Select-Object -First 1
        }
        if (-not $worksheet) {
            throw "Worksheet not found: $WorksheetName"
        }

        $rows = @(Get-WorksheetRows -Archive $archive -WorksheetEntryName $worksheet.Target -SharedStrings @($sharedStrings))
        if (@($rows).Count -eq 0) {
            throw "Worksheet has no rows: $InputPath -> $($worksheet.Name)"
        }

        $headerRow = $rows | Where-Object { $_.RowNumber -eq 1 } | Select-Object -First 1
        if (-not $headerRow) {
            throw "Header row not found: $InputPath"
        }

        $result = @()
        foreach ($row in $rows | Where-Object { $_.RowNumber -ge 2 }) {
            $rowMap = [ordered]@{}
            foreach ($column in $headerRow.Cells.Keys) {
                $rawValue = if ($row.Cells.Contains($column)) { [string]$row.Cells[$column] } else { "" }
                $rowMap[$column] = $rawValue
            }

            $hasValue = $false
            foreach ($value in $rowMap.Values) {
                if (-not [string]::IsNullOrWhiteSpace([string]$value)) {
                    $hasValue = $true
                    break
                }
            }
            if (-not $hasValue) {
                continue
            }

            $result += [pscustomobject]@{
                RowNumber = $row.RowNumber
                Values    = $rowMap
            }
        }

        return [pscustomobject]@{
            WorksheetName = $worksheet.Name
            HeaderValues  = $headerRow.Cells
            Rows          = $result
        }
    }
    finally {
        $archive.Dispose()
    }
}

function Get-NormalizedValue {
    param([AllowNull()][string]$Value)

    if ($null -eq $Value) {
        return $null
    }
    $trimmed = $Value.Trim()
    if ($trimmed.Length -eq 0) {
        return $null
    }
    return $trimmed
}

function Get-DepartmentSegments {
    param([Parameter(Mandatory = $true)][string]$DepartmentPath)

    return @(
        ($DepartmentPath -split "/" | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    )
}

function Get-LeafDepartmentName {
    param([Parameter(Mandatory = $true)][string]$DepartmentPath)

    $segments = Get-DepartmentSegments -DepartmentPath $DepartmentPath
    if (@($segments).Count -eq 0) {
        return $null
    }
    return $segments[-1]
}

function Get-Sha1Hex {
    param([Parameter(Mandatory = $true)][string]$Text)

    $sha1 = [System.Security.Cryptography.SHA1]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
        $hashBytes = $sha1.ComputeHash($bytes)
    }
    finally {
        $sha1.Dispose()
    }

    return ([System.BitConverter]::ToString($hashBytes)).Replace("-", "").ToLowerInvariant()
}

function New-DepartmentExternalId {
    param([Parameter(Mandatory = $true)][string]$DepartmentPath)

    return "xlsx_dept_" + (Get-Sha1Hex -Text $DepartmentPath)
}

function New-DepartmentCode {
    param([Parameter(Mandatory = $true)][string]$DepartmentPath)

    return "FD" + ((Get-Sha1Hex -Text $DepartmentPath).Substring(0, 12).ToUpperInvariant())
}

function New-UserExternalId {
    param(
        [string]$EmployeeNo,
        [string]$Mobile,
        [string]$RealName,
        [string]$DepartmentPath
    )

    $identity = ($EmployeeNo, $Mobile, $RealName, $DepartmentPath | ForEach-Object { if ($_){ $_ } else { '' } }) -join '|'
    return "xlsx_user_" + (Get-Sha1Hex -Text $identity)
}

function Normalize-Mobile {
    param([AllowNull()][string]$Value)

    $normalized = Get-NormalizedValue -Value $Value
    if ($null -eq $normalized) {
        return $null
    }
    return ($normalized -replace "\s", "")
}

function Convert-UserStatus {
    param([AllowNull()][string]$StatusValue)

    $normalized = Get-NormalizedValue -Value $StatusValue
    if ($null -eq $normalized) {
        return 1
    }

    $disabledStatuses = @("离职", "禁用", "停用", "disabled", "inactive")
    if ($disabledStatuses -contains $normalized.ToLowerInvariant()) {
        return 0
    }
    return 1
}

function Assert-RequiredHeaders {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$HeaderValues,
        [Parameter(Mandatory = $true)][string[]]$RequiredHeaders
    )

    $missingHeaders = @()
    foreach ($requiredHeader in $RequiredHeaders) {
        if (-not (@($HeaderValues.Values) -contains $requiredHeader)) {
            $missingHeaders += $requiredHeader
        }
    }

    if (@($missingHeaders).Count -gt 0) {
        throw "Missing required columns: $($missingHeaders -join ', ')"
    }
}

function Assert-UniqueField {
    param(
        [Parameter(Mandatory = $true)][object[]]$Rows,
        [Parameter(Mandatory = $true)][string]$FieldName
    )

    $counter = @{}
    foreach ($row in $Rows) {
        $value = Get-NormalizedValue -Value $row.$FieldName
        if ($null -eq $value) {
            continue
        }
        if (-not $counter.ContainsKey($value)) {
            $counter[$value] = @()
        }
        $counter[$value] += $row.RowNumber
    }

    $duplicates = @($counter.GetEnumerator() | Where-Object { @($_.Value).Count -gt 1 })
    if ($duplicates) {
        $messages = $duplicates | ForEach-Object {
            "$($_.Key) (rows: $($_.Value -join ', '))"
        }
        throw "$FieldName duplicate values: $($messages -join '; ')"
    }
}

function Ensure-PinyinHelperScript {
    $helperDirectory = Resolve-LocalPath -Path "scripts/pinyin-helper"
    $helperScriptPath = Join-Path $helperDirectory "name-to-pinyin.mjs"
    $helperModulePath = Join-Path $helperDirectory "node_modules/pinyin-pro/dist/index.mjs"

    if (-not (Test-Path -LiteralPath $helperScriptPath -PathType Leaf)) {
        throw "Pinyin helper script not found: $helperScriptPath"
    }

    if (-not (Test-Path -LiteralPath $helperModulePath -PathType Leaf)) {
        $npmCommand = Get-Command npm.cmd -ErrorAction SilentlyContinue
        if (-not $npmCommand) {
            throw "npm.cmd not found. Cannot install pinyin dependency."
        }
        Write-Host "Installing pinyin helper dependency..." -ForegroundColor Yellow
        & $npmCommand.Source install --prefix $helperDirectory | Out-Null
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $helperModulePath -PathType Leaf)) {
            throw "Failed to install pinyin helper dependency."
        }
    }

    return $helperScriptPath
}

function Convert-NamesToPinyinBase {
    param([Parameter(Mandatory = $true)][string[]]$Names)

    if (@($Names).Count -eq 0) {
        return @()
    }

    $helperScriptPath = Ensure-PinyinHelperScript
    $inputFile = Join-Path ([System.IO.Path]::GetTempPath()) ("pinyin-input-" + [guid]::NewGuid().ToString("N") + ".json")
    $outputFile = Join-Path ([System.IO.Path]::GetTempPath()) ("pinyin-output-" + [guid]::NewGuid().ToString("N") + ".json")

    try {
        Write-Utf8NoBomFile -Path $inputFile -Content (($Names | ConvertTo-Json -Depth 3))
        & node $helperScriptPath $inputFile $outputFile
        if ($LASTEXITCODE -ne 0) {
            throw "Pinyin conversion failed."
        }
        return @((Get-Content -Raw $outputFile | ConvertFrom-Json))
    }
    finally {
        Remove-Item -LiteralPath $inputFile -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $outputFile -ErrorAction SilentlyContinue
    }
}

function New-UniqueUsernameList {
    param([Parameter(Mandatory = $true)][string[]]$BaseUsernames)

    $counter = @{}
    $result = @()

    foreach ($baseUsername in $BaseUsernames) {
        $base = Get-NormalizedValue -Value $baseUsername
        if ($null -eq $base) {
            throw "Failed to generate username from name."
        }

        if (-not $counter.ContainsKey($base)) {
            $counter[$base] = 1
            $result += $base
            continue
        }

        $counter[$base]++
        $result += ($base + [string]$counter[$base])
    }

    return $result
}

$resolvedDepartmentInputPath = Resolve-LocalPath -Path $DepartmentInputPath
$resolvedUserInputPath = Resolve-LocalPath -Path $UserInputPath
$resolvedDepartmentOutputPath = Resolve-LocalPath -Path $DepartmentOutputPath
$resolvedUserOutputPath = Resolve-LocalPath -Path $UserOutputPath

if (-not (Test-Path -LiteralPath $resolvedDepartmentInputPath -PathType Leaf)) {
    throw "Department input file not found: $resolvedDepartmentInputPath"
}
if (-not (Test-Path -LiteralPath $resolvedUserInputPath -PathType Leaf)) {
    throw "User input file not found: $resolvedUserInputPath"
}

$departmentWorksheet = Get-WorksheetData -InputPath $resolvedDepartmentInputPath -WorksheetName $DepartmentWorksheetName
$userWorksheet = Get-WorksheetData -InputPath $resolvedUserInputPath -WorksheetName $UserWorksheetName

$departmentNodes = [ordered]@{}
$nextOrderByParent = @{}

foreach ($row in $departmentWorksheet.Rows) {
    $values = $row.Values
    $departmentPath = Get-NormalizedValue -Value $values[$DepartmentPathColumn]
    if ($null -eq $departmentPath) {
        continue
    }

    $segments = Get-DepartmentSegments -DepartmentPath $departmentPath
    if (@($segments).Count -eq 0) {
        throw "Empty department path on row $($row.RowNumber): $departmentPath"
    }

    $pathParts = New-Object System.Collections.Generic.List[string]
    foreach ($segment in $segments) {
        [void]$pathParts.Add($segment)
        $currentPath = ($pathParts -join "/")
        if ($departmentNodes.Contains($currentPath)) {
            continue
        }

        $parentPath = if ($pathParts.Count -eq 1) { $null } else { ($pathParts.GetRange(0, $pathParts.Count - 1) -join "/") }
        $parentKey = if ($parentPath) { $parentPath } else { "__ROOT__" }
        $orderNo = if ($nextOrderByParent.ContainsKey($parentKey)) { $nextOrderByParent[$parentKey] + 1 } else { 1 }
        $nextOrderByParent[$parentKey] = $orderNo

        $departmentNodes[$currentPath] = [pscustomobject]@{
            Path             = $currentPath
            Name             = $segment
            ExternalId       = New-DepartmentExternalId -DepartmentPath $currentPath
            DepartmentCode   = New-DepartmentCode -DepartmentPath $currentPath
            ParentPath       = $parentPath
            ParentExternalId = $null
            Status           = 1
            OrderNo          = $orderNo
        }
    }
}

foreach ($departmentNode in $departmentNodes.Values) {
    if ($departmentNode.ParentPath -and $departmentNodes.Contains($departmentNode.ParentPath)) {
        $departmentNode.ParentExternalId = $departmentNodes[$departmentNode.ParentPath].ExternalId
    }
}

$userRows = @()
$rawNames = @()
$rowOrder = 1

foreach ($row in $userWorksheet.Rows) {
    $values = $row.Values
    $realName = Get-NormalizedValue -Value $values[$UserRealNameColumn]
    if ($null -eq $realName) {
        throw "Missing name on row $($row.RowNumber)"
    }

    $departmentName = Get-NormalizedValue -Value $values[$UserDepartmentColumn]
    if ($null -eq $departmentName) {
        throw "Missing department on row $($row.RowNumber)"
    }

    $matchedPaths = @(
        $departmentNodes.Values |
            Where-Object { $_.Name -eq $departmentName } |
            ForEach-Object { $_.Path }
    )
    if (@($matchedPaths).Count -eq 0) {
        throw "Department name cannot be mapped from users.xlsx: $departmentName (row $($row.RowNumber))"
    }
    if (@($matchedPaths).Count -gt 1) {
        throw "Ambiguous department leaf name: $departmentName (row $($row.RowNumber))"
    }

    $departmentPath = $matchedPaths[0]
    $departmentNode = $departmentNodes[$departmentPath]
    $employeeNo = Get-NormalizedValue -Value $values[$UserEmployeeNoColumn]
    $mobile = Normalize-Mobile -Value $values[$UserMobileColumn]

    $status = 1
    if ($values.Contains("M")) {
        $status = Convert-UserStatus -StatusValue $values["M"]
    }

    $userRows += [pscustomobject]@{
        RowNumber                = $row.RowNumber
        ExternalId               = New-UserExternalId -EmployeeNo $employeeNo -Mobile $mobile -RealName $realName -DepartmentPath $departmentPath
        Username                 = $null
        RealName                 = $realName
        Email                    = $null
        Mobile                   = $mobile
        EmployeeNo               = $employeeNo
        MainDepartmentExternalId = $departmentNode.ExternalId
        Status                   = $status
        OrderNo                  = $rowOrder
    }
    $rawNames += $realName
    $rowOrder++
}

$baseUsernames = Convert-NamesToPinyinBase -Names $rawNames
$uniqueUsernames = New-UniqueUsernameList -BaseUsernames $baseUsernames
for ($index = 0; $index -lt $userRows.Count; $index++) {
    $userRows[$index].Username = $uniqueUsernames[$index]
}

Assert-UniqueField -Rows $userRows -FieldName "ExternalId"
Assert-UniqueField -Rows $userRows -FieldName "Username"
Assert-UniqueField -Rows $userRows -FieldName "EmployeeNo"

$departmentPayload = @(
    foreach ($departmentNode in $departmentNodes.Values) {
        [ordered]@{
            externalId       = $departmentNode.ExternalId
            departmentCode   = $departmentNode.DepartmentCode
            departmentName   = $departmentNode.Name
            parentExternalId = $departmentNode.ParentExternalId
            status           = $departmentNode.Status
            orderNo          = $departmentNode.OrderNo
        }
    }
)

$userPayload = @(
    foreach ($userRow in $userRows) {
        [ordered]@{
            externalId               = $userRow.ExternalId
            username                 = $userRow.Username
            realName                 = $userRow.RealName
            email                    = $userRow.Email
            mobile                   = $userRow.Mobile
            employeeNo               = $userRow.EmployeeNo
            mainDepartmentExternalId = $userRow.MainDepartmentExternalId
            status                   = $userRow.Status
            orderNo                  = $userRow.OrderNo
        }
    }
)

$departmentJson = $departmentPayload | ConvertTo-Json -Depth 8
$userJson = $userPayload | ConvertTo-Json -Depth 8

Write-Utf8NoBomFile -Path $resolvedDepartmentOutputPath -Content $departmentJson
Write-Utf8NoBomFile -Path $resolvedUserOutputPath -Content $userJson

$importRoot = Resolve-LocalPath -Path "docs/feishu-import"
$departmentImportHint = if ($resolvedDepartmentOutputPath.StartsWith($importRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    $resolvedDepartmentOutputPath.Substring($importRoot.Length).TrimStart("\", "/").Replace("\", "/")
}
else {
    $resolvedDepartmentOutputPath
}
$userImportHint = if ($resolvedUserOutputPath.StartsWith($importRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    $resolvedUserOutputPath.Substring($importRoot.Length).TrimStart("\", "/").Replace("\", "/")
}
else {
    $resolvedUserOutputPath
}

Write-Host "Conversion complete." -ForegroundColor Green
Write-Host "Department worksheet: $($departmentWorksheet.WorksheetName)"
Write-Host "User worksheet: $($userWorksheet.WorksheetName)"
Write-Host "Department count: $($departmentPayload.Count)"
Write-Host "User count: $($userPayload.Count)"
Write-Host "Department JSON: $resolvedDepartmentOutputPath"
Write-Host "User JSON: $resolvedUserOutputPath"
Write-Host "Import path hints:"
Write-Host "  Departments: $departmentImportHint"
Write-Host "  Users: $userImportHint"
