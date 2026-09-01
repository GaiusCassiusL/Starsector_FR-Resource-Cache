[CmdletBinding()]
param(
    [string] $JdkHome = $env:JAVA_HOME,
    [string] $OutputPath = (Join-Path $PSScriptRoot 'build\fr-resource-cache-agent.jar')
)

$ErrorActionPreference = 'Stop'

if (-not $JdkHome) {
    $javacCommand = Get-Command javac -ErrorAction SilentlyContinue
    if ($javacCommand) {
        $JdkHome = Split-Path (Split-Path $javacCommand.Source)
    }
}

$jdkInstructions = 'Pass its directory with -JdkHome, set JAVA_HOME, or add javac to PATH.'
if (-not $JdkHome) {
    throw "A JDK 17 or newer is required. $jdkInstructions"
}

$javac = Join-Path $JdkHome 'bin\javac.exe'
$jar = Join-Path $JdkHome 'bin\jar.exe'
if (-not (Test-Path $javac) -or -not (Test-Path $jar)) {
    throw "A JDK with javac.exe and jar.exe is required. $jdkInstructions"
}

$buildRoot = Join-Path $PSScriptRoot 'build'
$agentClasses = Join-Path $buildRoot 'agent-classes'
$stubClasses = Join-Path $buildRoot 'stub-classes'
$payloadClasses = Join-Path $buildRoot 'payload-classes'
$staging = Join-Path $buildRoot 'staging'
$manifest = Join-Path $buildRoot 'MANIFEST.MF'

Remove-Item $agentClasses, $stubClasses, $payloadClasses, $staging -Recurse -Force -ErrorAction SilentlyContinue
New-Item $agentClasses, $stubClasses, $payloadClasses, $staging -ItemType Directory -Force | Out-Null

$agentSources = Get-ChildItem (Join-Path $PSScriptRoot 'src\main\java') -Recurse -Filter *.java |
    ForEach-Object FullName
$stubSources = Get-ChildItem (Join-Path $PSScriptRoot 'src\stubs\java') -Recurse -Filter *.java |
    ForEach-Object FullName
$payloadSources = Get-ChildItem (Join-Path $PSScriptRoot 'src\payload\java') -Recurse -Filter *.java |
    ForEach-Object FullName

& $javac --release 17 -encoding UTF-8 -d $agentClasses $agentSources
if ($LASTEXITCODE) { throw "Agent compilation failed with exit code $LASTEXITCODE." }

& $javac --release 17 -encoding UTF-8 -d $stubClasses $stubSources
if ($LASTEXITCODE) { throw "Stub compilation failed with exit code $LASTEXITCODE." }

& $javac --release 17 -encoding UTF-8 -cp "$agentClasses;$stubClasses" -d $payloadClasses $payloadSources
if ($LASTEXITCODE) { throw "Payload compilation failed with exit code $LASTEXITCODE." }

Copy-Item (Join-Path $agentClasses '*') $staging -Recurse
$payloadResource = Join-Path $staging 'payload\com\genir\renderer\overrides\loading'
New-Item $payloadResource -ItemType Directory -Force | Out-Null
Copy-Item (
    Join-Path $payloadClasses 'com\genir\renderer\overrides\loading\ResourceHandle.class'
) (Join-Path $payloadResource 'ResourceHandle.v1.class.bin')

$manifestLines = @(
    'Manifest-Version: 1.0'
    'Premain-Class: dev.frresourcecache.FrResourceCacheAgent'
    'Can-Redefine-Classes: false'
    'Can-Retransform-Classes: false'
    'Implementation-Title: Fast Rendering Persistent Resource Cache Agent'
    'Implementation-Version: 0.3.0'
    'Created-By: reproducible build.ps1'
    ''
)
[IO.File]::WriteAllText($manifest, ($manifestLines -join "`r`n"), [Text.Encoding]::ASCII)

$outputDirectory = Split-Path $OutputPath
New-Item $outputDirectory -ItemType Directory -Force | Out-Null
Remove-Item $OutputPath -Force -ErrorAction SilentlyContinue
& $jar --create --file $OutputPath --manifest $manifest -C $staging .
if ($LASTEXITCODE) { throw "JAR creation failed with exit code $LASTEXITCODE." }

Remove-Item $agentClasses, $stubClasses, $payloadClasses, $staging -Recurse -Force
Remove-Item $manifest -Force

Write-Host "Built $OutputPath"
