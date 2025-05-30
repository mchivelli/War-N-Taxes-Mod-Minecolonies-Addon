package net.machiavelli.minecolonytax.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Utility class for translation and styled message generation.
 * Ensures consistent formatting and makes text internationalization easier.
 */
public class TranslationUtil {

    /**
     * Creates a styled message with title and body for broadcasting.
     * Format:
     * [TITLE]
     * ----------------------------------------
     * [BODY]
     * ----------------------------------------
     *
     * @param titleKey Translation key for title
     * @param titleArgs Format arguments for title
     * @param bodyKey Translation key for body
     * @param bodyArgs Format arguments for body
     * @param titleColor Color for the title
     * @param bodyColor Color for the body text
     * @return A fully styled MutableComponent
     */
    public static MutableComponent createStyledMessage(String titleKey, Object[] titleArgs, 
                                                      String bodyKey, Object[] bodyArgs,
                                                      ChatFormatting titleColor, 
                                                      ChatFormatting bodyColor) {
        // Create title with translation
        MutableComponent message = Component.translatable(titleKey, titleArgs)
                .withStyle(titleColor)
                .withStyle(ChatFormatting.BOLD);
                
        // Add separator
        message.append(Component.literal("\n----------------------------------------")
                .withStyle(ChatFormatting.DARK_GRAY));
                
        // Add body with translation
        message.append(Component.literal("\n"))
               .append(Component.translatable(bodyKey, bodyArgs)
                      .withStyle(bodyColor));
                      
        // Add closing separator
        message.append(Component.literal("\n----------------------------------------")
                .withStyle(ChatFormatting.DARK_GRAY));
                
        return message;
    }
    
    /**
     * Creates a styled notification message.
     *
     * @param key Translation key
     * @param args Format arguments
     * @param color Primary color
     * @param bold Whether to make it bold
     * @return A styled component
     */
    public static MutableComponent createNotification(String key, Object[] args, 
                                                    ChatFormatting color, boolean bold) {
        MutableComponent message = Component.translatable(key, args)
                .withStyle(color);
                
        if (bold) {
            message = message.withStyle(ChatFormatting.BOLD);
        }
        
        return message;
    }
    
    /**
     * Formats a colony name with appropriate styling.
     * 
     * @param colonyName The name of the colony
     * @param isAttacker Whether this colony is an attacker (red) or defender (blue)
     * @return A styled component
     */
    public static MutableComponent formatColonyName(String colonyName, boolean isAttacker) {
        return Component.literal(colonyName)
                .withStyle(isAttacker ? ChatFormatting.DARK_RED : ChatFormatting.BLUE)
                .withStyle(ChatFormatting.BOLD);
    }
    
    /**
     * Formats a player name with appropriate styling.
     * 
     * @param playerName The name of the player
     * @param isAttacker Whether this player is an attacker (red) or defender (blue)
     * @return A styled component
     */
    public static MutableComponent formatPlayerName(String playerName, boolean isAttacker) {
        return Component.literal(playerName)
                .withStyle(isAttacker ? ChatFormatting.DARK_RED : ChatFormatting.BLUE);
    }
    
    /**
     * Creates a complex message with inline styling (for colony names, player names, etc.)
     * Used for war/raid announcements where specific parts need custom colors.
     * 
     * @param baseKey The translation key with placeholders like {0}, {1}, etc.
     * @param baseColor The base text color
     * @param components Array of components to insert at placeholders
     * @return A styled component
     */
    public static MutableComponent createComplexMessage(String baseKey, ChatFormatting baseColor, 
                                                      MutableComponent... components) {
        // Get the base translated text
        String baseText = Component.translatable(baseKey).getString();
        
        // Split by placeholders {0}, {1}, etc.
        String[] parts = baseText.split("\\{\\d+\\}");
        
        // Start building the message
        MutableComponent message = Component.literal("").withStyle(baseColor);
        
        // Add each part with the corresponding component
        for (int i = 0; i < parts.length; i++) {
            message.append(Component.literal(parts[i]).withStyle(baseColor));
            if (i < components.length) {
                message.append(components[i]);
            }
        }
        
        return message;
    }
}
