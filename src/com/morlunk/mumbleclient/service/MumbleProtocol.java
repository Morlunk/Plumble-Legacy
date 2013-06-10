package com.morlunk.mumbleclient.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

import net.sf.mumble.MumbleProto.Authenticate;
import net.sf.mumble.MumbleProto.ChannelRemove;
import net.sf.mumble.MumbleProto.ChannelState;
import net.sf.mumble.MumbleProto.CodecVersion;
import net.sf.mumble.MumbleProto.CryptSetup;
import net.sf.mumble.MumbleProto.PermissionDenied;
import net.sf.mumble.MumbleProto.Ping;
import net.sf.mumble.MumbleProto.Reject;
import net.sf.mumble.MumbleProto.ServerSync;
import net.sf.mumble.MumbleProto.TextMessage;
import net.sf.mumble.MumbleProto.UDPTunnel;
import net.sf.mumble.MumbleProto.UserRemove;
import net.sf.mumble.MumbleProto.UserState;
import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.google.protobuf.ByteString;
import com.morlunk.mumbleclient.Globals;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.service.audio.AudioOutput;
import com.morlunk.mumbleclient.service.audio.AudioOutputHost;
import com.morlunk.mumbleclient.service.model.Channel;
import com.morlunk.mumbleclient.service.model.Message;
import com.morlunk.mumbleclient.service.model.User;

@SuppressLint("UseSparseArrays")
public class MumbleProtocol {
	public enum MessageType {
		// TODO find out what 'Unknown' is. Without that extra enum value, servers will crash.
		Version, UDPTunnel, Authenticate, Ping, Reject, ServerSync, ChannelRemove, ChannelState, UserRemove, UserState, BanList, TextMessage, PermissionDenied, ACL, QueryUsers, CryptSetup, ContextActionAdd, ContextAction, UserList, VoiceTarget, PermissionQuery, CodecVersion, UserStats, RequestBlob, SuggestConfig, Unknown
	}
	
	public enum DisconnectReason {
		Generic, Kick, Reject
	}

	public static final int UDPMESSAGETYPE_UDPVOICECELTALPHA = 0;
	public static final int UDPMESSAGETYPE_UDPPING = 1;
	public static final int UDPMESSAGETYPE_UDPVOICESPEEX = 2;
	public static final int UDPMESSAGETYPE_UDPVOICECELTBETA = 3;
	public static final int UDPMESSAGETYPE_UDPVOICEOPUS = 4;

	public static final int CODEC_NOCODEC = -1;
	public static final int CODEC_ALPHA = UDPMESSAGETYPE_UDPVOICECELTALPHA;
	public static final int CODEC_BETA = UDPMESSAGETYPE_UDPVOICECELTBETA;
	public static final int CODEC_OPUS = UDPMESSAGETYPE_UDPVOICEOPUS;
	
	public static final int SAMPLE_RATE = 48000;
	public static final int FRAME_SIZE = SAMPLE_RATE / 100;

	/**
	 * The time window during which the last successful UDP ping must have been
	 * transmitted. If the time since the last successful UDP ping is greater
	 * than this treshold the connection falls back on TCP tunneling.
	 *
	 * NOTE: This is the time when the last successfully received ping was SENT
	 * by the client.
	 *
	 * 6000 gives 1 second reply-time as the ping interval is 5000 seconds
	 * currently.
	 */
	public static final int UDP_PING_TRESHOLD = 6000;

	//private static final MessageType[] MT_CONSTANTS = MessageType.class.getEnumConstants();

	public Map<Integer, Channel> channels = new HashMap<Integer, Channel>();
	public Map<Integer, User> users = new HashMap<Integer, User>();
	public Map<Integer, List<User>> channelMap = new HashMap<Integer, List<User>>();
	public Channel currentChannel = null;
	public User currentUser = null;
	public boolean canSpeak = true;
	public int codec = CODEC_NOCODEC;
	private final AudioOutputHost audioHost;
	private final Context ctx;

	private AudioOutput ao;
	private Thread audioOutputThread;
	private Thread pingThread;

	private final MumbleProtocolHost host;
	private final MumbleConnection conn;

	private boolean stopped = false;

