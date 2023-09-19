package ru.bestaford.bstorage;

import com.pengrad.telegrambot.Callback;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.*;
import com.pengrad.telegrambot.model.request.InlineQueryResultPhoto;
import com.pengrad.telegrambot.request.AnswerInlineQuery;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import org.h2.fulltext.FullTextLucene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
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
        ResultSet resultSet = metaData.getTables(null, "FTL", null, null);
        if (!resultSet.next()) {
            FullTextLucene.init(connection);
            FullTextLucene.createIndex(connection, "PUBLIC", "FILES", null);
        }
    }

    public void start() {
        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                logger.debug(update.toString());
                try {
                    processUpdate(update);
                } catch (SQLException e) {
                    logger.error("Failed to process update", e);
                }
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
        logger.info("Bot started");
    }

    public void processUpdate(Update update) throws SQLException {
        InlineQuery inlineQuery = update.inlineQuery();
        if (inlineQuery != null) {
            processInlineQuery(inlineQuery);
            return;
        }
        Message message = update.message();
        if (message == null) {
            return;
        }
        User user = message.from();
        if (user == null) {
            return;
        }
        String text = message.text();
        if (text != null && !text.isBlank()) {
            processText(text, user);
            return;
        }
        PhotoSize[] photoSizes = message.photo();
        if (photoSizes != null) {
            PhotoSize photo = photoSizes[photoSizes.length - 1];
            saveFile(message, user, photo.fileUniqueId(), photo.fileId());
            return;
        }
        Video video = message.video();
        if (video != null) {
            saveFile(message, user, video.fileUniqueId(), video.fileId());
            return;
        }
        sendMessage(user, "help"); //TODO: change text
    }

    public void processInlineQuery(InlineQuery inlineQuery) {
        String query = inlineQuery.query();
        if (query == null || query.isBlank()) {
            return;
        }
        for (String fileId : findFileIdsByTags(query)) {
            bot.execute(new AnswerInlineQuery(inlineQuery.id(), new InlineQueryResultPhoto("id", fileId, fileId)));
        }
    }

    public void processText(String text, User user) {
        text = text.strip().toLowerCase();
        if (text.startsWith("/")) {
            text = text.substring(1);
        }
        switch (text) {
            case "start" -> sendMessage(user, "start command"); //TODO: change text
            case "help" -> sendMessage(user, "help command"); //TODO: change text
            case "last" -> sendMessage(user, "last command"); //TODO: change text
            case "latest" -> sendMessage(user, "latest command"); //TODO: change text
            case "random" -> sendMessage(user, "random command"); //TODO: change text
            default -> findFiles(text, user);
        }
    }

    public void findFiles(String tags, User user) {
        List<String> fileIds = findFileIdsByTags(tags);
        if (fileIds.isEmpty()) {
            sendMessage(user, "nothing found"); //TODO: change text
        } else {
            for (String fileId : fileIds) { //TODO: send media file
                sendMessage(user, fileId);
            }
        }
    }

    public List<String> findFileIdsByTags(String tags) {
        List<String> fileIds = new ArrayList<>();
        try {
            ResultSet resultSet = FullTextLucene.search(connection, tags, 20, 0);
            while (resultSet.next()) {
                String queryText = resultSet.getString(1);
                fileIds.add(queryFileId(queryText));
            }
        } catch (SQLException e) {
            logger.error("Failed to parse tags", e);
        }
        return fileIds;
    }

    public String queryFileId(String queryText) throws SQLException {
        Statement statement = connection.createStatement();
        statement.execute("SELECT * FROM " + queryText);
        ResultSet resultSet = statement.getResultSet();
        resultSet.next();
        return resultSet.getString(4);
    }

    public void saveFile(Message message, User user, String fileUniqueId, String fileId) throws SQLException {
        String mediaGroupId = message.mediaGroupId();
        String caption = message.caption();
        if (mediaGroupId != null) {
            if (caption == null) {
                caption = mediaGroupIdToCaptionMap.get(mediaGroupId);
            } else {
                mediaGroupIdToCaptionMap.put(mediaGroupId, caption);
            }
        }
        Long userId = user.id();
        PreparedStatement statement = connection.prepareStatement("MERGE INTO FILES VALUES (?, ?, ?, ?, ?, ?)");
        statement.setString(1, userId + fileUniqueId);
        statement.setLong(2, userId);
        statement.setString(3, fileUniqueId);
        statement.setString(4, fileId);
        statement.setString(5, caption);
        statement.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
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