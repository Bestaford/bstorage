package ru.bestaford.bstorage;

import com.pengrad.telegrambot.Callback;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.*;
import com.pengrad.telegrambot.model.request.*;
import com.pengrad.telegrambot.request.AnswerInlineQuery;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.GetMe;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.BaseResponse;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.bestaford.bstorage.command.AboutCommand;
import ru.bestaford.bstorage.command.Command;
import ru.bestaford.bstorage.command.HelpCommand;
import ru.bestaford.bstorage.command.TopCommand;
import ru.bestaford.bstorage.model.File;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public final class BStorageBot {

    public static final String VERSION = "1.0.5";

    public static final String JDBC_URL = "jdbc:h2:./bstorage";
    public static final String JDBC_USER = "";
    public static final String JDBC_PASSWORD = "";

    public static final String REGEX_WHITESPACES = "\\s+";

    public final Logger logger;
    public final TelegramBot bot;
    public final Map<String, String> mediaGroupIdToTagsMap;
    public final Map<Long, String> userIdToMessageTextMap;
    public final Map<String, Command> commandMap;
    public final Connection connection;
    public final ResourceBundle messages;
    public final User me;

    public BStorageBot(String botToken) throws Exception {
        Flyway.configure().dataSource(JDBC_URL, JDBC_USER, JDBC_PASSWORD).load().migrate();

        logger = LoggerFactory.getLogger(getClass());
        bot = new TelegramBot(botToken);
        mediaGroupIdToTagsMap = new HashMap<>();
        userIdToMessageTextMap = new HashMap<>();
        commandMap = new HashMap<>();
        connection = DriverManager.getConnection(JDBC_URL);
        messages = ResourceBundle.getBundle("messages");
        me = executeBotRequest(new GetMe()).user();

        commandMap.put("start", new HelpCommand(this));
        commandMap.put("help", new HelpCommand(this));
        commandMap.put("top", new TopCommand(this));
        commandMap.put("about", new AboutCommand(this));
    }

    public void start() {
        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                logger.debug(update.toString());
                try {
                    processUpdate(update);
                } catch (Exception e) {
                    logger.error("Failed to process update", e);
                }
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
        logger.info(String.format("bStorage v%s started", VERSION));
    }

    public void processUpdate(Update update) throws Exception {
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
            text = text.trim().toLowerCase();
            if (text.startsWith("/")) {
                Command command = commandMap.get(text.substring(1));
                if (command == null) {
                    sendMessage(user, messages.getString("command.unknown"));
                } else {
                    command.execute(user);
                }
            } else {
                userIdToMessageTextMap.put(user.id(), text);
            }
            return;
        }
        PhotoSize[] photoSizes = message.photo();
        if (photoSizes != null) {
            PhotoSize photo = photoSizes[photoSizes.length - 1];
            saveFile(message, user, photo.fileUniqueId(), photo.fileId(), null, File.Type.PHOTO);
            return;
        }
        Video video = message.video();
        if (video != null) {
            saveFile(message, user, video.fileUniqueId(), video.fileId(), video.fileName(), File.Type.VIDEO);
            return;
        }
        Document document = message.document();
        if (document != null) {
            saveFile(message, user, document.fileUniqueId(), document.fileId(), document.fileName(), File.Type.DOCUMENT);
            return;
        }
        commandMap.get("help").execute(user);
    }

    public void processInlineQuery(InlineQuery inlineQuery) {
        List<InlineQueryResult<?>> resultsList = new ArrayList<>();
        List<File> files = findFilesByTags(inlineQuery.from(), inlineQuery.query().trim().toLowerCase());
        for (File file : files) {
            String id = UUID.randomUUID().toString();
            String fileId = file.id();
            String fileTitle = file.title() == null ? " " : file.title();
            switch (file.type()) {
                case PHOTO -> resultsList.add(new InlineQueryResultCachedPhoto(id, fileId));
                case VIDEO -> resultsList.add(new InlineQueryResultCachedVideo(id, fileId, fileTitle));
                case DOCUMENT -> resultsList.add(new InlineQueryResultCachedDocument(id, fileId, fileTitle));
            }
        }
        InlineQueryResult<?>[] resultsArray = resultsList.toArray(new InlineQueryResult<?>[0]);
        executeAsyncBotRequest(new AnswerInlineQuery(inlineQuery.id(), resultsArray).isPersonal(true).cacheTime(0));
    }

    public List<File> findFilesByTags(User user, String tags) {
        List<File> files = new ArrayList<>();
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
            } else if (tags.equals("*")) {
                statement = connection.prepareStatement("""
                        SELECT
                            *
                        FROM
                            FILES
                        WHERE
                            USER_ID = ?
                        ORDER BY
                            RAND()
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
                String id = resultSet.getString(4);
                String title = resultSet.getString(8);
                File.Type type = File.Type.valueOf(resultSet.getString(5));
                files.add(new File(id, title, type));
            }
        } catch (Exception e) {
            logger.error("Failed to find files", e);
        }
        return files;
    }

    public void saveFile(Message message, User user, String fileUniqueId, String fileId, String title, File.Type fileType) throws Exception {
        Long userId = user.id();
        String mediaGroupId = message.mediaGroupId();
        String tags = userIdToMessageTextMap.remove(userId);
        if (tags == null) {
            tags = message.caption();
        }
        if (mediaGroupId != null) {
            if (tags == null) {
                tags = mediaGroupIdToTagsMap.get(mediaGroupId);
            } else {
                mediaGroupIdToTagsMap.put(mediaGroupId, tags);
            }
        }
        if (tags != null) {
            tags = tags.trim().replaceAll(REGEX_WHITESPACES, " ").toLowerCase();
        }
        PreparedStatement statement = connection.prepareStatement("MERGE INTO FILES VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
        statement.setString(1, userId + fileUniqueId);
        statement.setLong(2, userId);
        statement.setString(3, fileUniqueId);
        statement.setString(4, fileId);
        statement.setString(5, fileType.toString());
        statement.setString(6, tags);
        statement.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
        statement.setString(8, title);
        executeStatement(statement);
        if (tags == null) {
            sendMessage(user, messages.getString("file.saved"));
        } else {
            sendMessage(user, String.format(messages.getString("file.saved.tags"), tags.replaceAll("\\b(\\p{L}+)\\b", "#$1")));
        }
    }

    public ResultSet executeStatement(PreparedStatement statement) throws Exception {
        logger.debug(statement.toString());
        statement.execute();
        return statement.getResultSet();
    }

    public void sendMessage(User user, String text) {
        executeAsyncBotRequest(new SendMessage(user.id(), text).parseMode(ParseMode.HTML));
    }

    public <T extends BaseRequest<T, R>, R extends BaseResponse> R executeBotRequest(BaseRequest<T, R> request) {
        logger.debug(request.toString());
        R response = bot.execute(request);
        logger.debug(response.toString());
        return response;
    }

    public <T extends BaseRequest<T, R>, R extends BaseResponse> void executeAsyncBotRequest(T request) {
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

    public void stop() throws Exception {
        logger.info("Shutting down...");
        connection.close();
        bot.removeGetUpdatesListener();
        bot.shutdown();
    }

    public static String getenv(String name) {
        return Objects.requireNonNull(System.getenv(name), "Missing environment variable " + name);
    }

    public static void main(String[] args) throws Exception {
        BStorageBot bStorageBot = new BStorageBot(getenv("BSTORAGE_BOT_TOKEN"));
        bStorageBot.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                bStorageBot.stop();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            Runtime.getRuntime().halt(0);
        }));
    }
}