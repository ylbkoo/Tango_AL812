/* //device/java/android/android/os/IPowerManager.aidl
**
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License"); 
** you may not use this file except in compliance with the License. 
** You may obtain a copy of the License at 
**
**     http://www.apache.org/licenses/LICENSE-2.0 
**
** Unless required by applicable law or agreed to in writing, software 
** distributed under the License is distributed on an "AS IS" BASIS, 
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
** See the License for the specific language governing permissions and 
** limitations under the License.
*/

package android.os;

import android.os.WorkSource;

/** @hide */

interface IPowerManager
{
    // WARNING: The first five methods must remain the first five methods because their
    // transaction numbers must not change unless IPowerManager.cpp is also updated.
    void acquireWakeLock(IBinder lock, int flags, String tag, String packageName, in WorkSource ws,
            String historyTag);
    void acquireWakeLockWithUid(IBinder lock, int flags, String tag, String packageName,
            int uidtoblame);
    void releaseWakeLock(IBinder lock, int flags);
    void updateWakeLockUids(IBinder lock, in int[] uids);
    oneway void powerHint(int hintId, int data);

    void updateWakeLockWorkSource(IBinder lock, in WorkSource ws, String historyTag);
    boolean isWakeLockLevelSupported(int level);

    void userActivity(long time, int event, int flags);
    void wakeUp(long time);
    void goToSleep(long time, int reason, int flags);

    void startBacklight(int msec);
    void stopBacklight();

    void wakeUpByReason(long time, int reason);
    void setSmartBookScreen(boolean on);

    void nap(long time);
    boolean isInteractive();
    boolean isPowerSaveMode();
    boolean setPowerSaveMode(boolean mode);

    void reboot(boolean confirm, String reason, boolean wait);
    void shutdown(boolean confirm, boolean wait);
    void crash(String message);

    void setStayOnSetting(int val);
    void boostScreenBrightness(long time);

    // temporarily overrides the screen brightness settings to allow the user to
    // see the effect of a settings change without applying it immediately
    void setTemporaryScreenBrightnessSettingOverride(int brightness);
    void setTemporaryScreenAutoBrightnessAdjustmentSettingOverride(float adj);

    void setBacklightOffForWfd(boolean enable);
    void setBlNotify();

    // sets the attention light (used by phone app only)
    void setAttentionLight(boolean on, int color);
    void setLcdRatio(int ratio,boolean autoAdjust);
    
    
    //set FPC screen controller fengyaling add 
    void startWakeUpReady(long current);
    void stopWakeUpReady(long current,boolean enableBright);
    
}
