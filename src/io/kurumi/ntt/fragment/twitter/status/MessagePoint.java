package io.kurumi.ntt.fragment.twitter.status;

import io.kurumi.ntt.db.*;
import io.kurumi.ntt.fragment.twitter.status.*;

public class MessagePoint {
	
	public static AbsData<Integer,MessagePoint> data = new AbsData<Integer,MessagePoint>(MessagePoint.class);
	
	public static MessagePoint set(final int messageId,int type,long targetId) {
		
		MessagePoint point = new MessagePoint();
		
		point.id = messageId;
		
		point.type = type;
		
		point.targetId = targetId;
		
		data.setById(messageId,point);
		
		return point;
		
	}
	
	public static MessagePoint get(int messageId) {
		
		return data.getById(messageId);
		
	}
	
	public int id;
	public int type;
	
	// 0 : user
	// 1 : status
	
	public long targetId;

}