package io.kurumi.ntt.fragment.bots;

import cn.hutool.captcha.generator.CodeGenerator;
import cn.hutool.captcha.generator.MathGenerator;
import cn.hutool.captcha.generator.RandomGenerator;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.GetUserProfilePhotos;
import com.pengrad.telegrambot.request.LeaveChat;
import com.pengrad.telegrambot.request.SendPhoto;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.GetUserProfilePhotosResponse;
import com.pengrad.telegrambot.response.SendResponse;
import io.kurumi.ntt.db.PointData;
import io.kurumi.ntt.db.UserData;
import io.kurumi.ntt.fragment.BotFragment;
import io.kurumi.ntt.fragment.admin.Firewall;
import io.kurumi.ntt.model.Callback;
import io.kurumi.ntt.model.Msg;
import io.kurumi.ntt.model.request.ButtonMarkup;
import io.kurumi.ntt.model.request.Send;
import io.kurumi.ntt.utils.Html;
import io.kurumi.ntt.utils.Img;
import io.kurumi.ntt.utils.NTT;
import java.awt.Color;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.TimerTask;
import com.pengrad.telegrambot.request.EditMessageReplyMarkup;
import io.kurumi.ntt.Env;

public class JoinCaptchaBot extends UserBotFragment {

    final String POINT_AUTH = "auth";
	final String POINT_SEC_AUTH = "sec";
	final String POINT_ACC = "acc";
	final String POINT_REJ = "rej";

	final String POINT_DELETE = "del";

    HashMap<Long, HashMap<Long, Msg>> cache = new HashMap<>();
	HashMap<Long, HashMap<Long, Msg>> secCache = new HashMap<>();
	HashSet<Long> failed = new HashSet<>();

	Long logChannel;
    Boolean delJoin;
    String welcomeMessage;
    Integer lastWelcomeMessage;
    boolean lastChanged = false;
    Boolean deleteLastWelcome;
	
    @Override
    public void reload() {

		super.reload();

        delJoin = getParam("delJoin");

        if (delJoin == null) delJoin = false;

        logChannel = getParam("logChannel");

        welcomeMessage = getParam("welcome");

        lastWelcomeMessage = getParam("last");

        deleteLastWelcome = getParam("delLast");

	}

	@Override
	public int checkPoint(UserData user,Msg msg,String point,PointData data) {

		return PROCESS_SYNC;

	}

	@Override
	public int checkMsg(UserData user,Msg msg) {

		if (super.checkMsg(user,msg) == PROCESS_REJECT) return PROCESS_REJECT;

		return msg.message().newChatMembers() != null ? PROCESS_SYNC : PROCESS_ASYNC;

	}

