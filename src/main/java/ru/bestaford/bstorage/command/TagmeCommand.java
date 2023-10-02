package ru.bestaford.bstorage.command;

import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.*;
import ru.bestaford.bstorage.BStorageBot;
import ru.bestaford.bstorage.model.File;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class TagmeCommand extends Command {

    public TagmeCommand(BStorageBot bot) {
        super(bot);
    }

    @Override
    public void execute(User user) throws Exception {
        PreparedStatement statement = bot.connection.prepareStatement("""
                SELECT
                    *
                FROM
                    FILES
                WHERE
                    USER_ID = ?
                    AND TAGS IS NULL
                ORDER BY
                    DATETIME DESC
                FETCH FIRST 1 ROWS ONLY
                """);
        statement.setLong(1, user.id());
        ResultSet resultSet = bot.executeStatement(statement);
        if (resultSet.next()) {
            Long userId = user.id();
            String fileId = resultSet.getString(4);
            switch (File.Type.valueOf(resultSet.getString(5))) {
                case PHOTO -> bot.executeAsyncBotRequest(new SendPhoto(userId, fileId));
                case VIDEO -> bot.executeAsyncBotRequest(new SendVideo(userId, fileId));
                case DOCUMENT -> bot.executeAsyncBotRequest(new SendDocument(userId, fileId));
                case AUDIO -> bot.executeAsyncBotRequest(new SendAudio(userId, fileId));
                case GIF -> bot.executeAsyncBotRequest(new SendAnimation(userId, fileId));
                case STICKER -> bot.executeAsyncBotRequest(new SendSticker(userId, fileId));
                case VOICE -> bot.executeAsyncBotRequest(new SendVoice(userId, fileId));
            }
        } else {
            bot.sendMessage(user, bot.messages.getString("tagme.empty"));
        }
    }
}