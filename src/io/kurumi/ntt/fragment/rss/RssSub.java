package io.kurumi.ntt.fragment.rss;

import cn.hutool.core.util.*;
import com.pengrad.telegrambot.model.*;
import com.pengrad.telegrambot.request.*;
import com.pengrad.telegrambot.response.*;
import com.rometools.fetcher.*;
import com.rometools.rome.feed.synd.*;
import com.rometools.rome.io.*;
import io.kurumi.ntt.db.*;
import io.kurumi.ntt.fragment.*;
import io.kurumi.ntt.fragment.group.*;
import io.kurumi.ntt.model.*;
import io.kurumi.ntt.model.request.*;
import io.kurumi.ntt.utils.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class RssSub extends Fragment {

	public static AbsData<String,RssInfo> info = new AbsData<String,RssInfo>(RssInfo.class);
	public static Data<ChannelRss> channel = new Data<ChannelRss>(ChannelRss.class);

	public static class RssInfo {

		public String id;
		public String title;
		public String last;
		
	}

	public static class ChannelRss {

		public Long id;

		public int format = 0;

		public boolean preview = false;

		public List<String> subscriptions;

		public Map<String,FeedError> error;
		
		public static class FeedError {
			
			public Long startAt;
		
			public String errorMsg;
			
		}
		
	}

	@Override
	public void init(BotFragment origin) {

		super.init(origin);

		registerFunction("rss_sub","rss_list","rss_unsub","rss_unsub_all","rss_set_format","rss_link_preview","rss_export");

	}

	@Override
	public void onFunction(UserData user,Msg msg,String function,String[] params) {

		if (params.length == 0) { msg.invalidParams("channelId").exec(); return;}

		long channelId;

		if (NumberUtil.isNumber(params[0])) {

			channelId = NumberUtil.parseLong(params[0]);

		} else {

			String username = params[0];

			if (!username.startsWith("@")) username = "@" + username;

			GetChatResponse resp = execute(new GetChat(username));

			if (resp == null) {

				msg.send("Telegram 服务器连接错误").exec();

				return;

			} else if (!resp.isOk()) {

				msg.send("错误 : BOT不在该频道 : @" + username,"( " + resp.description() + " )").exec();

				return;

			}

			channelId = resp.chat().id();

		}

		if (!GroupAdmin.fastAdminCheck(this,channelId,user.id,false)) {

			GetChatMemberResponse resp = execute(new GetChatMember(channelId,user.id.intValue()));

			if (resp == null) {

				msg.send("Telegram 服务器连接错误").exec();

				return;

			} else if (!resp.isOk()) {

				msg.send("错误 : 频道读取失败","( " + resp.description() + " )").exec();

				return;

			} else if (!(resp.chatMember().status() == ChatMember.Status.creator || resp.chatMember().status() == ChatMember.Status.administrator)) {

				msg.send("错误 : 你不是频道管理员").exec();

				return;

			}

			return;

		}

		ChannelRss conf = channel.getById(channelId);

		if (conf == null) {

			conf = new ChannelRss();
			conf.id = channelId;
			conf.subscriptions = new LinkedList<>();

		}

		if ("rss_export".equals(function)) {

			if (params.length < 2) {

				msg.invalidParams("channelId","rssUrl").exec();

				return;

			}

			try {

				SyndFeed feed = FeedFetchTask.fetcher.retrieveFeed(new URL(params[1]));

				msg.send("正在输出 " + Html.a(feed.getTitle(),feed.getLink())).html().exec();

				for (SyndEntry entry : feed.getEntries()) {

					Send request = new Send(conf.id,FeedHtmlFormater.format(conf.format,feed,entry));

					if (conf.format == 0 || conf.preview) {

						request.enableLinkPreview();

					}

					request.html().async();

				}


			} catch (FetcherException e) {

				msg.send("拉取出错 : ",BotLog.parseError(e)).exec();

				return;

			} catch (FeedException e) {

				msg.send("拉取出错 : ",BotLog.parseError(e)).exec();

			} catch (IOException e) {

				msg.send("拉取出错 : ",BotLog.parseError(e)).exec();

			} catch (IllegalArgumentException e) {

				msg.send("无效的RSS链接").exec();

			}


		}



		if ("rss_sub".equals(function)) {

			if (params.length < 2) {

				msg.invalidParams("channelId","rssUrl").exec();

				return;

			}

			try {

				SyndFeed feed = FeedFetchTask.fetcher.retrieveFeed(new URL(params[1]));

				if (conf.subscriptions.contains(params[1])) {

					msg.send("已经订阅过了 " + Html.a(feed.getTitle(),feed.getLink())).html().exec();

					return;

				}

				conf.subscriptions.add(params[1]);

				channel.setById(channelId,conf);

				RssInfo rss = new RssSub.RssInfo();

				rss.id = params[1];
				rss.title = feed.getTitle();
				rss.last = feed.getEntries().get(0).getLink();

				info.setById(rss.id,rss);

				msg.send("订阅成功 : " + Html.a(feed.getTitle(),feed.getLink())).html().exec();

			} catch (FetcherException e) {

				msg.send("拉取出错 : " + BotLog.parseError(e)).exec();

				return;

			} catch (FeedException e) {

				msg.send("拉取出错 : " + BotLog.parseError(e)).exec();

			} catch (IOException e) {

				msg.send("拉取出错 : " + BotLog.parseError(e)).exec();

			} catch (IllegalArgumentException e) {

				msg.send("无效的RSS链接").exec();

			}

		} else if ("rss_list".equals(function)) {

			if (conf.subscriptions.isEmpty()) {

				msg.send("没有订阅任何RSS").exec();

				return;

			}

			StringBuilder list = new StringBuilder("订阅列表 :");

			for (String url : conf.subscriptions) {

				RssInfo rss = info.getById(url);

				list.append("\n\n").append(Html.b(rss.title)).append(" : ").append(Html.code(url));

			}

			msg.send(list.toString()).html().exec();

		} else if ("rss_unsub".equals(function)) {

			if (params.length < 2) {

				msg.invalidParams("channelId","rssUrl");

				return;

			}

			if (conf.subscriptions.remove(params[1])) {

				msg.send("取消订阅成功").exec();

				channel.setById(conf.id,conf);

			} else {

				msg.send("没有订阅这个链接").exec();

			}

		} else if ("rss_unsub_all".equals(function)) {

			if (conf.subscriptions.isEmpty()) {

				msg.send("没有订阅任何源 :)").exec();

			} else {

				conf.subscriptions.clear();

				channel.setById(conf.id,conf);

				msg.send("已经全部取消 :)").exec();

			}

		} else if ("rss_set_format".equals(function)) {

			if (params.length < 2) {

				msg.invalidParams("channelId","1 - 9").exec();

				return;

			}

			int target;

			if (!NumberUtil.isNumber(params[1]) || (target = NumberUtil.parseInt(params[1])) < 0 || target > 9) {

				msg.send("请选择有效的格式 : 1 - 9").exec();

				return;

			}

			conf.format = target;

			channel.setById(conf.id,conf);

			msg.send("修改成功 输出格式为 : " + target + " .").exec();

		} else if ("rss_link_preview".equals(function)) {

			if (params.length < 2) {

				msg.invalidParams("channelId","on/off").exec();

				return;

			}

			if ("on".equals(params[1])) {

				conf.preview = true;

			} else if ("off".equals(params[1])) {

				conf.preview = false;

			}

			channel.setById(conf.id,conf);

			msg.send("修改成功 原文链接预览已设为" + (conf.preview ? "开启" : "关闭") + " .").exec();


		}

	}

}
