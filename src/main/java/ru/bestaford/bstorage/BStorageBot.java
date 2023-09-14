package ru.bestaford.bstorage;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class BStorageBot {

    public final Logger logger;
    public final TelegramBot bot;
    public final Map<String, String> mediaGroupIdToCaptionMap;
    public final Connection connection;

    public BStorageBot() {
        logger = LoggerFactory.getLogger(getClass());
        bot = new TelegramBot(getenv("BSTORAGE_BOT_TOKEN"));
        mediaGroupIdToCaptionMap = new HashMap<>();
        try {
            connection = DriverManager.getConnection("jdbc:h2:./bstorage");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void start() {
        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                logger.debug(update.toString());
                processUpdate(update);
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
        logger.info("Bot started");
    }

    public void processUpdate(Update update) {
        Message message = update.message();
        if (message == null) {
            return;
        }
        User user = message.from();
        if (user == null) {
            return;
        }
        PhotoSize[] photoSizes = message.photo();
        if (photoSizes == null) {
            return;
        }
        PhotoSize photo = photoSizes[photoSizes.length - 1];
        String mediaGroupId = message.mediaGroupId();
        String caption = message.caption();
        if (caption == null) {
            if (mediaGroupId != null) {
                caption = mediaGroupIdToCaptionMap.get(mediaGroupId);
                if (caption != null) {
                    savePhoto(photo, caption);
                }
            }
        } else {
            if (mediaGroupId != null) {
                mediaGroupIdToCaptionMap.put(mediaGroupId, caption);
            }
            savePhoto(photo, caption);
        }
    }

    public void savePhoto(PhotoSize photo, String caption) {
        logger.info(String.format("Saving file id %s with caption %s", photo.fileId(), caption));
    }

    public void stop() {
        logger.info("Shutting down...");
        try {
            connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        bot.removeGetUpdatesListener();
        bot.shutdown();
    }

    public static String getenv(String name) {
        return Objects.requireNonNull(System.getenv(name), "Missing environment variable " + name);
    }

    public static void main(String[] args) {
        BStorageBot bStorageBot = new BStorageBot();
        bStorageBot.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            bStorageBot.stop();
            Runtime.getRuntime().halt(0);
        }));
    }
}