package io.kurumi.ntt.fragment.twitter.action;

import cn.hutool.core.util.NumberUtil;
import io.kurumi.ntt.db.UserData;
import io.kurumi.ntt.fragment.abs.Msg;
import io.kurumi.ntt.fragment.abs.TwitterFunction;
import io.kurumi.ntt.fragment.twitter.TAuth;
import io.kurumi.ntt.fragment.twitter.archive.StatusArchive;
import io.kurumi.ntt.fragment.twitter.archive.UserArchive;
import io.kurumi.ntt.fragment.twitter.status.MessagePoint;
import io.kurumi.ntt.utils.NTT;
import java.util.LinkedList;
import twitter4j.Relationship;
import twitter4j.Twitter;
import twitter4j.TwitterException;

public class Follow extends TwitterFunction {

	@Override
	public void functions(LinkedList<String> names) {

		names.add("follow");

	}
	
	@Override
	public boolean useCurrent() {

		return true;

	}
	
	@Override
	public void onFunction(UserData user,Msg msg,String function,String[] params,TAuth account) {
		
		Twitter api = account.createApi();
		
		if (params.length > 0) {
			
			for (String target : params) {
				
				long targetId;
				
				if (NumberUtil.isNumber(target)) {
					
					targetId = NumberUtil.parseLong(target);
					
				} else {
					
					String screenName = NTT.parseScreenName(target);
					
					try {
						
						targetId =  api.showUser(screenName).getId();
						
					} catch (TwitterException e) {
						
					msg.send(NTT.parseTwitterException(e)).exec();
						
						return;
						
					}

				}
				
				follow(user,msg,account,api,targetId);

			}
			
		} else if (msg.targetChatId == -1 && msg.isPrivate() && msg.isReply()) {
			
			MessagePoint point = MessagePoint.get(msg.replyTo().messageId());

			if (point == null) {
				
				msg.send("咱不知道目标是谁 (｡í _ ì｡)").exec();
				
				return;
				
			}
			
			long targetUser;
			
			if (point.type == 1) {
				
				targetUser = StatusArchive.get(point.targetId).from;
				
			} else {
				
				targetUser = point.targetId;
				
			}
			
			follow(user,msg,account,api,targetUser);
			
		} else {
			
			msg.send("/follow <ID|用户名|链接> / 或对私聊消息回复 (如果你觉得这条消息包含一个用户或推文)").exec();
			
			return;
			
		}
		
	}

	private void follow(UserData user,Msg msg,TAuth account,Twitter api,long targetId) {
		
		UserArchive archive;

		try {

			archive = UserArchive.save(api.showUser(targetId));

		} catch (TwitterException e) {

		msg.send(NTT.parseTwitterException(e)).exec();

			return;

		}

		try {

			Relationship ship = api.showFriendship(targetId,account.id);

			if (ship.isSourceBlockingTarget()) {

				msg.send("你被 " + archive.urlHtml() + " 屏蔽了").html().point(0,targetId);

				return;

			} else if (ship.isSourceFollowedByTarget()) {

				msg.send("你已经关注了 " + archive.urlHtml()).html().point(0,targetId);

				return;

			}

			api.createFriendship(targetId);

			msg.send((archive.isProtected ? "已发送关注请求给 " : "已关注 ") + archive.urlHtml() + " ~").html().point(0,targetId);

		} catch (TwitterException e) {

			msg.send("关注失败 :",NTT.parseTwitterException(e)).exec();

		}
		
	}
	
}
