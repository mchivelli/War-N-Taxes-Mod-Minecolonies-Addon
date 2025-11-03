# Fix TickEvent references to use NeoForge API
$javaFiles = Get-ChildItem -Path "src\main\java" -Filter "*.java" -Recurse
$count = 0

foreach ($file in $javaFiles) {
    $content = Get-Content -Path $file.FullName -Raw
    $modified = $false
    
    # Fix TickEvent.ServerTickEvent to use ServerTickEvent.Post
    if ($content -match 'TickEvent\.ServerTickEvent') {
        $content = $content -replace 'TickEvent\.ServerTickEvent', 'ServerTickEvent.Post'
        $modified = $true
        Write-Host "Updated $($file.Name): TickEvent.ServerTickEvent -> ServerTickEvent.Post"
        $count++
    }
    
    # Fix phase checks - NeoForge doesn't have phases anymore, events fire once
    if ($content -match 'if \(event\.phase [!=]= TickEvent\.Phase\.(END|START)\)') {
        # Remove the phase check entirely
        $content = $content -replace '        if \(event\.phase [!=]= TickEvent\.Phase\.(END|START)\) \{\s+return;\s+\}\s+', ''
        $content = $content -replace 'if \(event\.phase == TickEvent\.Phase\.END\) \{', '{'
        $modified = $true
        Write-Host "Updated $($file.Name): Removed phase check"
        $count++
    }
    
    # Fix TickEvent.ClientTickEvent
    if ($content -match 'TickEvent\.ClientTickEvent') {
        $content = $content -replace 'TickEvent\.ClientTickEvent', 'ClientTickEvent.Post'
        $modified = $true
        Write-Host "Updated $($file.Name): TickEvent.ClientTickEvent -> ClientTickEvent.Post"
        $count++
    }
    
    if ($modified) {
        Set-Content -Path $file.FullName -Value $content -NoNewline
    }
}

Write-Host "`nTotal TickEvent fixes: $count"
