# Fix remaining Forge references
$javaFiles = Get-ChildItem -Path "src\main\java" -Filter "*.java" -Recurse
$count = 0

foreach ($file in $javaFiles) {
    $content = Get-Content -Path $file.FullName -Raw
    $modified = $false
    
    # Fix ForgeRegistries to NeoForgeRegistries
    if ($content -match 'ForgeRegistries\.') {
        $content = $content -replace 'ForgeRegistries\.', 'BuiltInRegistries.'
        # Also fix the import
        $content = $content -replace 'import net\.neoforged\.neoforge\.registries\.ForgeRegistries;', 'import net.minecraft.core.registries.BuiltInRegistries;'
        $modified = $true
        Write-Host "Fixed ForgeRegistries in $($file.Name)"
        $count++
    }
    
    # Fix ResourceLocation constructor (NeoForge uses ResourceLocation.parse())
    if ($content -match 'new ResourceLocation\(([^)]+)\)') {
        $content = $content -replace 'new ResourceLocation\(([^)]+)\)', 'ResourceLocation.parse($1)'
        $modified = $true
        Write-Host "Fixed ResourceLocation constructor in $($file.Name)"
        $count++
    }
    
    # Fix Mod.EventBusSubscriber.Bus enum references
    if ($content -match '@Mod\.EventBusSubscriber' -and $content -notmatch 'Mod\.EventBusSubscriber\.Bus\.') {
        # Files that use the annotation but don't specify bus parameter correctly
        # These should already be fixed, but let's check
    }
    
    if ($modified) {
        Set-Content -Path $file.FullName -Value $content -NoNewline
    }
}

Write-Host "`nTotal fixes: $count"
