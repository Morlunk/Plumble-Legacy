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
				targetString = "Unknown";
			sb.append("To ");
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
				actorName = "Server";
			else
				actorName = msg.actor.name;
			
			appendNameHighlight(actorName, sb);
		}
		sb.append(": ");
		sb.append(Html.fromHtml(msg.message));
		return sb;
	}
	
	public Spannable formatUserStateUpdate(User user, UserState userState) {
		/*
		 *  Hi localizers,
		 *  
		 *  Please wait until I move all strings here into strings.xml before starting localization. Thanks.
		 *  
		 *  - Andrew
		 */
		
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
			appendNameHighlight(userState.getName(), sb);
			sb.append(" connected.");
			return sb;
		}
		
		// Channel move actions
		if(userState.hasChannelId() && userState.getChannelId() == service.getCurrentChannel().id) {
			appendNameHighlight(user.name, sb);
			sb.append(" moved in from ");
			appendNameHighlight(user.getChannel().name, sb); // This doesn't return the correct result. TODO fix!
			sb.append(" by ");
			if(actor != null) {
				appendNameHighlight(actor.name, sb);
			} else {
				sb.append("the server");
			}
			sb.append(".");
			return sb;
		}
		
		// Mute/deafen actions within the current user's channel
		if(user.getChannel().id == service.getCurrentChannel().id) {
			if(userState.hasSelfDeaf() || userState.hasSelfMute()) {
				if(userState.getSession() == service.getCurrentUser().session) {
					if(userState.getSelfMute() && userState.getSelfDeaf()) {
						sb.append("Muted and deafened.");
					} else if(userState.getSelfMute()) {
						sb.append("Muted.");
					} else {
						sb.append("Unmuted.");
					}
				} else {
					appendNameHighlight(user.name, sb);
					if(userState.getSelfMute() && userState.getSelfDeaf()) {
						sb.append(" is now muted and deafened.");
					} else if(userState.getSelfMute()) {
						sb.append(" is now muted.");
					} else {
						sb.append(" is now unmuted.");
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
