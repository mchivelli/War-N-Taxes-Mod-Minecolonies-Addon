import java.nio.file.*;
import java.io.IOException;

public class TestMigration {
    public static void main(String[] args) throws IOException {
        // Create test scenario - simulate old config files
        System.out.println("Setting up test scenario...");
        
        // Create test directories
        Files.createDirectories(Paths.get("test_config"));
        Files.createDirectories(Paths.get("test_config/warntaxmod"));
        Files.createDirectories(Paths.get("test_saves/TestWorld/serverconfig"));
        Files.createDirectories(Paths.get("test_saves/TestWorld/serverconfig/warntaxmod"));
        
        // Create test config files
        String testConfig = "# Test config content\n[General]\ntest_value = 42\n";
        Files.write(Paths.get("test_config/minecolonytax.toml"), testConfig.getBytes());
        Files.write(Paths.get("test_config/warntaxmod/minecolonytax.toml"), testConfig.getBytes());
        Files.write(Paths.get("test_saves/TestWorld/serverconfig/minecolonytax-server.toml"), testConfig.getBytes());
        Files.write(Paths.get("test_saves/TestWorld/serverconfig/warntaxmod/minecolonytax.toml"), testConfig.getBytes());
        
        System.out.println("Test files created:");
        System.out.println("- test_config/minecolonytax.toml");
        System.out.println("- test_config/warntaxmod/minecolonytax.toml");
        System.out.println("- test_saves/TestWorld/serverconfig/minecolonytax-server.toml");
        System.out.println("- test_saves/TestWorld/serverconfig/warntaxmod/minecolonytax.toml");
        
        System.out.println("\nTest setup complete. The migration logic will:");
        System.out.println("1. Create config/warntax/ directory");
        System.out.println("2. Copy the first found config to config/warntax/minecolonytax.toml");
        System.out.println("3. Delete all old config files");
        System.out.println("4. Clean up empty warntaxmod directories");
    }
}