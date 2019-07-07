package io.kurumi.ntt.fragment.forum.admin;

import cn.hutool.core.util.NumberUtil;
import com.mongodb.client.FindIterable;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.GetMe;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.GetMeResponse;
import com.pengrad.telegrambot.response.SendResponse;
import io.kurumi.ntt.db.PointStore;
import io.kurumi.ntt.db.UserData;
import io.kurumi.ntt.fragment.BotFragment;
import io.kurumi.ntt.fragment.Fragment;
import io.kurumi.ntt.fragment.forum.ForumE;
import io.kurumi.ntt.fragment.forum.ForumPost;
import io.kurumi.ntt.fragment.forum.ForumTag;
import io.kurumi.ntt.model.Callback;
import io.kurumi.ntt.model.Msg;
import io.kurumi.ntt.model.request.ButtonLine;
import io.kurumi.ntt.model.request.ButtonMarkup;
import io.kurumi.ntt.model.request.Keyboard;
import io.kurumi.ntt.utils.MongoIDs;
import java.util.LinkedList;
import java.util.List;
import io.kurumi.ntt.db.PointData;

public class ForumManage extends Fragment {

    final String POINT_CREATE_FORUM = "forum.create";
    final String POINT_FORUM_MANAGE = "forum.main";
    final String POINT_EDIT_NAME = "forum.name";
    final String POINT_EDIT_DESC = "forum.desc";
    final String POINT_EDIT_TOKEN = "forum.token";
    final String POINT_EDIT_CHAN = "forum.chan";
    final String POINT_EDIT_ADMIN = "forum.admin";
    final String POINT_EDIT_TAGS = "forum.tags";
    final String POINT_RESET = "forum.reset";
    final String POINT_DEL_FORUM = "forum.del";
    final String POINT_CREATE_TAG = "forum.tag.new";
    final String POINT_SHOW_TAG = "forum.tag.show";
    final String POINT_EDIT_TAG_NAME = "forum.tag.name";
    final String POINT_EDIT_TAG_DESC = "forum.tag.desc";
    final String POINT_DEL_TAG = "forum.tag.del";

	@Override
	public void init(BotFragment origin) {

		super.init(origin);

        registerFunction("forum");

		registerPoints(POINT_CREATE_FORUM,
					   POINT_FORUM_MANAGE,
					   POINT_EDIT_NAME,
					   POINT_EDIT_DESC,
					   POINT_EDIT_CHAN,
					   POINT_EDIT_TOKEN,
					   POINT_EDIT_ADMIN,
					   POINT_CREATE_TAG,
					   POINT_EDIT_TAGS,
					   POINT_SHOW_TAG,
					   POINT_EDIT_TAG_NAME,
					   POINT_EDIT_TAG_DESC);

    }

    @Override
    public void onFunction(UserData user,Msg msg,String function,String[] params) {

        long count = ForumE.data.countByField("owner",user.id);

        if (count == 0 && params.length > 0 && "init".equals(params[0])) {

            createForum(user,msg);

            return;

        } else if (count == 0) {

            msg.send("没有创建论坛，使用 /forum init 来创建。").exec();

            return;

        }

        forumMain(false,user,msg);

    }

    void createForum(UserData user,Msg msg) {

        String[] desc = new String[]{

			"电报论坛是一个由 NTT 驱动的基于 Channel 和 Bot 的简中论坛程序。\n",

			"要创建论坛，你必须同意 : 喵...\n",

			"警告 : 目前程序并未稳定，随时可能需要清除数据以更新程序",

        };

        setPrivatePoint(user,POINT_CREATE_FORUM);

        msg.send(desc).keyboard(new Keyboard() {{

					newButtonLine().newButton("同意").newButton("拒绝");

				}}).exec();

    }

	@Override
	public void onPoint(UserData user,Msg msg,String pointStr,Object data) {

		PointData point = getPrivatePoint(user);

        switch (pointStr) {

            case POINT_CREATE_FORUM:
                createForum(user,msg,point);
                break;
            case POINT_EDIT_NAME:
                forumNameEdit(user,msg,point);
                break;
            case POINT_EDIT_DESC:
                forumDiscEdit(user,msg,point);
                break;
            case POINT_EDIT_CHAN:
                forumChanEdit(user,msg,point);
                break;
            case POINT_EDIT_TOKEN:
                forumTokenEdit(user,msg,point);
                break;

            case POINT_CREATE_TAG:
                forumTagCreate(user,msg,point);
                break;
            case POINT_EDIT_TAG_NAME:
                tagNameEdit(user,msg,point);
                break;
            case POINT_EDIT_TAG_DESC:
                tagDescEdit(user,msg,point);
                break;

        }

    }

