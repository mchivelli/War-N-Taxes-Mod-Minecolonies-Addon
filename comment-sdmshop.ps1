# Temporarily comment out SDMShop references to allow compilation
# This allows us to get a working build while we resolve the SDMShop package issue

$files = @(
    "src\main\java\net\machiavelli\minecolonytax\commands\ClaimTaxCommand.java",
    "src\main\java\net\machiavelli\minecolonytax\commands\TaxDebtCommand.java",
    "src\main\java\net\machiavelli\minecolonytax\event\WarEconomyHandler.java",
    "src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java",
    "src\main\java\net\machiavelli\minecolonytax\integration\SDMShopIntegration.java"
)

foreach ($file in $files) {
    if (Test-Path $file) {
        $content = Get-Content -Path $file -Raw
        
        # Comment out SDMEconomy imports
        $content = $content -replace '(import net\.sixik\.sdm_economy\.SDMEconomy;)', '// $1 // TODO: Fix SDMShop 2.0 package'
        
        # Comment out SDMEconomy references but keep the logic structure
        # We'll add TODO comments
        $content = $content -replace '(\s+)(SDMEconomy\.)', '$1// SDMEconomy. // TODO: Fix SDMShop API'
        
        Set-Content -Path $file -Value $content -NoNewline
        Write-Host "Updated: $file"
    }
}

Write-Host "`nSDMShop references commented out. The mod will use fallback currency system."
Write-Host "TODO: Update SDMShop integration once package structure is confirmed."
