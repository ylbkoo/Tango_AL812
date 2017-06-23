/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telecom.VideoProfile;
import android.telephony.CellIdentityCdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.DataConnectionRealTimeInfo;
import android.telephony.RadioAccessFamily;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.VoLteServiceState;
import android.text.TextUtils;
import android.os.Bundle;

import com.android.ims.ImsManager;
import com.android.internal.R;
import com.android.internal.telephony.dataconnection.DcTrackerBase;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.RadioCapability;
import com.android.internal.telephony.test.SimulatedRadioControl;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IsimRecords;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.uicc.UsimServiceTable;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
/// M: SS OP01 Ut @{
import java.util.Arrays;
import java.util.Calendar;
/// @}
import java.util.List;
import java.util.Locale;
import java.util.Set;
/// M: SS OP01 Ut
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;

import com.mediatek.internal.telephony.FemtoCellInfo;
import com.mediatek.internal.telephony.NetworkInfoWithAcT;
import com.mediatek.internal.telephony.RadioManager;

//VoLTE
import com.mediatek.internal.telephony.QosStatus;
import com.mediatek.internal.telephony.TftStatus;
import com.mediatek.internal.telephony.PcscfInfo;
import com.mediatek.internal.telephony.DedicateBearerProperties;
import com.mediatek.internal.telephony.DefaultBearerConfig;
import com.android.internal.telephony.dataconnection.DcFailCause;
import com.android.internal.telephony.dataconnection.DcTracker;



/**
 * (<em>Not for SDK use</em>)
 * A base implementation for the com.android.internal.telephony.Phone interface.
 *
 * Note that implementations of Phone.java are expected to be used
 * from a single application thread. This should be the same thread that
 * originally called PhoneFactory to obtain the interface.
 *
 *  {@hide}
 *
 */

public abstract class PhoneBase extends Handler implements Phone {
    private static final String LOG_TAG = "PhoneBase";