    @Override
    public void onGroup(UserData user,final Msg msg) {

        if (msg.message().leftChatMember() != null) {

            if (cache.containsKey(msg.chatId())) {

                HashMap<Long, Msg> group = cache.get(msg.chatId());

                if (group.containsKey(msg.message().leftChatMember().id())) {

                    group.remove(msg.message().leftChatMember().id()).delete();

                    if (group.isEmpty()) cache.remove(msg.chatId());

				}

			}

            if (delJoin) msg.delete();

            if (user.id.equals(me.id())) {

                msg.delete();

			} else if (msg.message().leftChatMember().id().equals(me.id())) {

				StringBuilder groupInfo = new StringBuilder();

				groupInfo.append("群组 : ").append(msg.chat().title());

				if (msg.chat().username() != null) {

					groupInfo.append("\n群组身份 : ").append(msg.chat().username());

				}

				if (logChannel != null) {

					new Send(this,logChannel,"事件 : #移出群组",groupInfo.toString(),"[" + Html.code(msg.chatId().toString()) + "]","来自 : " + user.userName(),"#id" + user.id).html().exec();

				}

				if (!user.admin() && !userId.equals(user.id)) {

					banned_chat.add(msg.chatId());

				}

			} else if (!user.id.equals(msg.message().leftChatMember().id())) {
			}

		} else if (msg.message().newChatMembers() != null) {

            if (delJoin) msg.delete();

            final HashMap<Long, Msg> group = cache.containsKey(msg.chatId()) ? cache.get(msg.chatId()) : new HashMap<Long, Msg>();

            User newMember = msg.message().newChatMembers()[0];

			final UserData newData = UserData.get(newMember);

            if (newMember.isBot()) {

                if (newMember.id().equals(botId)) {

					StringBuilder groupInfo = new StringBuilder();

					groupInfo.append("群组 : ").append(msg.chat().title());

					if (msg.chat().username() != null) {

						groupInfo.append("\n群组身份 : ").append(msg.chat().username());

					}

					new Send(this,logChannel,"事件 : #加入群组",groupInfo.toString(),"[" + Html.code(msg.chatId().toString()) + "]","来自 : " + user.userName(),"#id" + user.id).html().exec();

					msg.send("欢迎使用加群验证BOT","给BOT 删除消息 和 封禁用户 权限就可以使用了 ~").exec();

				} else {

					new Send(this,logChannel,"事件 : #邀请 #机器人","群组 : " + msg.chat().title(),"[" + Html.code(msg.chatId().toString()) + "]","机器人 : " + newData.userName(),"#id" + newData.id,"来自 : " + user.userName(),"#id" + user.id).html().exec();

				}

                return;

			}

			if (user.admin() || msg.isGroupAdmin()) return;

		    if (!user.id.equals(newData.id)) {

				if (logChannel != null) {

					if (NTT.isGroupAdmin(this,msg.chatId(),newData.id)) {

						new Send(this,logChannel,"事件 : #邀请 #管理员邀请","群组 : " + msg.chat().title(),"[" + Html.code(msg.chatId().toString()) + "]","用户 : " + newData.userName(),"#id" + newData.id,"来自 : " + user.userName(),"#id" + user.id).html().exec();

					} else 	{

						new Send(this,logChannel,"事件 : #邀请","群组 : " + msg.chat().title(),"[" + Html.code(msg.chatId().toString()) + "]","用户 : " + newData.userName(),"#id" + newData.id,"来自 : " + user.userName(),"#id" + user.id).html().exec();

					}

				}

				return;

			}

			if (failed.contains(newData.id)) {

				msg.delete();

			} else if (Firewall.block.containsId(newData.id)) {

				failed.add(newData.id);

				/*

				 if (msg.kick() && logChannel != null) {

				 msg.delete();

				 new Send(this,logChannel,"事件 : #未通过 #SPAM","群组 : " + msg.chat().title(),"[" + Html.code(msg.chatId().toString()) + "]","用户 : " + user.userName(),"#id" + user.id).html().exec();

				 return;

				 }

				 */

			} 

			if (((System.currentTimeMillis() / 1000) - msg.message().date()) > 10 * 1000) {

				msg.send("你好呀，新来的绒布球 " + newData.userName() + " 因为咱处理超时，就算乃通过验证了 )").html().exec();

				return;

			}

			setGroupPoint(newData,POINT_DELETE);

			Img info = new Img(800,600,Color.WHITE);

		    info.drawLineInterfere(50);

			info.fontSize(39);

			boolean code = RandomUtil.randomBoolean();

			info.drawRandomColorTextCenter(0,0,0,400,"新加裙的绒布球你好呀");
			info.drawRandomColorTextCenter(0,200,0,200,"请发送 " + (code ? "喵" : "嘤") + " 通过验证");
			info.drawRandomColorTextCenter(0,400,0,0,"不要戳下面的按钮");

            ButtonMarkup buttons = new ButtonMarkup() {{

					newButtonLine()
						.newButton("喵",POINT_AUTH,newData.id)
                        .newButton("喵",POINT_AUTH,newData.id)
                        .newButton("喵",POINT_AUTH,newData.id)
                        .newButton("喵",POINT_AUTH,newData.id);

					newButtonLine()
						.newButton(" ※ 通过 ※ ",POINT_ACC,newData.id)
						.newButton(" ※ 滥权 ※ ",POINT_REJ,newData.id);

					newButtonLine()
						.newButton("喵",POINT_AUTH,newData.id)
                        .newButton("喵",POINT_AUTH,newData.id)
                        .newButton("喵",POINT_AUTH,newData.id)
                        .newButton("喵",POINT_AUTH,newData.id);


				}};

            setGroupPointData(newData,POINT_AUTH,code);

            group.put(newMember.id(),Msg.from(this,bot().execute(new SendPhoto(msg.chatId(),info.getBytes()).caption(newData.userName()).parseMode(ParseMode.HTML).replyMarkup(buttons.markup()))));

            cache.put(msg.chatId().longValue(),group);

            mainTimer.schedule(new TimerTask() {

					@Override
					public void run() {

						final HashMap<Long, Msg> group = cache.containsKey(msg.chatId()) ? cache.get(msg.chatId()) : new HashMap<Long, Msg>();

						if (group.containsKey(newData.id.longValue())) {

							clearGroupPoint(newData);

							group.remove(newData.id).delete();

							if (group.isEmpty()) {

								cache.remove(msg.chatId());

							} else {

								cache.put(msg.chatId().longValue(),group);

							}

							if (msg.kick(newData.id)) {

								msg.send(newData.userName() + " 是傻猫 , 真可惜喵...").html().failed(15 * 1000);

								failed.add(newData.id);

								if (logChannel != null) {

									new Send(origin,logChannel,"事件 : #未通过 #超时","群组 : " + msg.chat().title(),"[" + Html.code(msg.chatId().toString()) + "]","用户 : " + newData.userName(),"#id" + newData.id).html().exec();

								}

							}

						}

					}

				},new Date(System.currentTimeMillis() + 60 * 1000));

		}

	}

