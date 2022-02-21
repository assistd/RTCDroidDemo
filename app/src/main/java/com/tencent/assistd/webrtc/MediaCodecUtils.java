package com.tencent.assistd.webrtc;

import android.annotation.TargetApi;
import android.media.MediaCodecInfo;
import android.os.Build;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

public class MediaCodecUtils {
    private static final String TAG = "MediaCodecUtils";
    public static final String EXYNOS_PREFIX = "omx.exynos.";
    public static final String INTEL_PREFIX = "omx.intel.";
    public static final String NVIDIA_PREFIX = "omx.nvidia.";
    public static final String QCOM_PREFIX = "omx.qcom.";
    public static final String HISI_PREFIX = "omx.hisi.";
    public static final String QTI_PREFIX = "c2.qti.";
    public static final String[] SOFTWARE_IMPLEMENTATION_PREFIXES = new String[]{"OMX.google.", "OMX.SEC.", "c2.android"};
    public static final int COLOR_QCOM_FORMATYVU420PackedSemiPlanar32m4ka = 2141391873;
    public static final int COLOR_QCOM_FORMATYVU420PackedSemiPlanar16m4ka = 2141391874;
    public static final int COLOR_QCOM_FORMATYVU420PackedSemiPlanar64x32Tile2m8ka = 2141391875;
    public static final int COLOR_QCOM_FORMATYUV420PackedSemiPlanar32m = 2141391876;
    public static final int[] DECODER_COLOR_FORMATS = new int[]{19, 21, 2141391872, 2141391873, 2141391874, 2141391875, 2141391876};
    public static final int[] ENCODER_COLOR_FORMATS = new int[]{19, 21, 2141391872, 2141391876};
    public static final int[] TEXTURE_COLOR_FORMATS = getTextureColorFormats();

    public static int[] getTextureColorFormats() {
        return Build.VERSION.SDK_INT >= 18 ? new int[]{2130708361} : new int[0];
    }

    @Nullable
    public static Integer selectColorFormat(int[] supportedColorFormats, MediaCodecInfo.CodecCapabilities capabilities) {
        int[] var2 = supportedColorFormats;
        int var3 = supportedColorFormats.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            int supportedColorFormat = var2[var4];
            int[] var6 = capabilities.colorFormats;
            int var7 = var6.length;

            for(int var8 = 0; var8 < var7; ++var8) {
                int codecColorFormat = var6[var8];
                if (codecColorFormat == supportedColorFormat) {
                    return codecColorFormat;
                }
            }
        }

        return null;
    }

    public static boolean codecSupportsType(MediaCodecInfo info, VideoCodecMimeType type) {
        String[] var2 = info.getSupportedTypes();
        int var3 = var2.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            String mimeType = var2[var4];
            if (type.mimeType().equals(mimeType)) {
                return true;
            }
        }

        return false;
    }

    public static Map<String, String> getCodecProperties(VideoCodecMimeType type, boolean highProfile) {
        switch(type) {
            case VP8:
            case VP9:
                return new HashMap();
            case H264:
                return H264Utils.getDefaultH264Params(highProfile);
            default:
                throw new IllegalArgumentException("Unsupported codec: " + type);
        }
    }

    public static boolean isHardwareAccelerated(MediaCodecInfo info) {
        if (Build.VERSION.SDK_INT >= 29) {
            return isHardwareAcceleratedQOrHigher(info);
        } else {
            return !isSoftwareOnly(info);
        }
    }

    @TargetApi(29)
    public static boolean isHardwareAcceleratedQOrHigher(MediaCodecInfo codecInfo) {
        return codecInfo.isHardwareAccelerated();
    }

    public static boolean isSoftwareOnly(MediaCodecInfo codecInfo) {
        if (Build.VERSION.SDK_INT >= 29) {
            return isSoftwareOnlyQOrHigher(codecInfo);
        } else {
            String name = codecInfo.getName();
            String[] var2 = SOFTWARE_IMPLEMENTATION_PREFIXES;
            int var3 = var2.length;

            for(int var4 = 0; var4 < var3; ++var4) {
                String prefix = var2[var4];
                if (name.startsWith(prefix)) {
                    return true;
                }
            }

            return false;
        }
    }

    @TargetApi(29)
    public static boolean isSoftwareOnlyQOrHigher(MediaCodecInfo codecInfo) {
        return codecInfo.isSoftwareOnly();
    }

    public MediaCodecUtils() {
    }
}
