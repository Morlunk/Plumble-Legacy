package com.morlunk.mumbleclient;

import java.util.Observable;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

/**
 * Singleton settings class for universal access to the app's preferences.
 * You can listen to Settings events by registering your class as an observer and responding according to the observer key passed.
 * 
 * @author morlunk
 *
 */
public class Settings extends Observable {
	// If you can't find a specifically observable key here, listen for "all".
	public static final String OBSERVER_KEY_ALL = "all";
	public static final String OBSERVER_KEY_MUTED_AND_DEAFENED = "mutedAndDeafened";
	public static final String OBSERVER_KEY_CERTIFICATE_PATH = "certificatePath";
	public static final String OBSERVER_KEY_AMPLITUDE = "certificatePath";
	
	public static final String PREF_CALL_MODE = "callMode";
	public static final String ARRAY_CALL_MODE_SPEAKER = "speakerphone";
	public static final String ARRAY_CALL_MODE_VOICE = "voice";
	
	public static final String PREF_METHOD = "audioInputMethod";
	public static final String ARRAY_METHOD_VOICE = "voiceActivity";
	public static final String ARRAY_METHOD_PTT = "ptt";
	
	public static final String PREF_THRESHOLD = "detectionThreshold";
	public static final Integer DEFAULT_THRESHOLD = 1400;
	
	public static final String PREF_PUSH_KEY = "talkKey";
	public static final Integer DEFAULT_PUSH_KEY = -1;
	
	public static final String PREF_HOT_CORNER_KEY = "hotCorner";
	public static final String ARRAY_HOT_CORNER_NONE = "none";
	public static final String ARRAY_HOT_CORNER_TOP_LEFT = "topLeft";
	public static final String ARRAY_HOT_CORNER_BOTTOM_LEFT = "bottomLeft";
	public static final String ARRAY_HOT_CORNER_TOP_RIGHT = "topRight";
	public static final String ARRAY_HOT_CORNER_BOTTOM_RIGHT = "bottomRight";
	public static final String DEFAULT_HOT_CORNER = ARRAY_HOT_CORNER_NONE;
	
	public static final String PREF_PUSH_BUTTON_HIDE_KEY = "hidePtt";
	public static final Boolean DEFAULT_PUSH_BUTTON_HIDE = false;
	
	public static final String PREF_PTT_TOGGLE = "togglePtt";
	public static final Boolean DEFAULT_PTT_TOGGLE = false;
	
	public static final String PREF_QUALITY = "quality";
	public static final String DEFAULT_QUALITY = "48000";
	
	public static final String PREF_AMPLITUDE_BOOST = "amplitudeBoost";
	public static final Float DEFAULT_AMPLITUDE_BOOST = 0f;
	
	public static final String PREF_CHAT_NOTIFY = "chatNotify";
	public static final Boolean DEFAULT_CHAT_NOTIFY = true;
	
	public static final String PREF_USE_TTS = "useTts";
	public static final Boolean DEFAULT_USE_TTS = true;
	
	public static final String PREF_THEME = "theme";
	public static final String ARRAY_THEME_LIGHTDARK = "lightDark";
	public static final String ARRAY_THEME_DARK = "dark";
	
	public static final String PREF_GENERATE_CERT = "certificateGenerate";
	
	public static final String PREF_CERT = "certificatePath";
	public static final String PREF_CERT_PASSWORD = "certificatePassword";

	public static final String PREF_CHANNELLIST_ROW_HEIGHT = "channellistrowheight";
	public static final String DEFAULT_CHANNELLIST_ROW_HEIGHT = "35";

	public static final String PREF_COLORIZE_CHANNELLIST = "colorizechannellist";
	public static final Boolean DEFAULT_COLORIZE_CHANNELLIST = false;

	public static final String PREF_COLORIZE_THRESHOLD = "colorthresholdnumusers";
	public static final String DEFAULT_COLORIZE_THRESHOLD = "5";

	public static final String PREF_FORCE_TCP = "forceTcp";
	public static final Boolean DEFAULT_FORCE_TCP = false;
	
	public static final String PREF_DISABLE_OPUS = "disableOpus";
	public static final Boolean DEFAULT_DISABLE_OPUS = false;
	
	public static final String PREF_MUTED = "muted";
	public static final Boolean DEFAULT_MUTED = false;
	
	public static final String PREF_DEAFENED = "deafened";
	public static final Boolean DEFAULT_DEAFENED = false;
	