	@Override
	public void onCallback(UserData user,Callback callback,String point,String[] params) {

        long target = Long.parseLong(params[0]);
		HashMap<Long, Msg> group = cache.containsKey(callback.chatId()) ? cache.get(callback.chatId()) : new HashMap<Long, Msg>();
		HashMap<Long, Msg> secGroup = secCache.containsKey(callback.chatId()) ? secCache.get(callback.chatId()) : new HashMap<Long, Msg>();

		if (POINT_AUTH.equals(point)) {

			if (!user.id.equals(target)) {

				callback.alert("这个验证不针对乃 ~");

				return;

			}

			if (group.containsKey(user.id)) {

				group.remove(user.id).delete();

				if (group.isEmpty()) {

					cache.remove(callback.chatId());

				} else {

					cache.put(callback.chatId().longValue(),group);

				}

			} else if (secGroup.containsKey(user.id)) {

				secGroup.remove(user.id).delete();

				if (secGroup.isEmpty()) {

					secCache.remove(callback.chatId());

				} else {

					secCache.put(callback.chatId().longValue(),secGroup);

				}

			} else {

				callback.alert("这个验证已失效 (");
				callback.delete();

				return;

			}

			clearGroupPoint(user);

			if (callback.kick(user.id)) {

				callback.send(user.userName() + " 瞎按按钮 , 未通过验证 , 真可惜喵...").html().failed(15 * 1000);

				failed.add(user.id);

				if (logChannel != null) {

					new Send(this,logChannel,"事件 : #未通过 #点击按钮","群组 : " + callback.chat().title(),"[" + Html.code(callback.chatId().toString()) + "]","用户 : " + user.userName(),"#id" + user.id).html().exec();

				}

			}


		} else if (POINT_ACC.equals(point)) {

			if (NTT.checkGroupAdmin(callback)) 	{

				if (user.id.equals(target)) {

					if (group.containsKey(target)) {

						group.remove(user.id).delete();

						if (group.isEmpty()) {

							cache.remove(callback.chatId());

						} else {

							cache.put(callback.chatId().longValue(),group);

						}

					} else if (secGroup.containsKey(user.id)) {

						secGroup.remove(user.id).delete();

						if (secGroup.isEmpty()) {

							secCache.remove(callback.chatId());

						} else {

							secCache.put(callback.chatId().longValue(),secGroup);

						}

					} else {

						callback.alert("这个验证已失效 (");
						callback.delete();

						return;

					}

					clearGroupPoint(user);

					if (callback.kick(user.id)) {

						failed.add(user.id);

						callback.send(user.userName() + " 瞎按按钮 , 未通过验证 , 真可惜喵...").html().failed(15 * 1000);

						if (logChannel != null) {

							new Send(this,logChannel,"事件 : #未通过 #点击按钮","群组 : " + callback.chat().title(),"[" + Html.code(callback.chatId().toString()) + "]","用户 : " + user.userName(),"#id" + user.id).html().exec();

						}

					}

				} else {

					callback.alert("这个验证不针对乃 ~");
				}

				return;

			}

			if (group.containsKey(target)) {

				group.remove(target).delete();

			} else if (secGroup.containsKey(target)) {

				secGroup.remove(target).delete();

			} else {

				callback.alert("这个验证已失效 (");
				callback.delete();

				return;

			}

			point().groupPoints.remove(target);

			UserData targetUser = UserData.get(target);

			callback.send(targetUser.userName() + " py了管理之后通过了验证喵...").html().failed(15 * 1000);

            sendWelcome(user,callback);

			if (logChannel != null) {

				new Send(this,logChannel,"事件 : #通过验证 #管理员通过","群组 : " + callback.chat().title(),"[" + Html.code(callback.chatId().toString()) + "]","用户 : " + targetUser.userName(),"#id" + target).html().exec();

			}


		} else if (POINT_REJ.equals(point)) {

			if (NTT.checkGroupAdmin(callback)) 	{

				if (user.id.equals(target)) {

					if (group.containsKey(user.id)) {

						group.remove(user.id).delete();

						if (group.isEmpty()) {

							cache.remove(callback.chatId());

						} else {

							cache.put(callback.chatId().longValue(),group);

						}

					} else if (secGroup.containsKey(user.id)) {

						secGroup.remove(user.id).delete();

						if (secGroup.isEmpty()) {

							secCache.remove(callback.chatId());

						} else {

							secCache.put(callback.chatId().longValue(),secGroup);

						}

					} else {

						callback.alert("这个验证已失效 (");
						callback.delete();

						return;

					}

					if (callback.kick(user.id)) {

						failed.add(user.id);

						callback.send(user.userName() + " 瞎按按钮 , 未通过验证 , 真可惜喵...").html().failed(15 * 1000);

						if (logChannel != null) {

							new Send(this,logChannel,"事件 : #未通过 #点击按钮","群组 : " + callback.chat().title(),"[" + Html.code(callback.chatId().toString()) + "]","用户 : " + user.userName(),"#id" + user.id).html().exec();

						}

					}

				} else {

					callback.alert("这个验证不针对乃 ~");

				}

				return;

			}


			if (group.containsKey(target)) {

				group.remove(target).delete();

			} else if (secGroup.containsKey(target)) {

				secGroup.remove(target).delete();

			} else {

				callback.alert("这个验证已失效 (");
				callback.delete();

				return;

			}

			point().groupPoints.remove(target);

			UserData targetUser = UserData.get(target);

			if (callback.kick(target,true)) {

				callback.send(targetUser.userName() + " 被滥权了喵...").html().failed(15 * 1000);

				if (logChannel != null) {

					new Send(this,logChannel,"事件 : #未通过 #管理员移除","群组 : " + callback.chat().title(),"[" + Html.code(callback.chatId().toString()) + "]","用户 : " + targetUser.userName(),"#id" + target).html().exec();

				}

			}

		}

	}

