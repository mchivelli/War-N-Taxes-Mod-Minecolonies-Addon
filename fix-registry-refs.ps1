# Fix incorrect registry references
$javaFiles = Get-ChildItem -Path "src\main\java" -Filter "*.java" -Recurse
$count = 0

foreach ($file in $javaFiles) {
    $content = Get-Content -Path $file.FullName -Raw
    $modified = $false
    
    # Fix incorrect fully-qualified BuiltInRegistries path
    if ($content -match 'net\.neoforged\.neoforge\.registries\.BuiltInRegistries') {
        $content = $content -replace 'net\.neoforged\.neoforge\.registries\.BuiltInRegistries', 'net.minecraft.core.registries.BuiltInRegistries'
        $modified = $true
        Write-Host "Fixed BuiltInRegistries path in $($file.Name)"
        $count++
    }
    
    # Fix NeoBuiltInRegistries to NeoForgeRegistries
    if ($content -match 'NeoBuiltInRegistries\.Keys\.ATTACHMENT_TYPES') {
        $content = $content -replace 'NeoBuiltInRegistries\.Keys\.ATTACHMENT_TYPES', 'NeoForgeRegistries.Keys.ATTACHMENT_TYPES'
        $modified = $true
        Write-Host "Fixed NeoBuiltInRegistries in $($file.Name)"
        $count++
    }
    
    # Fix mangled SDMEconomy package names
    if ($content -match 'net\.sixik\.SDMEconomyework\.SDMShopCompat') {
        $content = $content -replace 'net\.sixik\.SDMEconomyework\.SDMShopCompat', 'net.machiavelli.minecolonytax.integration.SDMShopCompat'
        $modified = $true
        Write-Host "Fixed SDMShopCompat reference in $($file.Name)"
        $count++
    }
    
    # Fix any remaining "new ResourceLocation" that wasn't caught
    if ($content -match 'new net\.minecraft\.resources\.ResourceLocation\(') {
        $content = $content -replace 'new net\.minecraft\.resources\.ResourceLocation\(([^)]+)\)', 'net.minecraft.resources.ResourceLocation.parse($1)'
        $modified = $true
        Write-Host "Fixed ResourceLocation constructor in $($file.Name)"
        $count++
    }
    
    if ($modified) {
        Set-Content -Path $file.FullName -Value $content -NoNewline
    }
}

Write-Host "`nTotal fixes: $count"
