package com.tencent.assistd.webrtc;

import android.util.Log;

import androidx.annotation.Nullable;

import org.webrtc.LibvpxVp8Decoder;
import org.webrtc.LibvpxVp9Decoder;

import org.webrtc.VideoCodecInfo;
import org.webrtc.VideoDecoder;
import org.webrtc.VideoDecoderFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SoftwareVideoDecoderFactory implements VideoDecoderFactory {
    static final String TAG = "SoftVideoDecoderFactory";
    public SoftwareVideoDecoderFactory() {
    }

    /** @deprecated */
    @Deprecated
    @Nullable
    public VideoDecoder createDecoder(String codecType) {
        return this.createDecoder(new VideoCodecInfo(codecType, new HashMap()));
    }

    @Nullable
    public VideoDecoder createDecoder(VideoCodecInfo codecType) {
        if (codecType.name.equalsIgnoreCase("VP8")) {
            return new LibvpxVp8Decoder();
        } else {
            return codecType.name.equalsIgnoreCase("VP9") && nativeVp9IsSupported() ? new LibvpxVp9Decoder() : null;
        }
    }

    public VideoCodecInfo[] getSupportedCodecs() {
        return supportedCodecs();
    }

    static VideoCodecInfo[] supportedCodecs() {
        List<VideoCodecInfo> codecs = new ArrayList();
        codecs.add(new VideoCodecInfo("VP8", new HashMap()));
        if (nativeVp9IsSupported()) {
            codecs.add(new VideoCodecInfo("VP9", new HashMap()));
        }

        return (VideoCodecInfo[])codecs.toArray(new VideoCodecInfo[codecs.size()]);
    }

    static boolean nativeVp9IsSupported() {
        try {

            Method method = LibvpxVp9Decoder.class.getDeclaredMethod("nativeIsSupported");
            return  (boolean) method.invoke(null);
        }catch (NoSuchMethodException | NullPointerException | IllegalAccessException | InvocationTargetException ex) {
            Log.e(TAG, "nativeVp9IsSupported err: " + ex.getMessage());
        }
//        LibvpxVp9Decoder.nativeIsSupported
        return false;
    }
}