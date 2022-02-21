package com.tencent.assistd.webrtc;

import android.media.MediaCodecInfo;

import androidx.annotation.Nullable;

import org.webrtc.EglBase;
import org.webrtc.Predicate;

public class PlatformSoftwareVideoDecoderFactory extends MediaCodecVideoDecoderFactory {
    private static final Predicate<MediaCodecInfo> defaultAllowedPredicate = new Predicate<MediaCodecInfo>() {
        public boolean test(MediaCodecInfo arg) {
            return MediaCodecUtils.isSoftwareOnly(arg);
        }
    };

    public PlatformSoftwareVideoDecoderFactory(@Nullable EglBase.Context sharedContext) {
        super(sharedContext, defaultAllowedPredicate);
    }
}