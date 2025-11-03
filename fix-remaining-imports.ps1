# Fix remaining Forge API imports to NeoForge
$replacements = @{
    "import net.minecraftforge.event.CommandEvent" = "import net.neoforged.neoforge.event.CommandEvent"
    "import net.minecraftforge.event.server.ServerStartedEvent" = "import net.neoforged.neoforge.event.server.ServerStartedEvent"
    "import net.minecraftforge.event.server.ServerAboutToStartEvent" = "import net.neoforged.neoforge.event.server.ServerAboutToStartEvent"
    "import net.minecraftforge.event.AddReloadListenerEvent" = "import net.neoforged.neoforge.event.AddReloadListenerEvent"
    "import net.minecraftforge.event.entity.EntityJoinLevelEvent" = "import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent"
    "import net.minecraftforge.server.ServerLifecycleHooks" = "import net.neoforged.neoforge.server.ServerLifecycleHooks"
    "import net.minecraftforge.common.data.LanguageProvider" = "import net.neoforged.neoforge.common.data.LanguageProvider"
    "import net.minecraftforge.data.event.GatherDataEvent" = "import net.neoforged.neoforge.data.event.GatherDataEvent"
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
    
    if ($modified) {
        Set-Content -Path $file.FullName -Value $content -NoNewline
    }
}

Write-Host "`nTotal replacements: $count"
