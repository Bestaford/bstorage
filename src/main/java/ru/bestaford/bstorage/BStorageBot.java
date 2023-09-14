package ru.bestaford.bstorage;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class BStorageBot {

    public static final String ENV_BOT_TOKEN = "BSTORAGE_BOT_TOKEN";

    public final Logger logger;
    public final TelegramBot bot;
    public final Map<String, String> mediaGroupIdToCaptionMap;

    public BStorageBot() {
        logger = LoggerFactory.getLogger(getClass());
        String token = System.getenv(ENV_BOT_TOKEN);
        if (token == null || token.isBlank()) {
            exit("Missing environment variable " + ENV_BOT_TOKEN);
        }
        bot = new TelegramBot(token);
        mediaGroupIdToCaptionMap = new HashMap<>();
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
        bot.removeGetUpdatesListener();
        bot.shutdown();
    }

    public void exit(String message) {
        logger.error(message);
        Runtime.getRuntime().exit(1);
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