    private BroadcastReceiver mImsIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Rlog.d(LOG_TAG, "mImsIntentReceiver: action " + intent.getAction());
            if (intent.hasExtra(ImsManager.EXTRA_PHONE_ID)) {
                int extraPhoneId = intent.getIntExtra(ImsManager.EXTRA_PHONE_ID,
                        SubscriptionManager.INVALID_PHONE_INDEX);
                Rlog.d(LOG_TAG, "mImsIntentReceiver: extraPhoneId = " + extraPhoneId);
                if (extraPhoneId == SubscriptionManager.INVALID_PHONE_INDEX ||
                        extraPhoneId != getPhoneId()) {
                    return;
                }
            }
            /// M: Simulate MSIM switch @{
            Rlog.w(LOG_TAG, intent.getAction() + ", getSubId=" + getSubId() +
                    ", getPhoneId=" + getPhoneId());
            /// @}
            if (intent.getAction().equals(ImsManager.ACTION_IMS_SERVICE_UP)) {
                /// M: Simulate MSIM switch @{
                if (SystemProperties.getInt("persist.ims.simulate", 0) == 1
                        && SystemProperties.getInt("persist.ims.phoneid", 0) != getPhoneId()) {
                    return;
                }
                /// @}
                mImsServiceReady = true;
                updateImsPhone();
            } else if (intent.getAction().equals(ImsManager.ACTION_IMS_SERVICE_DOWN)) {
                mImsServiceReady = false;
                updateImsPhone();
            }
        }
    };

    // Key used to read and write the saved network selection numeric value
    public static final String NETWORK_SELECTION_KEY = "network_selection_key";
    // Key used to read and write the saved network selection operator name
    public static final String NETWORK_SELECTION_NAME_KEY = "network_selection_name_key";

    public static final String NETWORK_SELECTION_KEY_2 = "network_selection_key_2";
    public static final String NETWORK_SELECTION_NAME_KEY_2 = "network_selection_name_key_2";
    public static final String NETWORK_SELECTION_KEY_3 = "network_selection_key_3";
    public static final String NETWORK_SELECTION_NAME_KEY_3 = "network_selection_name_key_3";
    public static final String NETWORK_SELECTION_KEY_4 = "network_selection_key_4";
    public static final String NETWORK_SELECTION_NAME_KEY_4 = "network_selection_name_key_4";


    // Key used to read/write "disable data connection on boot" pref (used for testing)
    public static final String DATA_DISABLED_ON_BOOT_KEY = "disabled_on_boot_key";

    /* Event Constants */
    protected static final int EVENT_RADIO_AVAILABLE             = 1;
    /** Supplementary Service Notification received. */
    protected static final int EVENT_SSN                         = 2;
    protected static final int EVENT_SIM_RECORDS_LOADED          = 3;
    protected static final int EVENT_MMI_DONE                    = 4;
    protected static final int EVENT_RADIO_ON                    = 5;
    protected static final int EVENT_GET_BASEBAND_VERSION_DONE   = 6;
    protected static final int EVENT_USSD                        = 7;
    protected static final int EVENT_RADIO_OFF_OR_NOT_AVAILABLE  = 8;
    protected static final int EVENT_GET_IMEI_DONE               = 9;
    protected static final int EVENT_GET_IMEISV_DONE             = 10;
    protected static final int EVENT_GET_SIM_STATUS_DONE         = 11;
    protected static final int EVENT_SET_CALL_FORWARD_DONE       = 12;
    protected static final int EVENT_GET_CALL_FORWARD_DONE       = 13;
    protected static final int EVENT_CALL_RING                   = 14;
    protected static final int EVENT_CALL_RING_CONTINUE          = 15;

    // Used to intercept the carrier selection calls so that
    // we can save the values.
    protected static final int EVENT_SET_NETWORK_MANUAL_COMPLETE    = 16;
    protected static final int EVENT_SET_NETWORK_AUTOMATIC_COMPLETE = 17;
    protected static final int EVENT_SET_CLIR_COMPLETE              = 18;
    protected static final int EVENT_REGISTERED_TO_NETWORK          = 19;
    protected static final int EVENT_SET_VM_NUMBER_DONE             = 20;
    // Events for CDMA support
    protected static final int EVENT_GET_DEVICE_IDENTITY_DONE       = 21;
    protected static final int EVENT_RUIM_RECORDS_LOADED            = 22;
    protected static final int EVENT_NV_READY                       = 23;
    protected static final int EVENT_SET_ENHANCED_VP                = 24;
    protected static final int EVENT_EMERGENCY_CALLBACK_MODE_ENTER  = 25;
    protected static final int EVENT_EXIT_EMERGENCY_CALLBACK_RESPONSE = 26;
    protected static final int EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED = 27;
    // other
    protected static final int EVENT_SET_NETWORK_AUTOMATIC          = 28;
    protected static final int EVENT_ICC_RECORD_EVENTS              = 29;
    protected static final int EVENT_ICC_CHANGED                    = 30;
    // Single Radio Voice Call Continuity
    protected static final int EVENT_SRVCC_STATE_CHANGED            = 31;
    protected static final int EVENT_INITIATE_SILENT_REDIAL         = 32;
    protected static final int EVENT_RADIO_NOT_AVAILABLE            = 33;
    protected static final int EVENT_UNSOL_OEM_HOOK_RAW             = 34;
    protected static final int EVENT_GET_RADIO_CAPABILITY           = 35;
    protected static final int EVENT_SS                             = 36;

    /// M: CC060: Notify SIP call RING @{
    protected static final int EVENT_SIP_CALL_RING                  = 37;
    /// @}
    protected static final int EVENT_CFU_IND                        = 38;
    protected static final int EVENT_CFU_QUERY_TIMEOUT              = 39; /* For ALPS01023811 */
    /// M: CC010: Add RIL interface @{
    protected static final int EVENT_CRSS_IND                       = 40;
    protected static final int EVENT_VOICE_CALL_INCOMING_INDICATION = 41;
    protected static final int EVENT_CIPHER_INDICATION              = 42;
    /// @}
    protected static final int EVENT_GET_PHONE_RAT_FAMILY              = 43;
    protected static final int EVENT_PHONE_RAT_FAMILY_CHANGED_NOTIFY   = 44;
    // special cmd from battery
    protected static final int EVENT_CHARGING_STOP                  = 45;
    protected static final int EVENT_ABNORMAL_EVENT                 = 46;
    protected static final int EVENT_SET_BAND_MODE_DONE             = 47;

    /// M: c2k modify, event constants. @{
    protected static final int EVENT_QUERY_UIM_INSERTED_STATUS_DONE = 100;
    protected static final int EVENT_GET_UIM_INSERT_STATUS_RETRY    = 101;
    protected static final int EVENT_SET_MEID_DONE                  = 102;
    protected static final int EVENT_CDMA_MCC_MNC_CHANGED           = 103;
    protected static final int EVENT_RUIM_READY                     = 205;
    /// @}

    /// M: SS @{
    protected static final int EVENT_IMS_UT_DONE                    = 2000;
    protected static final int EVENT_IMS_UT_CSFB                    = 2001;
    protected static final int EVENT_QUERY_CFU                      = 2002;
    /// @}

    /** M: for suspend data during plmn list */
    protected static final int EVENT_GET_AVAILABLE_NETWORK_DONE = 500520;
    protected static final int EVENT_DC_SWITCH_STATE_CHANGE = 500521;
    protected static final int EVENT_GET_AVAILABLE_NETWORK = 500522;

    /// M: SS OP01 Ut @{
    protected static final int EVENT_GET_CALL_FORWARD_TIME_SLOT_DONE = 201;
    protected static final int EVENT_SET_CALL_FORWARD_TIME_SLOT_DONE = 202;
    /// @}

    protected static final int EVENT_GET_CALL_WAITING_DONE = 301;
    protected static final int EVENT_SET_CALL_WAITING_DONE = 302;

    protected static final int EVENT_LAST = EVENT_PHONE_RAT_FAMILY_CHANGED_NOTIFY;

    // For shared prefs.
    private static final String GSM_ROAMING_LIST_OVERRIDE_PREFIX = "gsm_roaming_list_";
    private static final String GSM_NON_ROAMING_LIST_OVERRIDE_PREFIX = "gsm_non_roaming_list_";
    private static final String CDMA_ROAMING_LIST_OVERRIDE_PREFIX = "cdma_roaming_list_";
    private static final String CDMA_NON_ROAMING_LIST_OVERRIDE_PREFIX = "cdma_non_roaming_list_";

    // Key used to read/write current CLIR setting
    public static final String CLIR_KEY = "clir_key";
    /* M: SS part */
    /// M: Add key for SIM2 CLIR setting.
    public static final String CLIR_KEY_2 = "clir_key_2";
    /* M: SS part end */

    // Key used for storing voice mail count
    public static final String VM_COUNT = "vm_count_key";
    // Key used to read/write the ID for storing the voice mail
    public static final String VM_ID = "vm_id_key";

    // Key used to read/write "disable DNS server check" pref (used for testing)
    public static final String DNS_SERVER_CHECK_DISABLED_KEY = "dns_server_check_disabled_key";

    /**
     * Small container class used to hold information relevant to
     * the carrier selection process. operatorNumeric can be ""
     * if we are looking for automatic selection. operatorAlphaLong is the
     * corresponding operator name.
     */
    public static class NetworkSelectMessage {
        public Message message;
        public String operatorNumeric;
        public String operatorAlphaLong;
    }

    /* Instance Variables */
    public CommandsInterface mCi;
    private int mVmCount = 0;
    boolean mDnsCheckDisabled;
    public DcTrackerBase mDcTracker;
    boolean mDoesRilSendMultipleCallRing;
    int mCallRingContinueToken;
    int mCallRingDelay;
    public boolean mIsTheCurrentActivePhone = true;
    boolean mIsVoiceCapable = true;
    protected UiccController mUiccController = null;
    public AtomicReference<IccRecords> mIccRecords = new AtomicReference<IccRecords>();
    public SmsStorageMonitor mSmsStorageMonitor;
    public SmsUsageMonitor mSmsUsageMonitor;
    protected AtomicReference<UiccCardApplication> mUiccApplication =
            new AtomicReference<UiccCardApplication>();

    private TelephonyTester mTelephonyTester;
    private final String mName;
    private final String mActionDetached;
    private final String mActionAttached;

    protected int mPhoneId;

    private final Object mImsLock = new Object();
    private boolean mImsServiceReady = false;
    protected ImsPhone mImsPhone = null;

    protected int mRadioAccessFamily = RadioAccessFamily.RAF_UNKNOWN;

    /// M: CC060: [ALPS00093395] Notify SIP call RING @{
    int mSipCallRingContinueToken = 0;
    /// @}

    /* M: VoLTE SS start */
    private int mCSFallbackMode = 0;
    /* M: VoLTE SS end */

    /// M: SS OP01 Ut @{
    private static final String CFU_TIME_SLOT = "persist.radio.cfu.timeslot.";
    /// @}

    @Override
    public String getPhoneName() {
        return mName;
    }

    public String getNai(){
         return null;
    }

    /**
     * Return the ActionDetached string. When this action is received by components
     * they are to simulate detaching from the network.
     *
     * @return com.android.internal.telephony.{mName}.action_detached
     *          {mName} is GSM, CDMA ...
     */
    public String getActionDetached() {
        return mActionDetached;
    }

    /**
     * Return the ActionAttached string. When this action is received by components
     * they are to simulate attaching to the network.
     *
     * @return com.android.internal.telephony.{mName}.action_detached
     *          {mName} is GSM, CDMA ...
     */
    public String getActionAttached() {
        return mActionAttached;
    }

    /**
     * Set a system property, unless we're in unit test mode
     */
    // CAF_MSIM TODO this need to be replated with TelephonyManager API ?
    public void setSystemProperty(String property, String value) {
        if(getUnitTestMode()) {
            return;
        }
        SystemProperties.set(property, value);
    }

    /**
     * Set a system property, unless we're in unit test mode
     */
    // CAF_MSIM TODO this need to be replated with TelephonyManager API ?
    public String getSystemProperty(String property, String defValue) {
        if(getUnitTestMode()) {
            return null;
        }
        return SystemProperties.get(property, defValue);
    }


    protected final RegistrantList mPreciseCallStateRegistrants
            = new RegistrantList();

    protected final RegistrantList mHandoverRegistrants
             = new RegistrantList();

    protected final RegistrantList mNewRingingConnectionRegistrants
            = new RegistrantList();

    protected final RegistrantList mIncomingRingRegistrants
            = new RegistrantList();

    protected final RegistrantList mDisconnectRegistrants
            = new RegistrantList();

    protected final RegistrantList mServiceStateRegistrants
            = new RegistrantList();

    protected final RegistrantList mMmiCompleteRegistrants
            = new RegistrantList();

    protected final RegistrantList mMmiRegistrants
            = new RegistrantList();

    protected final RegistrantList mUnknownConnectionRegistrants
            = new RegistrantList();

    protected final RegistrantList mSuppServiceFailedRegistrants
            = new RegistrantList();

    protected final RegistrantList mRadioOffOrNotAvailableRegistrants
            = new RegistrantList();

    protected final RegistrantList mSimRecordsLoadedRegistrants
            = new RegistrantList();

    /// M: CC010: Add RIL interface @{
    protected final RegistrantList mVoiceCallIncomingIndicationRegistrants
            = new RegistrantList();

    protected final RegistrantList mCipherIndicationRegistrants
            = new RegistrantList();
    /// @}

    /// M: CC077: 2/3G CAPABILITY_HIGH_DEF_AUDIO @{
    protected final RegistrantList mSpeechCodecInfoRegistrants
            = new RegistrantList();
    /// @}

    protected final RegistrantList mCdmaCallAcceptedRegistrants
            = new RegistrantList();

    /// M: IMS feature. @{
    // Used to notify IMS conference call indication. @{
    protected final RegistrantList mImsConferenceCallRegistrants
            = new RegistrantList();
    // Used to notify call mode or pau changed notiication
    protected final RegistrantList mCallInfoChangedRegistrants
            = new RegistrantList();
    /// @}

    /// M:Add for SVLTE. @{
    protected final RegistrantList mSvlteServiceStateRegistrants = new RegistrantList();
    /// @}

    protected Looper mLooper; /* to insure registrants are in correct thread*/

    protected final Context mContext;

    /**
     * PhoneNotifier is an abstraction for all system-wide
     * state change notification. DefaultPhoneNotifier is
     * used here unless running we're inside a unit test.
     */
    protected PhoneNotifier mNotifier;

    protected SimulatedRadioControl mSimulatedRadioControl;

    boolean mUnitTestMode;

    /**
     * Constructs a PhoneBase in normal (non-unit test) mode.
     *
     * @param notifier An instance of DefaultPhoneNotifier,
     * @param context Context object from hosting application
     * unless unit testing.
     * @param ci the CommandsInterface
     */
    protected PhoneBase(String name, PhoneNotifier notifier, Context context, CommandsInterface ci) {
        this(name, notifier, context, ci, false);
    }

    /**
     * Constructs a PhoneBase in normal (non-unit test) mode.
     *
     * @param notifier An instance of DefaultPhoneNotifier,
     * @param context Context object from hosting application
     * unless unit testing.
     * @param ci is CommandsInterface
     * @param unitTestMode when true, prevents notifications
     * of state change events
     */
    protected PhoneBase(String name, PhoneNotifier notifier, Context context, CommandsInterface ci,
            boolean unitTestMode) {
        this(name, notifier, context, ci, unitTestMode, SubscriptionManager.DEFAULT_PHONE_INDEX);
    }

    /**
     * Constructs a PhoneBase in normal (non-unit test) mode.
     *
     * @param notifier An instance of DefaultPhoneNotifier,
     * @param context Context object from hosting application
     * unless unit testing.
     * @param ci is CommandsInterface
     * @param unitTestMode when true, prevents notifications
     * of state change events
     * @param subscription is current phone subscription
     */
    protected PhoneBase(String name, PhoneNotifier notifier, Context context, CommandsInterface ci,
            boolean unitTestMode, int phoneId) {
        mPhoneId = phoneId;
        mName = name;
        mNotifier = notifier;
        mContext = context;
        mLooper = Looper.myLooper();
        mCi = ci;
        mActionDetached = this.getClass().getPackage().getName() + ".action_detached";
        mActionAttached = this.getClass().getPackage().getName() + ".action_attached";

        if (Build.IS_DEBUGGABLE) {
            mTelephonyTester = new TelephonyTester(this);
        }

        setUnitTestMode(unitTestMode);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        mDnsCheckDisabled = sp.getBoolean(DNS_SERVER_CHECK_DISABLED_KEY, false);
        mCi.setOnCallRing(this, EVENT_CALL_RING, null);

        /* "Voice capable" means that this device supports circuit-switched
        * (i.e. voice) phone calls over the telephony network, and is allowed
        * to display the in-call UI while a cellular voice call is active.
        * This will be false on "data only" devices which can't make voice
        * calls and don't support any in-call UI.
        */
        mIsVoiceCapable = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_voice_capable);

        /**
         *  Some RIL's don't always send RIL_UNSOL_CALL_RING so it needs
         *  to be generated locally. Ideally all ring tones should be loops
         * and this wouldn't be necessary. But to minimize changes to upper
         * layers it is requested that it be generated by lower layers.
         *
         * By default old phones won't have the property set but do generate
         * the RIL_UNSOL_CALL_RING so the default if there is no property is
         * true.
         */
        mDoesRilSendMultipleCallRing = SystemProperties.getBoolean(
                TelephonyProperties.PROPERTY_RIL_SENDS_MULTIPLE_CALL_RING, true);
        Rlog.d(LOG_TAG, "mDoesRilSendMultipleCallRing=" + mDoesRilSendMultipleCallRing);

        mCallRingDelay = SystemProperties.getInt(
                TelephonyProperties.PROPERTY_CALL_RING_DELAY, 3000);
        Rlog.d(LOG_TAG, "mCallRingDelay=" + mCallRingDelay);

        if (getPhoneType() == PhoneConstants.PHONE_TYPE_IMS) return;

        setPropertiesByCarrier();

        // Initialize device storage and outgoing SMS usage monitors for SMSDispatchers.
        mSmsStorageMonitor = new SmsStorageMonitor(this);
        mSmsUsageMonitor = new SmsUsageMonitor(context);
        mUiccController = UiccController.getInstance();
        mUiccController.registerForIccChanged(this, EVENT_ICC_CHANGED, null);
        mUiccController.registerForApplicationChanged(this, EVENT_ICC_CHANGED, null);

        // Monitor IMS service
        IntentFilter filter = new IntentFilter();
        filter.addAction(ImsManager.ACTION_IMS_SERVICE_UP);
        filter.addAction(ImsManager.ACTION_IMS_SERVICE_DOWN);
        mContext.registerReceiver(mImsIntentReceiver, filter);

        mCi.registerForSrvccStateChanged(this, EVENT_SRVCC_STATE_CHANGED, null);
        mCi.setOnUnsolOemHookRaw(this, EVENT_UNSOL_OEM_HOOK_RAW, null);
    }

    @Override
    public void dispose() {
        synchronized(PhoneProxy.lockForRadioTechnologyChange) {
            mContext.unregisterReceiver(mImsIntentReceiver);
            mCi.unSetOnCallRing(this);
            // Must cleanup all connectionS and needs to use sendMessage!
            mDcTracker.cleanUpAllConnections(null);
            mIsTheCurrentActivePhone = false;
            // Dispose the SMS usage and storage monitors
            mSmsStorageMonitor.dispose();
            mSmsUsageMonitor.dispose();
            mUiccController.unregisterForIccChanged(this);
            mCi.unregisterForSrvccStateChanged(this);
            mCi.unSetOnUnsolOemHookRaw(this);

            if (mTelephonyTester != null) {
                mTelephonyTester.dispose();
            }

            ImsPhone imsPhone = mImsPhone;
            if (imsPhone != null) {
                imsPhone.unregisterForSilentRedial(this);
                imsPhone.dispose();
            }
        }
    }

    @Override
    public void removeReferences() {
        mSmsStorageMonitor = null;
        mSmsUsageMonitor = null;
        mIccRecords.set(null);
        mUiccApplication.set(null);
        mDcTracker = null;
        mUiccController = null;

        ImsPhone imsPhone = mImsPhone;
        if (imsPhone != null) {
            imsPhone.removeReferences();
            mImsPhone = null;
        }
    }

    /**
     * When overridden the derived class needs to call
     * super.handleMessage(msg) so this method has a
     * a chance to process the message.
     *
     * @param msg
     */
    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar;

        // messages to be handled whether or not the phone is being destroyed
        // should only include messages which are being re-directed and do not use
        // resources of the phone being destroyed
        // Note: make sure to add code in GSMPhone/CDMAPhone to re-direct here before
        // they check if phone destroyed.
        switch (msg.what) {
            // handle the select network completion callbacks.
            case EVENT_SET_NETWORK_MANUAL_COMPLETE:
            case EVENT_SET_NETWORK_AUTOMATIC_COMPLETE:
                handleSetSelectNetwork((AsyncResult) msg.obj);
                return;
        }

        if (!mIsTheCurrentActivePhone) {
            Rlog.e(LOG_TAG, "Received message " + msg +
                    "[" + msg.what + "] while being destroyed. Ignoring.");
            return;
        }
        switch(msg.what) {
            case EVENT_CALL_RING:
                Rlog.d(LOG_TAG, "Event EVENT_CALL_RING Received state=" + getState());
                ar = (AsyncResult)msg.obj;
                if (ar.exception == null) {
                    PhoneConstants.State state = getState();
                    if ((!mDoesRilSendMultipleCallRing)
                            && ((state == PhoneConstants.State.RINGING) ||
                                    (state == PhoneConstants.State.IDLE))) {
                        mCallRingContinueToken += 1;
                        sendIncomingCallRingNotification(mCallRingContinueToken);
                    } else {
                        notifyIncomingRing();
                    }
                }
                break;

            case EVENT_CALL_RING_CONTINUE:
                Rlog.d(LOG_TAG, "Event EVENT_CALL_RING_CONTINUE Received stat=" + getState());
                if (getState() == PhoneConstants.State.RINGING) {
                    sendIncomingCallRingNotification(msg.arg1);
                }
                break;

            case EVENT_CHARGING_STOP:
                String s_fun[] = new String[2];
                String s_rat[] = new String[2];
                String s[] = new String[2];

                Rlog.d(LOG_TAG, "send special AT cmd to MD");

                s_fun[0] = "AT+EFUN=1";
                s_fun[1] = "";
                this.invokeOemRilRequestStrings(s_fun, null);

                //s_rat[0] = "AT+ERAT=3";
                //s_rat[1] = "";
                //this.invokeOemRilRequestStrings(s_rat, null);

                s[0] = "AT+ERFTX=1,0,0,"+SystemProperties.get("persist.radio.charging_stop","40");
                s[1] = "";
                this.invokeOemRilRequestStrings(s, null);
                break;

            case EVENT_ICC_CHANGED:
                onUpdateIccAvailability();
                break;

            case EVENT_INITIATE_SILENT_REDIAL:
                Rlog.d(LOG_TAG, "Event EVENT_INITIATE_SILENT_REDIAL Received");
                ar = (AsyncResult) msg.obj;
                if ((ar.exception == null) && (ar.result != null)) {
                    String dialString = (String) ar.result;
                    if (TextUtils.isEmpty(dialString)) return;
                    try {
                        dialInternal(dialString, null, VideoProfile.VideoState.AUDIO_ONLY);
                    } catch (CallStateException e) {
                        Rlog.e(LOG_TAG, "silent redial failed: " + e);
                    }
                }
                break;

            case EVENT_SRVCC_STATE_CHANGED:
                ar = (AsyncResult)msg.obj;
                if (ar.exception == null) {
                    handleSrvccStateChanged((int[]) ar.result);
                } else {
                    Rlog.e(LOG_TAG, "Srvcc exception: " + ar.exception);
                }
                break;

            case EVENT_UNSOL_OEM_HOOK_RAW:
                ar = (AsyncResult)msg.obj;
                if (ar.exception == null) {
                    byte[] data = (byte[])ar.result;
                    Rlog.d(LOG_TAG, "EVENT_UNSOL_OEM_HOOK_RAW data="
                            + IccUtils.bytesToHexString(data));
                    //mNotifier.notifyOemHookRawEventForSubscriber(getSubId(), data);
                } else {
                    Rlog.e(LOG_TAG, "OEM hook raw exception: " + ar.exception);
                }
                break;

            case EVENT_GET_RADIO_CAPABILITY:
                ar = (AsyncResult) msg.obj;
                RadioCapability rc = (RadioCapability) ar.result;
                if (ar.exception != null) {
                    Rlog.d(LOG_TAG, "get phone radio capability fail,"
                            + "no need to change mRadioAccessFamily");
                } else {
                    mRadioAccessFamily = rc.getRadioAccessFamily();
                }
                Rlog.d(LOG_TAG, "EVENT_GET_RADIO_CAPABILITY :"
                        + "phone " + mPhoneId + ", RAF : " + mRadioAccessFamily);
                break;

            /// M: CC060: [ALPS00093395] Notify SIP call RING @{
            case EVENT_SIP_CALL_RING:
                int token = msg.arg1;
                Rlog.d(LOG_TAG, "Event EVENT_SIP_CALL_RING Received state=" + getState() + " token=" + token);
                if (getState() == PhoneConstants.State.RINGING && token == mSipCallRingContinueToken) {
                    notifyIncomingRing();
                    mSipCallRingContinueToken++;
                    sendMessageDelayed(
                            obtainMessage(EVENT_SIP_CALL_RING, mSipCallRingContinueToken, 0), mCallRingDelay);
                }
                break;
            /// @}

            default:
                throw new RuntimeException("unexpected event not handled");
        }
    }

    private void handleSrvccStateChanged(int[] ret) {
        Rlog.d(LOG_TAG, "handleSrvccStateChanged");

        ArrayList<Connection> conn = null;
        ImsPhone imsPhone = mImsPhone;
        Call.SrvccState srvccState = Call.SrvccState.NONE;
        if (ret != null && ret.length != 0) {
            int state = ret[0];
            /// M: for debug. @{
            Rlog.d(LOG_TAG, "srvccState: " + state);
            /// @}
            switch(state) {
                case VoLteServiceState.HANDOVER_STARTED:
                    srvccState = Call.SrvccState.STARTED;
                    if (imsPhone != null) {
                        conn = imsPhone.getHandoverConnection();
                        migrateFrom(imsPhone);
                    } else {
                        Rlog.d(LOG_TAG, "HANDOVER_STARTED: mImsPhone null");
                    }
                    break;
                case VoLteServiceState.HANDOVER_COMPLETED:
                    srvccState = Call.SrvccState.COMPLETED;
                    if (imsPhone != null) {
                        imsPhone.notifySrvccState(srvccState);
                    } else {
                        Rlog.d(LOG_TAG, "HANDOVER_COMPLETED: mImsPhone null");
                    }
                    break;
                case VoLteServiceState.HANDOVER_FAILED:
                case VoLteServiceState.HANDOVER_CANCELED:
                    srvccState = Call.SrvccState.FAILED;
                    break;

                default:
                    //ignore invalid state
                    return;
            }

            getCallTracker().notifySrvccState(srvccState, conn);

            VoLteServiceState lteState = new VoLteServiceState(state);
            notifyVoLteServiceStateChanged(lteState);
        }
    }

    // Inherited documentation suffices.
    @Override
    public Context getContext() {
        return mContext;
    }

    // Will be called when icc changed
    protected abstract void onUpdateIccAvailability();

    /**
     * Disables the DNS check (i.e., allows "0.0.0.0").
     * Useful for lab testing environment.
     * @param b true disables the check, false enables.
     */
    @Override
    public void disableDnsCheck(boolean b) {
        mDnsCheckDisabled = b;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean(DNS_SERVER_CHECK_DISABLED_KEY, b);
        editor.apply();
    }

    /**
     * Returns true if the DNS check is currently disabled.
     */
    @Override
    public boolean isDnsCheckDisabled() {
        return mDnsCheckDisabled;
    }

    // Inherited documentation suffices.
    @Override
    public void registerForPreciseCallStateChanged(Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mPreciseCallStateRegistrants.addUnique(h, what, obj);
    }

    // Inherited documentation suffices.
    @Override
    public void unregisterForPreciseCallStateChanged(Handler h) {
        mPreciseCallStateRegistrants.remove(h);
    }

    /**
     * Subclasses of Phone probably want to replace this with a
     * version scoped to their packages
     */
    protected void notifyPreciseCallStateChangedP() {
        AsyncResult ar = new AsyncResult(null, this, null);
        mPreciseCallStateRegistrants.notifyRegistrants(ar);

        mNotifier.notifyPreciseCallState(this);
    }

    @Override
    public void registerForHandoverStateChanged(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        mHandoverRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForHandoverStateChanged(Handler h) {
        mHandoverRegistrants.remove(h);
    }

    /**
     * Subclasses of Phone probably want to replace this with a
     * version scoped to their packages
     * @param cn the handover connection
     */
    public void notifyHandoverStateChanged(Connection cn) {
       AsyncResult ar = new AsyncResult(null, cn, null);
       mHandoverRegistrants.notifyRegistrants(ar);
    }

    public void migrateFrom(PhoneBase from) {
        migrate(mHandoverRegistrants, from.mHandoverRegistrants);
        migrate(mPreciseCallStateRegistrants, from.mPreciseCallStateRegistrants);
        migrate(mNewRingingConnectionRegistrants, from.mNewRingingConnectionRegistrants);
        migrate(mIncomingRingRegistrants, from.mIncomingRingRegistrants);
        migrate(mDisconnectRegistrants, from.mDisconnectRegistrants);
        migrate(mServiceStateRegistrants, from.mServiceStateRegistrants);
        migrate(mMmiCompleteRegistrants, from.mMmiCompleteRegistrants);
        migrate(mMmiRegistrants, from.mMmiRegistrants);
        migrate(mUnknownConnectionRegistrants, from.mUnknownConnectionRegistrants);
        migrate(mSuppServiceFailedRegistrants, from.mSuppServiceFailedRegistrants);
    }

    public void migrate(RegistrantList to, RegistrantList from) {
        from.removeCleared();
        for (int i = 0, n = from.size(); i < n; i++) {
            to.add((Registrant) from.get(i));
        }
    }

    // Inherited documentation suffices.
    @Override
    public void registerForUnknownConnection(Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mUnknownConnectionRegistrants.addUnique(h, what, obj);
    }

    // Inherited documentation suffices.
    @Override
    public void unregisterForUnknownConnection(Handler h) {
        mUnknownConnectionRegistrants.remove(h);
    }

    // Inherited documentation suffices.
    @Override
    public void registerForNewRingingConnection(
            Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mNewRingingConnectionRegistrants.addUnique(h, what, obj);
    }

    // Inherited documentation suffices.
    @Override
    public void unregisterForNewRingingConnection(Handler h) {
        mNewRingingConnectionRegistrants.remove(h);
    }

    // Inherited documentation suffices.
    @Override
    public void registerForInCallVoicePrivacyOn(Handler h, int what, Object obj){
        mCi.registerForInCallVoicePrivacyOn(h, what, obj);
    }

    // Inherited documentation suffices.
    @Override
    public void unregisterForInCallVoicePrivacyOn(Handler h){
        mCi.unregisterForInCallVoicePrivacyOn(h);
    }

    // Inherited documentation suffices.
    @Override
    public void registerForInCallVoicePrivacyOff(Handler h, int what, Object obj){
        mCi.registerForInCallVoicePrivacyOff(h, what, obj);
    }

    // Inherited documentation suffices.
    @Override
    public void unregisterForInCallVoicePrivacyOff(Handler h){
        mCi.unregisterForInCallVoicePrivacyOff(h);
    }

    // Inherited documentation suffices.
    @Override
    public void registerForIncomingRing(
            Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mIncomingRingRegistrants.addUnique(h, what, obj);
    }

    // Inherited documentation suffices.
    @Override
    public void unregisterForIncomingRing(Handler h) {
        mIncomingRingRegistrants.remove(h);
    }

    // Inherited documentation suffices.
    @Override
    public void registerForDisconnect(Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mDisconnectRegistrants.addUnique(h, what, obj);
    }

    // Inherited documentation suffices.
    @Override
    public void unregisterForDisconnect(Handler h) {
        mDisconnectRegistrants.remove(h);
    }

    // Inherited documentation suffices.
    @Override
    public void registerForSuppServiceFailed(Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mSuppServiceFailedRegistrants.addUnique(h, what, obj);
    }

    // Inherited documentation suffices.
    @Override
    public void unregisterForSuppServiceFailed(Handler h) {
        mSuppServiceFailedRegistrants.remove(h);
    }

    // Inherited documentation suffices.
    @Override
    public void registerForMmiInitiate(Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mMmiRegistrants.addUnique(h, what, obj);
    }

    // Inherited documentation suffices.
    @Override
    public void unregisterForMmiInitiate(Handler h) {
        mMmiRegistrants.remove(h);
    }

    // Inherited documentation suffices.
    @Override
    public void registerForMmiComplete(Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mMmiCompleteRegistrants.addUnique(h, what, obj);
    }

    // Inherited documentation suffices.
    @Override
    public void unregisterForMmiComplete(Handler h) {
        checkCorrectThread(h);

        mMmiCompleteRegistrants.remove(h);
    }

    public void registerForSimRecordsLoaded(Handler h, int what, Object obj) {
        logUnexpectedCdmaMethodCall("registerForSimRecordsLoaded");
    }

    public void unregisterForSimRecordsLoaded(Handler h) {
        logUnexpectedCdmaMethodCall("unregisterForSimRecordsLoaded");
    }

    @Override
    public void registerForTtyModeReceived(Handler h, int what, Object obj) {
    }

    @Override
    public void unregisterForTtyModeReceived(Handler h) {
    }

    @Override
    public void setNetworkSelectionModeAutomatic(Message response) {
        // wrap the response message in our own message along with
        // an empty string (to indicate automatic selection) for the
        // operator's id.
        NetworkSelectMessage nsm = new NetworkSelectMessage();
        nsm.message = response;
        nsm.operatorNumeric = "";
        nsm.operatorAlphaLong = "";

        Message msg = obtainMessage(EVENT_SET_NETWORK_AUTOMATIC_COMPLETE, nsm);
        mCi.setNetworkSelectionModeAutomatic(msg);

        updateSavedNetworkOperator(nsm);
    }

    @Override
    public void getNetworkSelectionMode(Message message) {
        mCi.getNetworkSelectionMode(message);
    }

    @Override
    public void selectNetworkManually(OperatorInfo network, Message response) {
        // wrap the response message in our own message along with
        // the operator's id.
        NetworkSelectMessage nsm = new NetworkSelectMessage();
        nsm.message = response;
        nsm.operatorNumeric = network.getOperatorNumeric();
        nsm.operatorAlphaLong = network.getOperatorAlphaLong();

        Message msg = obtainMessage(EVENT_SET_NETWORK_MANUAL_COMPLETE, nsm);
        mCi.setNetworkSelectionModeManual(network.getOperatorNumeric(), msg);

        updateSavedNetworkOperator(nsm);
    }

    private void updateSavedNetworkOperator(NetworkSelectMessage nsm) {
        int subId = getSubId();
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            // open the shared preferences editor, and write the value.
            // nsm.operatorNumeric is "" if we're in automatic.selection.
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
            SharedPreferences.Editor editor = sp.edit();
            editor.putString(NETWORK_SELECTION_KEY + subId, nsm.operatorNumeric);
            editor.putString(NETWORK_SELECTION_NAME_KEY + subId, nsm.operatorAlphaLong);

            // commit and log the result.
            if (!editor.commit()) {
                Rlog.e(LOG_TAG, "failed to commit network selection preference");
            }
        } else {
            Rlog.e(LOG_TAG, "Cannot update network selection preference due to invalid subId " +
                    subId);
        }
    }

    /**
     * Used to track the settings upon completion of the network change.
     */
    private void handleSetSelectNetwork(AsyncResult ar) {
        // look for our wrapper within the asyncresult, skip the rest if it
        // is null.
        if (!(ar.userObj instanceof NetworkSelectMessage)) {
            Rlog.e(LOG_TAG, "unexpected result from user object.");
            return;
        }

        NetworkSelectMessage nsm = (NetworkSelectMessage) ar.userObj;

        // found the object, now we send off the message we had originally
        // attached to the request.
        if (nsm.message != null) {
            AsyncResult.forMessage(nsm.message, ar.result, ar.exception);
            nsm.message.sendToTarget();
        }
    }

    /**
     * Method to retrieve the saved operator id from the Shared Preferences
     */
    private String getSavedNetworkSelection() {
        // open the shared preferences and search with our key.
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        return sp.getString(NETWORK_SELECTION_KEY + getSubId(), "");
    }

    /**
     * Method to restore the previously saved operator id, or reset to
     * automatic selection, all depending upon the value in the shared
     * preferences.
     */
    public void restoreSavedNetworkSelection(Message response) {
        // retrieve the operator id
        String networkSelection = getSavedNetworkSelection();

        // set to auto if the id is empty, otherwise select the network.
        if (TextUtils.isEmpty(networkSelection)) {
            mCi.setNetworkSelectionModeAutomatic(response);
        } else {
            mCi.setNetworkSelectionModeManual(networkSelection, response);
        }
    }

    // Inherited documentation suffices.
    @Override
    public void setUnitTestMode(boolean f) {
        mUnitTestMode = f;
    }

    // Inherited documentation suffices.
    @Override
    public boolean getUnitTestMode() {
        return mUnitTestMode;
    }

    /**
     * To be invoked when a voice call Connection disconnects.
     *
     * Subclasses of Phone probably want to replace this with a
     * version scoped to their packages
     */
    protected void notifyDisconnectP(Connection cn) {
        AsyncResult ar = new AsyncResult(null, cn, null);
        mDisconnectRegistrants.notifyRegistrants(ar);
    }

    // Inherited documentation suffices.
    @Override
    public void registerForServiceStateChanged(
            Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mServiceStateRegistrants.add(h, what, obj);
    }

    // Inherited documentation suffices.
    @Override
    public void unregisterForServiceStateChanged(Handler h) {
        mServiceStateRegistrants.remove(h);
    }

    // Inherited documentation suffices.
    @Override
    public void registerForRingbackTone(Handler h, int what, Object obj) {
        mCi.registerForRingbackTone(h, what, obj);
    }

    // Inherited documentation suffices.
    @Override
    public void unregisterForRingbackTone(Handler h) {
        mCi.unregisterForRingbackTone(h);
    }

    // Inherited documentation suffices.
    @Override
    public void registerForOnHoldTone(Handler h, int what, Object obj) {
    }

    // Inherited documentation suffices.
    @Override
    public void unregisterForOnHoldTone(Handler h) {
    }

    // Inherited documentation suffices.
    @Override
    public void registerForResendIncallMute(Handler h, int what, Object obj) {
        mCi.registerForResendIncallMute(h, what, obj);
    }

    // Inherited documentation suffices.
    @Override
    public void unregisterForResendIncallMute(Handler h) {
        mCi.unregisterForResendIncallMute(h);
    }

    @Override
    public void setEchoSuppressionEnabled() {
        // no need for regular phone
    }

    /**
     * Subclasses of Phone probably want to replace this with a
     * version scoped to their packages
     */
    protected void notifyServiceStateChangedP(ServiceState ss) {
        AsyncResult ar = new AsyncResult(null, ss, null);
        mServiceStateRegistrants.notifyRegistrants(ar);

        mNotifier.notifyServiceState(this);

        if((ss.getState() != ServiceState.STATE_POWER_OFF) && (SystemProperties.get("ril.charging_stop_enable","0").equals("1"))) {

            // send special AT cmd to MD
            sendMessageDelayed(
                    obtainMessage(EVENT_CHARGING_STOP, 0, 0), 60000);
        }

    }

    // Inherited documentation suffices.
    @Override
    public SimulatedRadioControl getSimulatedRadioControl() {
        return mSimulatedRadioControl;
    }

    /**
     * Verifies the current thread is the same as the thread originally
     * used in the initialization of this instance. Throws RuntimeException
     * if not.
     *
     * @exception RuntimeException if the current thread is not
     * the thread that originally obtained this PhoneBase instance.
     */
    private void checkCorrectThread(Handler h) {
        if (h.getLooper() != mLooper) {
            throw new RuntimeException(
                    "com.android.internal.telephony.Phone must be used from within one thread");
        }
    }

    /**
     * Set the properties by matching the carrier string in
     * a string-array resource
     */
    private void setPropertiesByCarrier() {
        String carrier = SystemProperties.get("ro.carrier");

        if (null == carrier || 0 == carrier.length() || "unknown".equals(carrier)) {
            return;
        }

        CharSequence[] carrierLocales = mContext.
                getResources().getTextArray(R.array.carrier_properties);

        for (int i = 0; i < carrierLocales.length; i+=3) {
            String c = carrierLocales[i].toString();
            if (carrier.equals(c)) {
                final Locale l = Locale.forLanguageTag(carrierLocales[i + 1].toString().replace('_', '-'));
                final String country = l.getCountry();
                MccTable.setSystemLocale(mContext, l.getLanguage(), country);

                if (!country.isEmpty()) {
                    try {
                        Settings.Global.getInt(mContext.getContentResolver(),
                                Settings.Global.WIFI_COUNTRY_CODE);
                    } catch (Settings.SettingNotFoundException e) {
                        // note this is not persisting
                        WifiManager wM = (WifiManager)
                                mContext.getSystemService(Context.WIFI_SERVICE);
                        wM.setCountryCode(country, false);
                    }
                }
                return;
            }
        }
    }

    /**
     * Get state
     */
    @Override
    public abstract PhoneConstants.State getState();

    /**
     * Retrieves the IccFileHandler of the Phone instance
     */
    public IccFileHandler getIccFileHandler(){
        UiccCardApplication uiccApplication = mUiccApplication.get();
        IccFileHandler fh;

        if (uiccApplication == null) {
            Rlog.d(LOG_TAG, "getIccFileHandler: uiccApplication == null, return null");
            fh = null;
        } else {
            fh = uiccApplication.getIccFileHandler();
        }

        Rlog.d(LOG_TAG, "getIccFileHandler: fh=" + fh);
        return fh;
    }

    /*
     * Retrieves the Handler of the Phone instance
     */
    public Handler getHandler() {
        return this;
    }

    @Override
    public void updatePhoneObject(int voiceRadioTech) {
        // Only the PhoneProxy can update the phone object.
        /// M: c2k modify, for multiple SIM, call the corresponding PhoneProxy to update object. @{
        // PhoneFactory.getDefaultPhone().updatePhoneObject(voiceRadioTech);
        PhoneFactory.getPhone(mPhoneId).updatePhoneObject(voiceRadioTech);
        /// @}
    }

    /**
    * Retrieves the ServiceStateTracker of the phone instance.
    */
    public ServiceStateTracker getServiceStateTracker() {
        return null;
    }

    /**
    * Get call tracker
    */
    public CallTracker getCallTracker() {
        return null;
    }

    public AppType getCurrentUiccAppType() {
        UiccCardApplication currentApp = mUiccApplication.get();
        if (currentApp != null) {
            return currentApp.getType();
        }
        return AppType.APPTYPE_UNKNOWN;
    }

    @Override
    public IccCard getIccCard() {
        return null;
        //throw new Exception("getIccCard Shouldn't be called from PhoneBase");
    }

    @Override
    public String getIccSerialNumber() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getIccId() : null;
    }

    @Override
    public boolean getIccRecordsLoaded() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getRecordsLoaded() : false;
    }

    /**
     * @return all available cell information or null if none.
     */
    @Override
    public List<CellInfo> getAllCellInfo() {
        List<CellInfo> cellInfoList = getServiceStateTracker().getAllCellInfo();
        return privatizeCellInfoList(cellInfoList);
    }

    /**
     * Clear CDMA base station lat/long values if location setting is disabled.
     * @param cellInfoList the original cell info list from the RIL
     * @return the original list with CDMA lat/long cleared if necessary
     */
    private List<CellInfo> privatizeCellInfoList(List<CellInfo> cellInfoList) {
        int mode = Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);
        if (mode == Settings.Secure.LOCATION_MODE_OFF) {
            ArrayList<CellInfo> privateCellInfoList = new ArrayList<CellInfo>(cellInfoList.size());
            // clear lat/lon values for location privacy
            for (CellInfo c : cellInfoList) {
                if (c instanceof CellInfoCdma) {
                    CellInfoCdma cellInfoCdma = (CellInfoCdma) c;
                    CellIdentityCdma cellIdentity = cellInfoCdma.getCellIdentity();
                    CellIdentityCdma maskedCellIdentity = new CellIdentityCdma(
                            cellIdentity.getNetworkId(),
                            cellIdentity.getSystemId(),
                            cellIdentity.getBasestationId(),
                            Integer.MAX_VALUE, Integer.MAX_VALUE);
                    CellInfoCdma privateCellInfoCdma = new CellInfoCdma(cellInfoCdma);
                    privateCellInfoCdma.setCellIdentity(maskedCellIdentity);
                    privateCellInfoList.add(privateCellInfoCdma);
                } else {
                    privateCellInfoList.add(c);
                }
            }
            cellInfoList = privateCellInfoList;
        }
        return cellInfoList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCellInfoListRate(int rateInMillis) {
        mCi.setCellInfoListRate(rateInMillis, null);
        // M: MTK Add
        getServiceStateTracker().setCellInfoRate(rateInMillis);
    }

    @Override
    /** @return true if there are messages waiting, false otherwise. */
    public boolean getMessageWaitingIndicator() {
        return mVmCount != 0;
    }

    @Override
    public boolean getCallForwardingIndicator() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getVoiceCallForwardingFlag() : false;
    }

    /**
     *  Query the status of the CDMA roaming preference
     */
    @Override
    public void queryCdmaRoamingPreference(Message response) {
        mCi.queryCdmaRoamingPreference(response);
    }

    /**
     * Get the signal strength
     */
    @Override
    public SignalStrength getSignalStrength() {
        ServiceStateTracker sst = getServiceStateTracker();
        if (sst == null) {
            return new SignalStrength();
        } else {
            return sst.getSignalStrength();
        }
    }

    /**
     *  Set the status of the CDMA roaming preference
     */
    @Override
    public void setCdmaRoamingPreference(int cdmaRoamingType, Message response) {
        mCi.setCdmaRoamingPreference(cdmaRoamingType, response);
    }

    /**
     *  Set the status of the CDMA subscription mode
     */
    @Override
    public void setCdmaSubscription(int cdmaSubscriptionType, Message response) {
        mCi.setCdmaSubscriptionSource(cdmaSubscriptionType, response);
    }

    /**
     *  Set the preferred Network Type: Global, CDMA only or GSM/UMTS only
     */
    @Override
    public void setPreferredNetworkType(int networkType, Message response) {
        mCi.setPreferredNetworkType(networkType, response);
    }

    @Override
    public void getPreferredNetworkType(Message response) {
        mCi.getPreferredNetworkType(response);
    }

    @Override
    public void getSmscAddress(Message result) {
        mCi.getSmscAddress(result);
    }

    @Override
    public void setSmscAddress(String address, Message result) {
        mCi.setSmscAddress(address, result);
    }

    @Override
    public void setTTYMode(int ttyMode, Message onComplete) {
        mCi.setTTYMode(ttyMode, onComplete);
    }

    @Override
    public void setUiTTYMode(int uiTtyMode, Message onComplete) {
        Rlog.d(LOG_TAG, "unexpected setUiTTYMode method call");
    }

    @Override
    public void queryTTYMode(Message onComplete) {
        mCi.queryTTYMode(onComplete);
    }

    @Override
    public void enableEnhancedVoicePrivacy(boolean enable, Message onComplete) {
        // This function should be overridden by the class CDMAPhone. Not implemented in GSMPhone.
        logUnexpectedCdmaMethodCall("enableEnhancedVoicePrivacy");
    }

    @Override
    public void getEnhancedVoicePrivacy(Message onComplete) {
        // This function should be overridden by the class CDMAPhone. Not implemented in GSMPhone.
        logUnexpectedCdmaMethodCall("getEnhancedVoicePrivacy");
    }

    @Override
    public void setBandMode(int bandMode, Message response) {
        mCi.setBandMode(bandMode, response);
    }

    @Override
    public void queryAvailableBandMode(Message response) {
        mCi.queryAvailableBandMode(response);
    }

    @Override
    public void invokeOemRilRequestRaw(byte[] data, Message response) {
        mCi.invokeOemRilRequestRaw(data, response);
    }

    @Override
    public void invokeOemRilRequestStrings(String[] strings, Message response) {
        mCi.invokeOemRilRequestStrings(strings, response);
    }

    @Override
    public void nvReadItem(int itemID, Message response) {
        mCi.nvReadItem(itemID, response);
    }

    @Override
    public void nvWriteItem(int itemID, String itemValue, Message response) {
        mCi.nvWriteItem(itemID, itemValue, response);
    }

    @Override
    public void nvWriteCdmaPrl(byte[] preferredRoamingList, Message response) {
        mCi.nvWriteCdmaPrl(preferredRoamingList, response);
    }

    @Override
    public void nvResetConfig(int resetType, Message response) {
        mCi.nvResetConfig(resetType, response);
    }

    @Override
    public void notifyDataActivity() {
        mNotifier.notifyDataActivity(this);
    }

    public void notifyMessageWaitingIndicator() {
        // Do not notify voice mail waiting if device doesn't support voice
        if (!mIsVoiceCapable)
            return;

        // This function is added to send the notification to DefaultPhoneNotifier.
        mNotifier.notifyMessageWaitingChanged(this);
    }

    public void notifyDataConnection(String reason, String apnType,
            PhoneConstants.DataState state) {
        mNotifier.notifyDataConnection(this, reason, apnType, state);
    }

    public void notifyDataConnection(String reason, String apnType) {
        mNotifier.notifyDataConnection(this, reason, apnType, getDataConnectionState(apnType));
    }

    public void notifyDataConnection(String reason) {
        String types[] = getActiveApnTypes();
        for (String apnType : types) {
            mNotifier.notifyDataConnection(this, reason, apnType, getDataConnectionState(apnType));
        }
    }

    public void notifyOtaspChanged(int otaspMode) {
        mNotifier.notifyOtaspChanged(this, otaspMode);
    }

    // M: [LTE][Low Power][UL traffic shaping] Start
    public void notifyLteAccessStratumChanged(String state) {
        mNotifier.notifyLteAccessStratumChanged(this, state);
    }

    public void notifyPsNetworkTypeChanged(int nwType) {
        mNotifier.notifyPsNetworkTypeChanged(this, nwType);
    }

    public void notifySharedDefaultApnStateChanged(boolean isSharedDefaultApn) {
        mNotifier.notifySharedDefaultApnStateChanged(this, isSharedDefaultApn);
    }
    // M: [LTE][Low Power][UL traffic shaping] End

    public void notifySignalStrength() {
        mNotifier.notifySignalStrength(this);
    }

    public void notifyCellInfo(List<CellInfo> cellInfo) {
		//add by zhangjinqiang for HQ01493260 --start
		if(cellInfo!=null){
        		mNotifier.notifyCellInfo(this, privatizeCellInfoList(cellInfo));
		}
		//add by zjq end
    }

    public void notifyDataConnectionRealTimeInfo(DataConnectionRealTimeInfo dcRtInfo) {
        mNotifier.notifyDataConnectionRealTimeInfo(this, dcRtInfo);
    }

    public void notifyVoLteServiceStateChanged(VoLteServiceState lteState) {
        mNotifier.notifyVoLteServiceStateChanged(this, lteState);
    }

    /**
     * @return true if a mobile originating emergency call is active
     */
    public boolean isInEmergencyCall() {
        return false;
    }

    /**
     * @return true if we are in the emergency call back mode. This is a period where
     * the phone should be using as little power as possible and be ready to receive an
     * incoming call from the emergency operator.
     */
    public boolean isInEcm() {
        return false;
    }

    @Override
    public abstract int getPhoneType();

    /** @hide */
    /** @return number of voicemails */
    @Override
    public int getVoiceMessageCount(){
        return mVmCount;
    }

    /** sets the voice mail count of the phone and notifies listeners. */
    public void setVoiceMessageCount(int countWaiting) {
        mVmCount = countWaiting;
        // notify listeners of voice mail
        notifyMessageWaitingIndicator();
    }

    /** gets the voice mail count from preferences */
    protected int getStoredVoiceMessageCount() {
        int countVoiceMessages = 0;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        String subscriberId = sp.getString(VM_ID, null);
        String currentSubscriberId = getSubscriberId();

        Rlog.d(LOG_TAG, "Voicemail count retrieval for subscriberId = " + subscriberId +
                " current subscriberId = " + currentSubscriberId);

        if ((subscriberId != null) && (currentSubscriberId != null)
                && (currentSubscriberId.equals(subscriberId))) {
            // get voice mail count from preferences
            countVoiceMessages = sp.getInt(VM_COUNT, 0);
            Rlog.d(LOG_TAG, "Voice Mail Count from preference = " + countVoiceMessages);
        }
        return countVoiceMessages;
    }

    /**
     * Returns the CDMA ERI icon index to display
     */
    @Override
    public int getCdmaEriIconIndex() {
        logUnexpectedCdmaMethodCall("getCdmaEriIconIndex");
        return -1;
    }

    /**
     * Returns the CDMA ERI icon mode,
     * 0 - ON
     * 1 - FLASHING
     */
    @Override
    public int getCdmaEriIconMode() {
        logUnexpectedCdmaMethodCall("getCdmaEriIconMode");
        return -1;
    }

    /**
     * Returns the CDMA ERI text,
     */
    @Override
    public String getCdmaEriText() {
        logUnexpectedCdmaMethodCall("getCdmaEriText");
        return "GSM nw, no ERI";
    }

    @Override
    public String getCdmaMin() {
        // This function should be overridden by the class CDMAPhone. Not implemented in GSMPhone.
        logUnexpectedCdmaMethodCall("getCdmaMin");
        return null;
    }

    @Override
    public boolean isMinInfoReady() {
        // This function should be overridden by the class CDMAPhone. Not implemented in GSMPhone.
        logUnexpectedCdmaMethodCall("isMinInfoReady");
        return false;
    }

    @Override
    public String getCdmaPrlVersion(){
        //  This function should be overridden by the class CDMAPhone. Not implemented in GSMPhone.
        logUnexpectedCdmaMethodCall("getCdmaPrlVersion");
        return null;
    }

    @Override
    public void sendBurstDtmf(String dtmfString, int on, int off, Message onComplete) {
        // This function should be overridden by the class CDMAPhone. Not implemented in GSMPhone.
        logUnexpectedCdmaMethodCall("sendBurstDtmf");
    }

    @Override
    public void exitEmergencyCallbackMode() {
        // This function should be overridden by the class CDMAPhone. Not implemented in GSMPhone.
        logUnexpectedCdmaMethodCall("exitEmergencyCallbackMode");
    }

    @Override
    public void registerForCdmaOtaStatusChange(Handler h, int what, Object obj) {
        // This function should be overridden by the class CDMAPhone. Not implemented in GSMPhone.
        logUnexpectedCdmaMethodCall("registerForCdmaOtaStatusChange");
    }

    @Override
    public void unregisterForCdmaOtaStatusChange(Handler h) {
        // This function should be overridden by the class CDMAPhone. Not implemented in GSMPhone.
        logUnexpectedCdmaMethodCall("unregisterForCdmaOtaStatusChange");
    }

    @Override
    public void registerForSubscriptionInfoReady(Handler h, int what, Object obj) {
        // This function should be overridden by the class CDMAPhone. Not implemented in GSMPhone.
        logUnexpectedCdmaMethodCall("registerForSubscriptionInfoReady");
    }

    @Override
    public void unregisterForSubscriptionInfoReady(Handler h) {
        // This function should be overridden by the class CDMAPhone. Not implemented in GSMPhone.
        logUnexpectedCdmaMethodCall("unregisterForSubscriptionInfoReady");
    }

    /**
     * Returns true if OTA Service Provisioning needs to be performed.
     * If not overridden return false.
     */
    @Override
    public boolean needsOtaServiceProvisioning() {
        return false;
    }

    /**
     * Return true if number is an OTASP number.
     * If not overridden return false.
     */
    @Override
    public  boolean isOtaSpNumber(String dialStr) {
        return false;
    }

    @Override
    public void registerForCallWaiting(Handler h, int what, Object obj){
        // This function should be overridden by the class CDMAPhone. Not implemented in GSMPhone.
        logUnexpectedCdmaMethodCall("registerForCallWaiting");
    }

    @Override
    public void unregisterForCallWaiting(Handler h){
        // This function should be overridden by the class CDMAPhone. Not implemented in GSMPhone.
        logUnexpectedCdmaMethodCall("unregisterForCallWaiting");
    }

    @Override
    public void registerForEcmTimerReset(Handler h, int what, Object obj) {
        // This function should be overridden by the class CDMAPhone. Not implemented in GSMPhone.
        logUnexpectedCdmaMethodCall("registerForEcmTimerReset");
    }

    @Override
    public void unregisterForEcmTimerReset(Handler h) {
        // This function should be overridden by the class CDMAPhone. Not implemented in GSMPhone.
        logUnexpectedCdmaMethodCall("unregisterForEcmTimerReset");
    }

    @Override
    public void registerForSignalInfo(Handler h, int what, Object obj) {
        mCi.registerForSignalInfo(h, what, obj);
    }

    @Override
    public void unregisterForSignalInfo(Handler h) {
        mCi.unregisterForSignalInfo(h);
    }

    @Override
    public void registerForDisplayInfo(Handler h, int what, Object obj) {
        mCi.registerForDisplayInfo(h, what, obj);
    }

     @Override
    public void unregisterForDisplayInfo(Handler h) {
         mCi.unregisterForDisplayInfo(h);
     }

    @Override
    public void registerForNumberInfo(Handler h, int what, Object obj) {
        mCi.registerForNumberInfo(h, what, obj);
    }

    @Override
    public void unregisterForNumberInfo(Handler h) {
        mCi.unregisterForNumberInfo(h);
    }

    @Override
    public void registerForRedirectedNumberInfo(Handler h, int what, Object obj) {
        mCi.registerForRedirectedNumberInfo(h, what, obj);
    }

    @Override
    public void unregisterForRedirectedNumberInfo(Handler h) {
        mCi.unregisterForRedirectedNumberInfo(h);
    }

    @Override
    public void registerForLineControlInfo(Handler h, int what, Object obj) {
        mCi.registerForLineControlInfo( h, what, obj);
    }

    @Override
    public void unregisterForLineControlInfo(Handler h) {
        mCi.unregisterForLineControlInfo(h);
    }

    @Override
    public void registerFoT53ClirlInfo(Handler h, int what, Object obj) {
        mCi.registerFoT53ClirlInfo(h, what, obj);
    }

    @Override
    public void unregisterForT53ClirInfo(Handler h) {
        mCi.unregisterForT53ClirInfo(h);
    }

    @Override
    public void registerForT53AudioControlInfo(Handler h, int what, Object obj) {
        mCi.registerForT53AudioControlInfo( h, what, obj);
    }

    @Override
    public void unregisterForT53AudioControlInfo(Handler h) {
        mCi.unregisterForT53AudioControlInfo(h);
    }

     @Override
    public void setOnEcbModeExitResponse(Handler h, int what, Object obj){
         // This function should be overridden by the class CDMAPhone. Not implemented in GSMPhone.
         logUnexpectedCdmaMethodCall("setOnEcbModeExitResponse");
     }

     @Override
    public void unsetOnEcbModeExitResponse(Handler h){
        // This function should be overridden by the class CDMAPhone. Not implemented in GSMPhone.
         logUnexpectedCdmaMethodCall("unsetOnEcbModeExitResponse");
     }

    @Override
    public void registerForRadioOffOrNotAvailable(Handler h, int what, Object obj) {
        mRadioOffOrNotAvailableRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForRadioOffOrNotAvailable(Handler h) {
        mRadioOffOrNotAvailableRegistrants.remove(h);
    }

    @Override
    public String[] getActiveApnTypes() {
        return mDcTracker.getActiveApnTypes();
    }

    @Override
    public boolean hasMatchedTetherApnSetting() {
        return mDcTracker.hasMatchedTetherApnSetting();
    }

    @Override
    public String getActiveApnHost(String apnType) {
        return mDcTracker.getActiveApnString(apnType);
    }

    @Override
    public LinkProperties getLinkProperties(String apnType) {
        return mDcTracker.getLinkProperties(apnType);
    }

    @Override
    public NetworkCapabilities getNetworkCapabilities(String apnType) {
        return mDcTracker.getNetworkCapabilities(apnType);
    }

    @Override
    public boolean isDataConnectivityPossible() {
        return isDataConnectivityPossible(PhoneConstants.APN_TYPE_DEFAULT);
    }

    @Override
    public boolean isDataConnectivityPossible(String apnType) {
        return ((mDcTracker != null) &&
                (mDcTracker.isDataPossible(apnType)));
    }

    /**
     * Notify registrants of a new ringing Connection.
     * Subclasses of Phone probably want to replace this with a
     * version scoped to their packages
     */
    public void notifyNewRingingConnectionP(Connection cn) {
        if (!mIsVoiceCapable)
            return;
        AsyncResult ar = new AsyncResult(null, cn, null);
        mNewRingingConnectionRegistrants.notifyRegistrants(ar);
    }

    /**
     * Notify registrants of a RING event.
     */
    private void notifyIncomingRing() {
        if (!mIsVoiceCapable)
            return;
        AsyncResult ar = new AsyncResult(null, this, null);
        mIncomingRingRegistrants.notifyRegistrants(ar);
    }

    /**
     * Send the incoming call Ring notification if conditions are right.
     */
    private void sendIncomingCallRingNotification(int token) {
        if (mIsVoiceCapable && !mDoesRilSendMultipleCallRing &&
                (token == mCallRingContinueToken)) {
            Rlog.d(LOG_TAG, "Sending notifyIncomingRing");
            notifyIncomingRing();
            sendMessageDelayed(
                    obtainMessage(EVENT_CALL_RING_CONTINUE, token, 0), mCallRingDelay);
        } else {
            Rlog.d(LOG_TAG, "Ignoring ring notification request,"
                    + " mDoesRilSendMultipleCallRing=" + mDoesRilSendMultipleCallRing
                    + " token=" + token
                    + " mCallRingContinueToken=" + mCallRingContinueToken
                    + " mIsVoiceCapable=" + mIsVoiceCapable);
        }
    }

    @Override
    public boolean isCspPlmnEnabled() {
        // This function should be overridden by the class GSMPhone.
        // Not implemented in CDMAPhone.
        logUnexpectedGsmMethodCall("isCspPlmnEnabled");
        return false;
    }

    @Override
    public IsimRecords getIsimRecords() {
        Rlog.e(LOG_TAG, "getIsimRecords() is only supported on LTE devices");
        return null;
    }

    @Override
    public String getMsisdn() {
        logUnexpectedGsmMethodCall("getMsisdn");
        return null;
    }

    /**
     * Common error logger method for unexpected calls to CDMA-only methods.
     */
    private static void logUnexpectedCdmaMethodCall(String name)
    {
        Rlog.e(LOG_TAG, "Error! " + name + "() in PhoneBase should not be " +
                "called, CDMAPhone inactive.");
    }

    @Override
    public PhoneConstants.DataState getDataConnectionState() {
        return getDataConnectionState(PhoneConstants.APN_TYPE_DEFAULT);
    }

    /**
     * Common error logger method for unexpected calls to GSM/WCDMA-only methods.
     */
    private static void logUnexpectedGsmMethodCall(String name) {
        Rlog.e(LOG_TAG, "Error! " + name + "() in PhoneBase should not be " +
                "called, GSMPhone inactive.");
    }

    // Called by SimRecords which is constructed with a PhoneBase instead of a GSMPhone.
    public void notifyCallForwardingIndicator() {
        // This function should be overridden by the class GSMPhone. Not implemented in CDMAPhone.
        Rlog.e(LOG_TAG, "Error! This function should never be executed, inactive CDMAPhone.");
    }

    public void notifyDataConnectionFailed(String reason, String apnType) {
        mNotifier.notifyDataConnectionFailed(this, reason, apnType);
    }

    public void notifyPreciseDataConnectionFailed(String reason, String apnType, String apn,
            String failCause) {
        mNotifier.notifyPreciseDataConnectionFailed(this, reason, apnType, apn, failCause);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLteOnCdmaMode() {
        return mCi.getLteOnCdmaMode();
    }

    public void setVoiceMessageWaiting(int line, int countWaiting) {
        // This function should be overridden by class GSMPhone and CDMAPhone.
        Rlog.e(LOG_TAG, "Error! This function should never be executed, inactive Phone.");
    }

    /**
     * Gets the USIM service table from the UICC, if present and available.
     * @return an interface to the UsimServiceTable record, or null if not available
     */
    @Override
    public UsimServiceTable getUsimServiceTable() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getUsimServiceTable() : null;
    }

    /**
     * Gets the Uicc card corresponding to this phone.
     * @return the UiccCard object corresponding to the phone ID.
     */
    @Override
    public UiccCard getUiccCard() {
        return mUiccController.getUiccCard(mPhoneId);
    }

    /**
     * Get P-CSCF address from PCO after data connection is established or modified.
     * @param apnType the apnType, "ims" for IMS APN, "emergency" for EMERGENCY APN
     */
    @Override
    public String[] getPcscfAddress(String apnType) {
        return mDcTracker.getPcscfAddress(apnType);
    }

    /**
     * Set IMS registration state
     */
    @Override
    public void setImsRegistrationState(boolean registered) {
        mDcTracker.setImsRegistrationState(registered);
    }

    /**
     * Return an instance of a IMS phone
     */
    @Override
    public Phone getImsPhone() {
        return mImsPhone;
    }

    @Override
    public ImsPhone relinquishOwnershipOfImsPhone() {
        synchronized (mImsLock) {
            if (mImsPhone == null)
                return null;

            ImsPhone imsPhone = mImsPhone;
            mImsPhone = null;

            CallManager.getInstance().unregisterPhone(imsPhone);
            imsPhone.unregisterForSilentRedial(this);

            return imsPhone;
        }
    }

    @Override
    public void acquireOwnershipOfImsPhone(ImsPhone imsPhone) {
        synchronized (mImsLock) {
            if (imsPhone == null)
                return;

            if (mImsPhone != null) {
                Rlog.e(LOG_TAG, "acquireOwnershipOfImsPhone: non-null mImsPhone." +
                        " Shouldn't happen - but disposing");
                mImsPhone.dispose();
                // Potential GC issue if someone keeps a reference to ImsPhone.
                // However: this change will make sure that such a reference does
                // not access functions through NULL pointer.
                //mImsPhone.removeReferences();
            }

            mImsPhone = imsPhone;

            mImsServiceReady = true;
            mImsPhone.updateParentPhone(this);
            CallManager.getInstance().registerPhone(mImsPhone);
            mImsPhone.registerForSilentRedial(
                    this, EVENT_INITIATE_SILENT_REDIAL, null);
        }
    }

    protected void updateImsPhone() {
        synchronized (mImsLock) {
            Rlog.d(LOG_TAG, "updateImsPhone"
                    + " mImsServiceReady=" + mImsServiceReady);

            if (mImsServiceReady && (mImsPhone == null)) {
                mImsPhone = PhoneFactory.makeImsPhone(mNotifier, this);
                CallManager.getInstance().registerPhone(mImsPhone);
                mImsPhone.registerForSilentRedial(
                        this, EVENT_INITIATE_SILENT_REDIAL, null);
            } else if (!mImsServiceReady && (mImsPhone != null)) {
                CallManager.getInstance().unregisterPhone(mImsPhone);
                mImsPhone.unregisterForSilentRedial(this);

                mImsPhone.dispose();
                // Potential GC issue if someone keeps a reference to ImsPhone.
                // However: this change will make sure that such a reference does
                // not access functions through NULL pointer.
                //mImsPhone.removeReferences();
                mImsPhone = null;
            }
        }
    }

    /**
     * Dials a number.
     *
     * @param dialString The number to dial.
     * @param uusInfo The UUSInfo.
     * @param videoState The video state for the call.
     * @return The Connection.
     * @throws CallStateException
     */
    protected Connection dialInternal(String dialString, UUSInfo uusInfo, int videoState)
            throws CallStateException {
        // dialInternal shall be overriden by GSMPhone and CDMAPhone
        return null;
    }

    /**
     * Returns the subscription id.
     */
    public int getSubId() {
        return SubscriptionController.getInstance().getSubIdUsingPhoneId(mPhoneId);
    }

    /**
     * Returns the phone id.
     */
    public int getPhoneId() {
        return mPhoneId;
    }

    /**
     * Return the service state of mImsPhone if it is STATE_IN_SERVICE
     * otherwise return the current voice service state
     */
    @Override
    public int getVoicePhoneServiceState() {
        ImsPhone imsPhone = mImsPhone;
        if (imsPhone != null
                && imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE) {
            return ServiceState.STATE_IN_SERVICE;
        }
        return getServiceState().getState();
    }

    @Override
    public boolean setOperatorBrandOverride(String brand) {
        return false;
    }

    @Override
    public boolean setRoamingOverride(List<String> gsmRoamingList,
            List<String> gsmNonRoamingList, List<String> cdmaRoamingList,
            List<String> cdmaNonRoamingList) {
        String iccId = getIccSerialNumber();
        if (TextUtils.isEmpty(iccId)) {
            return false;
        }

        setRoamingOverrideHelper(gsmRoamingList, GSM_ROAMING_LIST_OVERRIDE_PREFIX, iccId);
        setRoamingOverrideHelper(gsmNonRoamingList, GSM_NON_ROAMING_LIST_OVERRIDE_PREFIX, iccId);
        setRoamingOverrideHelper(cdmaRoamingList, CDMA_ROAMING_LIST_OVERRIDE_PREFIX, iccId);
        setRoamingOverrideHelper(cdmaNonRoamingList, CDMA_NON_ROAMING_LIST_OVERRIDE_PREFIX, iccId);

        // Refresh.
        ServiceStateTracker tracker = getServiceStateTracker();
        if (tracker != null) {
            tracker.pollState();
        }
        return true;
    }

    private void setRoamingOverrideHelper(List<String> list, String prefix, String iccId) {
        SharedPreferences.Editor spEditor =
                PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        String key = prefix + iccId;
        if (list == null || list.isEmpty()) {
            spEditor.remove(key).commit();
        } else {
            spEditor.putStringSet(key, new HashSet<String>(list)).commit();
        }
    }

    public boolean isMccMncMarkedAsRoaming(String mccMnc) {
        return getRoamingOverrideHelper(GSM_ROAMING_LIST_OVERRIDE_PREFIX, mccMnc);
    }

    public boolean isMccMncMarkedAsNonRoaming(String mccMnc) {
        return getRoamingOverrideHelper(GSM_NON_ROAMING_LIST_OVERRIDE_PREFIX, mccMnc);
    }

    public boolean isSidMarkedAsRoaming(int SID) {
        return getRoamingOverrideHelper(CDMA_ROAMING_LIST_OVERRIDE_PREFIX,
                Integer.toString(SID));
    }

    public boolean isSidMarkedAsNonRoaming(int SID) {
        return getRoamingOverrideHelper(CDMA_NON_ROAMING_LIST_OVERRIDE_PREFIX,
                Integer.toString(SID));
    }

    /**
     * Get IMS Registration Status
     */
    @Override
    public boolean isImsRegistered() {
        ImsPhone imsPhone = mImsPhone;
        boolean isImsRegistered = false;
        if (imsPhone != null) {
            isImsRegistered = imsPhone.isImsRegistered();
        } else {
            ServiceStateTracker sst = getServiceStateTracker();
            if (sst != null) {
                isImsRegistered = sst.isImsRegistered();
            }
        }
        Rlog.d(LOG_TAG, "isImsRegistered =" + isImsRegistered);
        return isImsRegistered;
    }

    private boolean getRoamingOverrideHelper(String prefix, String key) {
        String iccId = getIccSerialNumber();
        if (TextUtils.isEmpty(iccId) || TextUtils.isEmpty(key)) {
            return false;
        }

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        Set<String> value = sp.getStringSet(prefix + iccId, null);
        if (value == null) {
            return false;
        }
        return value.contains(key);
    }

    @Override
    public boolean isRadioAvailable() {
        return mCi.getRadioState().isAvailable();
    }

    @Override
    public void shutdownRadio() {
        getServiceStateTracker().requestShutdown();
    }

    @Override
    public void setRadioCapability(RadioCapability rc, Message response) {
        mCi.setRadioCapability(rc, response);
    }

    @Override
    public int getRadioAccessFamily() {
        return mRadioAccessFamily;
    }

    @Override
    public int getSupportedRadioAccessFamily() {
        return mCi.getSupportedRadioAccessFamily();
    }

    @Override
    public void registerForRadioCapabilityChanged(Handler h, int what, Object obj) {
        mCi.registerForRadioCapabilityChanged(h, what, obj);
    }

    @Override
    public void unregisterForRadioCapabilityChanged(Handler h) {
        mCi.unregisterForRadioCapabilityChanged(this);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("PhoneBase: subId=" + getSubId());
        pw.println(" mPhoneId=" + mPhoneId);
        pw.println(" mCi=" + mCi);
        pw.println(" mDnsCheckDisabled=" + mDnsCheckDisabled);
        pw.println(" mDcTracker=" + mDcTracker);
        pw.println(" mDoesRilSendMultipleCallRing=" + mDoesRilSendMultipleCallRing);
        pw.println(" mCallRingContinueToken=" + mCallRingContinueToken);
        pw.println(" mCallRingDelay=" + mCallRingDelay);
        pw.println(" mIsTheCurrentActivePhone=" + mIsTheCurrentActivePhone);
        pw.println(" mIsVoiceCapable=" + mIsVoiceCapable);
        if (android.os.Build.TYPE.equals("eng")) {
            pw.println(" mIccRecords=" + mIccRecords.get());
        }
        pw.println(" mUiccApplication=" + mUiccApplication.get());
        pw.println(" mSmsStorageMonitor=" + mSmsStorageMonitor);
        pw.println(" mSmsUsageMonitor=" + mSmsUsageMonitor);
        pw.flush();
        pw.println(" mLooper=" + mLooper);
        pw.println(" mContext=" + mContext);
        pw.println(" mNotifier=" + mNotifier);
        pw.println(" mSimulatedRadioControl=" + mSimulatedRadioControl);
        pw.println(" mUnitTestMode=" + mUnitTestMode);
        pw.println(" isDnsCheckDisabled()=" + isDnsCheckDisabled());
        pw.println(" getUnitTestMode()=" + getUnitTestMode());
        pw.println(" getState()=" + getState());
        pw.println(" getIccSerialNumber()=" + getIccSerialNumber());
        pw.println(" getIccRecordsLoaded()=" + getIccRecordsLoaded());
        pw.println(" getMessageWaitingIndicator()=" + getMessageWaitingIndicator());
        pw.println(" getCallForwardingIndicator()=" + getCallForwardingIndicator());
        pw.println(" isInEmergencyCall()=" + isInEmergencyCall());
        pw.flush();
        pw.println(" isInEcm()=" + isInEcm());
        pw.println(" getPhoneName()=" + getPhoneName());
        pw.println(" getPhoneType()=" + getPhoneType());
        pw.println(" getVoiceMessageCount()=" + getVoiceMessageCount());
        pw.println(" getActiveApnTypes()=" + getActiveApnTypes());
        pw.println(" isDataConnectivityPossible()=" + isDataConnectivityPossible());
        pw.println(" needsOtaServiceProvisioning=" + needsOtaServiceProvisioning());
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");

        try {
            mDcTracker.dump(fd, pw, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");

        try {
            getServiceStateTracker().dump(fd, pw, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");

        try {
            getCallTracker().dump(fd, pw, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");

        try {
            ((RIL)mCi).dump(fd, pw, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");
    }

    /// M: CC060: [ALPS00093395] Notify SIP call RING @{
    protected void notifySipCallRing() {
       Rlog.d(LOG_TAG, "notify Sip Call Ring");
       mSipCallRingContinueToken = 0;
       sendMessage(obtainMessage(EVENT_SIP_CALL_RING, mSipCallRingContinueToken, 0));
    }
    /// @}

    /// M: CC010: Add RIL interface @{
    public void registerForCrssSuppServiceNotification(Handler h, int what, Object obj) {
    }

    public void unregisterForCrssSuppServiceNotification(Handler h) {
    }

    public void registerForVoiceCallIncomingIndication(
            Handler h, int what, Object obj) {
        Rlog.e(LOG_TAG, "registerForVoiceCallIncomingIndication");
        mVoiceCallIncomingIndicationRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForVoiceCallIncomingIndication(Handler h) {
        mVoiceCallIncomingIndicationRegistrants.remove(h);
    }

    public void registerForCipherIndication(Handler h, int what, Object obj) {
        mCipherIndicationRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForCipherIndication(Handler h) {
        mCipherIndicationRegistrants.remove(h);
    }
    /// @}

    /// M: CC077: 2/3G CAPABILITY_HIGH_DEF_AUDIO @{
    // Inherited documentation suffices.
    @Override
    public void registerForSpeechCodecInfo(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        mSpeechCodecInfoRegistrants.addUnique(h, what, obj);
    }

    // Inherited documentation suffices.
    @Override
    public void unregisterForSpeechCodecInfo(Handler h) {
        mSpeechCodecInfoRegistrants.remove(h);
    }
    /// @}

    /// M: CC010: Add RIL interface @{
    public void hangupAll() throws CallStateException {}
    /// @}

    /// M: For 3G VT only @{
    public void registerForVtStatusInfo(Handler h, int what, Object obj) {
        mCi.registerForVtStatusInfo(h, what, obj);
    }

    public void unregisterForVtStatusInfo(Handler h) {
        mCi.unregisterForVtStatusInfo(h);
    }
    /// @}

    // Added by M begin
    // ALPS00302702 RAT balancing
    public int getEfRatBalancing() {
        return IccRecords.EF_RAT_UNDEFINED;
    }

    // MVNO-API START
    public String getMvnoMatchType() {
        // override by child-class
        return null;
    }
    public String getMvnoPattern(String type) {
        // override by child-class
        return null;
    }
    // MVNO-API END

    /**
    *send BT SAP profile
    */
    public void sendBtSimProfile(int nAction, int nType, String strData, Message response) {
        mCi.sendBTSIMProfile(nAction, nType, strData, response);
    }

    @Override
    public void doGeneralSimAuthentication(int sessionId, int mode, int tag,
            String param1, String param2, Message result) {
    }

    @Override
    public void queryPhbStorageInfo(int type, Message response) {
    }

    // M: network part START
    public void registerForNeighboringInfo(Handler h, int what, Object obj) {
        mCi.registerForNeighboringInfo(h, what, obj);
    }

    public void unregisterForNeighboringInfo(Handler h) {
        mCi.unregisterForNeighboringInfo(h);
    }

    public void registerForNetworkInfo(Handler h, int what, Object obj) {
        mCi.registerForNetworkInfo(h, what, obj);
    }

    public void unregisterForNetworkInfo(Handler h) {
        mCi.unregisterForNetworkInfo(h);
    }

    /**
     * Refresh Spn Display due to configuration change
     */
    public void refreshSpnDisplay() {
    }


    public int getNetworkHideState() {
        return 0;
    }

    public String getLocatedPlmn() {
        return null;
    }

    public void getPolCapability(Message onComplete) {
    }

    public void getPol(Message onComplete) {
    }

    public void setPolEntry(NetworkInfoWithAcT networkWithAct, Message onComplete) {
    }

    public void
    cancelAvailableNetworks(Message response) {
    }

    public void setNetworkSelectionModeSemiAutomatic(OperatorInfo network, Message response){
    }

    // Femtocell (CSG) feature START
    public void getFemtoCellList(String operatorNumeric, int rat, Message response){
        // override by child-class
    }

    public void abortFemtoCellList(Message response){
        // override by child-class
    }

    public void selectFemtoCell(FemtoCellInfo femtocell, Message response){
        // override by child-class
    }
     // Femtocell (CSG) feature END
    //M: network part END

// VOLTE
    public int enableDedicateBearer(String apnType, boolean signalingFlag, QosStatus qosStatus, TftStatus tftStatus) {
        return ((DcTracker)mDcTracker).enableDedicateBearer(apnType, signalingFlag, qosStatus, tftStatus);
    }

    public int disableDedicateBearer(String reason, int cid) {
        return ((DcTracker)mDcTracker).disableDedicateBearer(reason, cid);
    }

    public int modifyDedicateBearer(int cid, QosStatus qosStatus, TftStatus tftStatus) {
        return ((DcTracker)mDcTracker).modifyDedicateBearer(cid, qosStatus, tftStatus);
    }

    public int abortEnableDedicateBearer(String reason, int ddcId) {
        return ((DcTracker)mDcTracker).abortEnableDedicateBearer(reason, ddcId);
    }

    public int pcscfDiscovery(String apnType, int cid, Message onComplete) {
        //since PCO, get it from DcTracker
        return ((DcTracker)mDcTracker).pcscfDiscovery(apnType, cid, onComplete);
    }

    public PcscfInfo getPcoPcscf(String apnType) {
        return ((DcTracker)mDcTracker).getPcscf(apnType);
    }

    public DedicateBearerProperties getDefaultBearerProperties(String apnType) {
        return ((DcTracker)mDcTracker).getDefaultBearerProperties(apnType);
    }

    public DedicateBearerProperties [] getConcatenatedBearersPropertiesOfDefaultBearer(String apnType) {
        return ((DcTracker)mDcTracker).getConcatenatedBearersPropertiesOfDefaultBearer(apnType);
    }

    public int setDefaultBearerConfig(String apnType, DefaultBearerConfig defaultBearerConfig) {
        return ((DcTracker)mDcTracker).setDefaultBearerConfig(apnType, defaultBearerConfig);
    }

    public int [] getDeactivateCidArray(String apnType) {
        return ((DcTracker)mDcTracker).getDeactivateCidArray(apnType);
    }

    public boolean isEmergencyCid(int cid) {
        return ((DcTracker) mDcTracker).isEmergencyCid(cid);
    }

    public DcFailCause getLastDataConnectionFailCause(String apnType) {
        return ((DcTracker)mDcTracker).getLastDataConnectionFailCause(apnType);
    }

    public boolean isDedicateBearer(int cid) {
        return ((DcTracker)mDcTracker).isDedicateBearer(cid);
    }

    public void clearDataBearer() {}

    /* M: SS part */
    public void getFacilityLock(String facility, String password, Message onComplete) {
    }

    public void setFacilityLock(String facility, boolean enable,
        String password, Message onComplete) {
    }

    public void changeBarringPassword(String facility, String oldPwd,
        String newPwd, Message onComplete) {
    }

    public void changeBarringPassword(String facility, String oldPwd,
        String newPwd, String newCfm, Message onComplete) {
    }
    /* M: SS part end */

    /// M: SS OP01 Ut @{
    public void getCallForwardInTimeSlot(int commandInterfaceCFReason,
            Message onComplete) {
    }

    public void setCallForwardInTimeSlot(int commandInterfaceCFAction,
            int commandInterfaceCFReason, String dialingNumber,
            int timerSeconds, long[] timeSlot, Message onComplete) {
    }

    public void saveTimeSlot(long[] timeSlot) {
        String timeSlotKey = CFU_TIME_SLOT + mPhoneId;
        String timeSlotString = "";
        if (timeSlot != null && timeSlot.length == 2) {
            timeSlotString = Long.toString(timeSlot[0]) + "," + Long.toString(timeSlot[1]);
        }
        SystemProperties.set(timeSlotKey, timeSlotString);
        Rlog.d(LOG_TAG, "timeSlotString = " + timeSlotString);
    }

    public long[] getTimeSlot() {
        String timeSlotKey = CFU_TIME_SLOT + mPhoneId;
        String timeSlotString = SystemProperties.get(timeSlotKey, "");
        long[] timeSlot = null;
        if (timeSlotString != null && !timeSlotString.equals("")) {
            String[] timeArray = timeSlotString.split(",");
            if (timeArray.length == 2) {
                timeSlot = new long[2];
                for (int i = 0; i < 2; i++) {
                    timeSlot[i] = Long.parseLong(timeArray[i]);
                    Calendar calenar = Calendar.getInstance(TimeZone.getDefault());
                    calenar.setTimeInMillis(timeSlot[i]);
                    int hour = calenar.get(Calendar.HOUR_OF_DAY);
                    int min = calenar.get(Calendar.MINUTE);
                    Calendar calenar2 = Calendar.getInstance(TimeZone.getDefault());
                    calenar2.set(Calendar.HOUR_OF_DAY, hour);
                    calenar2.set(Calendar.MINUTE, min);
                    timeSlot[i] = calenar2.getTimeInMillis();
                }
            }
        }
        Rlog.d(LOG_TAG, "timeSlot = " + Arrays.toString(timeSlot));
        return timeSlot;
    }
    /// @}

    /// M: IMS feature. @{
    /* Used to notify IMS conference call indication. */
    public void registerForImsConferenceCallNotification(Handler h, int what, Object obj) {
        mImsConferenceCallRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForImsConferenceCallNotification(Handler h) {
        mImsConferenceCallRegistrants.remove(h);
    }

    public void registerForCallInfoChangedNotification(Handler h, int what, Object obj) {
        mCallInfoChangedRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForCallInfoChangedNotification(Handler h) {
        mCallInfoChangedRegistrants.remove(h);
    }

    /**
    * Notify call info changed for PAU.
    *
    * @param callInfo Bundle
    */
    public void notifyCallInfoChanged(Bundle callInfo) {
        AsyncResult ar = new AsyncResult(null, callInfo, null);
        mCallInfoChangedRegistrants.notifyRegistrants(ar);
    }

    /**
    * Add a new member to VoLTE conference call according to the parameter - address.
    *
    * @param confCallId IMS(VoLTE) conference call id
    * @param address The address(phone number or SIP URI)
    * @param callIdToAdd The call id of call which is preparing to merge to current conference call.
    *
    */
    public void addConferenceMember(int confCallId, String address, int callIdToAdd) throws CallStateException {
       //do nothing
    }

    /**
     * Initiate a new conference host connection.
     *
     * @param numbers The dial numbers.
     * @param videoState The desired video state for the connection.
     * @exception CallStateException if a new outgoing call is not currently possible because
     * no more call slots exist or a call exists that is dialing, alerting, ringing, or waiting.
     * Other errors are handled asynchronously.
     * @return Connection the MO connection.
     * @hide
     */
    public Connection dial(List<String> numbers, int videoState) throws CallStateException {
        return null;
    }
    /// @}

    /* M: VoLTE SS part start */
    /**
     * Get the UT CS fallback status.
     *
     * @return The UT CS fallback status:
     *         {@link PhoneConstants.UT_CSFB_PS_PREFERRED}
     *         {@link PhoneConstants.UT_CSFB_ONCE}
     *         {@link PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT}.
     */
    public int getCsFallbackStatus() {
        Rlog.d(LOG_TAG, "getCsFallbackStatus is " + mCSFallbackMode);
        return mCSFallbackMode;
    }

    /**
     * Set the UT CS fallback status.
     *
     * @param newStatus The UT CS fallback status:
     *        {@link PhoneConstants.UT_CSFB_PS_PREFERRED} for PS domain UT/XCAP SS request.
     *        {@link PhoneConstants.UT_CSFB_ONCE} to fallback to CS domain SS request only once.
     *        {@link PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT} to fallback to CS domain SS request
     *        until next reboot.
     */
    public void setCsFallbackStatus(int newStatus) {
        Rlog.d(LOG_TAG, "setCsFallbackStatus to " + newStatus);
        mCSFallbackMode = newStatus;
    }
    /* M: VoLTE SS part end */

    /// M: c2k modify, phone base interface. @{

    @Override
    public void requestSwitchHPF(boolean enableHPF, Message response) {
    }

    @Override
    public void setAvoidSYS(boolean avoidSYS, Message response) {
    }

    @Override
    public void getAvoidSYSList(Message response) {
    }

    @Override
    public void queryCDMANetworkInfo(Message response) {
    }

    /// M: [C2K][SVLTE] Support OPLMN version. @{
    /**
     * Get the version of OPLMN.
     * @param response the responding message
     */
    public void getOplmnVersion(Message response) {
        mCi.getOplmnVersion(response);
    }
    /// @}

    /**
     * M: Register for VIA CDMA modem suspend register network URC, add dummy
     * interface to separate from default CDMA implement.
     *
     * @param h the handler which listen the changed.
     * @param what the message's what value.
     * @param obj the message's obj value.
     */
    public void registerForMccMncChange(Handler h, int what, Object obj) {
    }

    /**
     * M: Unregister for VIA CDMA modem suspend register network URC, add dummy
     * interface to separate from default CDMA implement.
     *
     * @param h the handler which listen the changed.
     */
    public void unregisterForMccMncChange(Handler h) {
    }

    /**
     * M: Resume VIA CDMA modem register network, add dummy interface to
     * separate from legacy CDMA implement.
     *
     * @param result the responding message.
     */
    public void resumeCdmaRegister(Message result) {
    }

    /// M: [C2K][SVLTE] Support modem remote SIM access. @{
    @Override
    public void configModemStatus(int modemStatus, int remoteSimProtocol, Message result) {
        mCi.configModemStatus(modemStatus, remoteSimProtocol, result);
    }
    /// @}

    /// M: [C2K] for eng mode start
    @Override
    public void registerForEngModeNetworkInfo(Handler h, int what, Object obj) {
        mCi.registerForEngModeNetworkInfo(h, what, obj);
    }

    @Override
    public void unregisterForEngModeNetworkInfo(Handler h) {
        mCi.unregisterForEngModeNetworkInfo(h);
    }
    /// M: [C2K] for eng mode end

    /// For SVLTE to update Phone Id
    @Override
    public void setPhoneId(int phoneId) {
        mPhoneId = phoneId;
        // Should also update RIL instance id
        mCi.setInstanceId(phoneId);
    }

    /**
     * Register for CDMA call really be accepted.
     *
     * @param h the handler which listen the changed.
     * @param what the message's what value.
     * @param obj the message's obj value.
     */
    public void registerForCdmaCallAccepted(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        mCdmaCallAcceptedRegistrants.addUnique(h, what, obj);
    }

    /**
     * Unregister for CDMA call really be accepted.
     *
     * @param h the handler which listen the changed.
     */
    public void unregisterForCdmaCallAccepted(Handler h) {
        mCdmaCallAcceptedRegistrants.remove(h);
    }

    /**
     * Notify CDMA call really be accepted.
     */
    public void notifyCdmaCallAccepted() {
        AsyncResult ar = new AsyncResult(null, this, null);
        mCdmaCallAcceptedRegistrants.notifyRegistrants(ar);
    }

    ///M: For svlte support. @{
    /**
     * Notify the service state change for the phone state listener.
     * @param ss The ServiceState of phone.
     */
    protected void notifyServiceStateChangedPForSvlte(ServiceState ss) {
        mNotifier.notifySvlteServiceStateChanged(this, ss);

        if ((ss.getState() != ServiceState.STATE_POWER_OFF)
                && (SystemProperties.get("ril.charging_stop_enable", "0")
                        .equals("1"))) {

            // send special AT cmd to MD
            sendMessageDelayed(obtainMessage(EVENT_CHARGING_STOP, 0, 0), 60000);
        }
    }
    /**
     * Notify the service state change for service state registrants.
     * @param ss The ServiceState of phone.
     */
    public void notifyServiceStateChangedPForRegistrants(ServiceState ss) {
        AsyncResult ar = new AsyncResult(null, ss, null);
        mServiceStateRegistrants.notifyRegistrants(ar);
    }
    // Inherited documentation suffices.
    @Override
    public void registerForSvlteServiceStateChanged(
            Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mSvlteServiceStateRegistrants.add(h, what, obj);
    }

    // Inherited documentation suffices.
    @Override
    public void unregisterForSvlteServiceStateChanged(Handler h) {
        mSvlteServiceStateRegistrants.remove(h);
    }

    /**
     * Notify the svlte service state change for service state registrants.
     * @param ss The ServiceState of phone.
     */
    public void notifySvlteServiceStateChangedPForRegistrants(ServiceState ss) {
        AsyncResult ar = new AsyncResult(null, ss, null);
        mSvlteServiceStateRegistrants.notifyRegistrants(ar);
    }

    @Override
    public ServiceState getSvlteServiceState() {
        return null;
    }
    /// @}

    @Override
    public void setRadioAccessFamily(int radioAccessFamily) {
        Rlog.w(LOG_TAG, "setRadioAccessFamily: " + radioAccessFamily);
        mRadioAccessFamily = radioAccessFamily;
    }

    /// M: ALPS01953873. @{
    /**
     * Query if currently this phone supports the specific feature.
     * @param feature defineded in Phone.java
     * @return true if supporting.
     * @hide
     */
    @Override
    public boolean isFeatureSupported(FeatureType feature) {
        if (feature == FeatureType.VOLTE_ENHANCED_CONFERENCE
                || feature == FeatureType.VOLTE_CONF_REMOVE_MEMBER) {
            if (mImsPhone != null) {
                return mImsPhone.isFeatureSupported(feature);
            }
        }
        // Add checking for other different features here.

        // in the end, return false for unknown features.
        Rlog.d(LOG_TAG, "isFeatureSupported = False with " + feature);
        return false;
    }
    /// @}

    @Override
    public void switchAntenna(int callState, int ratMode) {
        logUnexpectedCdmaMethodCall("switchAntenna");
    }
}