    void sendWelcome(UserData user,Msg msg) {

        if (welcomeMessage != null) {

            if (lastWelcomeMessage != null) {

                bot().execute(new DeleteMessage(msg.chatId(),lastWelcomeMessage));

			}

            SendResponse resp = msg.send(welcomeMessage.replace("$新成员",user.userName())).html().exec();

            if (resp.isOk()) {

                lastWelcomeMessage = resp.message().messageId();

                lastChanged = true;

			}

		}

	}

    @Override
    public void stop() {
		
		for (HashMap<Long,Msg> last : cache.values()) {
			
			for (final Long id : last.keySet()) {
				
				Msg msg = last.get(id);
				
				msg.edit("[[ 程序更新重启 验证丢失 (*σ´∀`)σ ]]").buttons(new ButtonMarkup() {{ newUrlButtonLine("(*σ´∀`)σ","tg://user?id=" + id); }}).exec();
				
			}
			
		}
		
		for (HashMap<Long,Msg> last : secCache.values()) {

			for (final Long id : last.keySet()) {

				Msg msg = last.get(id);

				msg.edit("[[ 程序更新重启 验证丢失 (*σ´∀`)σ ]]").buttons(new ButtonMarkup() {{ newUrlButtonLine("(*σ´∀`)σ","tg://user?id=" + id); }}).exec();

			}

		}
		
		
        if (lastChanged) {

            setParam("last",lastWelcomeMessage);

		}
		
        super.stop();

	}

	class GeneratedCode {

		CodeGenerator generator;
		String code;

	}