	public MumbleProtocol(
		final MumbleProtocolHost host,
		final AudioOutputHost audioHost,
		final MumbleConnection connection,
		final Context ctx) {
		this.host = host;
		this.audioHost = audioHost;
		this.conn = connection;
		this.ctx = ctx;

		this.host.setSynchronized(false);
	}

	public final void joinChannel(final int channelId) {
		if(currentUser != null) {
			final UserState.Builder us = UserState.newBuilder();
			us.setSession(currentUser.session);
			us.setChannelId(channelId);
			conn.sendTcpMessage(MessageType.UserState, us);
		}
	}

	public void registerUser(User user) {
		UserState.Builder stateBuilder = UserState.newBuilder();
		stateBuilder.setSession(user.session);
		stateBuilder.setUserId(0);
		conn.sendTcpMessage(MessageType.UserState, stateBuilder);
	}
	
	public void sendAccessTokens(List<String> tokens) {
		Authenticate.Builder authenticate = Authenticate.newBuilder();
		authenticate.addAllTokens(tokens);
		conn.sendTcpMessage(MessageType.Authenticate, authenticate);
	}
	
	/**
	 * Goes up the channel tree from the specified channel and increments the user count.
	 * @param channel
	 * @param userCountChange
	 */
	private void modifyChannelUserCountChain(Channel channel, int userCountChange) {
		channel.userCount += userCountChange;
		for(Channel c : channels.values()) {
			if(channel.parent == c.id)
				modifyChannelUserCountChain(c, userCountChange);
		}
	}

