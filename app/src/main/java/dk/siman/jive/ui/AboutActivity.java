
/*
 * Copyright (c) 2015. Anders Nielsen
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package dk.siman.jive.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.webkit.WebView;

import dk.siman.jive.BuildConfig;
import dk.siman.jive.R;
import dk.siman.jive.utils.LogHelper;

/**
 * About activity for app info
 */
public class AboutActivity extends BaseActivity {

    private static final String TAG = LogHelper.makeLogTag(AboutActivity.class);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogHelper.d(TAG, "Activity onCreate");

        setContentView(R.layout.activity_about);

        initializeToolbar();

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .add(R.id.container, new PreferencesFragment())
                .commit();
    }

    public static class PreferencesFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.about);

            final Preference aboutSettings = findPreference("about_settings_name");
            aboutSettings.setSelectable(false);
            aboutSettings.setSummary(("Version " + BuildConfig.VERSION_NAME));

            final Preference licenseButton = findPreference("settings_license");
            licenseButton.setSummary(getResources().getString(R.string.about_license_summary));
            licenseButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    LogHelper.i(TAG, "Show licenses");
                    showOpenSourceLicenses(getActivity());

                    return true;
                }
            });
        }

        public static void showOpenSourceLicenses(Activity activity) {
            FragmentManager fm = activity.getFragmentManager();
            FragmentTransaction ft = fm.beginTransaction();
            Fragment prev = fm.findFragmentByTag("dialog_licenses");
            if (prev != null) {
                ft.remove(prev);
            }
            ft.addToBackStack(null);

            new OpenSourceLicensesDialog().show(ft, "dialog_licenses");
        }

        public static class OpenSourceLicensesDialog extends DialogFragment {

            public OpenSourceLicensesDialog() {
            }

            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                WebView webView = new WebView(getActivity());
                webView.getSettings().setUseWideViewPort(true);
                webView.loadUrl("file:///android_asset/licenses.html");

                return new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.about_license_summary)
                        .setView(webView)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        dialog.dismiss();
                                    }
                                }
                        )
                        .create();
            }
        }
    }
}
