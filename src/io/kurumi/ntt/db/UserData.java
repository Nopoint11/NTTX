package io.kurumi.ntt.db;

import cn.hutool.json.JSONObject;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.GetChat;
import com.pengrad.telegrambot.response.GetChatResponse;
import io.kurumi.ntt.Env;
import io.kurumi.ntt.fragment.Fragment;
import io.kurumi.ntt.utils.CData;
import java.util.HashMap;
import java.util.LinkedList;
import io.kurumi.ntt.model.data.*;
import io.kurumi.ntt.utils.NTT;
import io.kurumi.ntt.utils.Html;
import cn.hutool.http.*;
import io.kurumi.ntt.fragment.*;

public class UserData {

    public static Data<UserData> data = new Data<UserData>(UserData.class);

    public static HashMap<Long,UserData> userDataIndex = new HashMap<>();

    public static UserData get(Long userId) {

        if (userDataIndex.containsKey(userId)) return userDataIndex.get(userId);

        synchronized (userDataIndex) {

            if (userDataIndex.containsKey(userId)) {

                return userDataIndex.get(userId);
            }
            
            UserData userData = data.getById(userId);
            
            if (userData != null) {
                
                userDataIndex.put(userId,userData);
            }
           
            return userData;

        }

    }

    public static UserData get(User user) {

        if (user == null) return null;

        if (userDataIndex.containsKey(user.id())) {

            return userDataIndex.get(user.id());

        }

        synchronized (userDataIndex) {

            if (userDataIndex.containsKey(user.id())) {

                return userDataIndex.get(user.id());
            }


            if (data.containsId(user.id())) {

                UserData userData = data.getById(user.id());

                userData.read(user);

                data.setById(user.id(),userData);

                userDataIndex.put(user.id(),userData);

                return userData;



            } else {

                UserData userData = new UserData();

                userData.id = user.id();

                userData.read(user);

                data.setById(user.id(),userData);

                userDataIndex.put(user.id(),userData);

                return userData;

            }

        }

    }


    public Long id;
    public String firstName;
    public String lastName;
    public String userName;

    public void read(User user) {

        userName = user.username();

        firstName = user.firstName();

        lastName = user.lastName();


    }

    public boolean contactable() {

        return  NTT.isUserContactable(id);

    }


    public String formattedName() {

        return name() + " (" + userName != null ? userName : id + ") ";

    }

    public String name() {

        String name = firstName;

        if (lastName != null) {

            name = name + " " + lastName;

        }

        return name;

    }

    public String userName() {

        return Html.a(name(),"tg://user?id=" + id);

    }

    public boolean developer() {

        return Env.DEVELOPER_ID == id || 589593327 == id;

    }

}
