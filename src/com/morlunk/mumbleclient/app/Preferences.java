package com.morlunk.mumbleclient.app;

import java.io.File;
import java.util.List;

import android.os.Bundle;
import android.os.Environment;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.service.PlumbleCertificateGenerateTask;
import com.morlunk.mumbleclient.service.PlumbleCertificateManager;

@SuppressWarnings("deprecation")
public class Preferences extends SherlockPreferenceActivity {

	public static final String EXTRA_CONNECTED = "connected";
	
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
	
	private boolean connected = false;
	
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Bundle bundle = getIntent().getExtras();
		if(bundle != null)
			connected = bundle.getBoolean(EXTRA_CONNECTED);
		
		//if(android.os.Build.VERSION.SDK_INT >= 11) {
		//	getFragmentManager().beginTransaction().replace(android.R.id.content, new PreferencesFragment()).commit();
		//} else {
			addPreferencesFromResource(R.xml.preferences);
			configurePreferences();
		//}
	}
	
	@Override
	protected void onDestroy() {
		// Notify of settings changes
		Settings.getInstance(this).forceUpdateObservers();
		
		super.onDestroy();
	}
	
	/**
	 * Sets up all necessary programmatic preference modifications.
	 */
	private void configurePreferences() {
		// Disable options that are immutable when connected
		if(connected) {
			for(String key : IMMUTABLE_WHEN_CONNECTED) {
				Preference preference = findPreference(key);
				preference.setEnabled(false);
				preference.setSummary(R.string.preferences_immutable);
			}
		}
		
		final Preference certificateGeneratePreference = findPreference(CERTIFICATE_GENERATE_KEY);
		final ListPreference certificatePathPreference = (ListPreference) findPreference(CERTIFICATE_PATH_KEY);
		
		certificateGeneratePreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				generateCertificate(certificatePathPreference);
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
	
	/**
	 * Updates the passed preference with the certificate paths found on external storage.
	 * @param preference The ListPreference to update.
	 */
	private void updateCertificatePath(ListPreference preference) throws NullPointerException {
		List<File> certificateFiles = PlumbleCertificateManager.getAvailableCertificates();
		
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
		certificateNames[certificateNames.length-1] = getResources().getString(R.string.noCert);
		
		preference.setEntries(certificateNames);
		preference.setEntryValues(certificatePaths);
	}
	
	/**
	 * Generates a new certificate and sets it as active.
	 * @param certificateList If passed, will update the list of certificates available. Messy.
	 */
	private void generateCertificate(final ListPreference certificateList) {
		PlumbleCertificateGenerateTask generateTask = new PlumbleCertificateGenerateTask(this) {
			@Override
			protected void onPostExecute(File result) {
				super.onPostExecute(result);
				
				if(result != null) {
					updateCertificatePath(certificateList); // Update cert path after
					certificateList.setValue(result.getAbsolutePath());
				}
			}
		};
		generateTask.execute();
	}
	
	/*
	class PreferencesFragment extends PreferenceFragment implements PreferenceProvider {
		
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			
			addPreferencesFromResource(R.xml.preferences);
			
			configurePreferences(this);
		}
	}
	*/
}
