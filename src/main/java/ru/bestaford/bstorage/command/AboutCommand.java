package ru.bestaford.bstorage.command;

import com.pengrad.telegrambot.model.User;
import ru.bestaford.bstorage.BStorageBot;

public final class AboutCommand extends Command {

    public AboutCommand(BStorageBot bot) {
        super(bot, "About this bot");
    }

    @Override
    public void execute(User user) {
        bot.sendMessage(user, String.format(bot.messages.getString("about"), BStorageBot.VERSION));
    }
}