    void createForum(UserData user,Msg msg,PointData point) {

        if (point.data == null) {

            if (!"同意".equals(msg.text())) {

                msg.send("这足够公平,如果你考虑好了就再来。").removeKeyboard().exec();

                clearPrivatePoint(user);

                return;

            }

            point.data = new ForumCreate();

            msg.send("好，现在输入用于论坛的BotToken :","\nBotToken可以当成TelegramBot登录的账号密码、需要在 @BotFather 申请。").withCancel().removeKeyboard().exec();

            return;

        }

        ForumCreate create = (ForumCreate) point.data;

        if (create.progress == 0) {

            if (!msg.hasText() || !msg.text().contains(":")) {

                msg.send("无效的Token.请重试. ","Token 看起来像这样: '12345678:ABCDEfgHIDUROVjkLmNOPQRSTUvw-cdEfgHI'").withCancel().exec();

                return;

            }

            msg.send("正在检查BOT信息...").exec();

            GetMeResponse me = (create.bot = new TelegramBot(create.token = msg.text())).execute(new GetMe());

            if (!me.isOk()) {

                msg.send("Token无效... 请重新输入").withCancel().exec();

                return;

            }

            create.botMe = me.user();

            create.progress = 1;

            String[] channel = new String[]{

				"现在发送作为论坛版面的频道 (Channel) :\n",

				"你使用的 @" + me.user().username() + " 必须可以在频道发言",
				"现在转发一条频道的消息来,以设置频道\n",

				"不用担心，频道可以在创建完成后更改 :)",

            };

            msg.send(channel).withCancel().exec();

        } else if (create.progress == 1) {

            Message message = msg.message();

            Chat chat = message.forwardFromChat();

            if (chat == null || chat.type() != Chat.Type.channel) {

                msg.send("请直接转发一条频道消息 : 如果没有消息，那就自己发一条").withCancel().exec();

                return;

            }

            SendResponse resp = create.bot.execute(new SendMessage(chat.id(),"Test").disableNotification(true));

            if (!resp.isOk()) {

                msg.send("设置的BOT @" + create.botMe.username() + " 无法在该频道 (" + chat.title() + ") 发言... 请重试").withCancel().exec();

                return;

            }

            create.bot.execute(new DeleteMessage(chat.id(),resp.message().messageId()));

            create.channelId = chat.id();

            create.progress = 2;

            msg.send("十分顺利。现在发送论坛的名称 : 十个字以内 ","\n如果超过、你可以手动设置频道和BOT的名称 (如果字数允许) ,这里的名称仅作为一个简称").withCancel().exec();

        } else if (create.progress == 2) {

            if (!msg.hasText()) {

                msg.send("忘记了吗？你正在创建一个论坛。现在发送名称 :").withCancel().exec();

                return;

            }

            if (msg.text().length() > 10) {

                msg.send("好吧，再说一遍。名称限制十个字 : 你可以手动设置频道和BOT的名称 (如果字数允许) ,这里的名称仅作为一个简称").withCancel().exec();

                return;

            }

            clearPrivatePoint(user);

            ForumE forum = new ForumE();

            forum.name = msg.text();
            forum.owner = user.id;
            forum.token = create.token;
            forum.channel = create.channelId;

            forum.id = MongoIDs.getNextId(ForumE.class.getSimpleName());

            ForumE.data.setById(forum.id,forum);

            msg.send("好，现在创建成功。不要忘记设置好简介、分类这些信息。完成之后 '重置频道信息' 来立即更新缓存。").exec();

            forumMain(false,user,msg);

        }

    }

