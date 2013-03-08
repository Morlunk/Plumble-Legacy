package com.morlunk.mumbleclient.app;

import java.io.File;
import java.io.FileFilter;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.service.MumbleService;
import com.morlunk.mumbleclient.service.PlumbleCertificateManager;

/**
 * Hackish interface so we can reuse code between PreferenceActivity and PreferenceFragment.
 * All we need is findPreference.
 * @author morlunk
 */
interface PreferenceProvider {
	public Preference findPreference(CharSequence key);
}

public class Preferences extends SherlockPreferenceActivity implements PreferenceProvider {

	// A list of preference keys that can't be changed when connected to a server.
	private static final String[] IMMUTABLE_WHEN_CONNECTED = new String[] {
		Settings.PREF_GENERATE_CERT,
		Settings.PREF_CERT,
		Settings.PREF_CERT_PASSWORD,
		Settings.PREF_THEME,
		Settings.PREF_QUALITY,
		Settings.PREF_DISABLE_OPUS,
		Settings.PREF_FORCE_TCP
	};
	
	private static final String CERTIFICATE_GENERATE_KEY = "certificateGenerate";
	private static final String CERTIFICATE_PATH_KEY = "certificatePath";
	private static final String CERTIFICATE_FOLDER = "Plumble";
	private static final String CERTIFICATE_EXTENSION = "p12";
	
	private static final String AUDIO_INPUT_KEY = "audioInputMethod";
	private static final String PTT_SETTINGS_KEY = "pttSettings";
	private static final String VOICE_SETTINGS_KEY = "voiceActivitySettings";

	private static Context context;
	
	@SuppressLint("NewApi")
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Preferences.context = this;
		