	public void processTcp(final short type, final byte[] buffer)
		throws IOException {
		if (stopped) {
			return;
		}

		final MessageType t = MessageType.values()[type];
		
		Channel channel = null;
		User user = null;

		switch (t) {
		case UDPTunnel:
			processUdp(buffer, buffer.length);
			break;
		case Ping:
			Ping msg = Ping.parseFrom(buffer);

			CryptState cryptState = conn.cryptState;
			cryptState.uiRemoteGood = msg.getGood();
			cryptState.uiRemoteLate = msg.getLate();
			cryptState.uiRemoteLost = msg.getLost();
			cryptState.uiRemoteResync = msg.getResync();
			
			if(((cryptState.uiRemoteGood == 0) || (cryptState.uiGood == 0)) && conn.usingUdp && (conn.getElapsedTime() > 2000) && !conn.forceTcp) {
				// Disable UDP: no ping responses after 2 seconds
				conn.usingUdp = false;
				Log.i(Globals.LOG_TAG, "UDP packets cannot be sent to or received from the server. Switching to TCP mode.");
				// Mumble protocol docs say we should send a blank UDP tunnel packet to tell the server we want UDP tunneling.
				UDPTunnel.Builder tunnelBuilder = UDPTunnel.newBuilder();
				tunnelBuilder.setPacket(ByteString.EMPTY);
				conn.sendTcpMessage(MessageType.UDPTunnel, tunnelBuilder);
			} else if(!conn.usingUdp && (cryptState.uiRemoteGood > 3) && (cryptState.uiGood > 3) && !conn.forceTcp) {
				// Enable UDP: received 3 good pings
				conn.usingUdp = true;
				Log.i(Globals.LOG_TAG, "UDP packets can be sent and received from the server. Switching to UDP mode.");
			}
			
			break;
		case CodecVersion:
			final boolean oldCanSpeak = canSpeak;
			final boolean opusSupported = !Settings.getInstance(ctx).isOpusDisabled();
			final CodecVersion codecVersion = CodecVersion.parseFrom(buffer);
			codec = CODEC_NOCODEC;
			if(codecVersion.hasOpus() &&
					  codecVersion.getOpus() &&
					  opusSupported) {
				Log.i(Globals.LOG_TAG, "Experimental opus support enabled.");
				codec = CODEC_OPUS;
			} else if (codecVersion.hasAlpha() &&
				codecVersion.getAlpha() == Globals.CELT_VERSION) {
				codec = CODEC_ALPHA;
			} else if (codecVersion.hasBeta() &&
					   codecVersion.getBeta() == Globals.CELT_VERSION) {
				codec = CODEC_BETA;
			}
			
			canSpeak = canSpeak && (codec != CODEC_NOCODEC);

			if (canSpeak != oldCanSpeak) {
				host.currentUserUpdated();
			}

			break;
		case Reject:
			final Reject reject = Reject.parseFrom(buffer);
			host.setReject(reject);
			Log.e(Globals.LOG_TAG, String.format(
				"Received Reject message: %s",
				reject.getReason()));
			break;
		case ServerSync:
			final ServerSync ss = ServerSync.parseFrom(buffer);
			
			boolean resync = currentUser != null;
			if(resync)
				Log.i(Globals.LOG_TAG, "We received a second ServerSync message.");

			currentUser = findUser(ss.getSession());
			currentUser.isCurrent = true;
			currentChannel = currentUser.getChannel();
			
			// Perform one-time synchronization operations. Will not be called if we get >1 ServerSync messages.
			if(!resync) {
				pingThread = new Thread(new PingThread(conn), "Ping");
				pingThread.start();
				
				if(conn.forceTcp) {
					// Mumble protocol docs say we should send a blank UDP tunnel packet to tell the server we want UDP tunneling.
					UDPTunnel.Builder tunnelBuilder = UDPTunnel.newBuilder();
					tunnelBuilder.setPacket(ByteString.EMPTY);
					conn.sendTcpMessage(MessageType.UDPTunnel, tunnelBuilder);
				}
                setupAudioOutput(false);
			}
			
			Log.d(Globals.LOG_TAG, ">>> " + t);

			final UserState.Builder usb = UserState.newBuilder();
			usb.setSession(currentUser.session);
			conn.sendTcpMessage(MessageType.UserState, usb);

			host.setSynchronized(true);

			host.currentChannelChanged();
			host.currentUserUpdated();
			break;
		case ChannelState:
			final ChannelState cs = ChannelState.parseFrom(buffer);
			channel = findChannel(cs.getChannelId());
			boolean channelAdded = channel == null;
			if(channelAdded)
				channel = new Channel();
			
			channel.id = cs.getChannelId();
			
			if(cs.hasName())
				channel.name = cs.getName();
			if(cs.hasParent())
				channel.parent = cs.getParent();
			if(cs.hasPosition())
				channel.position = cs.getPosition();
			if(cs.hasDescription())
				channel.description = cs.getDescription();
			if(cs.hasDescriptionHash()) {
				channel.descriptionHash = cs.getDescriptionHash();
				channel.description = null;
			}
			
			if(channelAdded) {
				channels.put(channel.id, channel);
				channelMap.put(channel.id, new ArrayList<User>());
				host.channelAdded(channel);
			} else {
				if(cs.hasPosition())
					host.channelMoved(channel);
				host.channelUpdated(channel);
			}
			break;
		case ChannelRemove:
			final ChannelRemove cr = ChannelRemove.parseFrom(buffer);
			channel = findChannel(cr.getChannelId());
			channel.removed = true;
			channels.remove(channel.id);
			channelMap.remove(channel.id);
			host.channelRemoved(channel.id);
			break;
		case UserState:
			final UserState us = UserState.parseFrom(buffer);
			user = findUser(us.getSession());
			
			host.userStateUpdated(user, us);

			boolean added = false;
			boolean currentUserUpdated = false;
			boolean channelUpdated = false;

			if (user == null) {
				user = new User();
				user.session = us.getSession();
				users.put(user.session, user);
				added = true;
			}
			
			if(us.hasUserId())
				user.isRegistered = true;

			if (us.hasSelfDeaf()) {
				user.selfDeafened = us.getSelfDeaf();
			}
			
			if (us.hasSelfMute()) {
				user.selfMuted = us.getSelfMute();
			}

			if (us.hasMute()) {
				user.serverMuted = us.getMute();
			}

			if (us.hasDeaf()) {
				user.serverDeafened = us.getDeaf();
				user.serverMuted |= user.serverDeafened;
			}

			if (us.hasSuppress()) {
				user.suppressed = us.getSuppress();
			}

			if (us.hasName()) {
				user.name = us.getName();
			}
			
			if(us.hasComment()) {
				user.comment = us.getComment();
			}
			
			if(us.hasCommentHash()) {
				user.commentHash = us.getCommentHash();
				user.comment = null;
			}

			if (added || us.hasChannelId()) {
				Channel userChannel = channels.get(us.getChannelId());
				
				if(user.getChannel() != null) {
					// Remove from old channel
					modifyChannelUserCountChain(user.getChannel(), -1);
					List<User> channelUsers = channelMap.get(user.getChannel().id);
					channelUsers.remove(user);
				}
				
				modifyChannelUserCountChain(userChannel, 1);
				user.setChannel(userChannel);
				// Add to new channel
				List<User> channelUsers = channelMap.get(userChannel.id);
				channelUsers.add(user);
				sortChannelUsers(channelUsers);
				
				channelUpdated = true;
			}

			// If this is the current user, do extra updates on local state.
			if (currentUser != null && us.getSession() == currentUser.session) {
				if (us.hasMute() || us.hasSuppress()) {
					// TODO: Check the logic
					// Currently Mute+Suppress true -> Either of them false results
					// in canSpeak = true
					if (us.hasMute()) {
						canSpeak = (codec != CODEC_NOCODEC) && !us.getMute();
					}
					if (us.hasSuppress()) {
						canSpeak = (codec != CODEC_NOCODEC) &&
								   !us.getSuppress();
					}
				}

				currentUserUpdated = true;
			}

			if (channelUpdated) {
				host.channelUpdated(user.getChannel());
			}

			if (added) {
				host.userAdded(user);
			} else {
				host.userUpdated(user);
			}

			if (currentUserUpdated) {
				host.currentUserUpdated();
			}
			if (currentUserUpdated && channelUpdated) {
				currentChannel = user.getChannel();
				host.currentChannelChanged();
			}
			break;
		case PermissionDenied:
			PermissionDenied denied = PermissionDenied.parseFrom(buffer);
			host.permissionDenied(denied.getReason(), denied.getType().getNumber());
			break;
		case UserRemove:
			final UserRemove ur = UserRemove.parseFrom(buffer);
			user = findUser(ur.getSession());
			
			if(user != null) {
				if(user.equals(currentUser)) {
					host.setKick(ur); // Kicked.
					break;
				}
				users.remove(user.session);
				
				// Remove the user from the channel as well.
				modifyChannelUserCountChain(user.getChannel(), -1);
				List<User> channelUsers = channelMap.get(user.getChannel().id);
				channelUsers.remove(user);

				host.channelUpdated(user.getChannel());
				host.userRemoved(user.session, ur);
			}
			break;
		case TextMessage:
			handleTextMessage(TextMessage.parseFrom(buffer));
			break;
		case CryptSetup:
			final CryptSetup cryptsetup = CryptSetup.parseFrom(buffer);

			Log.d(Globals.LOG_TAG, "MumbleConnection: CryptSetup");

			if (cryptsetup.hasKey() && cryptsetup.hasClientNonce() &&
				cryptsetup.hasServerNonce()) {
				// Full key setup
				conn.cryptState.setKeys(
					cryptsetup.getKey().toByteArray(),
					cryptsetup.getClientNonce().toByteArray(),
					cryptsetup.getServerNonce().toByteArray());
			}
			break;
		default:
			Log.w(Globals.LOG_TAG, "unhandled message type " + t);
		}
	}

