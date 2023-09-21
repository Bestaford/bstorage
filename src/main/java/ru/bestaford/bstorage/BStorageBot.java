package ru.bestaford.bstorage;

import com.pengrad.telegrambot.Callback;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.*;
import com.pengrad.telegrambot.model.request.InlineQueryResult;
import com.pengrad.telegrambot.model.request.InlineQueryResultCachedPhoto;
import com.pengrad.telegrambot.request.AnswerInlineQuery;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.BaseResponse;
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
    public final Map<Long, String> userIdToMessageTextMap;
    public final Connection connection;

    public final String MESSAGE_HELP = "help";

    public BStorageBot() throws SQLException {
        logger = LoggerFactory.getLogger(getClass());
        bot = new TelegramBot(getenv("BSTORAGE_BOT_TOKEN"));
        mediaGroupIdToCaptionMap = new HashMap<>();
        userIdToMessageTextMap = new HashMap<>();
        connection = DriverManager.getConnection("jdbc:h2:./bstorage");
        PreparedStatement statement = connection.prepareStatement("""
                CREATE TABLE IF NOT EXISTS FILES (
                    ID VARCHAR PRIMARY KEY,
                    USER_ID BIGINT NOT NULL,
                    FILE_UNIQUE_ID VARCHAR NOT NULL,
                    FILE_ID VARCHAR NOT NULL,
                    FILE_TYPE VARCHAR NOT NULL,
                    TAGS VARCHAR,
                    DATETIME TIMESTAMP NOT NULL
                )
                """);
        executeStatement(statement);
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
            userIdToMessageTextMap.put(user.id(), text);
            return;
        }
        PhotoSize[] photoSizes = message.photo();
        if (photoSizes != null) {
            PhotoSize photo = photoSizes[photoSizes.length - 1];
            saveFile(message, user, photo.fileUniqueId(), photo.fileId(), FileType.PHOTO);
            return;
        }
        Video video = message.video();
        if (video != null) {
            saveFile(message, user, video.fileUniqueId(), video.fileId(), FileType.VIDEO);
            return;
        }
        sendMessage(user, MESSAGE_HELP);
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
        executeBotRequest(new AnswerInlineQuery(inlineQuery.id(), resultsArray).isPersonal(true).cacheTime(0));
    }

    public List<String> findFileIdsByTags(User user, String tags) {
        List<String> fileIds = new ArrayList<>();
        try {
            PreparedStatement statement;
            if (tags == null || tags.isBlank()) {
                statement = connection.prepareStatement("""
                        SELECT
                            *
                        FROM
                            FILES
                        WHERE
                            USER_ID = ?
                        ORDER BY
                            DATETIME DESC
                        FETCH FIRST 50 ROWS ONLY
                        """);
                statement.setLong(1, user.id());
            } else {
                statement = connection.prepareStatement("""
                        SELECT
                            F.*
                        FROM
                            FTL_SEARCH_DATA(?, 0, 0) FTL,
                            FILES F
                        WHERE
                            FTL."TABLE" = 'FILES'
                            AND F.ID = FTL.KEYS[1]
                            AND F.USER_ID = ?
                        ORDER BY
                            FTL.SCORE DESC
                        FETCH FIRST 50 ROWS ONLY
                        """);
                statement.setString(1, tags);
                statement.setLong(2, user.id());
            }
            ResultSet resultSet = executeStatement(statement);
            while (resultSet.next()) {
                fileIds.add(resultSet.getString(4));
            }
        } catch (SQLException e) {
            logger.error("Failed to find files", e);
        }
        return fileIds;
    }

    public void saveFile(Message message, User user, String fileUniqueId, String fileId, FileType fileType) throws SQLException {
        Long userId = user.id();
        String tags = userIdToMessageTextMap.remove(userId);
        if (tags == null) {
            String mediaGroupId = message.mediaGroupId();
            String caption = message.caption();
            if (mediaGroupId != null) {
                if (caption == null) {
                    caption = mediaGroupIdToCaptionMap.get(mediaGroupId);
                } else {
                    mediaGroupIdToCaptionMap.put(mediaGroupId, caption);
                }
            }
            tags = caption;
        }
        if (tags != null) {
            tags = tags.trim().replaceAll("\\s+", " ").toLowerCase();
        }
        PreparedStatement statement = connection.prepareStatement("MERGE INTO FILES VALUES (?, ?, ?, ?, ?, ?, ?)");
        statement.setString(1, userId + fileUniqueId);
        statement.setLong(2, userId);
        statement.setString(3, fileUniqueId);
        statement.setString(4, fileId);
        statement.setString(5, fileType.toString());
        statement.setString(6, tags);
        statement.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
        executeStatement(statement);
        if (tags == null) {
            sendMessage(user, "File saved without tags.");
        } else {
            sendMessage(user, String.format("File saved with tags \"%s\".", tags));
        }
    }

    public ResultSet executeStatement(PreparedStatement statement) throws SQLException {
        logger.debug(statement.toString());
        statement.execute();
        return statement.getResultSet();
    }

    public void sendMessage(User user, String text) {
        executeBotRequest(new SendMessage(user.id(), text));
    }

    public <T extends BaseRequest<T, R>, R extends BaseResponse> void executeBotRequest(T request) {
        logger.debug(request.toString());
        bot.execute(request, new Callback<T, R>() {
            @Override
            public void onResponse(T request, R response) {
                logger.debug(response.toString());
            }

            @Override
            public void onFailure(T request, IOException e) {
                logger.error("Failed to execute request", e);
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