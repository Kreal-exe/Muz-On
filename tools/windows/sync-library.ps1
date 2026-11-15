# Keeps the phone and iTunes on Windows in step, both ways (the Windows twin of tools/sync-library.sh).
#  1. Playlists made on the phone are made in iTunes; songs added, removed or moved, names changed and
#     playlists deleted on the phone are applied to the regular playlists of iTunes (COM interface).
#     Smart playlists are left to their rules. Hearts cannot be set: the iTunes COM interface has no
#     "loved" property, so they stay on the phone until a Mac sync or until set by hand.
#  2. The XML library iTunes writes, and the list of applied changes, go to the phone.
#
# Needs adb on the PATH and, in iTunes, Edit > Preferences > Advanced > "Share iTunes Library XML with
# other applications". Run:  powershell -ExecutionPolicy Bypass -File tools\windows\sync-library.ps1 [adb-serial]
param([string]$Serial = "")
$ErrorActionPreference = "Stop"
$serialArgs = @(); if ($Serial) { $serialArgs = @("-s", $Serial) }
# adb writes notes and "file not found" to stderr; under "Stop" Windows PowerShell 5.1 would end the
# script on them, so adb runs with errors allowed and its exit code decides.
function Adb { $ErrorActionPreference = "Continue"; & adb @serialArgs @args 2>$null }
$appDir = "/sdcard/Android/data/dev.kreal.muzon/files"
$target = "/sdcard/Music/iTunes"
$stateFile = Join-Path $env:APPDATA "Muz On\made-playlists.txt"
$tmp = Join-Path $env:TEMP ("muzon-" + [guid]::NewGuid())
New-Item -ItemType Directory $tmp | Out-Null
try {
    $itunes = New-Object -ComObject iTunes.Application
    $xmlPath = $itunes.LibraryXMLPath
    $library = $itunes.LibraryPlaylist

    function Pid-Of($obj) { "{0:X8}{1:X8}" -f $itunes.ITObjectPersistentIDHigh($obj), $itunes.ITObjectPersistentIDLow($obj) }
    function Split-Pid([string]$id) { [Convert]::ToInt32($id.Substring(0, 8), 16), [Convert]::ToInt32($id.Substring(8, 8), 16) }
    function Track-Of([string]$id) { $h, $l = Split-Pid $id; $library.Tracks.ItemByPersistentID($h, $l) }
    function Playlist-Of([string]$id) { $h, $l = Split-Pid $id; $itunes.LibrarySource.Playlists.ItemByPersistentID($h, $l) }
    function Ids-Of($playlist) { $out = @(); for ($i = 1; $i -le $playlist.Tracks.Count; $i++) { $out += Pid-Of $playlist.Tracks.Item($i) }; ,$out }

    # Brings the playlist's songs to $wanted with as little work as it can.
    function Set-Tracks($playlist, [string[]]$wanted) {
        $wanted = @($wanted | ForEach-Object { $_.ToUpper() } | Where-Object { Track-Of $_ })
        $now = Ids-Of $playlist
        if (($now -join ",") -eq ($wanted -join ",")) { return }
        $kept = @($now | Where-Object { $wanted -contains $_ })
        $prefix = $now.Count -le $wanted.Count
        for ($i = 0; $prefix -and $i -lt $now.Count; $i++) { if ($wanted[$i] -ne $now[$i]) { $prefix = $false } }
        if ($prefix) {
            $wanted | Select-Object -Skip $now.Count | ForEach-Object { [void]$playlist.AddTrack((Track-Of $_)) }
        } elseif (($kept -join ",") -eq ($wanted -join ",")) {
            for ($i = $now.Count; $i -ge 1; $i--) { if ($wanted -notcontains $now[$i - 1]) { $playlist.Tracks.Item($i).Delete() } }
        } else {
            for ($i = $playlist.Tracks.Count; $i -ge 1; $i--) { $playlist.Tracks.Item($i).Delete() }
            $wanted | ForEach-Object { [void]$playlist.AddTrack((Track-Of $_)) }
        }
    }

    $made = @{}
    if (Test-Path $stateFile) { Get-Content $stateFile -Encoding UTF8 | ForEach-Object { $p = $_.Trim().Split(" "); if ($p.Count -eq 2) { $made[$p[0]] = $p[1] } } }
    $applied = @()
    $changed = $false
    Adb pull "$appDir/library-changes.json" "$tmp\changes.json" | Out-Null
    if (Test-Path "$tmp\changes.json") {
        $changes = Get-Content "$tmp\changes.json" -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($changes.favourites) { Write-Host "$(@($changes.favourites).Count) hearts stay on the phone: iTunes for Windows cannot set them from a script." }
        foreach ($c in @($changes.playlists)) {
            try {
                if ($c.new) {
                    $pl = $null
                    if ($made[$c.id]) { $pl = Playlist-Of $made[$c.id] }
                    if (-not $pl) {
                        $pl = $itunes.CreatePlaylist($c.name)
                        $made[$c.id] = Pid-Of $pl
                        New-Item -ItemType Directory -Force (Split-Path $stateFile) | Out-Null
                        [IO.File]::WriteAllLines($stateFile, @($made.GetEnumerator() | ForEach-Object { "$($_.Key) $($_.Value)" }))
                        Write-Host "new playlist $($c.name)"
                    } elseif ($pl.Name -ne $c.name) { $pl.Name = $c.name }
                    Set-Tracks $pl @($c.tracks)
                    $applied += "$($c.id) $($made[$c.id]) $($c.rev)"
                    $changed = $true
                    continue
                }
                $pl = Playlist-Of $c.id
                if (-not $pl) { Write-Host "no playlist $($c.id)"; continue }
                if ($pl.Smart) { Write-Host "smart, left to its rules: $($pl.Name)"; continue }
                if ($c.deleted) { Write-Host "deleted $($pl.Name)"; $pl.Delete() }
                else {
                    if ($c.name -and $pl.Name -ne $c.name) { $pl.Name = $c.name }
                    if ($null -ne $c.tracks) { Set-Tracks $pl @($c.tracks) }
                    Write-Host "changed $($pl.Name)"
                }
                $applied += "$($c.id) $($c.rev)"
                $changed = $true
            } catch { Write-Host "cannot apply $($c.id): $_" }
        }
        # Hearts are kept in the file for the phone; only the playlists are done.
        Adb shell rm -f "$appDir/library-changes.json" | Out-Null
    }

    if (-not (Test-Path $xmlPath)) { throw "No XML library at $xmlPath. Turn on iTunes > Edit > Preferences > Advanced > Share iTunes Library XML with other applications." }
    if ($changed) {
        # iTunes writes the XML a little after a change; wait for it.
        $before = (Get-Item $xmlPath).LastWriteTime
        for ($i = 0; $i -lt 30 -and (Get-Item $xmlPath).LastWriteTime -eq $before; $i++) { Start-Sleep 1 }
        Start-Sleep 2
    }
    Adb shell mkdir -p $target | Out-Null
    Adb push $xmlPath "$target/iTunes Music Library.xml"
    [IO.File]::WriteAllLines("$tmp\muzon-applied.txt", [string[]]$applied)
    Adb push "$tmp\muzon-applied.txt" "$target/muzon-applied.txt" | Out-Null
    Write-Host "Done."
} finally {
    Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
}
