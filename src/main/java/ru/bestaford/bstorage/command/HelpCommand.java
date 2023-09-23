package ru.bestaford.bstorage.command;

import com.pengrad.telegrambot.model.User;
import ru.bestaford.bstorage.BStorageBot;

public final class HelpCommand extends Command {

    public HelpCommand(BStorageBot bot) {
        super(bot);
    }

    @Override
    public void execute(User user) {
        bot.sendMessage(user, String.format(bot.messages.getString("help"), bot.me.username()));
    }
}