/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dk.siman.jive.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Util for setting and accessing {@link SharedPreferences} for the current application.
 */
public class PrefUtils {

    private static final String PREF_NAMESPACE = "dk.siman.jive.utils.PREFS";
    private static final String FTU_SHOWN = "ftu_shown";
    private static final String INVERT_SWIPE = "invert_swipe";
    private static final String VERBOSE_LOGGING = "verbose_logging";

    private static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(PREF_NAMESPACE, Context.MODE_PRIVATE);
    }

    public static void setFtuShown(Context context, boolean shown) {
        getPreferences(context).edit().putBoolean(FTU_SHOWN, shown).apply();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isFtuShown(Context context) {
        return getPreferences(context).getBoolean(FTU_SHOWN, false);
    }

    public static void setInvertSwipe(Context context, boolean shown) {
        getPreferences(context).edit().putBoolean(INVERT_SWIPE, shown).apply();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isInvertSwipe(Context context) {
        return getPreferences(context).getBoolean(INVERT_SWIPE, false);
    }

    public static void setVerboseLogging(Context context, boolean shown) {
        getPreferences(context).edit().putBoolean(VERBOSE_LOGGING, shown).apply();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isVerboseLogging(Context context) {
        return getPreferences(context).getBoolean(VERBOSE_LOGGING, false);
    }
}
