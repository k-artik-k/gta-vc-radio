<#+
Packages locally supplied radio recordings without touching the originals in /stations.
128 kbps MP3 sources become 80 kbps AAC/M4A; already-small recordings remain MP3.
#>
[CmdletBinding()]
param(
    [ValidateRange(48, 128)]
    [int]$AacBitrateKbps = 80,
    [switch]$KeepOriginalMp3
)

$projectRoot = Split-Path -Parent $PSScriptRoot
$sourceDirectory = Join-Path $projectRoot 'stations'
$assetDirectory = Join-Path $projectRoot 'app\src\main\assets\stations'
if (-not (Test-Path -LiteralPath $sourceDirectory)) {
    throw "Missing source recordings: $sourceDirectory"
}

$recordings = Get-ChildItem -LiteralPath $sourceDirectory -File -Filter '*.mp3' | Sort-Object Name
if ($recordings.Count -eq 0) { throw "No MP3 recordings found in $sourceDirectory" }

function Find-CodecTool([string]$name) {
    $onPath = Get-Command $name -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    $kdenliveTool = Join-Path ${env:ProgramFiles} "Kdenlive\bin\$name.exe"
    if (Test-Path -LiteralPath $kdenliveTool) { return $kdenliveTool }
    return $null
}

$ffmpeg = Find-CodecTool 'ffmpeg'
$ffprobe = Find-CodecTool 'ffprobe'
if (-not $KeepOriginalMp3 -and (-not $ffmpeg -or -not $ffprobe)) {
    throw 'FFmpeg and FFprobe are required for optimized packaging. Install FFmpeg or rerun with -KeepOriginalMp3.'
}

New-Item -ItemType Directory -Force -Path $assetDirectory | Out-Null
Get-ChildItem -LiteralPath $assetDirectory -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Extension -in '.mp3', '.m4a' } |
    Remove-Item -Force

foreach ($recording in $recordings) {
    $sourceBitrate = if ($KeepOriginalMp3) { 0 } else {
        [int](& $ffprobe -v error -show_entries format=bit_rate -of default=noprint_wrappers=1:nokey=1 $recording.FullName)
    }
    if (-not $KeepOriginalMp3 -and $sourceBitrate -gt 96000) {
        $destination = Join-Path $assetDirectory ($recording.BaseName + '.m4a')
        Write-Host "Transcoding $($recording.Name) to $AacBitrateKbps kbps AAC"
        & $ffmpeg -y -v error -i $recording.FullName -map_metadata -1 -vn -c:a aac -b:a "${AacBitrateKbps}k" -movflags +faststart $destination
        if ($LASTEXITCODE -ne 0) { throw "FFmpeg failed for $($recording.Name)" }
    } else {
        Write-Host "Copying $($recording.Name)"
        Copy-Item -LiteralPath $recording.FullName -Destination (Join-Path $assetDirectory $recording.Name)
    }
}

Get-ChildItem -LiteralPath $assetDirectory -File | Select-Object Name, Length
