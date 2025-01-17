/*
 * hymnchtv: COG hymns' lyrics viewer and player client
 * Copyright 2020 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cog.hymnchtv.utils;

import static org.cog.hymnchtv.MainActivity.HYMN_BB;
import static org.cog.hymnchtv.MainActivity.HYMN_DB;
import static org.cog.hymnchtv.MainActivity.HYMN_ER;
import static org.cog.hymnchtv.MainActivity.HYMN_XB;

import android.util.Range;

import org.cog.hymnchtv.HymnsApp;
import org.cog.hymnchtv.R;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Check the hymnNo is valid for a given hymnType.
 * Return the correct hymnNo or -1 if invalid
 *
 * @author Eng Chong Meng
 */
public class HymnNoValidate
{
    /* Maximum HymnNo/HymnIndex: 大本诗歌 and start of its supplement */
    // The values and the similar must be updated if there are any new contents added
    public static final int HYMN_DB_NO_MAX = 780;
    public static final int HYMN_DBS_NO_MAX = 6;

    // FuGe pass-in index is HYMN_DB_NO_MAX + fu Number
    public static final int HYMN_DB_NO_TMAX = 786;
    // Max index use by PageAdapter
    public static final int HYMN_DB_INDEX_MAX = 786;

    /* Maximum HymnNo/HymnIndex (excluding multiPage i.e. a,b,c,d,e): 補充本 */
    public static final int HYMN_BB_NO_MAX = 1005;
    public static final int HYMN_BB_INDEX_MAX = 513;

    /* Maximum HymnNo/HymnIndex: 新歌颂咏 */
    public static final int HYMN_XB_NO_MAX = 169;
    public static final int HYMN_XB_INDEX_MAX = 169;

    /* Maximum HymnNo/HymnIndex: 儿童诗歌 */
    public static final int HYMN_ER_NO_MAX = 1232;
    public static final int HYMN_ER_INDEX_MAX = 330;

    // ======================================================== //
    // 補充本 range parameters for page number (i.e. less than in each 100 range)
    // Each value is hymnNo + 1 within each 100 range; it is used to generate rangeBbInvalid
    // The values must be updated if there are any new contents added
    public static final int[] rangeBbLimit = {38, 151, 259, 350, 471, 544, 630, 763, 881, 931, 1006};

    // invalid range for 補充本
    public static final List<Range<Integer>> rangeBbInvalid = new ArrayList<>();

    // Auto generated invalid range for based 補充本 on rangeBbLimit; invalid range for 補充本:
    // (38, 100),(151, 200),(259, 300),(350, 400),(471, 500),(544, 600),(630, 700),(763, 800),(881, 900),(931, 1000)
    static {
        for (int i = 0; i < (rangeBbLimit.length - 1); i++) {
            rangeBbInvalid.add(Range.create(rangeBbLimit[i], 100 * (i + 1)));
        }
    }

    // ======================================================== //
    // 儿童诗歌 range parameters for page number (i.e. less than in each 100 range)
    // Each value is hymnNo + 1 within each 100 range; it is used to generate rangeErInvalid
    // The values must be updated if there are any new contents added
    public static final int[] rangeErLimit = {18, 125, 213, 324, 446, 525, 622, 720, 837, 921, 1040, 1119, 1233};

    // invalid range for 儿童诗歌
    public static final List<Range<Integer>> rangeErInvalid = new ArrayList<>();

    // Auto generated invalid range for 儿童诗歌 based on rangeErLimit
    static {
        for (int i = 0; i < (rangeErLimit.length - 1); i++) {
            rangeErInvalid.add(Range.create(rangeErLimit[i], 100 * (i + 1)));
        }
    }

    /**
     * Check the hymnNo is valid for the given hymnType.
     *
     * @param hymnType The hymnTye
     * @param hymnNo The given hymnNo for validation
     * @param isFu true if the given hymnNo is Fu
     *
     * @return the valid hymNo or -1 if invalid
     */
    public static int validateHymnNo(String hymnType, int hymnNo, boolean isFu)
    {
        boolean isValid = true;

        switch (hymnType) {
            case HYMN_ER:
                if (isFu) {
                    HymnsApp.showToastMessage(R.string.hymn_info_sp_none);
                    isValid = false;
                    break;
                }

                if (hymnNo > HYMN_ER_NO_MAX) {
                    HymnsApp.showToastMessage(R.string.hymn_info_er_max, HYMN_ER_NO_MAX);
                    isValid = false;
                }
                else if (hymnNo < 1) {
                    HymnsApp.showToastMessage(R.string.gui_error_invalid);
                    isValid = false;
                }
                else {
                    for (Range<Integer> rangeX : rangeErInvalid) {
                        if (rangeX.contains(hymnNo)) {
                            HymnsApp.showToastMessage(R.string.hymn_info_er_range_over, rangeX.getLower(), rangeX.getUpper());
                            isValid = false;
                            break;
                        }
                    }
                }
                break;

            case HYMN_XB:
                if (isFu) {
                    HymnsApp.showToastMessage(R.string.hymn_info_sp_none);
                    isValid = false;
                    break;
                }

                if (hymnNo > HYMN_XB_NO_MAX) {
                    HymnsApp.showToastMessage(R.string.hymn_info_xb_max, HYMN_XB_NO_MAX);
                    isValid = false;
                }
                else if (hymnNo < 1) {
                    HymnsApp.showToastMessage(R.string.gui_error_invalid);
                    isValid = false;
                }
                break;

            case HYMN_BB:
                if (isFu) {
                    HymnsApp.showToastMessage(R.string.hymn_info_sp_none);
                    isValid = false;
                    break;
                }

                if (hymnNo > HYMN_BB_NO_MAX) {
                    HymnsApp.showToastMessage(R.string.hymn_info_bb_max, HYMN_BB_NO_MAX);
                    isValid = false;
                }
                else if (hymnNo < 1) {
                    HymnsApp.showToastMessage(R.string.gui_error_invalid);
                    isValid = false;
                }
                else {
                    for (Range<Integer> rangeX : rangeBbInvalid) {
                        if (rangeX.contains(hymnNo)) {
                            HymnsApp.showToastMessage(R.string.hymn_info_bb_range_over, rangeX.getLower(), rangeX.getUpper());
                            isValid = false;
                            break;
                        }
                    }
                }
                break;

            case HYMN_DB:
                // Fu hymnNo continues from HYMN_DB_NO_MAX
                if (isFu && hymnNo <= HYMN_DBS_NO_MAX) {
                    hymnNo += HYMN_DB_NO_MAX;
                }
                if (hymnNo > HYMN_DB_NO_TMAX) {
                    HymnsApp.showToastMessage(R.string.hymn_info_db_max, HYMN_DB_NO_MAX);
                    isValid = false;
                }
                else if (hymnNo < 1) {
                    HymnsApp.showToastMessage(R.string.gui_error_invalid);
                    isValid = false;
                }
                break;

            default:
                Timber.e("Unsupported content type: %s", hymnType);
        }
        return isValid ? hymnNo : -1;
    }
}