    void forumMain(boolean edit,UserData user,Msg msg) {

        final ForumE forum = ForumE.data.getByField("owner",user.id);

        if (forum == null) {

            msg.sendOrEdit(edit,"没有创建论坛，使用 /forum init 来创建。").exec();

            return;

        }

        String[] info = new String[]{

			"论坛名称 : " + forum.name,
			"论坛简介 : " + forum.description,

			"\n绑定频道 : " + forum.channel,
			"BotToken : " + forum.token.substring(0,11) + "...",
			"状态 : " + (forum.error == null ? "正常运行" : forum.error),

			"\n帖子数量 : " + ForumPost.data.countByField("forumId",forum.id),

        };

        ButtonMarkup buttons = new ButtonMarkup() {{

				newButtonLine()
                    .newButton("修改名称",POINT_EDIT_NAME,forum.id)
                    .newButton("修改简介",POINT_EDIT_DESC,forum.id);

				newButtonLine()
                    .newButton("修改频道",POINT_EDIT_CHAN,forum.id)
                    .newButton("修改Token",POINT_EDIT_TOKEN,forum.id);

				newButtonLine()
                    .newButton("分类管理",POINT_EDIT_TAGS,forum.id)
                    .newButton("管理员设置",POINT_EDIT_ADMIN,forum.id);

				newButtonLine("重置频道消息",POINT_RESET,forum.id);
				newButtonLine("删库跑路",POINT_DEL_FORUM,forum.id);

				// TODO : 转移论坛

			}};


        msg.sendOrEdit(edit,info).buttons(buttons).exec();

    }

    @Override
    public void onCallback(UserData user,Callback callback,String point,String[] params) {

        long id = params.length == 0 ? -1 : NumberUtil.parseLong(params[0]);

        switch (point) {

            case POINT_FORUM_MANAGE:
                forumMain(true,user,callback);
                break;
            case POINT_EDIT_NAME:
                editForumName(user,callback,id);
                break;
            case POINT_EDIT_DESC:
                editForumDesc(user,callback,id);
                break;
            case POINT_EDIT_CHAN:
                editForumChan(user,callback,id);
                break;
            case POINT_EDIT_TOKEN:
                editForumToken(user,callback,id);
                break;


            case POINT_CREATE_TAG:
                createForumTag(user,callback,id);
                break;
            case POINT_EDIT_TAGS:
                showForumTags(true,user,callback,id);
                break;
            case POINT_SHOW_TAG:
                showTag(true,user,callback,id);
                break;
            case POINT_EDIT_TAG_NAME:
                editTagName(user,callback,id);
                break;
            case POINT_EDIT_TAG_DESC:
                editTagDesc(user,callback,id);
                break;

        }

    }

    void editForumName(UserData user,Callback callback,long forumId) {

        ForumEdit edit = new ForumEdit();

        edit.id = forumId;

        setPrivatePointData(user,POINT_EDIT_NAME,edit);

        callback.confirm();

        edit.msg.add(callback);
        edit.msg.add(callback.send("好。现在发送新的论坛名称 : 十个字以内 ","\n如果超过、你可以手动设置频道和BOT的名称 (如果字数允许) ,这里的名称仅作为一个简称").withCancel().send());

    }

    void forumNameEdit(UserData user,Msg msg,PointData point) {

        ForumEdit edit = (ForumEdit) point.data;

        edit.msg.add(msg);

        long forumId = edit.id;

        if (!msg.hasText()) {

            edit.msg.add(msg.send("忘记了吗？你正在修改论坛名称。现在发送新名称 :").withCancel().send());

            return;

        }

        if (msg.text().length() > 10) {

            edit.msg.add(msg.send("好吧，再说一遍。名称限制十个字 : 你可以手动设置频道和BOT的名称 (如果字数允许) ,这里的名称仅作为一个简称").withCancel().send());

            return;

        }

        clearPrivatePoint(user);

        ForumE forum = ForumE.data.getById(forumId);

        forum.name = msg.text();

        ForumE.data.setById(forumId,forum);

        for (Msg it : edit.msg) it.delete();

        msg.send("修改成功 : 请使用 '重置频道信息' 来立即更新缓存").exec();

        forumMain(false,user,msg);

    }

