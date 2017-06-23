/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.internal.telephony.uicc;

/**
 * See also RIL_SimRefresh in include/telephony/ril.h
 *
 * {@hide}
 */

public class IccRefreshResponse {

    public static final int REFRESH_RESULT_FILE_UPDATE = 0; /* Single file was updated */
    public static final int REFRESH_RESULT_INIT = 1;        /* The Icc has been initialized */
    public static final int REFRESH_RESULT_RESET = 2;       /* The Icc was reset */
    public static final int REFRESH_RESULT_APP_INIT = 3; /* UICC APP reset, for wifi calling */
    public static final int REFRESH_INIT_FULL_FILE_UPDATED = 4;  /* SIM init and full file upload */
    public static final int REFRESH_INIT_FILE_UPDATED = 5;  /* SIM init and single file updated */
    public static final int REFRESH_SESSION_RESET = 6;      /* Session reset */

    public int  sessionId; /* active channel id */
    public int             refreshResult;      /* Sim Refresh result */
    public int[]           efId;               /* EFID */
    public String          aid;                /* null terminated string, e.g.,
                                                  from 0xA0, 0x00 -> 0x41,
                                                  0x30, 0x30, 0x30 */
                                               /* Example: a0000000871002f310ffff89080000ff */

    @Override
    public String toString() {
        return "{" + refreshResult + ", " + aid +", " + efId + "}";
    }
}
