# Fix final compilation issues

$replacements = @{
    # FMLEnvironment moved
    "import net.minecraftforge.fml.loading.FMLEnvironment" = "import net.neoforged.fml.loading.FMLEnvironment"
    
    # SDMShop package changed in version 2.0 - try the new package
    "import net.sixik.sdmshoprework.SDMShopR" = "import net.sixik.sdm_economy.SDMEconomy"
    "SDMShopR" = "SDMEconomy"
    
    # TickEvent.PlayerTickEvent
    "TickEvent.PlayerTickEvent" = "PlayerTickEvent.Post"
}

$javaFiles = Get-ChildItem -Path "src\main\java" -Filter "*.java" -Recurse
$count = 0

foreach ($file in $javaFiles) {
    $content = Get-Content -Path $file.FullName -Raw
    $modified = $false
    
    foreach ($old in $replacements.Keys) {
        if ($content -match [regex]::Escape($old)) {
            $content = $content -replace [regex]::Escape($old), $replacements[$old]
            $modified = $true
            Write-Host "Updated $($file.Name): $old"
            $count++
        }
    }
    
    # Add PlayerTickEvent import if TickEvent.PlayerTickEvent was found
    if ($content -match 'PlayerTickEvent\.Post' -and $content -notmatch 'import net\.neoforged\.neoforge\.event\.tick\.PlayerTickEvent') {
        $content = $content -replace '(import net\.neoforged\.neoforge\.event\.tick\..*?;\r?\n)', "`$1import net.neoforged.neoforge.event.tick.PlayerTickEvent;`n"
        $modified = $true
        Write-Host "Added PlayerTickEvent import to $($file.Name)"
        $count++
    }
    
    if ($modified) {
        Set-Content -Path $file.FullName -Value $content -NoNewline
    }
}

Write-Host "`nTotal fixes: $count"
