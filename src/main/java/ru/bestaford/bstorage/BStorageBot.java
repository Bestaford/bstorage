package ru.bestaford.bstorage;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class BStorageBot {

    public final Logger logger;
    public final TelegramBot bot;
    public final Map<String, String> mediaGroupIdToCaptionMap;
    public final Connection connection;

    public BStorageBot() throws SQLException, IOException {
        logger = LoggerFactory.getLogger(getClass());
        bot = new TelegramBot(getenv("BSTORAGE_BOT_TOKEN"));
        mediaGroupIdToCaptionMap = new HashMap<>();
        connection = DriverManager.getConnection("jdbc:h2:./bstorage");
        connection.createStatement().execute(getResourceFileAsString("bstorage.sql"));
    }

    public void start() {
        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                try {
                    logger.debug(update.toString());
                    if (!processUpdate(update)) {
                        //TODO: send error message
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
        logger.info("Bot started");
    }

    public boolean processUpdate(Update update) throws SQLException {
        Message message = update.message();
        if (message == null) {
            return true;
        }
        User user = message.from();
        if (user == null) {
            return true;
        }
        PhotoSize[] photoSizes = message.photo();
        if (photoSizes == null) {
            String text = message.text();
            if (text != null && !text.isBlank() && text.startsWith("/")) {
                return processCommand(user, text.substring(1).toLowerCase().strip());
            }
        } else {
            return processPhoto(message, user, photoSizes);
        }
        //TODO: send usage message
        return true;
    }

    public boolean processCommand(User user, String command) {
        switch (command) {
            case "start":
                logger.info("start"); //TODO: implement
                break;
            case "help":
                logger.info("help"); //TODO: implement
                break;
            default:
                logger.info("default"); //TODO: implement
        }
        return true;
    }

    public boolean processPhoto(Message message, User user, PhotoSize[] photoSizes) throws SQLException {
        PhotoSize photo = photoSizes[photoSizes.length - 1];
        String mediaGroupId = message.mediaGroupId();
        String caption = message.caption();
        if (caption == null) {
            if (mediaGroupId != null) {
                caption = mediaGroupIdToCaptionMap.get(mediaGroupId);
                if (caption != null) {
                    return savePhoto(user, photo, caption);
                }
            }
        } else {
            if (mediaGroupId != null) {
                mediaGroupIdToCaptionMap.put(mediaGroupId, caption);
            }
            return savePhoto(user, photo, caption);
        }
        return true;
    }

    public boolean savePhoto(User user, PhotoSize photo, String caption) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("MERGE INTO files VALUES (?, ?, ?, ?, ?)");
        statement.setString(1, user.id() + photo.fileUniqueId());
        statement.setString(2, photo.fileId());
        statement.setString(3, caption);
        statement.setLong(4, user.id());
        statement.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
        logger.debug(statement.toString());
        return statement.execute();
    }

    public void stop() throws SQLException {
        logger.info("Shutting down...");
        connection.close();
        bot.removeGetUpdatesListener();
        bot.shutdown();
    }

    //https://stackoverflow.com/a/46613809
    public static String getResourceFileAsString(String fileName) throws IOException {
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        try (InputStream is = classLoader.getResourceAsStream(fileName)) {
            if (is == null) return null;
            try (InputStreamReader isr = new InputStreamReader(is); BufferedReader reader = new BufferedReader(isr)) {
                return reader.lines().collect(Collectors.joining(System.lineSeparator()));
            }
        }
    }

    public static String getenv(String name) {
        return Objects.requireNonNull(System.getenv(name), "Missing environment variable " + name);
    }

    public static void main(String[] args) throws SQLException, IOException {
        BStorageBot bStorageBot = new BStorageBot();
        bStorageBot.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                bStorageBot.stop();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            Runtime.getRuntime().halt(0);
        }));
    }
}