package net.machiavelli.minecolonytax.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the default key for the codex against colliding with a vanilla binding.
 *
 * <p>It shipped bound to <b>T</b>, which is vanilla's "open chat". Every player hit that conflict on
 * their first session — pressing T opened the codex instead of chat, or both fired, depending on
 * which binding won. A keybind default is the kind of thing that gets changed casually while
 * refactoring, so the rule is pinned here rather than left to reviewer memory.
 *
 * <p>Asserted against the SOURCE rather than the KeyMapping object: the binding class pulls in
 * client-only Minecraft types that will not load in a plain JVM, and the thing worth protecting is
 * the declared default anyway.
 */
class KeyBindingDefaultTest {

    private static final Path SOURCE = Path.of(
            "src/main/java/net/machiavelli/minecolonytax/client/KeyBindings.java");

    /** Vanilla Minecraft defaults a mod must not claim. Value = what vanilla uses it for. */
    private static final Map<String, String> VANILLA_RESERVED = new LinkedHashMap<>();
    static {
        VANILLA_RESERVED.put("GLFW_KEY_T", "open chat");
        VANILLA_RESERVED.put("GLFW_KEY_E", "inventory");
        VANILLA_RESERVED.put("GLFW_KEY_Q", "drop item");
        VANILLA_RESERVED.put("GLFW_KEY_F", "swap item to offhand");
        VANILLA_RESERVED.put("GLFW_KEY_W", "walk forwards");
        VANILLA_RESERVED.put("GLFW_KEY_A", "strafe left");
        VANILLA_RESERVED.put("GLFW_KEY_S", "walk backwards");
        VANILLA_RESERVED.put("GLFW_KEY_D", "strafe right");
        VANILLA_RESERVED.put("GLFW_KEY_SPACE", "jump");
        VANILLA_RESERVED.put("GLFW_KEY_ESCAPE", "pause menu");
        VANILLA_RESERVED.put("GLFW_KEY_TAB", "player list");
        VANILLA_RESERVED.put("GLFW_KEY_SLASH", "open chat with command");
    }

    @Test
    @DisplayName("the codex key never defaults to a vanilla-reserved key")
    void defaultKeyDoesNotCollideWithVanilla() throws Exception {
        String code = readSource();

        for (Map.Entry<String, String> reserved : VANILLA_RESERVED.entrySet()) {
            // Word boundary so GLFW_KEY_T does not also match GLFW_KEY_TAB.
            Pattern p = Pattern.compile("\\b" + Pattern.quote(reserved.getKey()) + "\\b");
            Matcher m = p.matcher(stripComments(code));
            assertFalse(m.find(),
                    "The codex key defaults to " + reserved.getKey() + ", which vanilla uses for "
                            + reserved.getValue() + ". Pick a key vanilla leaves free (G is the current choice).");
        }
    }

    @Test
    @DisplayName("a default key is actually declared")
    void aDefaultIsDeclared() throws Exception {
        // Without this, the test above would pass trivially if the binding were deleted or the
        // constant renamed — a guard that can only ever pass protects nothing.
        String code = stripComments(readSource());
        assertTrue(code.contains("GLFW.GLFW_KEY_"),
                "No GLFW key constant found in KeyBindings.java - has the binding moved? "
                        + "This guard must be updated to follow it.");
    }

    private static String readSource() throws Exception {
        assertTrue(Files.exists(SOURCE), "KeyBindings.java not found at " + SOURCE.toAbsolutePath());
        return Files.readString(SOURCE);
    }

    /** Drops // and block comments so the explanation of why T is banned cannot fail the test. */
    private static String stripComments(String code) {
        return code.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }
}
