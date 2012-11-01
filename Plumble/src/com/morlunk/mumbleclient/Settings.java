package com.morlunk.mumbleclient;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Settings {
	public static final String PREF_CALL_MODE = "callMode";
	public static final String ARRAY_CALL_MODE_SPEAKER = "speakerphone";
	public static final String ARRAY_CALL_MODE_VOICE = "voice";
	
	public enum PlumbleCallMode {
		VOICE_CALL,
		SPEAKERPHONE
	}
	
	public static final String PREF_METHOD = "audioInputMethod";
	public static final String ARRAY_METHOD_VOICE = "voiceActivity";
	public static final String ARRAY_METHOD_PTT = "ptt";
	
	public static final String PREF_THRESHOLD = "detectionThreshold";
	public static final Integer DEFAULT_THRESHOLD = 1400;
	
	public static final String PREF_PUSH_KEY = "talkKey";
	public static final Integer DEFAULT_PUSH_KEY = -1;
	
	public static final String PREF_QUALITY = "quality";
	public static final String DEFAULT_QUALITY = "60000";
	
	public static final String PREF_THEME = "theme";
	public static final String ARRAY_THEME_LIGHTDARK = "lightDark";
	public static final String ARRAY_THEME_DARK = "dark";
	
	public static final String PREF_CERT = "certificatePath";
	public static final String PREF_CERT_PASSWORD = "certificatePassword";
	
	public static final String PREF_LAST_CHANNEL = "lastChannels";
	public static final String LAST_CHANNEL_PREFIX = "lastChannel_"; // TODO move this to db code or something. It's messy as hell.

	public static final String PREF_CHANNELLIST_ROW_HEIGHT = "channellistrowheight";
	public static final String DEFAULT_CHANNELLIST_ROW_HEIGHT = "30";

	public static final String PREF_COLORIZE_CHANNELLIST = "colorizechannellist";
	public static final String DEFAULT_COLORIZE_CHANNELLIST = "false";

	public static final String PREF_COLORIZE_THRESHOLD = "colorthresholdnumusers";
	public static final String DEFAULT_COLORIZE_THRESHOLD = "5";

	private final SharedPreferences preferences;

	public Settings(final Context ctx) {
		preferences = PreferenceManager.getDefaultSharedPreferences(ctx);
	}
	
	public PlumbleCallMode getCallMode() {
		String callModeValue = preferences.getString(PREF_CALL_MODE, ARRAY_CALL_MODE_SPEAKER);
		if(callModeValue.equals(ARRAY_CALL_MODE_SPEAKER)) {
			return PlumbleCallMode.SPEAKERPHONE;
		} else if(callModeValue.equals(ARRAY_CALL_MODE_VOICE)) {
			return PlumbleCallMode.VOICE_CALL;
		}
		return null;
	}

	public int getAudioQuality() {
		return Integer.parseInt(preferences.getString(Settings.PREF_QUALITY, DEFAULT_QUALITY));
	}
	
	
	public int getDetectionThreshold() {
		return preferences.getInt(PREF_THRESHOLD, DEFAULT_THRESHOLD);
	}
	
	public int getPushToTalkKey() {
		return preferences.getInt(PREF_PUSH_KEY, DEFAULT_PUSH_KEY);
	}
	
	public String getTheme() {
		return preferences.getString(PREF_THEME, ARRAY_THEME_LIGHTDARK);
	}

	public String getCertificatePath() {
		return preferences.getString(PREF_CERT, null);
	}
	
	public String getCertificatePassword() {
		return preferences.getString(PREF_CERT_PASSWORD, "");
	}
	
	public boolean isVoiceActivity() {
		return preferences.getString(PREF_METHOD, ARRAY_METHOD_VOICE).equals(ARRAY_METHOD_VOICE);
	}
	
	public boolean isPushToTalk() {
		return preferences.getString(PREF_METHOD, ARRAY_METHOD_VOICE).equals(ARRAY_METHOD_PTT);
	}
	
	public int getLastChannel(int serverId) {
		return preferences.getInt(String.format("%s%d", LAST_CHANNEL_PREFIX, serverId), -1);
	}
	
	public void setLastChannel(int serverId, int channelId) {
		preferences.edit()
		.putInt(String.format("%s%d", LAST_CHANNEL_PREFIX, serverId), channelId).commit();
	}

	public int getChannelListRowHeight() {
		return Integer.parseInt(preferences.getString(
				Settings.PREF_CHANNELLIST_ROW_HEIGHT,
				DEFAULT_CHANNELLIST_ROW_HEIGHT));
	}

	public Boolean getChannellistColorized() {
		return preferences.getString(Settings.PREF_COLORIZE_CHANNELLIST,
				DEFAULT_COLORIZE_CHANNELLIST).equals("true");
	}

	public int getColorizeThreshold() {
		return Integer.parseInt(preferences.getString(
				Settings.PREF_COLORIZE_THRESHOLD, DEFAULT_COLORIZE_THRESHOLD));
	}
}
