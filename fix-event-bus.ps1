# Fix Mod.EventBusSubscriber.Bus.FORGE -> Bus.GAME for NeoForge
$javaFiles = Get-ChildItem -Path "src\main\java" -Filter "*.java" -Recurse
$count = 0

foreach ($file in $javaFiles) {
    $content = Get-Content -Path $file.FullName -Raw
    $modified = $false
    
    if ($content -match 'Mod\.EventBusSubscriber\.Bus\.FORGE') {
        $content = $content -replace 'Mod\.EventBusSubscriber\.Bus\.FORGE', 'Mod.EventBusSubscriber.Bus.GAME'
        $modified = $true
        Write-Host "Fixed $($file.Name): Bus.FORGE -> Bus.GAME"
        $count++
    }
    
    if ($modified) {
        Set-Content -Path $file.FullName -Value $content -NoNewline
    }
}

Write-Host "`nTotal event bus fixes: $count"
