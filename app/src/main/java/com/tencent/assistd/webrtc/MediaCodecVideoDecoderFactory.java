package com.tencent.assistd.webrtc;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;

import androidx.annotation.Nullable;

import org.webrtc.EglBase;
import org.webrtc.Logging;
import org.webrtc.Predicate;
import org.webrtc.VideoCodecInfo;
import org.webrtc.VideoDecoder;
import org.webrtc.VideoDecoderFactory;

import java.util.ArrayList;
import java.util.List;

public class MediaCodecVideoDecoderFactory implements VideoDecoderFactory {
    private static final String TAG = "MediaCodecVideoDecoderFactory";
    @Nullable
    private final EglBase.Context sharedContext;
    @Nullable
    private final Predicate<MediaCodecInfo> codecAllowedPredicate;

    public MediaCodecVideoDecoderFactory(@Nullable EglBase.Context sharedContext, @Nullable Predicate<MediaCodecInfo> codecAllowedPredicate) {
        this.sharedContext = sharedContext;
        this.codecAllowedPredicate = codecAllowedPredicate;
    }

    @Nullable
    public VideoDecoder createDecoder(VideoCodecInfo codecType) {
        VideoCodecMimeType type = VideoCodecMimeType.valueOf(codecType.name);
        MediaCodecInfo info = this.findCodecForType(type);
        if (info == null) {
            return null;
        } else {
            MediaCodecInfo.CodecCapabilities capabilities = info.getCapabilitiesForType(type.mimeType());
            return new AndroidVideoDecoder(new MediaCodecWrapperFactoryImpl(), info.getName(), type, MediaCodecUtils.selectColorFormat(MediaCodecUtils.DECODER_COLOR_FORMATS, capabilities), this.sharedContext);
        }
    }

    public VideoCodecInfo[] getSupportedCodecs() {
        List<VideoCodecInfo> supportedCodecInfos = new ArrayList();
        VideoCodecMimeType[] var2 = new VideoCodecMimeType[]{VideoCodecMimeType.VP8, VideoCodecMimeType.VP9, VideoCodecMimeType.H264};
        int var3 = var2.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            VideoCodecMimeType type = var2[var4];
            MediaCodecInfo codec = this.findCodecForType(type);
            if (codec != null) {
                String name = type.name();
                if (type == VideoCodecMimeType.H264 && this.isH264HighProfileSupported(codec)) {
                    supportedCodecInfos.add(new VideoCodecInfo(name, MediaCodecUtils.getCodecProperties(type, true)));
                }

                supportedCodecInfos.add(new VideoCodecInfo(name, MediaCodecUtils.getCodecProperties(type, false)));
            }
        }

        return (VideoCodecInfo[])supportedCodecInfos.toArray(new VideoCodecInfo[supportedCodecInfos.size()]);
    }

    @Nullable
    private MediaCodecInfo findCodecForType(VideoCodecMimeType type) {
        if (Build.VERSION.SDK_INT < 19) {
            return null;
        } else {
            for(int i = 0; i < MediaCodecList.getCodecCount(); ++i) {
                MediaCodecInfo info = null;

                try {
                    info = MediaCodecList.getCodecInfoAt(i);
                } catch (IllegalArgumentException var5) {
                    Logging.e("MediaCodecVideoDecoderFactory", "Cannot retrieve decoder codec info", var5);
                }

                if (info != null && !info.isEncoder() && this.isSupportedCodec(info, type)) {
                    return info;
                }
            }

            return null;
        }
    }

    private boolean isSupportedCodec(MediaCodecInfo info, VideoCodecMimeType type) {
        String name = info.getName();
        if (!MediaCodecUtils.codecSupportsType(info, type)) {
            return false;
        } else {
            return MediaCodecUtils.selectColorFormat(MediaCodecUtils.DECODER_COLOR_FORMATS, info.getCapabilitiesForType(type.mimeType())) == null ? false : this.isCodecAllowed(info);
        }
    }

    private boolean isCodecAllowed(MediaCodecInfo info) {
        return this.codecAllowedPredicate == null ? true : this.codecAllowedPredicate.test(info);
    }

    private boolean isH264HighProfileSupported(MediaCodecInfo info) {
        String name = info.getName();
        if (Build.VERSION.SDK_INT >= 21 && name.startsWith("OMX.qcom.")) {
            return true;
        } else {
            return Build.VERSION.SDK_INT >= 23 && name.startsWith("OMX.Exynos.");
        }
    }
}

