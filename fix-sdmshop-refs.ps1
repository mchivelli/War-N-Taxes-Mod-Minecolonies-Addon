# Replace SDMEconomy references with SDMShopCompat
$files = Get-ChildItem -Path "src\main\java" -Filter "*.java" -Recurse

$count = 0
foreach ($file in $files) {
    $content = Get-Content -Path $file.FullName -Raw
    $modified = $false
    
    # Replace import
    if ($content -match 'import net\.sixik\.sdm_economy\.SDMEconomy') {
        $content = $content -replace 'import net\.sixik\.sdm_economy\.SDMEconomy;', 'import net.machiavelli.minecolonytax.integration.SDMShopCompat;'
        $modified = $true
        Write-Host "Fixed import in $($file.Name)"
        $count++
    }
    
    # Replace usage - be careful to handle different patterns
    if ($content -match 'SDMEconomy\.') {
        # Replace SDMEconomy class references with SDMShopCompat
        $content = $content -replace 'SDMEconomy\.', 'SDMShopCompat.'
        $modified = $true
        Write-Host "Fixed usage in $($file.Name)"
        $count++
    }
    
    if ($modified) {
        Set-Content -Path $file.FullName -Value $content -NoNewline
    }
}

Write-Host "`nTotal fixes: $count"
Write-Host "SDMShop references now use compatibility layer"
