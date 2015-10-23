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
package dk.siman.jive;

import android.app.Application;

import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.google.android.libraries.cast.companionlibrary.cast.player.VideoCastController;

import dk.siman.jive.ui.FullScreenPlayerActivity;

/**
 * The {@link Application} for the uAmp application.
 */
public class JIVEApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        String applicationId = getResources().getString(R.string.cast_application_id);
        VideoCastManager castManager = VideoCastManager.initialize(
                getApplicationContext(),
                applicationId,
                FullScreenPlayerActivity.class,
                null);

        castManager.enableFeatures(VideoCastManager.FEATURE_WIFI_RECONNECT |
                VideoCastManager.FEATURE_AUTO_RECONNECT |
                VideoCastManager.FEATURE_CAPTIONS_PREFERENCE |
                VideoCastManager.FEATURE_DEBUGGING);

        // this is the default behavior but is mentioned to make it clear that it is configurable.
        VideoCastManager.getInstance().setNextPreviousVisibilityPolicy(
                VideoCastController.NEXT_PREV_VISIBILITY_POLICY_DISABLED);

        // this is the default behavior but is mentioned to make it clear that it is configurable.
        VideoCastManager.getInstance().setCastControllerImmersive(true);
    }
}
