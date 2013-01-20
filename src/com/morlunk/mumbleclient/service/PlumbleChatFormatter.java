package com.morlunk.mumbleclient.service;

import net.sf.mumble.MumbleProto.UserState;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;

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
	
	public Spannable formatMessage(Message msg) {
		final SpannableStringBuilder sb = new SpannableStringBuilder();
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
			sb.append(service.getString(R.string.chat_message_to)+" ");
			appendNameHighlight(targetString, sb);
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
			
			appendNameHighlight(actorName, sb);
		}
		sb.append(": ");
		sb.append(Html.fromHtml(msg.message));
		return sb;
	}
	
	public Spannable formatUserStateUpdate(User user, UserState userState) {
		final SpannableStringBuilder sb = new SpannableStringBuilder();
		sb.append("[");
		sb.append(DateUtils.formatDateTime(
			service,
			System.currentTimeMillis(),
			DateUtils.FORMAT_SHOW_TIME));
		sb.append("] ");
		
		User actor = null;
		if(userState.hasActor()) {
			actor = service.getUser(userState.getActor());
		}
		
		// Connect action
		if(user == null) {
			sb.append(service.getString(R.string.chat_notify_connected, userState.getName()));
			return sb;
		}
		
		// Channel move actions
		if(userState.hasChannelId() && userState.getChannelId() == service.getCurrentChannel().id) {
			String actorName = (actor != null) ? actor.name : service.getString(R.string.server);
			sb.append(service.getString(R.string.chat_notify_moved, user.name, user.getChannel().name, actorName));
			return sb;
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
						sb.append(service.getString(R.string.chat_notify_now_muted_deafened, user.name));
					} else if(userState.getSelfMute()) {
						sb.append(service.getString(R.string.chat_notify_now_muted, user.name));
					} else {
						sb.append(service.getString(R.string.chat_notify_now_unmuted, user.name));
					}
				}
				return sb;
			}
		}
		
		return null;
	}
	
	/**
	 * Convenience method to insert a span for colouring a string to a SSB. For channel/user names.
	 */
	private void appendNameHighlight(String name, SpannableStringBuilder sb) {
		sb.append(name);
		sb.setSpan(new ForegroundColorSpan(service.getResources().getColor(R.color.abs__holo_blue_light)), sb.length()-name.length(), sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	}

}
