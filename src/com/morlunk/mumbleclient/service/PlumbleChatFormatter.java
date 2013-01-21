package com.morlunk.mumbleclient.service;

import net.sf.mumble.MumbleProto.UserState;
import android.text.format.DateUtils;

import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.service.model.Message;
import com.morlunk.mumbleclient.service.model.User;

/**
 * A class for conversion of UserState updates and Messages into Spannables for display in the chat fragment.
 * @author morlunk
 */
public class PlumbleChatFormatter {
	
	private MumbleService service;
	
	public PlumbleChatFormatter(MumbleService service) {
		this.service = service;
	}
	
	public String formatMessage(Message msg) {
		final StringBuilder sb = new StringBuilder();
		sb.append("[");
		sb.append(DateUtils.formatDateTime(
			service,
			msg.timestamp,
			DateUtils.FORMAT_SHOW_TIME));
		sb.append("] ");

		if (msg.direction == Message.DIRECTION_SENT) {
			String targetString;
			if(msg.target != null)
				targetString = msg.target.name;
			else if(msg.channel != null)
				targetString = msg.channel.name;
			else
				targetString = service.getString(R.string.unknown);
			sb.append(service.getString(R.string.chat_message_to)+" "+getHighlightedString(targetString));
		} else {
			if (msg.channelIds > 0) {
				sb.append("(C) ");
			}
			if (msg.treeIds > 0) {
				sb.append("(T) ");
			}
			
			String actorName;
			
			if(msg.actor == null || msg.actor.name == null)
				actorName = service.getString(R.string.server);
			else
				actorName = msg.actor.name;
			
			sb.append(getHighlightedString(actorName));
		}
		sb.append(": ");
		sb.append(msg.message);
		return sb.toString();
	}
	
	public String formatUserStateUpdate(User user, UserState userState) {
		final StringBuilder sb = new StringBuilder();
		sb.append("[");
		sb.append(DateUtils.formatDateTime(
			service,
			System.currentTimeMillis(),
			DateUtils.FORMAT_SHOW_TIME));
		sb.append("] ");
		
		// Connect action
		if(user == null) {
			sb.append(service.getString(R.string.chat_notify_connected, getHighlightedString(userState.getName())));
			return sb.toString();
		}
		
		// Disconnect action
		if(userState == null) {
			sb.append(service.getString(R.string.chat_notify_disconnected, getHighlightedString(user.name)));
			return sb.toString();
		}
		
		User actor = null;
		if(userState.hasActor()) {
			actor = service.getUser(userState.getActor());
		}
		
		// Channel move actions
		if(userState.hasChannelId() && userState.getChannelId() == service.getCurrentChannel().id) {
			String actorName = (actor != null) ? actor.name : service.getString(R.string.server);
			sb.append(service.getString(R.string.chat_notify_moved, getHighlightedString(user.name), getHighlightedString(user.getChannel().name), getHighlightedString(actorName)));
			return sb.toString();
		}
		
		// Mute/deafen actions within the current user's channel
		if(user.getChannel().id == service.getCurrentChannel().id) {
			if(userState.hasSelfDeaf() || userState.hasSelfMute()) {
				if(userState.getSession() == service.getCurrentUser().session) {
					if(userState.getSelfMute() && userState.getSelfDeaf()) {
						sb.append(service.getString(R.string.chat_notify_muted_deafened));
					} else if(userState.getSelfMute()) {
						sb.append(service.getString(R.string.chat_notify_muted));
					} else {
						sb.append(service.getString(R.string.chat_notify_unmuted));
					}
				} else {
					if(userState.getSelfMute() && userState.getSelfDeaf()) {
						sb.append(service.getString(R.string.chat_notify_now_muted_deafened, getHighlightedString(user.name)));
					} else if(userState.getSelfMute()) {
						sb.append(service.getString(R.string.chat_notify_now_muted, getHighlightedString(user.name)));
					} else if(user.userState == User.USERSTATE_DEAFENED && !userState.getSelfDeaf()){
						sb.append(service.getString(R.string.chat_notify_now_unmuted_undeafened, getHighlightedString(user.name)));
					} else {
						sb.append(service.getString(R.string.chat_notify_now_unmuted, getHighlightedString(user.name)));
					}
				}
				return sb.toString();
			}
		}
		
		return null;
	}
	
	/**
	 * Convenience method to get a colored HTML string. For channel/user names.
	 */
	private String getHighlightedString(String name) {
		return "<font color=\"#33b5e5\">"+name+"</font>";
	}

}
