package com.morlunk.mumbleclient.app;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.List;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.morlunk.mumbleclient.R;

public class Preferences extends SherlockPreferenceActivity {
	
	private static final String CERTIFICATE_PATH_KEY = "certificatePath";
	private static final String CERTIFICATE_FOLDER = "Plumble";
	private static final String CERTIFICATE_EXTENSION = "p12";

	private static Context context;
	
	@SuppressLint("NewApi")
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Preferences.context = getApplicationContext();
		
		if(android.os.Build.VERSION.SDK_INT >= 11) {
			getFragmentManager().beginTransaction().replace(android.R.id.content, new PreferencesFragment()).commit();
		} else {
			addPreferencesFromResource(R.xml.preferences);
			
			// Set certificate preference
			ListPreference certificatePathPreference = (ListPreference) findPreference(CERTIFICATE_PATH_KEY);
			updateCertificatePath(certificatePathPreference);
		}
	}

	public static Context getAppContext() {
		return Preferences.context;
	}
	
	/**
	 * Updates the passed preference with the certificate paths found on external storage.
	 * @param preference The ListPreference to update.
	 */
	private static void updateCertificatePath(ListPreference preference) {
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
		certificateNames[certificateNames.length-1] = getAppContext().getResources().getString(R.string.noCert);
		
		preference.setEntries(certificateNames);
		preference.setEntryValues(certificatePaths);
	}
	
	@TargetApi(11)
	public static class PreferencesFragment extends PreferenceFragment {
		
		/* (non-Javadoc)
		 * @see android.support.v4.app.Fragment#onCreate(android.os.Bundle)
		 */
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			
			addPreferencesFromResource(R.xml.preferences);
			ListPreference certificatePathPreference = (ListPreference) findPreference(CERTIFICATE_PATH_KEY);
			updateCertificatePath(certificatePathPreference);
		}
	}
}
