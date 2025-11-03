# Fix TickEvent imports to use specific event classes
$javaFiles = Get-ChildItem -Path "src\main\java" -Filter "*.java" -Recurse
$count = 0

foreach ($file in $javaFiles) {
    $content = Get-Content -Path $file.FullName -Raw
    $modified = $false
    
    # If file uses ServerTickEvent.Post, make sure it has the right import
    if ($content -match 'ServerTickEvent\.Post' -and $content -match 'import net\.neoforged\.neoforge\.event\.tick\.TickEvent;') {
        $content = $content -replace 'import net\.neoforged\.neoforge\.event\.tick\.TickEvent;', 'import net.neoforged.neoforge.event.tick.ServerTickEvent;'
        $modified = $true
        Write-Host "Updated $($file.Name): Import ServerTickEvent"
        $count++
    }
    
    # If file uses ClientTickEvent.Post, make sure it has the right import
    if ($content -match 'ClientTickEvent\.Post' -and $content -match 'import net\.neoforged\.neoforge\.event\.tick\.TickEvent;') {
        $content = $content -replace 'import net\.neoforged\.neoforge\.event\.tick\.TickEvent;', 'import net.neoforged.neoforge.event.tick.ClientTickEvent;'
        $modified = $true
        Write-Host "Updated $($file.Name): Import ClientTickEvent"
        $count++
    }
    
    if ($modified) {
        Set-Content -Path $file.FullName -Value $content -NoNewline
    }
}

Write-Host "`nTotal import fixes: $count"
