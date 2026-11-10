<#
로컬 네 서비스 .env에 같은 공개 주체 UUID만 준비합니다. 키와 비밀번호는 출력하거나 변경하지 않습니다.
기존 주체가 충돌하면 모든 쓰기 전에 중단합니다. 실제 서비스 재시작은 별도로 수행합니다.
#>
$ErrorActionPreference = 'Stop'
$accountRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$desktop = [IO.Directory]::GetParent($accountRoot).FullName
$encoding = New-Object Text.UTF8Encoding($false)
$entries = @()
$subject = $null
foreach ($project in @('AccountService','ProductCommandService','OrderCommandService','PaymentService')) {
    $path = Join-Path (Join-Path $desktop $project) '.env'
    if (-not (Test-Path -LiteralPath $path)) { throw '네 서비스의 기존 .env를 먼저 준비해야 합니다.' }
    $text = [IO.File]::ReadAllText($path)
    $found = [regex]::Matches($text, '(?m)^ACCOUNT_WITHDRAWAL_SUBJECT=([^\r\n]*)')
    if ($found.Count -gt 1) { throw '중복된 탈퇴 주체 설정을 먼저 정리해 주세요.' }
    if ($found.Count -eq 1 -and -not [string]::IsNullOrWhiteSpace($found[0].Groups[1].Value)) {
        $value = $found[0].Groups[1].Value
        $parsed = [Guid]::Empty
        if (-not [Guid]::TryParseExact($value,'D',[ref]$parsed)) { throw '탈퇴 서비스 주체는 UUID여야 합니다.' }
        if ($subject -and $subject -ne $value) { throw '서비스별 탈퇴 주체가 달라 자동 변경하지 않습니다.' }
        $subject = $value
    }
    if ($project -eq 'AccountService') {
        $enabled = [regex]::Matches($text,'(?m)^ACCOUNT_WITHDRAWAL_ENABLED=([^\r\n]*)')
        if ($enabled.Count -gt 1) { throw '중복된 활성화 설정을 먼저 정리해 주세요.' }
    }
    $entries += [pscustomobject]@{ Path=$path; Before=$text; Project=$project }
}
if (-not $subject) { $subject = [Guid]::NewGuid().ToString() }
foreach ($entry in $entries) {
    $text = $entry.Before
    if ($text -match '(?m)^ACCOUNT_WITHDRAWAL_SUBJECT=') {
        $text = [regex]::Replace($text,'(?m)^ACCOUNT_WITHDRAWAL_SUBJECT=[^\r\n]*',('ACCOUNT_WITHDRAWAL_SUBJECT=' + $subject))
    } else { $text += [Environment]::NewLine + 'ACCOUNT_WITHDRAWAL_SUBJECT=' + $subject + [Environment]::NewLine }
    if ($entry.Project -eq 'AccountService') {
        if ($text -match '(?m)^ACCOUNT_WITHDRAWAL_ENABLED=') { $text = [regex]::Replace($text,'(?m)^ACCOUNT_WITHDRAWAL_ENABLED=[^\r\n]*','ACCOUNT_WITHDRAWAL_ENABLED=true') }
        else { $text += [Environment]::NewLine + 'ACCOUNT_WITHDRAWAL_ENABLED=true' + [Environment]::NewLine }
    }
    $entry | Add-Member -NotePropertyName After -NotePropertyValue $text
}
foreach ($entry in $entries) { if ([IO.File]::ReadAllText($entry.Path) -ne $entry.Before) { throw '작업 중 .env가 변경돼 중단합니다.' } }
foreach ($entry in $entries) {
    # 각 파일을 같은 디렉토리에서 원자적으로 교체합니다. 네 파일을 묶은 분산 파일 트랜잭션은 아닙니다.
    $temporary = Join-Path ([IO.Path]::GetDirectoryName($entry.Path)) ([Guid]::NewGuid().ToString() + '.tmp')
    try { [IO.File]::WriteAllText($temporary,$entry.After,$encoding); [IO.File]::Replace($temporary,$entry.Path,[NullString]::Value) }
    finally { if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary } }
}
Write-Host '로컬 탈퇴 연동 주체와 Account 활성화를 준비했습니다. 네 서비스를 새 코드로 재시작해 주세요. 비밀값은 출력하지 않았습니다.'