	@Override
	public void onPoint(final UserData user,final Msg msg,String point,PointData data) {

        HashMap<Long, Msg> group = cache.containsKey(msg.chatId()) ? cache.get(msg.chatId()) : new HashMap<Long, Msg>();
		HashMap<Long, Msg> secGroup = secCache.containsKey(msg.chatId()) ? secCache.get(msg.chatId()) : new HashMap<Long, Msg>();

		if (msg.message().newChatMembers() != null && msg.message().newChatMembers()[0].id().equals(user.id)) {

			clearGroupPoint(user);

			onGroup(user,msg);

		} else if (msg.message().leftChatMember() != null) {

			if (group.containsKey(msg.message().leftChatMember().id())) {

				group.remove(msg.message().leftChatMember().id()).delete();

				if (group.isEmpty()) cache.remove(msg.chatId());

			}

			if (secGroup.containsKey(msg.message().leftChatMember().id())) {

				secGroup.remove(msg.message().leftChatMember().id()).delete();

				if (secGroup.isEmpty()) secCache.remove(msg.chatId());

			}

			if (delJoin) msg.delete();

			if (user.id.equals(me.id())) {

				msg.delete();

				return;

			} else if (!user.id.equals(msg.message().leftChatMember().id())) {

				return;

			}

			UserData left = UserData.get(msg.message().leftChatMember());

			if (logChannel != null) {

				failed.add(left.id);

				new Send(this,logChannel,"事件 : #成员退出","群组 : " + msg.chat().title(),"[" + Html.code(msg.chatId().toString()) + "]","用户 : " + left.userName(),"#id" + left.id).html().exec();

			}

			clearGroupPoint(user);

		} else {

			if (POINT_AUTH.equals(point) && msg.message().forwardSignature() == null && msg.hasText() && (((Boolean)data.data) ? msg.text().contains("喵") : (msg.text().contains("嘤") || msg.text().contains("嚶")))) {

				clearGroupPoint(user);

				if (cache.containsKey(msg.chatId())) {

					if (group.containsKey(user.id)) {

						group.remove(user.id).delete();

						if (group.isEmpty()) cache.remove(msg.chatId());

					}

				}

				if (needSecondaryVerification(user)) {

					setGroupPoint(user,POINT_DELETE);

					GeneratedCode code = new GeneratedCode();

					code.generator = RandomUtil.randomBoolean() ? new MathGenerator(1) : new RandomGenerator("苟利国家生死以岂因祸福避趋之",7);

					Img info = new Img(1000,600,Color.WHITE);

					info.drawLineInterfere(50);

					info.font("Noto Sans CJK SC Thin",39);

					code.code = code.generator.generate();

					info.drawRandomColorTextCenter(0,0,0,400,"这是二次验证 ~");
					info.drawRandomColorTextCenter(0,200,0,200,code.code);
					info.drawRandomColorTextCenter(0,400,0,0,"请输入验证码 ~");

					ButtonMarkup buttons = new ButtonMarkup() {{

							newButtonLine()
								.newButton("喵",POINT_AUTH,user.id)
								.newButton("喵",POINT_AUTH,user.id)
								.newButton("喵",POINT_AUTH,user.id)
								.newButton("喵",POINT_AUTH,user.id);

							newButtonLine()
								.newButton(" ※ 通过 ※ ",POINT_ACC,user.id)
								.newButton(" ※ 滥权 ※ ",POINT_REJ,user.id);

							newButtonLine()
								.newButton("喵",POINT_AUTH,user.id)
								.newButton("喵",POINT_AUTH,user.id)
								.newButton("喵",POINT_AUTH,user.id)
								.newButton("喵",POINT_AUTH,user.id);


						}};

					setGroupPointData(user,POINT_SEC_AUTH,code);

					secGroup.put(user.id,Msg.from(this,bot().execute(new SendPhoto(msg.chatId(),info.getBytes()).caption(user.userName()).parseMode(ParseMode.HTML).replyMarkup(buttons.markup()))));

					secCache.put(msg.chatId().longValue(),secGroup);

					mainTimer.schedule(new TimerTask() {

							@Override
							public void run() {

								final HashMap<Long, Msg> group = secCache.containsKey(msg.chatId()) ? secCache.get(msg.chatId()) : new HashMap<Long, Msg>();

								if (group.containsKey(user.id)) {

									clearGroupPoint(user);

									group.remove(user.id).delete();

									if (group.isEmpty()) {

										secCache.remove(msg.chatId());

									} else {

										secCache.put(msg.chatId().longValue(),group);

									}

									if (msg.kick(user.id)) {

										failed.add(user.id);

										msg.send(user.userName() + " 是傻猫 , 真可惜喵...").html().failed(15 * 1000);

										if (logChannel != null) {

											new Send(origin,logChannel,"事件 : #未通过 #超时 #二次验证","群组 : " + msg.chat().title(),"[" + Html.code(msg.chatId().toString()) + "]","用户 : " + user.userName(),"#id" + user.id).html().exec();

										}

									}

								}

							}

						},new Date(System.currentTimeMillis() + 2 * 60 * 1000));

				} else {

					msg.send(user.userName() + " 通过了验证 ~").html().failed(5 * 1000);

					sendWelcome(user,msg);

					if (logChannel != null) {

						new Send(this,logChannel,"事件 : #通过验证","群组 : " + msg.chat().title(),"[" + Html.code(msg.chatId().toString()) + "]","用户 : " + user.userName(),"#id" + user.id).html().exec();

					}


				}

			} else if (POINT_SEC_AUTH.equals(point)) {

				if (((GeneratedCode)data.data()).generator.verify(((GeneratedCode)data.data()).code,msg.text())) {

					if (secGroup.containsKey(user.id)) {

						secGroup.remove(user.id).delete();

						if (secGroup.isEmpty()) secCache.remove(msg.chatId());

					}

					msg.delete();

					clearGroupPoint(user);

					failed.remove(user.id);

					msg.send(user.userName() + " 通过了验证 ~").html().failed(5 * 1000);

					sendWelcome(user,msg);

					if (logChannel != null) {

						new Send(this,logChannel,"事件 : #通过验证 #通过二次验证","群组 : " + msg.chat().title(),"[" + Html.code(msg.chatId().toString()) + "]","用户 : " + user.userName(),"#id" + user.id).html().exec();

					}

				} else {

					if (secGroup.containsKey(user.id)) {

						secGroup.remove(user.id).delete();

						if (secGroup.isEmpty()) secCache.remove(msg.chatId());

					}

					if (msg.kick()) {

						failed.add(user.id);

						msg.send(user.userName() + " 不懂喵喵的语言 , 真可惜喵...").html().failed(15 * 1000);

						if (logChannel != null) {

							msg.forwardTo(logChannel);

							msg.delete();

							new Send(this,logChannel,"事件 : #未通过 #二次验证失败","验证码为 : " + ((GeneratedCode)data.data()).code,"群组 : " + msg.chat().title(),"[" + Html.code(msg.chatId().toString()) + "]","用户 : " + user.userName(),"#id" + user.id).html().exec();

						} else {

							msg.delete();

						}

					}


				}

			} else if (POINT_DELETE.equals(point)) {

				msg.delete();

			} else if (msg.kick()) {

				if (group.containsKey(user.id)) {

					group.remove(user.id).delete();

					if (group.isEmpty()) cache.remove(msg.chatId());

				}

				failed.add(user.id);

				msg.send(user.userName() + " 不懂喵喵的语言 , 真可惜喵...").html().failed(15 * 1000);

				if (logChannel != null) {

					msg.forwardTo(logChannel);

					msg.delete();

					new Send(this,logChannel,"事件 : #未通过 #发送其他内容","群组 : " + msg.chat().title(),"[" + Html.code(msg.chatId().toString()) + "]","用户 : " + user.userName(),"#id" + user.id).html().exec();

				} else {

					msg.delete();

				}

			}


		}

    }

	boolean needSecondaryVerification(UserData user) {

		if (failed.contains(user.id)) return true;

		if (user.userName == null) {

			return true;

		}

		for (Character c : user.name().toCharArray()) {

			if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.ARABIC) {

				return true;

			}

		}

		GetUserProfilePhotosResponse photos = bot().execute(new GetUserProfilePhotos(user.id.intValue()));

		if (photos.isOk()) {

			if (photos.photos().totalCount() == 0) {

				return true;

			} else if (photos.photos().totalCount() == 1) {

				return RandomUtil.randomInt(3) > 0;

			}

		}

		return (user.contactable != null && user.contactable) ? (RandomUtil.randomInt(3) > 1) : RandomUtil.randomBoolean();

	}

}
