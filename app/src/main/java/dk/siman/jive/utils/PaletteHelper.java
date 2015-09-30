/*
 * Copyright (c) 2015. Anders Nielsen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dk.siman.jive.utils;

import android.graphics.Bitmap;
import android.support.v7.graphics.Palette;

import java.util.Collections;
import java.util.Comparator;


public class PaletteHelper {
    private static final String TAG = LogHelper.makeLogTag(PaletteHelper.class);

    public static int generatePalette(Bitmap bitmap) {
        if (bitmap == null) {
            return 0;
        }
        Palette.Builder paletteBuilder = new Palette.Builder(bitmap);
        paletteBuilder.maximumColorCount(10);
        Palette palette = paletteBuilder.generate();

        Palette.Swatch swatchDominant = getMainSwatch(palette);

        int color = swatchDominant.getRgb();
        String hexColor = String.format("#%06X", (0xFFFFFF & color));
        LogHelper.d(TAG, "hexColor: ", hexColor, ", color: ", color);

        return color;
    }

    public static Palette.Swatch getMainSwatch(Palette palette) {
        return Collections.max(palette.getSwatches(), new Comparator<Palette.Swatch>() {
            @Override
            public int compare(Palette.Swatch sw1, Palette.Swatch sw2) {
                return Integer.compare(sw1.getPopulation(), sw2.getPopulation());
            }
        });
    }
}