    void editForumDesc(UserData user,Callback callback,long forumId) {

        ForumEdit edit = new ForumEdit();

        edit.id = forumId;

        setPrivatePointData(user,POINT_EDIT_DESC,edit);

        callback.confirm();

        edit.msg.add(callback);
        edit.msg.add(callback.send("好。现在发送新的论坛简介 : 160字以内 , 你可以开一个置顶帖详细介绍论坛或是规定。").withCancel().send());

    }

    void forumDiscEdit(UserData user,Msg msg,PointData point) {

        ForumEdit edit = (ForumEdit) point.data;

        edit.msg.add(msg);

        long forumId = edit.id;

        if (!msg.hasText()) {

            edit.msg.add(msg.send("忘记了吗？你正在修改论坛简介。现在发送新简介 :").withCancel().send());

            return;

        }

        if (msg.text().length() > 160) {

            edit.msg.add(msg.send("好吧，再说一遍。简介限制160字 : 你可以开一个置顶帖详细介绍论坛或是规定 。").withCancel().send());

            return;

        }

        clearPrivatePoint(user);

        ForumE forum = ForumE.data.getById(forumId);

        forum.description = msg.text();

        ForumE.data.setById(forumId,forum);

        for (Msg it : edit.msg) it.delete();

        msg.send("修改成功 : 请使用 '重置频道信息' 来立即更新缓存").exec();

        forumMain(false,user,msg);

    }

    void editForumChan(UserData user,Callback callback,long forumId) {

        ForumEdit edit = new ForumEdit();

        edit.id = forumId;

        setPrivatePointData(user,POINT_EDIT_CHAN,edit);

        callback.confirm();

        String[] channel = new String[]{

			"现在发送作为论坛版面的频道 (Channel) :\n",

			"BOT必须可以在频道发言",
			"现在转发一条频道的消息来,以设置频道",

        };

        edit.msg.add(callback);
        edit.msg.add(callback.send(channel).withCancel().send());

    }

    void forumChanEdit(UserData user,Msg msg,PointData point) {

        ForumEdit edit = (ForumEdit) point.data;

        edit.msg.add(msg);

        long forumId = edit.id;

        Message message = msg.message();

        Chat chat = message.forwardFromChat();

        if (chat == null || chat.type() != Chat.Type.channel) {

            msg.send("请直接转发一条频道消息 : 如果没有消息，那就自己发一条").withCancel().exec();

            return;

        }

        ForumE forum = ForumE.data.getById(forumId);

        TelegramBot bot = new TelegramBot(forum.token);

        SendResponse resp = bot.execute(new SendMessage(chat.id(),"Test").disableNotification(true));

        if (!resp.isOk()) {

            msg.send("设置的BOT无法在该频道 (" + chat.title() + ") 发言... 请重试").withCancel().exec();

            return;

        }

        clearPrivatePoint(user);

        bot.execute(new DeleteMessage(chat.id(),resp.message().messageId()));

        forum.deleteCache();

        forum.channel = chat.id();

        ForumE.data.setById(forumId,forum);

        for (Msg it : edit.msg) it.delete();

        msg.send("修改成功 : 请使用 '重置频道信息' 来立即更新缓存").exec();

        forumMain(false,user,msg);

    }

    void editForumToken(UserData user,Callback callback,long forumId) {

        ForumEdit edit = new ForumEdit();

        edit.id = forumId;

        setPrivatePointData(user,POINT_EDIT_TOKEN,edit);

        callback.confirm();

        edit.msg.add(callback);
        edit.msg.add(callback.send("好，现在输入用于论坛的BotToken :","\nBotToken可以当成TelegramBot登录的账号密码、需要在 @BotFather 申请。").withCancel().send());

    }

    void forumTokenEdit(UserData user,Msg msg,PointData point) {

        ForumEdit edit = (ForumEdit) point.data;
        edit.msg.add(msg);

        long forumId = edit.id;

        if (!msg.hasText() || !msg.text().contains(":")) {

            msg.send("无效的Token.请重试. ","Token 看起来像这样: '12345678:ABCDEfgHIDUROVjkLmNOPQRSTUvw-cdEfgHI'").withCancel().exec();

            return;

        }

        msg.send("正在检查BOT信息...").exec();

        GetMeResponse me = (new TelegramBot(msg.text())).execute(new GetMe());

        if (!me.isOk()) {

            msg.send("Token无效... 请重新输入").withCancel().exec();

            return;

        }

        clearPrivatePoint(user);

        ForumE forum = ForumE.data.getById(forumId);

        forum.deleteCache();

        forum.token = msg.text();

        ForumE.data.setById(forumId,forum);

        for (Msg it : edit.msg) it.delete();

        msg.send("修改成功 : 请使用 '重置频道信息' 来立即更新缓存").exec();

        forumMain(false,user,msg);

    }

