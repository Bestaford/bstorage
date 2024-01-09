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
import java.util.stream.Collectors;

public final class TopCommand extends Command {

    public static final int PAGE_SIZE = 10;

    public final Map<UUID, Message> uuidToMessageMap;

    public TopCommand(BStorageBot bot) {
        super(bot, "Your most used tags");
        uuidToMessageMap = new HashMap<>();
    }

    @Override
    public void execute(User user) throws Exception {
        send(user, 0, UUID.randomUUID(), true);
    }

    public void send(User user, int page_index, UUID uuid, boolean isNewRequest) throws Exception {
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
                tagList.addAll(Arrays.stream(tags.split(BStorageBot.REGEX_WHITESPACES)).map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toSet()));
            }
        }
        Set<String> sortedTagSet = new TreeSet<>((o1, o2) -> {
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
        Set<String> tagSet = new HashSet<>(tagList);
        sortedTagSet.addAll(tagSet);
        List<String> result = new ArrayList<>(sortedTagSet);
        if (result.isEmpty()) {
            bot.sendMessage(user, bot.messages.getString("top.empty"));
            return;
        }
        List<String> page = new ArrayList<>();
        for (int i = page_index * PAGE_SIZE; page.size() < PAGE_SIZE; i++) {
            if (i < 0 || i >= result.size()) {
                break;
            }
            page.add(result.get(i));
        }
        int last_page = (int) Math.max(Math.ceil((double) result.size() / PAGE_SIZE) - 1, 0);
        StringBuilder text = new StringBuilder(String.format(bot.messages.getString("top.list"), page_index + 1, last_page + 1));
        text.append("\n");
        for (String tag : page) {
            text.append(String.format("\n#%s: %d", tag, Collections.frequency(tagList, tag)));
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        InlineKeyboardButton[] buttons = new InlineKeyboardButton[]{
                getButton("⏪", "first", uuid, 0),
                getButton("⬅️️", "previous", uuid, Math.max(page_index - 1, 0)),
                getButton("\uD83D\uDD01", "refresh", uuid, page_index),
                getButton("➡️", "next", uuid, Math.min(page_index + 1, last_page)),
                getButton("⏩", "last", uuid, last_page)
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

    public InlineKeyboardButton getButton(String text, String name, UUID uuid, int page_index) {
        return new InlineKeyboardButton(text).callbackData(getClass().getSimpleName() + ":" + name + ":" + uuid + ":" + page_index);
    }

    @Override
    public void processCallbackQuery(CallbackQuery callbackQuery) throws Exception {
        String[] data = callbackQuery.data().split(":");
        if (data[0].equals(getClass().getSimpleName())) {
            send(callbackQuery.from(), Integer.parseInt(data[3]), UUID.fromString(data[2]), false);
            bot.executeAsyncBotRequest(new AnswerCallbackQuery(callbackQuery.id()));
        }
    }
}