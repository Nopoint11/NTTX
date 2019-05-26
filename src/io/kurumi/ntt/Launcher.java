package io.kurumi.ntt;

import cn.hutool.core.lang.*;
import cn.hutool.core.util.*;
import com.pengrad.telegrambot.model.*;
import com.pengrad.telegrambot.request.*;
import io.kurumi.ntt.*;
import io.kurumi.ntt.db.*;
import io.kurumi.ntt.fragment.*;
import io.kurumi.ntt.funcs.*;
import io.kurumi.ntt.funcs.admin.*;
import io.kurumi.ntt.funcs.chatbot.*;
import io.kurumi.ntt.funcs.nlp.*;
import io.kurumi.ntt.funcs.twitter.*;
import io.kurumi.ntt.funcs.twitter.ext.*;
import io.kurumi.ntt.funcs.twitter.track.*;
import io.kurumi.ntt.model.*;
import io.kurumi.ntt.utils.*;
import java.io.*;
import java.util.*;

import cn.hutool.core.lang.Console;
import io.kurumi.ntt.funcs.chatbot.ForwardMessage;
import io.kurumi.ntt.fragment.base.*;

public class Launcher extends BotFragment implements Thread.UncaughtExceptionHandler {

    public static final Launcher INSTANCE = new Launcher();

    @Override
    public boolean onMsg(UserData user,Msg msg) {

        if (super.onMsg(user,msg)) return true;

        if ("start".equals(msg.command()) && msg.params().length == 0) {

			msg.send("start failed successfully ᕙ(`▿´)ᕗ").publicFailed();

            return true;

        } else if ("help".equals(msg.command())) {

            msg.send("没有帮助 ~").publicFailed();

			return true;

        }

        return false;

    }

    @Override
    public void realStart() {

		addFragment(new PingFunction());

		addFragment(new Final());

        BotLog.info("初始化 完成 :)");

    }


	// public MtProtoBot mtp;

    @Override
    public void stop() {

		// mtp.stopBot();

        for (BotFragment bot : BotServer.fragments.values()) {

            if (bot != this) bot.stop();

        }

        super.stop();

        BotServer.INSTANCE.stop();

        /*

         FTTask.stop();
         UTTask.stop();
         SubTask.stopAll();

         */

        TrackTask.stop();

        Backup.AutoBackupTask.INSTANCE.stop();

        //  BotServer.INSTACNCE.stop();

    }



    public static void main(String[] args) {

        Thread.setDefaultUncaughtExceptionHandler(INSTANCE);

		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));

		if (!INSTANCE.isLongPulling()) {

			int serverPort = Integer.parseInt(Env.getOrDefault("server_port","-1"));
			String serverDomain = Env.get("server_domain");

			while (serverPort == -1) {

				System.out.print("输入本地Http服务器端口 : ");

				try {

					serverPort = Integer.parseInt(Console.input());

					Env.set("server_port",serverPort);

				} catch (Exception e) {}

			}

			if (serverDomain == null) {

				System.out.print("输入BotWebHook域名 : ");

				serverDomain = Console.input();

				Env.set("server_domain",serverDomain);

			}

			BotServer.INSTANCE = new BotServer(serverPort,serverDomain);

			try {

				BotServer.INSTANCE.start();

			} catch (IOException e) {

				BotLog.error("端口被占用 请检查其他BOT进程。");

				return;

			}

			String dbAddr = Env.getOrDefault("db_address","127.0.0.1");
			Integer dbPort = Integer.parseInt(Env.getOrDefault("db_port","27017"));

			while (!initDB(dbAddr,dbPort)) {

				System.out.print("输入MongoDb地址 : ");
				dbAddr = Console.scanner().nextLine();

				try {

					System.out.print("输入MongoDb端口 : ");
					dbPort = Console.scanner().nextInt();

					Env.set("db_address",dbAddr);
					Env.set("db_port",dbPort);

				} catch (Exception e) {}

			}

			RuntimeUtil.addShutdownHook(new Runnable() {

					@Override
					public void run() {

						INSTANCE.stop();

					}

				});

		}

        INSTANCE.start();

    }

    static boolean initDB(String dbAddr,Integer dbPort) {

        try {

            BotDB.init(dbAddr,dbPort);

            return true;

        } catch (Exception e) {

            return false;

        }

    }

    @Override
    public String botName() {

        return "NTTBot";

    }

    @Override
    public boolean isLongPulling() {

        return false;

    }

    @Override
    public boolean onUpdate(UserData user,Update update) {

        BotLog.process(user,update);

        return false;

    }

    @Override
    public void uncaughtException(Thread thread,Throwable throwable) {

        BotLog.error("无法处理的错误,正在停止BOT",throwable);

        INSTANCE.stop();

        System.exit(1);

    }




}