    void showForumTags(boolean edit,UserData user,Msg msg,final long forumId) {

        final FindIterable<ForumTag> tags = ForumTag.data.findByField("forumId",forumId);

        String[] info = new String[]{

			"修改分类 : 论坛主要由分类组成，每个分类会在频道创建版面。",

        };

        ButtonMarkup buttons = new ButtonMarkup() {{

				newButtonLine()
                    .newButton("新建分类",POINT_CREATE_TAG,forumId)
                    .newButton("返回设置",POINT_FORUM_MANAGE);

				ButtonLine line = null;

				for (ForumTag tag : tags) {

					if (line == null) {

						line = newButtonLine();
						line.newButton(tag.name,POINT_SHOW_TAG,tag.id);

					} else {

						line.newButton(tag.name,POINT_SHOW_TAG,tag.id);
						line = null;

					}

				}

			}};

        msg.sendOrEdit(edit,info).buttons(buttons).exec();

    }

    void createForumTag(UserData user,Callback callback,final long forumId) {

        if (!ForumE.data.containsId(forumId)) {

            callback.alert("不存在的论坛");
            callback.delete();

            return;

        }

        ForumEdit edit = new ForumEdit();

        edit.id = forumId;

        setPrivatePointData(user,POINT_CREATE_TAG,edit);

        callback.confirm();

        edit.msg.add(callback);
        edit.msg.add(callback.send("好，现在输入分类名称，不要与已有名称重复，十个字符以内 (中文记两个字符)。").withCancel().send());

    }

    void forumTagCreate(UserData user,Msg msg,PointData point) {

        ForumEdit edit = (ForumEdit) point.data;
        edit.msg.add(msg);

        long forumId = edit.id;

        if (!msg.hasText()) {

            edit.msg.add(msg.send("忘记了吗？你正在新建论坛分类。现在发送新分类名称 :").withCancel().send());

            return;

        }

        if (msg.text().toCharArray().length > 10) {

            edit.msg.add(msg.send("好吧，再说一遍。分类限制十个字符 (一个中文字占两个字符) : 分类通常应该为 2 -3 字。").withCancel().send());

            return;

        }

        if (ForumTag.tagExists(forumId,msg.text().trim())) {

            edit.msg.add(msg.send("好吧，再说一遍。分类名称不能与已有的分类重复 : 有什么重复的必要呢。？").withCancel().send());

            return;

        }

        ForumTag tag = new ForumTag();

        tag.forumId = forumId;
        tag.name = msg.text();

        tag.id = MongoIDs.getNextId(ForumTag.class.getSimpleName());

        ForumTag.data.setById(tag.id,tag);

        for (Msg it : edit.msg) it.delete();

        msg.send("修改成功 : 返回设置主页使用 '重置频道信息' 来立即更新缓存").exec();

        showForumTags(false,user,msg,forumId);

    }

    void showTag(boolean edit,UserData user,Msg msg,final long tagId) {

        final ForumTag tag = ForumTag.data.getById(tagId);

        if (tag == null) {

            if (msg instanceof Callback) {

                ((Callback) msg).alert("不存在的分类");

            } else {

                msg.send("分类不存在").exec();

            }

            return;

        }

        String[] info = new String[]{

			"分类 : " + tag.name,

			"\n简介 : " + tag.description == null ? "无" : tag.description,

			"\n帖子数量 : " + ForumPost.data.countByField("tagId",tagId)

        };

        ButtonMarkup buttons = new ButtonMarkup() {{

				newButtonLine()
                    .newButton("修改名称",POINT_EDIT_TAG_NAME,tagId)
                    .newButton("修改简介",POINT_EDIT_TAG_DESC,tagId);

				newButtonLine("返回列表",POINT_EDIT_TAGS,tag.forumId);
				newButtonLine("删除分类",POINT_DEL_TAG,tagId);

			}};

        msg.sendOrEdit(edit,info).buttons(buttons).exec();

    }

