/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 * 
 * MediaTek Inc. (C) 2014. All rights reserved.
 * 
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */
package com.mediatek.camera;

import com.mediatek.camera.ICameraAddition;
import com.mediatek.camera.ICameraAddition.AdditionActionType;
import com.mediatek.camera.ICameraAddition.Listener;
import com.mediatek.camera.ICameraMode.ActionType;
import com.mediatek.camera.ICameraMode.CameraModeType;
import com.mediatek.camera.addition.Asd;
import com.mediatek.camera.addition.DistanceInfo;
import com.mediatek.camera.addition.GestureShot;
import com.mediatek.camera.addition.SmileShot;
import com.mediatek.camera.addition.continuousshot.ContinuousShot;
import com.mediatek.camera.addition.effect.EffectAddition;
import com.mediatek.camera.addition.objecttracking.ObjectTracking;
import com.mediatek.camera.addition.remotecamera.RemoteCameraAddition;
import com.mediatek.camera.addition.thermalthrottle.ThermalThrottle;
import com.mediatek.camera.addition.VoiceCapture;
import com.mediatek.camera.util.Log;

import java.util.Vector;

public class AdditionManager {
    private final static String TAG = "AdditionManager";
    
    private Vector<ICameraAddition> mNormalAddition = new Vector<ICameraAddition>();
    private Vector<ICameraAddition> mPhotoAddtion = new Vector<ICameraAddition>();
    private Vector<ICameraAddition> mVideoAddtion = new Vector<ICameraAddition>();
    private Vector<ICameraAddition> mPipPhotoAddition = new Vector<ICameraAddition>();
    private Vector<ICameraAddition> mFaceBeautyAddition = new Vector<ICameraAddition>();
    private Vector<ICameraAddition> mPipVideoAddition = new Vector<ICameraAddition>();
    private Vector<ICameraAddition> mDummyAddtion = new Vector<ICameraAddition>();
    private Vector<ICameraAddition> mModeAddition;
    
    private final ICameraAddition mIContinuousShotAddition;
    private ICameraAddition mIEffect;
    private final ICameraAddition mISmileShot;
    private final ICameraAddition mGestureShot;
    private final ICameraAddition mObjectTracking;
    private final ICameraAddition mDistanceInfo;
    private final ICameraAddition mIVoiceCapture;
    private final ICameraAddition mRemoteCameraAddition;

    public AdditionManager(ICameraContext cameraContext) {
        if (cameraContext.getFeatureConfig().isLomoEffectSupport()) {
            mIEffect = new EffectAddition(cameraContext);
            mPhotoAddtion.add(mIEffect);
            mVideoAddtion.add(mIEffect);
        }
        mISmileShot = new SmileShot(cameraContext);
        mGestureShot = new GestureShot(cameraContext); 
        mIContinuousShotAddition = new ContinuousShot(cameraContext);
        mIVoiceCapture = new VoiceCapture(cameraContext);
        mObjectTracking = new ObjectTracking(cameraContext);
        mDistanceInfo = new DistanceInfo(cameraContext);
        mRemoteCameraAddition = new RemoteCameraAddition(cameraContext);
        mNormalAddition.add(new ThermalThrottle(cameraContext));
        mNormalAddition.add(mIVoiceCapture);
        mPhotoAddtion.add(new Asd(cameraContext));
        mPhotoAddtion.add(mIContinuousShotAddition);
        mPhotoAddtion.add(mGestureShot);
        mPipPhotoAddition.add(mGestureShot);
        mFaceBeautyAddition.add(mGestureShot);
        mPhotoAddtion.add(mISmileShot);
        mPipPhotoAddition.add(mISmileShot);
        mPhotoAddtion.add(mObjectTracking);
        mVideoAddtion.add(mObjectTracking);
        mPipPhotoAddition.add(mObjectTracking);
        mPipVideoAddition.add(mObjectTracking);
        mNormalAddition.add(mDistanceInfo);
        mPhotoAddtion.add(mRemoteCameraAddition);
    }
    
