# Find SDMShop JAR and list its contents
$gradleCache = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1"

Write-Host "Searching for SDMShop JAR..."

$jars = Get-ChildItem -Recurse -Path $gradleCache -Filter "*.jar" -ErrorAction SilentlyContinue | 
    Where-Object { $_.Name -like "*sdm*" -or $_.Directory.FullName -like "*sdm*" } | 
    Select-Object -First 5

if ($jars) {
    foreach ($jar in $jars) {
        Write-Host "`nFound: $($jar.FullName)"
        Write-Host "Examining JAR contents..."
        
        # List main classes
        $output = & jar tf "$($jar.FullName)" 2>&1 | Select-String -Pattern "\.class$" | Select-Object -First 30
        Write-Host "`nClasses found:"
        $output | ForEach-Object { Write-Host $_ }
    }
} else {
    Write-Host "No SDMShop JARs found in Gradle cache."
    Write-Host "Checking build/libs for downloaded dependencies..."
}
