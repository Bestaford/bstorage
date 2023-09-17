package ru.bestaford.bstorage;

import com.pengrad.telegrambot.Callback;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import org.h2.fulltext.FullText;
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
        DatabaseMetaData metaData = connection.getMetaData();
        ResultSet resultSet = metaData.getTables(null, "FT", null, null);
        if (!resultSet.next()) {
            FullText.init(connection);
            FullText.createIndex(connection, "PUBLIC", "FILES", null);
        }
    }

    public void start() {
        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                logger.debug(update.toString());
                Message message = update.message();
                if (message == null) {
                    continue;
                }
                User user = message.from();
                if (user == null) {
                    continue;
                }
                try {
                    processMessage(message, user);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
        logger.info("Bot started");
    }

    public void processMessage(Message message, User user) throws SQLException {
        PhotoSize[] photoSizes = message.photo();
        if (photoSizes == null) {
            String text = message.text();
            if (text != null && !text.isBlank()) {
                processText(text, user);
                return;
            }
        } else {
            processPhoto(message, user, photoSizes);
            return;
        }
        sendMessage(user, "help"); //TODO: change text
    }

    public void processText(String text, User user) {
        text = text.strip().toLowerCase();
        if (text.startsWith("/")) {
            switch (text.substring(1)) {
                case "start":
                    sendMessage(user, "start command"); //TODO: change text
                    break;
                case "help":
                    sendMessage(user, "help command"); //TODO: change text
                    break;
            }
        } else {
            sendMessage(user, "search by text"); //TODO: change text
        }
    }

    public void processPhoto(Message message, User user, PhotoSize[] photoSizes) throws SQLException {
        PhotoSize photo = photoSizes[photoSizes.length - 1];
        String mediaGroupId = message.mediaGroupId();
        String caption = message.caption();
        if (caption == null) {
            if (mediaGroupId != null) {
                caption = mediaGroupIdToCaptionMap.get(mediaGroupId);
                if (caption != null) {
                    savePhoto(user, photo, caption);
                }
            }
        } else {
            if (mediaGroupId != null) {
                mediaGroupIdToCaptionMap.put(mediaGroupId, caption);
            }
            savePhoto(user, photo, caption);
        }
    }

    public void savePhoto(User user, PhotoSize photo, String caption) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("MERGE INTO FILES VALUES (?, ?, ?, ?, ?)");
        statement.setString(1, user.id() + photo.fileUniqueId());
        statement.setString(2, photo.fileId());
        statement.setString(3, caption);
        statement.setLong(4, user.id());
        statement.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
        logger.debug(statement.toString());
        statement.execute();
        if (statement.getUpdateCount() == 1) {
            sendMessage(user, "saved"); //TODO: change text
        } else {
            sendMessage(user, "save error"); //TODO: change text
        }
    }

    public void sendMessage(User user, String text) {
        bot.execute(new SendMessage(user.id(), text), new Callback<SendMessage, SendResponse>() {
            @Override
            public void onResponse(SendMessage request, SendResponse response) {

            }

            @Override
            public void onFailure(SendMessage request, IOException exception) {
                logger.error("Failed to send message", exception);
            }
        });
    }

    public void stop() throws SQLException {
        logger.info("Shutting down...");
        connection.close();
        bot.removeGetUpdatesListener();
        bot.shutdown();
    }

    /* https://stackoverflow.com/a/46613809 */
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