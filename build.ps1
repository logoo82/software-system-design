param([string]$JdkHome = $env:JAVA_HOME)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not $JdkHome) {
    $compiler = (Get-Command javac -ErrorAction Stop).Source
    $JdkHome = Split-Path (Split-Path $compiler)

    if (-not (Test-Path (Join-Path $JdkHome 'bin/jar.exe'))) {
        $candidates = Get-ChildItem 'C:/Program Files/Java' -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending

        $JdkHome = (
            $candidates |
                Where-Object { Test-Path (Join-Path $_.FullName 'bin/javadoc.exe') } |
                Select-Object -First 1
        ).FullName
    }
}

if (-not (Test-Path (Join-Path $JdkHome 'bin/javac.exe'))) {
    throw 'JDK 17 이상 경로를 -JdkHome으로 지정하세요.'
}

$javac = Join-Path $JdkHome 'bin/javac.exe'
$java = Join-Path $JdkHome 'bin/java.exe'
$jar = Join-Path $JdkHome 'bin/jar.exe'
$javadoc = Join-Path $JdkHome 'bin/javadoc.exe'

New-Item -ItemType Directory -Force build/classes, build/test-classes, build/tmp, dist, docs/api |
    Out-Null

$sources = (Get-ChildItem src -Recurse -Filter *.java).FullName
& $javac --release 17 -encoding UTF-8 -d build/classes $sources

if ($LASTEXITCODE -ne 0) {
    throw '컴파일 실패'
}

$tempPath = Join-Path $PSScriptRoot 'build/tmp'
& $jar "-J-Djava.io.tmpdir=$tempPath" `
    --create `
    --file dist/ssd_ip_cp4_2026f_223247_app.jar `
    --main-class kr.ac.jnu.household.Main `
    -C build/classes .

if ($LASTEXITCODE -ne 0) {
    throw 'JAR 생성 실패'
}

$tests = (Get-ChildItem test -Recurse -Filter *.java).FullName
& $javac --release 17 -encoding UTF-8 -cp build/classes -d build/test-classes $tests

if ($LASTEXITCODE -ne 0) {
    throw '검사 코드 컴파일 실패'
}

& $java '-Dfile.encoding=UTF-8' `
    -cp 'build/classes;build/test-classes' `
    kr.ac.jnu.household.ServiceTest build/test-data

if ($LASTEXITCODE -ne 0) {
    throw '기능 검사 실패'
}

& $javadoc --release 17 `
    -encoding UTF-8 `
    -docencoding UTF-8 `
    -charset UTF-8 `
    -notimestamp `
    -author `
    -version `
    -Xdoclint:all,-missing `
    -d docs/api `
    -sourcepath src `
    -subpackages kr.ac.jnu.household

if ($LASTEXITCODE -ne 0) {
    throw 'Javadoc 생성 실패'
}

Write-Output '완료: dist/ssd_ip_cp4_2026f_223247_app.jar / docs/api/index.html'
