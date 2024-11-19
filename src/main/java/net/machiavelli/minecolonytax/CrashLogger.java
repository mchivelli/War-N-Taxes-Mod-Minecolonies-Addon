package net.machiavelli.minecolonytax;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.mojang.text2speech.Narrator.LOGGER;

public class CrashLogger {

    private static final String CRASH_LOG_FILE = "crash_report.log";

    public static void logCrash(Exception e, String additionalInfo) {
        try (FileWriter fileWriter = new FileWriter(CRASH_LOG_FILE, true);
             PrintWriter printWriter = new PrintWriter(fileWriter)) {

            // Timestamp for the crash report
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            printWriter.println("---- Crash Report ----");
            printWriter.println("Timestamp: " + timestamp);
            printWriter.println("Additional Info: " + additionalInfo);
            printWriter.println("Exception: " + e.toString());
            for (StackTraceElement element : e.getStackTrace()) {
                printWriter.println("\tat " + element);
            }
            Throwable cause = e.getCause();
            while (cause != null) {
                printWriter.println("Caused by: " + cause.toString());
                for (StackTraceElement element : cause.getStackTrace()) {
                    printWriter.println("\tat " + element);
                }
                cause = cause.getCause();
            }
            printWriter.println("----------------------");
            printWriter.println();

        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    public static void log(String message, Throwable throwable) {
        LOGGER.error(message, throwable);
    }

    public static void logCrash(Throwable t, String additionalInfo) {
        try (FileWriter fileWriter = new FileWriter(CRASH_LOG_FILE, true);
             PrintWriter printWriter = new PrintWriter(fileWriter)) {

            // Timestamp for the crash report
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            printWriter.println("---- Crash Report ----");
            printWriter.println("Timestamp: " + timestamp);
            printWriter.println("Additional Info: " + additionalInfo);
            printWriter.println("Throwable: " + t.toString());
            for (StackTraceElement element : t.getStackTrace()) {
                printWriter.println("\tat " + element);
            }
            Throwable cause = t.getCause();
            while (cause != null) {
                printWriter.println("Caused by: " + cause.toString());
                for (StackTraceElement element : cause.getStackTrace()) {
                    printWriter.println("\tat " + element);
                }
                cause = cause.getCause();
            }
            printWriter.println("----------------------");
            printWriter.println();

        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }
}