    public void setCurrentMode(CameraModeType type) {
        Log.i(TAG, "[setCurrentMode]type = " + type);
        switch (type) {
        case EXT_MODE_PHOTO:
            mModeAddition = mPhotoAddtion;
            break;
        
        case EXT_MODE_VIDEO:
            mModeAddition = mVideoAddtion;
            break;
            
        case EXT_MODE_PHOTO_PIP:
            mModeAddition = mPipPhotoAddition;
            break;
            
        case EXT_MODE_VIDEO_PIP:
            mModeAddition = mPipVideoAddition;
            break;
            
        case EXT_MODE_FACE_BEAUTY:
            mModeAddition = mFaceBeautyAddition;
            break;
        default:
            mModeAddition = mDummyAddtion;
            break;
        }
    }
    
    public void setListener(Listener listener) {
        for (ICameraAddition addition : mModeAddition) {
            addition.setListener(listener);
        }
    }
    
    public void open(boolean isMode) {
        Log.i(TAG, "[open]isMode = " + isMode);
        Vector<ICameraAddition> curAddition = mModeAddition;
        if (!isMode) {
            curAddition = mNormalAddition;
        }
        for (ICameraAddition addition : curAddition) {
            if (addition.isSupport()) {
                addition.open();
            }
        }
    }
    
    public void onCameraParameterReady(boolean isMode) {
        Log.i(TAG, "[onCameraParameterReady]isMode = " + isMode);
        Vector<ICameraAddition> curAddition = mModeAddition;
        if (!isMode) {
            curAddition = mNormalAddition;
        }
        for (ICameraAddition addition : curAddition) {
            boolean isSupport = addition.isSupport();
            boolean isOpen = addition.isOpen();
            if (isSupport && !isOpen) {
                addition.open();
            } else if (!isSupport && isOpen) {
                addition.close();
            }
        }
    }
    
    public void resume() {
        Log.i(TAG, "[resume]");
        for (ICameraAddition addition : mNormalAddition) {
            addition.resume();
        }
        for (ICameraAddition addition : mModeAddition) {
            addition.resume();
        }
    }
    
    public void pause() {
        Log.i(TAG, "[pause]");
        for (ICameraAddition addition : mNormalAddition) {
            addition.pause();
        }
        for (ICameraAddition addition : mModeAddition) {
            addition.pause();
        }
    }
    
    public void destory() {
        Log.i(TAG, "[destory]");
        for (ICameraAddition addition : mNormalAddition) {
            addition.destory();
        }
        for (ICameraAddition addition : mModeAddition) {
            addition.destory();
        }
    }
    
    public void close(boolean isMode) {
        Log.i(TAG, "[close]isMode = " + isMode);
        Vector<ICameraAddition> curAddition = mModeAddition;
        if (!isMode) {
            curAddition = mNormalAddition;
        }
        for (ICameraAddition addition : curAddition) {
            if (addition.isOpen()) {
                addition.close();
            }
        }
    }
    
    public boolean execute(ActionType type, boolean isMode, Object... arg) {
        Log.i(TAG, "[execute]isMode = " + isMode + ",action type = " + type);
        Vector<ICameraAddition> curAddition = mModeAddition;
        if (!isMode) {
            curAddition = mNormalAddition;
        }
        boolean result = false;
        for (ICameraAddition addition : curAddition) {
            result = result || addition.execute(type, arg);
        }
        // must tell effect if preview size changed.
        if (!isMode && type == ActionType.ACTION_ON_PREVIEW_DISPLAY_SIZE_CHANGED
                && !mModeAddition.contains(mIEffect)) {
            if (mIEffect != null) {
                mIEffect.execute(type, arg);
            }
        }
        return result;
    }
    
    public boolean execute(AdditionActionType type, Object... arg) {
        Log.i(TAG, "[execute],addition action type = " + type);
        boolean result = false;
        for (ICameraAddition addition : mModeAddition) {
            result = addition.execute(type, arg) || result;
        }
        return result;
    }
    
    public void setContinuousShotEnable(boolean enable) {
        if (enable) {
            mPhotoAddtion.add(mIContinuousShotAddition);
        } else {
            mPhotoAddtion.remove(mIContinuousShotAddition);
        }
    }
    
    public void onVoiceCommandNotify(int command) {
        mIVoiceCapture.execute(AdditionActionType.ACTION_ON_VOICE_COMMAND_NOTIFY, command);
    }
    
    public void onEffectClick() {
        if (mIEffect != null ) {
            mIEffect.execute(AdditionActionType.ACTION_EFFECT_CLICK);
        }
    }
    
    public void removeVideoOt() {
        Log.i(TAG, "removeVideoOt");
        mVideoAddtion.remove(mObjectTracking);
    }
}
