package com.morlunk.mumbleclient.app;

import java.io.File;
import java.util.List;

import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;

import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.service.PlumbleCertificateGenerateTask;
import com.morlunk.mumbleclient.service.PlumbleCertificateManager;

public class Preferences extends SherlockPreferenceActivity {

    public static final String ACTION_PREFS_GENERAL = "com.morlunk.mumbleclient.app.PREFS_GENERAL";
    public static final String ACTION_PREFS_AUTHENTICATION = "com.morlunk.mumbleclient.app.PREFS_AUTHENTICATION";
    public static final String ACTION_PREFS_AUDIO = "com.morlunk.mumbleclient.app.PREFS_AUDIO";
    public static final String ACTION_PREFS_APPEARANCE = "com.morlunk.mumbleclient.app.PREFS_APPEARANCE";

    private static final String CERTIFICATE_GENERATE_KEY = "certificateGenerate";
    private static final String CERTIFICATE_PATH_KEY = "certificatePath";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Legacy preference section handling

        String action = getIntent().getAction();
        if (action != null) {
            if (ACTION_PREFS_GENERAL.equals(action)) {
                addPreferencesFromResource(R.xml.settings_general);
            } else if (ACTION_PREFS_AUTHENTICATION.equals(action)) {
                addPreferencesFromResource(R.xml.settings_authentication);
                configureCertificatePreferences(getPreferenceScreen());
            } else if (ACTION_PREFS_AUDIO.equals(action)) {
                addPreferencesFromResource(R.xml.settings_audio);
            } else if (ACTION_PREFS_APPEARANCE.equals(action)) {
                addPreferencesFromResource(R.xml.settings_appearance);
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            addPreferencesFromResource(R.xml.preference_headers_legacy);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.preference_headers, target);
    }

    @Override
    protected void onDestroy() {
        // Notify of settings changes
        Settings.getInstance(this).forceUpdateObservers();

        super.onDestroy();
    }

    private static void configureCertificatePreferences(PreferenceScreen screen) {
        final Preference certificateGeneratePreference = screen.findPreference(CERTIFICATE_GENERATE_KEY);
        final ListPreference certificatePathPreference = (ListPreference) screen.findPreference(CERTIFICATE_PATH_KEY);

        certificateGeneratePreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                generateCertificate(certificatePathPreference);
                return true;
            }
        });

        // Make sure media is mounted, otherwise do not allow certificate loading.
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            try {
                updateCertificatePath(certificatePathPreference);
            } catch (NullPointerException exception) {
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
     *
     * @param preference The ListPreference to update.
     */
    private static void updateCertificatePath(ListPreference preference) throws NullPointerException {
        List<File> certificateFiles = PlumbleCertificateManager.getAvailableCertificates();

        // Get arrays of certificate paths and names.
        String[] certificatePaths = new String[certificateFiles.size() + 1]; // Extra space for 'None' option
        for (int x = 0; x < certificateFiles.size(); x++) {
            certificatePaths[x] = certificateFiles.get(x).getPath();
        }
        certificatePaths[certificatePaths.length - 1] = "";

        String[] certificateNames = new String[certificateFiles.size() + 1]; // Extra space for 'None' option
        for (int x = 0; x < certificateFiles.size(); x++) {
            certificateNames[x] = certificateFiles.get(x).getName();
        }
        certificateNames[certificateNames.length - 1] = preference.getContext().getString(R.string.noCert);

        preference.setEntries(certificateNames);
        preference.setEntryValues(certificatePaths);
    }

    /**
     * Generates a new certificate and sets it as active.
     *
     * @param certificateList If passed, will update the list of certificates available. Messy.
     */
    private static void generateCertificate(final ListPreference certificateList) {
        PlumbleCertificateGenerateTask generateTask = new PlumbleCertificateGenerateTask(certificateList.getContext()) {
            @Override
            protected void onPostExecute(File result) {
                super.onPostExecute(result);

                if (result != null) {
                    updateCertificatePath(certificateList); // Update cert path after
                    certificateList.setValue(result.getAbsolutePath());
                }
            }
        };
        generateTask.execute();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class PlumblePreferenceFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            String section = getArguments().getString("settings");
            if ("general".equals(section)) {
                addPreferencesFromResource(R.xml.settings_general);
            } else if ("authentication".equals(section)) {
                addPreferencesFromResource(R.xml.settings_authentication);
                configureCertificatePreferences(getPreferenceScreen());
            } else if ("audio".equals(section)) {
                addPreferencesFromResource(R.xml.settings_audio);
            } else if ("appearance".equals(section)) {
                addPreferencesFromResource(R.xml.settings_appearance);
            }
        }
    }
}
