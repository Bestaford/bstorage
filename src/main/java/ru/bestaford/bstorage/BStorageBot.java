package ru.bestaford.bstorage;

import com.pengrad.telegrambot.Callback;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.*;
import com.pengrad.telegrambot.model.request.InlineQueryResult;
import com.pengrad.telegrambot.model.request.InlineQueryResultCachedPhoto;
import com.pengrad.telegrambot.request.AnswerInlineQuery;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import org.h2.fulltext.FullTextLucene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class BStorageBot {

    public final Logger logger;
    public final TelegramBot bot;
    public final Map<String, String> mediaGroupIdToCaptionMap;
    public final Connection connection;

    public BStorageBot() throws SQLException {
        logger = LoggerFactory.getLogger(getClass());
        bot = new TelegramBot(getenv("BSTORAGE_BOT_TOKEN"));
        mediaGroupIdToCaptionMap = new HashMap<>();
        connection = DriverManager.getConnection("jdbc:h2:./bstorage");
        connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS FILES (
                    ID VARCHAR PRIMARY KEY,
                    USER_ID BIGINT NOT NULL,
                    FILE_UNIQUE_ID VARCHAR NOT NULL,
                    FILE_ID VARCHAR NOT NULL,
                    TAGS VARCHAR,
                    DATETIME TIMESTAMP NOT NULL
                )
                """);
        if (!new File("bstorage").isDirectory()) {
            FullTextLucene.init(connection);
            FullTextLucene.createIndex(connection, "PUBLIC", "FILES", "TAGS");
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
        List<InlineQueryResult<?>> resultsList = new ArrayList<>();
        List<String> fileIds = findFileIdsByTags(inlineQuery.from(), inlineQuery.query());
        for (String fileId : fileIds) {
            if (resultsList.size() < 50) {
                resultsList.add(new InlineQueryResultCachedPhoto(UUID.randomUUID().toString(), fileId));
            } else {
                break;
            }
        }
        InlineQueryResult<?>[] resultsArray = resultsList.toArray(new InlineQueryResult<?>[0]);
        bot.execute(new AnswerInlineQuery(inlineQuery.id(), resultsArray).isPersonal(true).cacheTime(0));
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
            default -> findFiles(user, text);
        }
    }

    public void findFiles(User user, String tags) {
        List<String> fileIds = findFileIdsByTags(user, tags);
        if (fileIds.isEmpty()) {
            sendMessage(user, "nothing found"); //TODO: change text
        } else {
            for (String fileId : fileIds) { //TODO: send media file
                sendMessage(user, fileId);
            }
        }
    }

    public List<String> findFileIdsByTags(User user, String tags) {
        List<String> fileIds = new ArrayList<>();
        try {
            PreparedStatement statement = connection.prepareStatement("SELECT * FROM FTL_SEARCH(?, 0, 0) ORDER BY SCORE DESC");
            statement.setString(1, tags);
            statement.execute();
            ResultSet resultSet = statement.getResultSet();
            while (resultSet.next()) {
                String queryText = resultSet.getString(1);
                String fileId = queryFileId(user, queryText);
                if (fileId != null) {
                    fileIds.add(fileId);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to parse tags", e);
        }
        return fileIds;
    }

    public String queryFileId(User user, String queryText) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + queryText + " AND \"USER_ID\"=?");
        statement.setLong(1, user.id());
        statement.execute();
        ResultSet resultSet = statement.getResultSet();
        if (resultSet.next()) {
            return resultSet.getString(4);
        }
        return null;
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

    public static String getenv(String name) {
        return Objects.requireNonNull(System.getenv(name), "Missing environment variable " + name);
    }

    public static void main(String[] args) throws SQLException {
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