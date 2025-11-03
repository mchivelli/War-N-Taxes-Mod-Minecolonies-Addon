# PowerShell script to migrate Forge imports to NeoForge
# Run this from the project root directory

$replacements = @{
    'net.minecraftforge.fml.common.Mod' = 'net.neoforged.fml.common.Mod'
    'net.minecraftforge.fml.ModLoadingContext' = 'net.neoforged.fml.ModLoadingContext'
    'net.minecraftforge.fml.config.ModConfig' = 'net.neoforged.fml.config.ModConfig'
    'net.minecraftforge.fml.event.lifecycle' = 'net.neoforged.fml.event.lifecycle'
    'net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext' = 'net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext'
    'net.minecraftforge.common.MinecraftForge' = 'net.neoforged.neoforge.common.NeoForge'
    'net.minecraftforge.common.ForgeConfigSpec' = 'net.neoforged.neoforge.common.ModConfigSpec'
    'net.minecraftforge.event.server.ServerStartingEvent' = 'net.neoforged.neoforge.event.server.ServerStartingEvent'
    'net.minecraftforge.event.server.ServerStoppingEvent' = 'net.neoforged.neoforge.event.server.ServerStoppingEvent'
    'net.minecraftforge.event.entity.player' = 'net.neoforged.neoforge.event.entity.player'
    'net.minecraftforge.event.entity.living' = 'net.neoforged.neoforge.event.entity.living'
    'net.minecraftforge.event.level' = 'net.neoforged.neoforge.event.level'
    'net.minecraftforge.event.RegisterCommandsEvent' = 'net.neoforged.neoforge.event.RegisterCommandsEvent'
    'net.minecraftforge.event.TickEvent' = 'net.neoforged.neoforge.event.tick.TickEvent'
    'net.minecraftforge.eventbus.api' = 'net.neoforged.bus.api'
    'net.minecraftforge.network' = 'net.neoforged.neoforge.network'
    'net.minecraftforge.registries' = 'net.neoforged.neoforge.registries'
    'net.minecraftforge.client.event' = 'net.neoforged.neoforge.client.event'
    'net.minecraftforge.api.distmarker' = 'net.neoforged.api.distmarker'
    'MinecraftForge.EVENT_BUS' = 'NeoForge.EVENT_BUS'
    'ForgeConfigSpec' = 'ModConfigSpec'
}

$javaFiles = Get-ChildItem -Path "src\main\java" -Filter "*.java" -Recurse

Write-Host "Found $($javaFiles.Count) Java files to process"

$totalReplacements = 0

foreach ($file in $javaFiles) {
    $content = Get-Content $file.FullName -Raw
    $modified = $false
    
    foreach ($old in $replacements.Keys) {
        $new = $replacements[$old]
        if ($content -match [regex]::Escape($old)) {
            $content = $content -replace [regex]::Escape($old), $new
            $modified = $true
            $totalReplacements++
        }
    }
    
    if ($modified) {
        Set-Content -Path $file.FullName -Value $content -NoNewline
        Write-Host "Updated: $($file.Name)"
    }
}

Write-Host "`nMigration complete! Made $totalReplacements replacements."
Write-Host "Please review changes with 'git diff' before committing."
