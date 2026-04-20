param(
    [Parameter(Position = 0)]
    [string]$InputPath = "docs/demo.xlsx",

    [string]$WorksheetName,

    [string]$DepartmentOutputPath,

    [string]$UserOutputPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$ScriptRoot = Split-Path -Parent $PSCommandPath

function Resolve-LocalPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

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
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$Content
    )

    $directory = Split-Path -Parent $Path
    if ($directory) {
        [System.IO.Directory]::CreateDirectory($directory) | Out-Null
    }
    [System.IO.File]::WriteAllText($Path, $Content, (New-Utf8NoBomEncoding))
}

function Read-ZipEntryText {
    param(
        [Parameter(Mandatory = $true)]
        [System.IO.Compression.ZipArchive]$Archive,

        [Parameter(Mandatory = $true)]
        [string]$EntryName
    )

    $entry = $Archive.GetEntry($EntryName)
    if (-not $entry) {
        throw "Excel 文件缺少必要条目：$EntryName"
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
    param(
        [Parameter(Mandatory = $true)]
        [System.IO.Compression.ZipArchive]$Archive
    )

    $entry = $Archive.GetEntry("xl/sharedStrings.xml")
    if (-not $entry) {
        return @()
    }

    [xml]$sharedStringsXml = Read-ZipEntryText -Archive $Archive -EntryName "xl/sharedStrings.xml"
    $result = @()
    foreach ($stringNode in $sharedStringsXml.SelectNodes("//*[local-name()='si']")) {
        $textNodes = $stringNode.SelectNodes(".//*[local-name()='t']")
        if (@($textNodes).Count -eq 0) {
            $result += ""
            continue
        }
        $value = ($textNodes | ForEach-Object { $_.InnerText }) -join ""
        $result += $value
    }
    return $result
}

function Get-WorkbookSheets {
    param(
        [Parameter(Mandatory = $true)]
        [System.IO.Compression.ZipArchive]$Archive
    )

    [xml]$workbookXml = Read-ZipEntryText -Archive $Archive -EntryName "xl/workbook.xml"
    [xml]$relationXml = Read-ZipEntryText -Archive $Archive -EntryName "xl/_rels/workbook.xml.rels"

    $relationById = @{}
    foreach ($relationNode in $relationXml.SelectNodes("//*[local-name()='Relationship']")) {
        $relationById[$relationNode.Id] = $relationNode.Target
    }

    $sheets = @()
    foreach ($sheetNode in $workbookXml.SelectNodes("//*[local-name()='sheet']")) {
        $relationshipId = $sheetNode.GetAttribute("id", "http://schemas.openxmlformats.org/officeDocument/2006/relationships")
        $target = $relationById[$relationshipId]
        if (-not $target) {
            continue
        }
        $sheets += [pscustomobject]@{
            Name      = $sheetNode.name
            Target    = "xl/$target"
            SheetId   = [string]$sheetNode.sheetId
        }
    }
    return $sheets
}

function Get-CellColumnLetters {
    param(
        [Parameter(Mandatory = $true)]
        [string]$CellReference
    )

    return ($CellReference -replace "\d", "")
}

function Get-CellValue {
    param(
        [Parameter(Mandatory = $true)]
        [System.Xml.XmlElement]$CellNode,

        [Parameter(Mandatory = $true)]
        [object[]]$SharedStrings
    )

    $cellType = $CellNode.GetAttribute("t")
    if ($cellType -eq "inlineStr") {
        $textNodes = $CellNode.SelectNodes(".//*[local-name()='t']")
        return ($textNodes | ForEach-Object { $_.InnerText }) -join ""
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
        [Parameter(Mandatory = $true)]
        [System.IO.Compression.ZipArchive]$Archive,

        [Parameter(Mandatory = $true)]
        [string]$WorksheetEntryName,

        [Parameter(Mandatory = $true)]
        [object[]]$SharedStrings
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

function Get-MetaConfiguration {
    param(
        [Parameter(Mandatory = $true)]
        [System.IO.Compression.ZipArchive]$Archive,

        [Parameter(Mandatory = $true)]
        [object[]]$SharedStrings,

        [Parameter(Mandatory = $true)]
        [object[]]$WorkbookSheets
    )

    $metaSheet = $WorkbookSheets | Where-Object { $_.Name -eq "meta" } | Select-Object -First 1
    if (-not $metaSheet) {
        return $null
    }

    $rows = @(Get-WorksheetRows -Archive $Archive -WorksheetEntryName $metaSheet.Target -SharedStrings @($SharedStrings))
    if (@($rows).Count -eq 0) {
        return $null
    }

    $metaPayload = $rows[0].Cells["A"]
    if ([string]::IsNullOrWhiteSpace($metaPayload)) {
        return $null
    }

    try {
        $metaJson = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($metaPayload))
        return $metaJson | ConvertFrom-Json -Depth 10
    }
    catch {
        return $null
    }
}

function Get-RowMaps {
    param(
        [Parameter(Mandatory = $true)]
        [object[]]$Rows,

        [Parameter(Mandatory = $true)]
        [int]$HeaderRowNumber,

        [Parameter(Mandatory = $true)]
        [int]$DataStartRowNumber
    )

    $headerRow = $Rows | Where-Object { $_.RowNumber -eq $HeaderRowNumber } | Select-Object -First 1
    if (-not $headerRow) {
        throw "未找到表头行：第 $HeaderRowNumber 行"
    }

    $headerByColumn = @{}
    foreach ($entry in $headerRow.Cells.GetEnumerator()) {
        $headerByColumn[$entry.Key] = [string]$entry.Value
    }

    $result = @()
    foreach ($row in $Rows | Where-Object { $_.RowNumber -ge $DataStartRowNumber }) {
        $rowMap = [ordered]@{}
        foreach ($header in $headerByColumn.GetEnumerator()) {
            $rawValue = if ($row.Cells.Contains($header.Key)) { [string]$row.Cells[$header.Key] } else { "" }
            $rowMap[$header.Value] = $rawValue
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
    return $result
}

function Get-NormalizedValue {
    param(
        [AllowNull()]
        [string]$Value
    )

    if ($null -eq $Value) {
        return $null
    }
    $trimmed = $Value.Trim()
    if ($trimmed.Length -eq 0) {
        return $null
    }
    return $trimmed
}

function Get-PreferredValue {
    param(
        [Parameter(Mandatory = $true)]
        [object[]]$Candidates
    )

    foreach ($candidate in $Candidates) {
        $normalized = Get-NormalizedValue -Value ([string]$candidate)
        if ($null -ne $normalized) {
            return $normalized
        }
    }
    return $null
}

function Get-DepartmentSegments {
    param(
        [Parameter(Mandatory = $true)]
        [string]$DepartmentPath
    )

    return @(
        ($DepartmentPath -split "[/／]+" | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    )
}

function Get-Sha1Hex {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Text
    )

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
    param(
        [Parameter(Mandatory = $true)]
        [string]$DepartmentPath
    )

    return "xlsx_dept_" + (Get-Sha1Hex -Text $DepartmentPath)
}

function New-DepartmentCode {
    param(
        [Parameter(Mandatory = $true)]
        [string]$DepartmentPath
    )

    return "FD" + ((Get-Sha1Hex -Text $DepartmentPath).Substring(0, 12).ToUpperInvariant())
}

function Get-UsernameFromRow {
    param(
        [Parameter(Mandatory = $true)]
        [System.Collections.IDictionary]$Values
    )

    $employeeNo = Get-NormalizedValue -Value $Values["工号"]
    if ($employeeNo) {
        return $employeeNo
    }

    $email = Get-NormalizedValue -Value $Values["工作邮箱"]
    if ($email -and $email.Contains("@")) {
        return $email.Substring(0, $email.IndexOf("@"))
    }

    $mobile = Get-NormalizedValue -Value $Values["联系手机"]
    if ($mobile) {
        return $mobile
    }

    return Get-PreferredValue @(
        $Values["用户 ID（修改值）"],
        $Values["用户 ID"]
    )
}

function Convert-UserStatus {
    param(
        [AllowNull()]
        [string]$AccountStatus
    )

    $normalized = Get-NormalizedValue -Value $AccountStatus
    if ($null -eq $normalized) {
        return 1
    }

    $enabledStatuses = @("正常", "启用", "在职", "已激活", "active", "enabled")
    if ($enabledStatuses -contains $normalized) {
        return 1
    }
    return 0
}

function Assert-RequiredHeaders {
    param(
        [Parameter(Mandatory = $true)]
        [System.Collections.IDictionary]$HeaderValues,

        [Parameter(Mandatory = $true)]
        [string[]]$RequiredHeaders
    )

    $missingHeaders = @()
    foreach ($requiredHeader in $RequiredHeaders) {
        if (-not (@($HeaderValues.Values) -contains $requiredHeader)) {
            $missingHeaders += $requiredHeader
        }
    }

    if (@($missingHeaders).Count -gt 0) {
        throw "Excel 中缺少必要列：$($missingHeaders -join '、')"
    }
}

function Assert-UniqueField {
    param(
        [Parameter(Mandatory = $true)]
        [object[]]$Rows,

        [Parameter(Mandatory = $true)]
        [string]$FieldName
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
            "$($_.Key)（行号：$($_.Value -join ', ')）"
        }
        throw "$FieldName 存在重复：$($messages -join '；')"
    }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem

$resolvedInputPath = Resolve-LocalPath -Path $InputPath
if (-not (Test-Path -LiteralPath $resolvedInputPath -PathType Leaf)) {
    throw "找不到输入文件：$resolvedInputPath"
}

$baseName = [System.IO.Path]::GetFileNameWithoutExtension($resolvedInputPath)
if (-not $DepartmentOutputPath) {
    $DepartmentOutputPath = "docs/feishu-import/departments/$baseName.json"
}
if (-not $UserOutputPath) {
    $UserOutputPath = "docs/feishu-import/users/$baseName.json"
}

$resolvedDepartmentOutputPath = Resolve-LocalPath -Path $DepartmentOutputPath
$resolvedUserOutputPath = Resolve-LocalPath -Path $UserOutputPath

$archive = [System.IO.Compression.ZipFile]::OpenRead($resolvedInputPath)
try {
    $sharedStrings = @(Get-SharedStrings -Archive $archive)
    $workbookSheets = @(Get-WorkbookSheets -Archive $archive)
    if (@($workbookSheets).Count -eq 0) {
        throw "Excel 文件中未找到可读取的工作表"
    }

    $metaConfig = Get-MetaConfiguration -Archive $archive -SharedStrings @($sharedStrings) -WorkbookSheets $workbookSheets
    $dataStartRowNumber = if ($metaConfig -and $metaConfig.data_row_start) { [int]$metaConfig.data_row_start } else { 3 }
    $headerRowNumber = $dataStartRowNumber - 1

    $primarySheet = if ($WorksheetName) {
        $workbookSheets | Where-Object { $_.Name -eq $WorksheetName } | Select-Object -First 1
    }
    else {
        $workbookSheets | Where-Object { $_.Name -ne "meta" } | Select-Object -First 1
    }
    if (-not $primarySheet) {
        throw "未找到要读取的工作表：$WorksheetName"
    }

    $worksheetRows = @(Get-WorksheetRows -Archive $archive -WorksheetEntryName $primarySheet.Target -SharedStrings @($sharedStrings))
    $headerRow = $worksheetRows | Where-Object { $_.RowNumber -eq $headerRowNumber } | Select-Object -First 1
    if (-not $headerRow) {
        throw "未找到 Excel 表头行：第 $headerRowNumber 行"
    }

    Assert-RequiredHeaders -HeaderValues $headerRow.Cells -RequiredHeaders @("用户 ID", "姓名", "部门", "账号状态")

    $rowMaps = @(Get-RowMaps -Rows $worksheetRows -HeaderRowNumber $headerRowNumber -DataStartRowNumber $dataStartRowNumber)
    if (@($rowMaps).Count -eq 0) {
        throw "Excel 中没有可转换的数据行"
    }

    $departmentNodes = [ordered]@{}
    $nextOrderByParent = @{}
    $userRows = @()
    $rowOrder = 1

    foreach ($row in $rowMaps) {
        $values = $row.Values
        $departmentPath = Get-NormalizedValue -Value $values["部门"]
        if ($null -eq $departmentPath) {
            throw "第 $($row.RowNumber) 行缺少部门路径"
        }

        $segments = Get-DepartmentSegments -DepartmentPath $departmentPath
        if (@($segments).Count -eq 0) {
            throw "第 $($row.RowNumber) 行部门路径为空：$departmentPath"
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
                Depth            = $pathParts.Count
            }
        }

        $primaryUserId = Get-PreferredValue @(
            $values["用户 ID（修改值）"],
            $values["用户 ID"]
        )
        if ($null -eq $primaryUserId) {
            throw "第 $($row.RowNumber) 行缺少用户 ID"
        }

        $userName = Get-UsernameFromRow -Values $values
        if ($null -eq $userName) {
            throw "第 $($row.RowNumber) 行无法推导 username，请补充工号、工作邮箱、联系手机或用户 ID"
        }

        $userRows += [pscustomobject]@{
            RowNumber                 = $row.RowNumber
            ExternalId                = $primaryUserId
            Username                  = $userName
            RealName                  = Get-NormalizedValue -Value $values["姓名"]
            Email                     = Get-NormalizedValue -Value $values["工作邮箱"]
            Mobile                    = Get-NormalizedValue -Value $values["联系手机"]
            EmployeeNo                = Get-NormalizedValue -Value $values["工号"]
            MainDepartmentPath        = $departmentPath
            MainDepartmentExternalId  = $null
            Status                    = Convert-UserStatus -AccountStatus $values["账号状态"]
            OrderNo                   = $rowOrder
        }
        $rowOrder++
    }

    foreach ($departmentNode in $departmentNodes.Values) {
        if ($departmentNode.ParentPath -and -not $departmentNodes.Contains($departmentNode.ParentPath)) {
            throw "部门路径缺少父节点：$($departmentNode.Path)"
        }
        if ($departmentNode.ParentPath) {
            $departmentNode.ParentExternalId = $departmentNodes[$departmentNode.ParentPath].ExternalId
        }
    }

    foreach ($userRow in $userRows) {
        if (-not $departmentNodes.Contains($userRow.MainDepartmentPath)) {
            throw "第 $($userRow.RowNumber) 行主部门不存在：$($userRow.MainDepartmentPath)"
        }
        $userRow.MainDepartmentExternalId = $departmentNodes[$userRow.MainDepartmentPath].ExternalId
    }

    Assert-UniqueField -Rows $userRows -FieldName "ExternalId"
    Assert-UniqueField -Rows $userRows -FieldName "Username"
    Assert-UniqueField -Rows $userRows -FieldName "EmployeeNo"

    $departmentPayload = @(
        foreach ($departmentNode in $departmentNodes.Values) {
        [ordered]@{
            externalId      = $departmentNode.ExternalId
            departmentCode  = $departmentNode.DepartmentCode
            departmentName  = $departmentNode.Name
            parentExternalId = $departmentNode.ParentExternalId
            status          = $departmentNode.Status
            orderNo         = $departmentNode.OrderNo
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

    $departmentImportRoot = Resolve-LocalPath -Path "docs/feishu-import"
    $departmentImportHint = if ($resolvedDepartmentOutputPath.StartsWith($departmentImportRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        $resolvedDepartmentOutputPath.Substring($departmentImportRoot.Length).TrimStart("\", "/").Replace("\", "/")
    }
    else {
        $resolvedDepartmentOutputPath
    }
    $userImportHint = if ($resolvedUserOutputPath.StartsWith($departmentImportRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        $resolvedUserOutputPath.Substring($departmentImportRoot.Length).TrimStart("\", "/").Replace("\", "/")
    }
    else {
        $resolvedUserOutputPath
    }

    Write-Host "转换完成。" -ForegroundColor Green
    Write-Host "数据工作表: $($primarySheet.Name)"
    Write-Host "用户数: $($userPayload.Count)"
    Write-Host "部门数: $($departmentPayload.Count)"
    Write-Host "部门 JSON: $resolvedDepartmentOutputPath"
    Write-Host "用户 JSON: $resolvedUserOutputPath"
    Write-Host "导入路径提示:"
    Write-Host "  部门: $departmentImportHint"
    Write-Host "  用户: $userImportHint"
}
finally {
    $archive.Dispose()
}
