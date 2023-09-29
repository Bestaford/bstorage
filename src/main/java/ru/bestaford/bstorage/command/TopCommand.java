package ru.bestaford.bstorage.command;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import ru.bestaford.bstorage.BStorageBot;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public final class TopCommand extends Command {

    public static final int ITEMS_ON_PAGE = 10;

    public final Map<UUID, Message> uuidToMessageMap;

    public TopCommand(BStorageBot bot) {
        super(bot);
        uuidToMessageMap = new HashMap<>();
    }

    @Override
    public void execute(User user) throws Exception {
        send(user, 0, UUID.randomUUID(), true);
    }

    public void send(User user, int offset, UUID uuid, boolean isNewRequest) throws Exception {
        List<String> tagList = new ArrayList<>();
        PreparedStatement statement = bot.connection.prepareStatement("""
                SELECT
                    *
                FROM
                    FILES
                WHERE
                    USER_ID = ?
                """);
        statement.setLong(1, user.id());
        ResultSet resultSet = bot.executeStatement(statement);
        while (resultSet.next()) {
            String tags = resultSet.getString(6);
            if (tags != null) {
                tagList.addAll(Arrays.stream(tags.split(BStorageBot.REGEX_WHITESPACES)).map(String::trim).filter(s -> !s.isBlank()).toList());
            }
        }
        Set<String> tagSet = new TreeSet<>((o1, o2) -> {
            if (o1.equals(o2)) {
                return 0;
            }
            int f1 = Collections.frequency(tagList, o1);
            int f2 = Collections.frequency(tagList, o2);
            if (f1 > f2) {
                return -1;
            }
            return 1;
        });
        tagSet.addAll(tagList);
        List<String> result = new ArrayList<>(tagSet);
        if (result.isEmpty()) {
            bot.sendMessage(user, bot.messages.getString("top.empty"));
            return;
        }
        List<String> page = new ArrayList<>();
        for (int i = offset; page.size() < ITEMS_ON_PAGE; i++) {
            if (i < 0 || i >= result.size()) {
                break;
            }
            page.add(result.get(i));
        }
        StringBuilder text = new StringBuilder(String.format(bot.messages.getString("top.list"), (offset + ITEMS_ON_PAGE) / ITEMS_ON_PAGE, (result.size() + ITEMS_ON_PAGE) / ITEMS_ON_PAGE));
        text.append("\n");
        for (String tag : page) {
            text.append(String.format("\n#%s: %d", tag, Collections.frequency(tagList, tag)));
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        InlineKeyboardButton[] buttons = new InlineKeyboardButton[]{
                new InlineKeyboardButton("⏪").callbackData(uuid + ":" + 0),
                new InlineKeyboardButton("⬅️️").callbackData(uuid + ":" + (offset >= ITEMS_ON_PAGE ? (offset - ITEMS_ON_PAGE) : offset)),
                new InlineKeyboardButton("\uD83D\uDD01").callbackData(uuid + ":" + offset),
                new InlineKeyboardButton("➡️").callbackData(uuid + ":" + (result.size() > offset + page.size() ? (offset + ITEMS_ON_PAGE) : offset)),
                new InlineKeyboardButton("⏩").callbackData(uuid + ":" + (ITEMS_ON_PAGE * (result.size() / ITEMS_ON_PAGE)))
        };
        markup.addRow(buttons);
        if (uuidToMessageMap.containsKey(uuid)) {
            Message message = uuidToMessageMap.get(uuid);
            bot.executeAsyncBotRequest(new EditMessageText(message.chat().id(), message.messageId(), text.toString()).replyMarkup(markup));
        } else {
            if (isNewRequest) {
                SendResponse response = bot.executeBotRequest(new SendMessage(user.id(), text.toString()).replyMarkup(markup));
                uuidToMessageMap.put(uuid, response.message());
            }
        }
    }

    @Override
    public void processCallbackQuery(CallbackQuery callbackQuery) throws Exception {
        String[] data = callbackQuery.data().split(":");
        send(callbackQuery.from(), Integer.parseInt(data[1]), UUID.fromString(data[0]), false);
        bot.executeAsyncBotRequest(new AnswerCallbackQuery(callbackQuery.id()));
    }
}