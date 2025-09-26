package net.machiavelli.minecolonytax.event;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraft.commands.CommandSourceStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates a world datapack on server start that disables MineColonies hut recipes
 * by copying the pre-bundled empty recipe jsons from the development folder `recipes/`
 * into the world's datapacks directory when the config option is enabled.
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DatapackInjector {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatapackInjector.class);
    private static final String PACK_FOLDER_NAME = "mct_disable_huts";

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        // Create or remove the datapack BEFORE packs are scanned
        try {
            MinecraftServer server = event.getServer();
            Path datapacksDir = server.getWorldPath(LevelResource.DATAPACK_DIR);
            Path packRoot = datapacksDir.resolve(PACK_FOLDER_NAME);

            if (!TaxConfig.isDisableHutRecipesEnabled()) {
                if (Files.exists(packRoot)) {
                    deleteRecursive(packRoot);
                    LOGGER.info("DatapackInjector: (pre-scan) removed datapack '{}' at {}", PACK_FOLDER_NAME, packRoot.toAbsolutePath());
                }
                return;
            }

            Path packMeta = packRoot.resolve("pack.mcmeta");
            Path dataRecipesDir = packRoot.resolve("data/minecolonies/recipes");
            Files.createDirectories(dataRecipesDir);

            if (!Files.exists(packMeta)) {
                String mcmeta = "{\n" +
                        "  \"pack\": {\n" +
                        "    \"pack_format\": 10,\n" +
                        "    \"description\": \"MineColonyTax: Disable MineColonies Hut Recipes\"\n" +
                        "  }\n" +
                        "}";
                Files.writeString(packMeta, mcmeta, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }

            String[] recipeFiles = new String[]{
                "blockhutcombatacademy.json","blockhutarchery.json","blockhutbarracks.json","blockhutbarrackstower.json","blockhutbeekeeper.json","blockhutblacksmith.json","blockhutbuilder.json","blockhutchickenherder.json","blockhutcitizen.json","blockhutcomposter.json","blockhutconcretemixer.json","blockhutcowboy.json","blockhutdeliveryman.json","blockhutdeliverymaniron.json","blockhutdyer.json","blockhutfarmer.json","blockhutfarmerstone.json","blockhutfisherman.json","blockhutfletcher.json","blockhutglassblower.json","blockhutgraveyard.json","blockhutguardtower.json","blockhutkitchen.json","blockhutlibrary.json","blockhutlumberjack.json","blockhutlumberjackstone.json","blockhutmechanic.json","blockhutmediumquarry.json","blockhutminer.json","blockhutminerstone.json","blockhutnetherworker.json","blockhutplantation.json","blockhutplantationfield.json","blockhutrabbithutch.json","blockhutsawmill.json","blockhutschool.json","blockhutshepherd.json","blockhutsimplequarry.json","blockhutsmeltery.json","blockhutstonemason.json","blockhutstonesmeltery.json","blockhutswineherder.json","blockhuttavern.json","blockhuttownhall.json","blockhutuniversity.json","blockhutwarehouse.json","mediumquarry.json","supplycampdeployer.json","supplychestdeployer.json"
            };

            int written = 0;
            for (String name : recipeFiles) {
                Path dest = dataRecipesDir.resolve(name);
                Files.writeString(dest, "{}\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                written++;
            }

            LOGGER.info("DatapackInjector: (pre-scan) prepared datapack '{}' at {} ({} files)", PACK_FOLDER_NAME, packRoot.toAbsolutePath(), written);
        } catch (Throwable t) {
            LOGGER.error("DatapackInjector: pre-scan datapack setup failed", t);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        try {
            MinecraftServer server = event.getServer();
            Path datapacksDir = server.getWorldPath(LevelResource.DATAPACK_DIR);
            Path packRoot = datapacksDir.resolve(PACK_FOLDER_NAME);

            if (!TaxConfig.isDisableHutRecipesEnabled()) {
                // Config disabled -> ensure datapack is removed to re-enable recipes
                if (Files.exists(packRoot)) {
                    deleteRecursive(packRoot);
                    LOGGER.info("DatapackInjector: Removed datapack '{}' (recipes re-enabled).", PACK_FOLDER_NAME);
                    // Reload without the pack
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
                }
                return;
            }

            Path packMeta = packRoot.resolve("pack.mcmeta");
            Path dataRecipesDir = packRoot.resolve("data/minecolonies/recipes");

            Files.createDirectories(dataRecipesDir);

            if (!Files.exists(packMeta)) {
                String mcmeta = "{\n" +
                        "  \"pack\": {\n" +
                        "    \"pack_format\": 10,\n" +
                        "    \"description\": \"MineColonyTax: Disable MineColonies Hut Recipes\"\n" +
                        "  }\n" +
                        "}";
                Files.writeString(packMeta, mcmeta, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }

            // Write empty override files for all known hut recipe ids
            String[] recipeFiles = new String[]{
                "blockhutcombatacademy.json","blockhutarchery.json","blockhutbarracks.json","blockhutbarrackstower.json","blockhutbeekeeper.json","blockhutblacksmith.json","blockhutbuilder.json","blockhutchickenherder.json","blockhutcitizen.json","blockhutcomposter.json","blockhutconcretemixer.json","blockhutcowboy.json","blockhutdeliveryman.json","blockhutdeliverymaniron.json","blockhutdyer.json","blockhutfarmer.json","blockhutfarmerstone.json","blockhutfisherman.json","blockhutfletcher.json","blockhutglassblower.json","blockhutgraveyard.json","blockhutguardtower.json","blockhutkitchen.json","blockhutlibrary.json","blockhutlumberjack.json","blockhutlumberjackstone.json","blockhutmechanic.json","blockhutmediumquarry.json","blockhutminer.json","blockhutminerstone.json","blockhutnetherworker.json","blockhutplantation.json","blockhutplantationfield.json","blockhutrabbithutch.json","blockhutsawmill.json","blockhutschool.json","blockhutshepherd.json","blockhutsimplequarry.json","blockhutsmeltery.json","blockhutstonemason.json","blockhutstonesmeltery.json","blockhutswineherder.json","blockhuttavern.json","blockhuttownhall.json","blockhutuniversity.json","blockhutwarehouse.json","mediumquarry.json","supplycampdeployer.json","supplychestdeployer.json"
            };

            int written = 0;
            for (String name : recipeFiles) {
                Path dest = dataRecipesDir.resolve(name);
                Files.writeString(dest, "{}\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                written++;
            }

            // Enable and reload via commands so the repository picks it up
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "datapack enable \"file/" + PACK_FOLDER_NAME + "\"");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
            LOGGER.info("DatapackInjector: Installed datapack '{}' and reloaded resources ({} recipes).", PACK_FOLDER_NAME, written);
        } catch (IOException e) {
            LOGGER.error("DatapackInjector: IO error while creating datapack", e);
        } catch (Throwable t) {
            LOGGER.error("DatapackInjector: Failed to create/apply datapack", t);
        }
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walk(path)
            .sorted((a,b) -> b.getNameCount() - a.getNameCount())
            .forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
    }
}


