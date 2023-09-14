package ru.bestaford.bstorage;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BStorageBot {

    public static final String ENV_BOT_TOKEN = "BSTORAGE_BOT_TOKEN";

    public final Logger logger;
    public final TelegramBot bot;

    public BStorageBot() {
        logger = LoggerFactory.getLogger(getClass());
        String token = System.getenv(ENV_BOT_TOKEN);
        if (token == null || token.isBlank()) {
            exit("Missing environment variable " + ENV_BOT_TOKEN);
        }
        bot = new TelegramBot(token);
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
        PhotoSize[] photo = message.photo();
        if (photo == null) {
            return;
        }
        logger.info(photo[0].fileId());
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