    public void setupAudioOutput(boolean bluetoothConnected) {
        if(ao != null && audioOutputThread != null) {
            ao.stop();
            try {
                audioOutputThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        ao = new AudioOutput(ctx, audioHost, bluetoothConnected);
        audioOutputThread = new Thread(ao, "audio output");
        audioOutputThread.start();
    }
	
	private void sortChannelUsers(List<User> users) {
		// Sort alphabetically
		Collections.sort(users, new Comparator<User>() {
			@Override
			public int compare(User lhs, User rhs) {
				return lhs.name.toLowerCase(Locale.getDefault()).compareTo(rhs.name.toLowerCase(Locale.getDefault()));
			}
		});;
	}

	public void processUdp(final byte[] buffer, final int length) {
		
		if (stopped) {
			return;
		}

		final int type = buffer[0] >> 5 & 0x7;
		if (type == UDPMESSAGETYPE_UDPPING) {
			/*
			final long timestamp = ((long) (buffer[1] & 0xFF) << 56) |
								   ((long) (buffer[2] & 0xFF) << 48) |
								   ((long) (buffer[3] & 0xFF) << 40) |
								   ((long) (buffer[4] & 0xFF) << 32) |
								   ((long) (buffer[5] & 0xFF) << 24) |
								   ((long) (buffer[6] & 0xFF) << 16) |
								   ((long) (buffer[7] & 0xFF) << 8) |
								   ((buffer[8] & 0xFF));

			conn.refreshUdpLimit(timestamp + UDP_PING_TRESHOLD);
			*/
		} else {
			processVoicePacket(buffer);
		}
	}

	public final void sendChannelTextMessage(
		final String message,
		final Channel channel) {
		final TextMessage.Builder tmb = TextMessage.newBuilder();
		tmb.addChannelId(channel.id);
		tmb.setMessage(message);
		conn.sendTcpMessage(MessageType.TextMessage, tmb);

		final Message msg = new Message();
		msg.timestamp = System.currentTimeMillis();
		msg.message = message;
		msg.channel = channel;
		msg.direction = Message.DIRECTION_SENT;
		host.messageSent(msg);
	}

	public void sendUserTestMessage(String message, User chatTarget) {
		final TextMessage.Builder tmb = TextMessage.newBuilder();
		tmb.addSession(chatTarget.session);
		tmb.setMessage(message);
		conn.sendTcpMessage(MessageType.TextMessage, tmb);

		final Message msg = new Message();
		msg.timestamp = System.currentTimeMillis();
		msg.message = message;
		msg.target = chatTarget;
		msg.direction = Message.DIRECTION_SENT;
		host.messageSent(msg);
	}

	public void stop() {
		stopped = true;
		stopThreads();
	}

	private Channel findChannel(final int id) {
		return channels.get(id);
	}

	private User findUser(final int session_) {
		return users.get(session_);
	}

	private void handleTextMessage(final TextMessage ts) {
		User u = null;
		if (ts.hasActor()) {
			u = findUser(ts.getActor());
		}

		final Message msg = new Message();
		msg.timestamp = System.currentTimeMillis();
		msg.message = ts.getMessage();
		msg.actor = u;
		msg.direction = Message.DIRECTION_RECEIVED;
		msg.channelIds = ts.getChannelIdCount();
		msg.treeIds = ts.getTreeIdCount();
		host.messageReceived(msg);
	}

	private void processVoicePacket(final byte[] buffer) {
		final int type = buffer[0] >> 5 & 0x7;
		final int flags = buffer[0] & 0x1f;

		// There is no speex support...
		if (type != UDPMESSAGETYPE_UDPVOICECELTALPHA &&
			type != UDPMESSAGETYPE_UDPVOICECELTBETA &&
			type != UDPMESSAGETYPE_UDPVOICEOPUS) {
			return;
		}

		// Don't try to decode the unsupported codec version.
		if (type != codec) {
			return;
		}

        Log.v(Globals.LOG_TAG, "Packet data: "+ Arrays.toString(buffer));

		final PacketDataStream pds = new PacketDataStream(buffer);
		// skip type / flags
		pds.skip(1);

		final long session = pds.readLong();
        final long sequence = pds.readLong();

		final User u = findUser((int) session);
		if (u == null) {
			Log.e(Globals.LOG_TAG, "User session " + session + " not found!");

			// This might happen if user leaves while there are still UDP packets
			// en route to the clients. In this case we should just ignore these
			// packets.
			return;
		}

        byte[] audioData = new byte[pds.left()+1];
        audioData[0] = (byte)flags;
        pds.dataBlock(audioData, 1, pds.left());

        if(!pds.isValid()) {
            Log.e(Globals.LOG_TAG, "Invalid PacketDataStream when processing voice packet!");
            return;
        }

		if(ao != null)
			ao.addFrameToBuffer(u, audioData, sequence, type);
	}

	private void stopThreads() {
		if (ao != null) {
			ao.stop();
			try {
				audioOutputThread.join();
			} catch (final InterruptedException e) {
				Log.e(
					Globals.LOG_TAG,
					"Interrupted while waiting for audio thread to end",
					e);
			}
		}

		if (pingThread != null) {
			pingThread.interrupt();
			try {
				pingThread.join();
			} catch (final InterruptedException e) {
				Log.e(
					Globals.LOG_TAG,
					"Interrupted while waiting for ping thread to end",
					e);
			}
		}
	}

}
