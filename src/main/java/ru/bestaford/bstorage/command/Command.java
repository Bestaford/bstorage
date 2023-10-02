package ru.bestaford.bstorage.command;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.User;
import ru.bestaford.bstorage.BStorageBot;

public abstract class Command {

    public final BStorageBot bot;
    public final String description;

    public Command(BStorageBot bot, String description) {
        this.bot = bot;
        this.description = description;
    }

    public abstract void execute(User user) throws Exception;

    public void processCallbackQuery(CallbackQuery callbackQuery) throws Exception {

    }
}