	private final SharedPreferences preferences;

	private static Settings settings;
	
	public static Settings getInstance(Context context) {
		if(settings == null)
			settings = new Settings(context);
		return settings;
	}
	
	private Settings(final Context ctx) {
		preferences = PreferenceManager.getDefaultSharedPreferences(ctx);
	}
	
	public String getCallMode() {
		return preferences.getString(PREF_CALL_MODE, ARRAY_CALL_MODE_SPEAKER);
	}

	public int getAudioQuality() {
		return Integer.parseInt(preferences.getString(Settings.PREF_QUALITY, DEFAULT_QUALITY));
	}
	
	public float getAmplitudeBoostMultiplier() {
		return preferences.getFloat(Settings.PREF_AMPLITUDE_BOOST, DEFAULT_AMPLITUDE_BOOST);
	}
	
	public void setAmplitudeBoostMultiplier(Float multiplier) {
		preferences.edit().putFloat(PREF_AMPLITUDE_BOOST, multiplier).commit();
		setChanged();
		notifyObservers(OBSERVER_KEY_AMPLITUDE);
	}
	
	public int getDetectionThreshold() {
		return preferences.getInt(PREF_THRESHOLD, DEFAULT_THRESHOLD);
	}
	
	public int getPushToTalkKey() {
		return preferences.getInt(PREF_PUSH_KEY, DEFAULT_PUSH_KEY);
	}
	
	public String getHotCorner() {
		return preferences.getString(PREF_HOT_CORNER_KEY, DEFAULT_HOT_CORNER);
	}
	
	public String getTheme() {
		return preferences.getString(PREF_THEME, ARRAY_THEME_LIGHTDARK);
	}

	public String getCertificatePath() {
		return preferences.getString(PREF_CERT, null);
	}
	
	public void setCertificatePath(String path) {
		preferences.edit().putString(PREF_CERT, path).commit();
		setChanged();
		notifyObservers(OBSERVER_KEY_CERTIFICATE_PATH);
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
	
	public boolean isPushToTalkToggle() {
		return preferences.getBoolean(PREF_PTT_TOGGLE, DEFAULT_PTT_TOGGLE);
	}
	
	public boolean isPushToTalkButtonShown() {
		return !preferences.getBoolean(PREF_PUSH_BUTTON_HIDE_KEY, DEFAULT_PUSH_BUTTON_HIDE);
	}
	
	public boolean isChatNotifyEnabled() {
		return preferences.getBoolean(PREF_CHAT_NOTIFY, DEFAULT_CHAT_NOTIFY);
	}
	
	public boolean isTextToSpeechEnabled() {
		return preferences.getBoolean(PREF_USE_TTS, DEFAULT_USE_TTS);
	}

	public int getChannelListRowHeight() {
		return Integer.parseInt(preferences.getString(
				PREF_CHANNELLIST_ROW_HEIGHT,
				DEFAULT_CHANNELLIST_ROW_HEIGHT));
	}

	public Boolean getChannellistColorized() {
		return preferences.getBoolean(Settings.PREF_COLORIZE_CHANNELLIST,
				DEFAULT_COLORIZE_CHANNELLIST);
	}

	public int getColorizeThreshold() {
		return Integer.parseInt(preferences.getString(
				Settings.PREF_COLORIZE_THRESHOLD, DEFAULT_COLORIZE_THRESHOLD));
	}
	
	public boolean isTcpForced() {
		return preferences.getBoolean(PREF_FORCE_TCP, DEFAULT_FORCE_TCP);
	}
	
	public boolean isOpusDisabled() {
		return preferences.getBoolean(PREF_DISABLE_OPUS, DEFAULT_DISABLE_OPUS);
	}
	
	public boolean isMuted() {
		return preferences.getBoolean(PREF_MUTED, DEFAULT_MUTED);
	}
	
	public boolean isDeafened() {
		return preferences.getBoolean(PREF_DEAFENED, DEFAULT_DEAFENED);
	}
	
	public void setMutedAndDeafened(boolean muted, boolean deafened) {
		Editor editor = preferences.edit();
		editor.putBoolean(PREF_MUTED, muted || deafened);
		editor.putBoolean(PREF_DEAFENED, deafened);
		editor.commit();
		setChanged();
		notifyObservers(OBSERVER_KEY_MUTED_AND_DEAFENED);
	}
	
	public void forceUpdateObservers() {
		setChanged();
		notifyObservers(OBSERVER_KEY_ALL);
	}
}
