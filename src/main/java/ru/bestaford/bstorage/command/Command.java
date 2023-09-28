package ru.bestaford.bstorage.command;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.User;
import ru.bestaford.bstorage.BStorageBot;

public abstract class Command {

    protected final BStorageBot bot;

    public Command(BStorageBot bot) {
        this.bot = bot;
    }

    public abstract void execute(User user) throws Exception;

    public void processCallbackQuery(CallbackQuery callbackQuery) throws Exception {

    }
}
