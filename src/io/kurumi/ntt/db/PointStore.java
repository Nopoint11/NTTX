package io.kurumi.ntt.db;

import java.util.*;

import io.kurumi.ntt.fragment.*;

public class PointStore {

    private static HashMap<BotFragment, PointStore> point = new HashMap<>();

    public final BotFragment bot;
	
    public final HashMap<Long, PointData> privatePoints = new HashMap<>();
	public final HashMap<Long, PointData> groupPoints = new HashMap<>();
	
    private PointStore(BotFragment bot) {
        this.bot = bot;
    }

    public static synchronized PointStore getInstance(BotFragment bot) {

        if (point.containsKey(bot)) return point.get(bot);

        PointStore instance = new PointStore(bot);

        point.put(bot, instance);

        return instance;

    }

    public boolean containsPrivate(UserData user) {

        return privatePoints.containsKey(user.id);

    }
	
	public boolean containsGroup(UserData user) {

        return groupPoints.containsKey(user.id);

    }

    public PointData getPrivate(UserData user) {

        if (containsPrivate(user)) {

            return  privatePoints.get(user.id);

        }

        return null;

    }

	public PointData getGroup(UserData user) {

        if (containsGroup(user)) {

            return  groupPoints.get(user.id);

        }

        return null;

    }
	
	public void setPrivate(UserData user, final String pointTo, final PointData data) {

        privatePoints.put(user.id, data);

    }
	
	
    public void setPrivateData(UserData user, final String pointTo, final Object content) {

        privatePoints.put(user.id, new PointData() {{

            point = pointTo;
            data = content;

        }});

    }
	
	public void setGroup(UserData user, final String pointTo, final PointData data) {

        groupPoints.put(user.id, data);

    }
	
	
	public void setGroupData(UserData user, final String pointTo, final Object content) {

        groupPoints.put(user.id, new PointData() {{

					point = pointTo;
					data = content;

				}});

    }
	

    public PointData clearPrivate(UserData user) {

        return  privatePoints.remove(user.id);

    }

	public PointData clearGroup(UserData user) {

        return  groupPoints.remove(user.id);

    }

}