		if(android.os.Build.VERSION.SDK_INT >= 11) {
			getFragmentManager().beginTransaction().replace(android.R.id.content, new PreferencesFragment()).commit();
		} else {
			addPreferencesFromResource(R.xml.preferences);
			configurePreferences(this);
		}
	}
	
	@Override
	protected void onDestroy() {
		// Notify of settings changes
		Settings.getInstance(this).forceUpdateObservers();
		
		super.onDestroy();
	}

	public static Context getContext() {
		return Preferences.context;
	}
	
	/**
	 * Sets up all necessary programmatic preference modifications.
	 * @param preferenceProvider
	 */
	private static void configurePreferences(final PreferenceProvider preferenceProvider) {
		// Disable options that are immutable when connected
		if(MumbleService.getCurrentService() != null && MumbleService.getCurrentService().isConnected()) {
			for(String key : IMMUTABLE_WHEN_CONNECTED) {
				Preference preference = preferenceProvider.findPreference(key);
				preference.setEnabled(false);
				preference.setSummary(R.string.preferences_immutable);
			}
		}
		
		final Preference certificateGeneratePreference = preferenceProvider.findPreference(CERTIFICATE_GENERATE_KEY);
		final ListPreference certificatePathPreference = (ListPreference) preferenceProvider.findPreference(CERTIFICATE_PATH_KEY);
		
		certificateGeneratePreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				generateCertificate(certificatePathPreference);
				return true;
			}
		});

		final ListPreference inputPreference = (ListPreference) preferenceProvider.findPreference(AUDIO_INPUT_KEY);
		updateAudioInput(preferenceProvider, inputPreference.getValue());
		inputPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				updateAudioInput(preferenceProvider, (String) newValue);
				return true;
			}
		});
		
		// Make sure media is mounted, otherwise do not allow certificate loading.
		if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			try {
				updateCertificatePath(certificatePathPreference);
			} catch(NullPointerException exception) {
				certificatePathPreference.setEnabled(false);
				certificatePathPreference.setSummary(R.string.externalStorageUnavailable);
			}
		} else {
			certificatePathPreference.setEnabled(false);
			certificatePathPreference.setSummary(R.string.externalStorageUnavailable);
		}
	}
	
	private static void updateAudioInput(PreferenceProvider preferenceProvider, String newValue) {		
		Preference pttSettingsPreference = preferenceProvider.findPreference(PTT_SETTINGS_KEY);
		Preference voiceSettingsPreference = preferenceProvider.findPreference(VOICE_SETTINGS_KEY);

		boolean pushToTalk = newValue.equals(Settings.ARRAY_METHOD_PTT);
		pttSettingsPreference.setEnabled(pushToTalk);
		voiceSettingsPreference.setEnabled(!pushToTalk);
	}
	
	/**
	 * Updates the passed preference with the certificate paths found on external storage.
	 * @param preference The ListPreference to update.
	 */
	private static void updateCertificatePath(ListPreference preference) throws NullPointerException {
		File externalStorageDirectory = Environment.getExternalStorageDirectory();
		File plumbleFolder = new File(externalStorageDirectory, CERTIFICATE_FOLDER);
		
		if(!plumbleFolder.exists()) {
			plumbleFolder.mkdir();
		}
		
		List<File> certificateFiles = Arrays.asList(plumbleFolder.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.getName().endsWith(CERTIFICATE_EXTENSION);
			}
		}));
		
		// Get arrays of certificate paths and names.
		String[] certificatePaths = new String[certificateFiles.size()+1]; // Extra space for 'None' option
		for(int x=0;x<certificateFiles.size();x++) {
			certificatePaths[x] = certificateFiles.get(x).getPath();
		}
		certificatePaths[certificatePaths.length-1] = "";
		
		String[] certificateNames = new String[certificateFiles.size()+1]; // Extra space for 'None' option
		for(int x=0;x<certificateFiles.size();x++) {
			certificateNames[x] = certificateFiles.get(x).getName();
		}
		certificateNames[certificateNames.length-1] = getContext().getResources().getString(R.string.noCert);
		
		preference.setEntries(certificateNames);
		preference.setEntryValues(certificatePaths);
	}
	
	/**
	 * Generates a new certificate and sets it as active.
	 * @param certificateList If passed, will update the list of certificates available. Messy.
	 */
	private static void generateCertificate(final ListPreference certificateList) {
		AsyncTask<File, Void, X509Certificate> task = new AsyncTask<File, Void, X509Certificate>() {
			
			private ProgressDialog loadingDialog;
			private File certificatePath;
			
			@Override
			protected void onPreExecute() {
				super.onPreExecute();
				
				loadingDialog = new ProgressDialog(getContext());
				loadingDialog.setIndeterminate(true);
				loadingDialog.setMessage(getContext().getResources().getString(R.string.generateCertProgress));
				loadingDialog.setOnCancelListener(new OnCancelListener() {
					
					@Override
					public void onCancel(DialogInterface arg0) {
						cancel(true);
						
					}
				});
				loadingDialog.show();
			}
			@Override
			protected X509Certificate doInBackground(File... params) {
				certificatePath = params[0];
				try {
					X509Certificate certificate = PlumbleCertificateManager.createCertificate(certificatePath);
					
					Settings settings = Settings.getInstance(getContext());
					settings.setCertificatePath(certificatePath.getAbsolutePath());
					return certificate;
				} catch (Exception e) {
					e.printStackTrace();
					return null;
				}
			}
			
			@Override
			protected void onPostExecute(X509Certificate result) {
				super.onPostExecute(result);
				if(result != null) {
					if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
						updateCertificatePath(certificateList); // Update cert path after
						certificateList.setValue(certificatePath.getAbsolutePath());
					}
					
					Toast.makeText(getContext(), context.getString(R.string.generateCertSuccess, certificatePath.getName()), Toast.LENGTH_SHORT).show();
				} else {
					Toast.makeText(getContext(), R.string.generateCertFailure, Toast.LENGTH_SHORT).show();
				}
				
				loadingDialog.dismiss();
			}
			
		};
		File externalStorageDirectory = Environment.getExternalStorageDirectory();
		File plumbleFolder = new File(externalStorageDirectory, CERTIFICATE_FOLDER);
		if(!plumbleFolder.exists()) {
			plumbleFolder.mkdir();
		}
		File certificatePath = new File(plumbleFolder, String.format("plumble-%d.p12", (int) (System.currentTimeMillis() / 1000L)));
		task.execute(certificatePath);
	}
	
	@TargetApi(11)
	public static class PreferencesFragment extends PreferenceFragment implements PreferenceProvider {
		
		/* (non-Javadoc)
		 * @see android.support.v4.app.Fragment#onCreate(android.os.Bundle)
		 */
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			
			addPreferencesFromResource(R.xml.preferences);
			
			configurePreferences(this);
		}
	}
}