    void editTagName(UserData user,Callback callback,long tagId) {

        ForumEdit edit = new ForumEdit();

        edit.id = tagId;

        setPrivatePointData(user,POINT_EDIT_TAG_NAME,edit);

        callback.confirm();

        edit.msg.add(callback);
        edit.msg.add(callback.send("好，现在输入新分类名称，不要与已有名称重复，十个字符以内 (中文记两个字符)。").withCancel().send());

    }

    void tagNameEdit(UserData user,Msg msg,PointData point) {

        ForumEdit edit = (ForumEdit) point.data;
        edit.msg.add(msg);

        long tagId = edit.id;

        ForumTag tag = ForumTag.data.getById(tagId);

        if (tag == null) {

            clearPrivatePoint(user);

            msg.send("分类不存在 (").exec();

            return;

        } else if (!msg.hasText()) {

            edit.msg.add(msg.send("忘记了吗？你正在新建论坛分类。现在发送新分类名称 :").withCancel().send());

            return;

        } else if (msg.text().toCharArray().length > 10) {

            edit.msg.add(msg.send("好吧，再说一遍。分类限制十个字符 (一个中文字占两个字符) : 分类通常应该为 2 -3 字。").withCancel().send());

            return;

        } else if (ForumTag.tagExists(tag.forumId,msg.text().trim())) {

            edit.msg.add(msg.send("好吧，再说一遍。分类名称不能与已有的分类重复 : 有什么重复的必要呢。？").withCancel().send());

            return;

        }

        clearPrivatePoint(user);

        tag.name = msg.text();

        ForumTag.data.setById(tag.id,tag);

        for (Msg it : edit.msg) it.delete();

        msg.send("修改成功 : 返回设置主页使用 '重置频道信息' 来立即更新缓存").exec();

        showTag(false,user,msg,tagId);

    }

    void editTagDesc(UserData user,Callback callback,long tagId) {

        ForumEdit edit = new ForumEdit();

        edit.id = tagId;

        setPrivatePointData(user,POINT_EDIT_TAG_DESC,edit);

        callback.confirm();

        edit.msg.add(callback);
        edit.msg.add(callback.send("好。现在发送新的分类简介 : 160字以内 , 你可以开一个置顶帖详细介绍分类或是规定。").withCancel().send());

    }

    void tagDescEdit(UserData user,Msg msg,PointData point) {

        ForumEdit edit = (ForumEdit) point.data;
        edit.msg.add(msg);

        long tagId = edit.id;

        ForumTag tag = ForumTag.data.getById(tagId);

        if (tag == null) {

            clearPrivatePoint(user);

            msg.send("分类不存在 (").exec();

            return;

        } else if (!msg.hasText()) {

            edit.msg.add(msg.send("忘记了吗？你正在修改分类简介。现在发送新简介 :").withCancel().send());

            return;

        }

        if (msg.text().length() > 160) {

            edit.msg.add(msg.send("好吧，再说一遍。简介限制160字 : 你可以开一个置顶帖详细介绍分类或是规定 。").withCancel().send());

            return;

        }

        clearPrivatePoint(user);

        tag.description = msg.text();

        ForumTag.data.setById(tag.id,tag);

        for (Msg it : edit.msg) it.delete();

        msg.send("修改成功 : 返回设置主页使用 '重置频道信息' 来立即更新缓存").exec();

        showTag(false,user,msg,tagId);


    }

	void delTag(UserData user,Callback callback,final long tagId) {
		
        callback
			.edit("确认删除分类吗？该分类下所有帖子将被删除！")
			.buttons(new ButtonMarkup() {{

					newButtonLine("返回分类",POINT_SHOW_TAG,tagId);
					newButtonLine("返回分类",POINT_DEL_TAG,tagId);


				}});

    }

    class ForumCreate {

        int progress = 0;

        List<Msg> msg = new LinkedList<>();

        // ---------------

        String token;

        TelegramBot bot;

        User botMe;

        // ---------------

        long channelId;

    }

    class ForumEdit {

        long id;
        List<Msg> msg = new LinkedList<>();

    }


}
