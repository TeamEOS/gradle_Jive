
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

import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;

import dk.siman.jive.MusicService;
import dk.siman.jive.R;
import dk.siman.jive.utils.LogHelper;
import dk.siman.jive.utils.PrefUtils;

/**
 * Settings activity for features like equalizer
 */
public class SettingsActivity extends BaseActivity {

    private static final String TAG = LogHelper.makeLogTag(SettingsActivity.class);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogHelper.d(TAG, "Activity onCreate");

        setContentView(R.layout.activity_settings);

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
            addPreferencesFromResource(R.xml.settings);

            final Preference eqButton = findPreference("settings_equalizer");
            final SwitchPreference swipeInvertSwitch = (SwitchPreference) findPreference("settings_inverted_swipe");
            final SwitchPreference verboseLoggingSwitch = (SwitchPreference) findPreference("settings_verbose_logging");

            eqButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    Intent i = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
                    i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, MusicService.AUDIO_ID);
                    startActivityForResult(i, Defs.EFFECTS_PANEL);
                    return true;
                }
            });

            swipeInvertSwitch.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    LogHelper.d(TAG, "swipeInvertSwitch: ", newValue);
                    if (newValue.equals(true)) {
                        PrefUtils.setInvertSwipe(getActivity().getApplicationContext(), true);
                    } else {
                        PrefUtils.setInvertSwipe(getActivity().getApplicationContext(), false);
                    }

                    return true;
                }
            });

            verboseLoggingSwitch.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    LogHelper.d(TAG, "verboseLoggingSwitch: ", newValue);
                    if (newValue.equals(true)) {
                        PrefUtils.setVerboseLogging(getActivity().getApplicationContext(), true);
                    } else {
                        PrefUtils.setVerboseLogging(getActivity().getApplicationContext(), false);
                    }

                    return true;
                }
            });
        }
    }
}
