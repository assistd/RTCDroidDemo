package com.tencent.assistd.webrtc;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Surface;

import androidx.annotation.Nullable;

import org.webrtc.EglBase;
import org.webrtc.EncodedImage;
import org.webrtc.JavaI420Buffer;
import org.webrtc.Logging;
import org.webrtc.NV12Buffer;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.ThreadUtils;
import org.webrtc.VideoCodecStatus;
import org.webrtc.VideoDecoder;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.YuvHelper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class AndroidVideoDecoder implements VideoDecoder, VideoSink {
    private static final String TAG = "AndroidVideoDecoder";
    private static final String MEDIA_FORMAT_KEY_STRIDE = "stride";
    private static final String MEDIA_FORMAT_KEY_SLICE_HEIGHT = "slice-height";
    private static final String MEDIA_FORMAT_KEY_CROP_LEFT = "crop-left";
    private static final String MEDIA_FORMAT_KEY_CROP_RIGHT = "crop-right";
    private static final String MEDIA_FORMAT_KEY_CROP_TOP = "crop-top";
    private static final String MEDIA_FORMAT_KEY_CROP_BOTTOM = "crop-bottom";
    private static final int MEDIA_CODEC_RELEASE_TIMEOUT_MS = 5000;
    private static final int DEQUEUE_INPUT_TIMEOUT_US = 50000;
    private static final int DEQUEUE_OUTPUT_BUFFER_TIMEOUT_US = 50000;
    private final MediaCodecWrapperFactory mediaCodecWrapperFactory;
    private final String codecName;
    private final VideoCodecMimeType codecType;
    private final BlockingDeque<AndroidVideoDecoder.FrameInfo> frameInfos;
    private int colorFormat;
    @Nullable
    private Thread outputThread;
    private ThreadUtils.ThreadChecker outputThreadChecker;
    private ThreadUtils.ThreadChecker decoderThreadChecker;
    private volatile boolean running;
    @Nullable
    private volatile Exception shutdownException;
    private final Object dimensionLock = new Object();
    private int width;
    private int height;
    private int stride;
    private int sliceHeight;
    private boolean hasDecodedFirstFrame;
    private boolean keyFrameRequired;
    @Nullable
    private final EglBase.Context sharedContext;
    @Nullable
    private SurfaceTextureHelper surfaceTextureHelper;
    @Nullable
    private Surface surface;
    private final Object renderedTextureMetadataLock = new Object();
    @Nullable
    private AndroidVideoDecoder.DecodedTextureMetadata renderedTextureMetadata;
    @Nullable
    private Callback callback;
    @Nullable
    private MediaCodecWrapper codec;

    AndroidVideoDecoder(MediaCodecWrapperFactory mediaCodecWrapperFactory, String codecName, VideoCodecMimeType codecType, int colorFormat, @Nullable EglBase.Context sharedContext) {
        if (!this.isSupportedColorFormat(colorFormat)) {
            throw new IllegalArgumentException("Unsupported color format: " + colorFormat);
        } else {
            Logging.d(TAG, "ctor name: " + codecName + " type: " + codecType + " color format: " + colorFormat + " context: " + sharedContext);
            this.mediaCodecWrapperFactory = mediaCodecWrapperFactory;
            this.codecName = codecName;
            this.codecType = codecType;
            this.colorFormat = colorFormat;
            this.sharedContext = sharedContext;
            this.frameInfos = new LinkedBlockingDeque();
        }
    }

    public VideoCodecStatus initDecode(Settings settings, Callback callback) {
        this.decoderThreadChecker = new ThreadUtils.ThreadChecker();
        this.callback = callback;
        if (this.sharedContext != null) {
            this.surfaceTextureHelper = this.createSurfaceTextureHelper();
            this.surface = new Surface(this.surfaceTextureHelper.getSurfaceTexture());
            this.surfaceTextureHelper.startListening(this);
        }

        return this.initDecodeInternal(settings.width, settings.height);
    }

//    private static boolean decoderSupportsAndroidRLowLatency(MediaCodecInfo decoderInfo, String mimeType) {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//            try {
//                if (decoderInfo.getCapabilitiesForType(mimeType).isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency)) {
//                    return true;
//                }
//            } catch (Exception e) {
//                // Tolerate buggy codecs
//                e.printStackTrace();
//            }
//        }
//
//        return false;
//    }

    private VideoCodecStatus initDecodeInternal(int width, int height) {
        this.decoderThreadChecker.checkIsOnValidThread();
        Logging.d(TAG, "initDecodeInternal name: " + this.codecName + " type: " + this.codecType + " width: " + width + " height: " + height);
        if (this.outputThread != null) {
            Logging.e(TAG, "initDecodeInternal called while the codec is already running");
            return VideoCodecStatus.FALLBACK_SOFTWARE;
        } else {
            this.width = width;
            this.height = height;
            this.stride = width;
            this.sliceHeight = height;
            this.hasDecodedFirstFrame = false;
            this.keyFrameRequired = true;

            try {
                Logging.d(TAG, "mediaCodecWrapperFactory width: " + this.width + ", height: " + this.height);
                this.codec = this.mediaCodecWrapperFactory.createByCodecName(this.codecName);
            } catch (IllegalArgumentException | IllegalStateException | IOException var4) {
                Logging.e(TAG, "Cannot create media decoder " + this.codecName);
                return VideoCodecStatus.FALLBACK_SOFTWARE;
            }

            try {
                MediaFormat format = MediaFormat.createVideoFormat(this.codecType.mimeType(), width, height);
                if (this.sharedContext == null) {
                    format.setInteger(MediaFormat.KEY_COLOR_FORMAT, this.colorFormat);
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        (codecName.startsWith(MediaCodecUtils.QCOM_PREFIX) ||
                                codecName.startsWith(MediaCodecUtils.QTI_PREFIX))) {
                    format.setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE);
                }

//                 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && decoderSupportsAndroidRLowLatency()) {
//                     format.setInteger (MediaFormat.KEY_LOW_LATENCY,1);
//                     Logging.d(TAG, "set MediaFormat.KEY_LOW_LATENCY,: " + codecName);
//                 }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    String codecName = this.codecName.toLowerCase();
                    // MediaCodec supports vendor-defined format keys using the "vendor.<extension name>.<parameter name>" syntax.
                    // These allow access to functionality that is not exposed through documented MediaFormat.KEY_* values.
                    // https://cs.android.com/android/platform/superproject/+/master:hardware/qcom/sdm845/media/mm-video-v4l2/vidc/common/inc/vidc_vendor_extensions.h;l=67
                    //
                    // MediaCodec vendor extension support was introduced in Android 8.0:
                    // https://cs.android.com/android/_/android/platform/frameworks/av/+/01c10f8cdcd58d1e7025f426a72e6e75ba5d7fc2
                    if (codecName.startsWith(MediaCodecUtils.QCOM_PREFIX) ||
                            codecName.startsWith(MediaCodecUtils.QTI_PREFIX)) {
                        // Examples of Qualcomm's vendor extensions for Snapdragon 845:
                        // https://cs.android.com/android/platform/superproject/+/master:hardware/qcom/sdm845/media/mm-video-v4l2/vidc/vdec/src/omx_vdec_extensions.hpp
                        // https://cs.android.com/android/_/android/platform/hardware/qcom/sm8150/media/+/0621ceb1c1b19564999db8293574a0e12952ff6c
                        format.setInteger ("vendor.qti-ext-dec-low-latency.enable",1);
                        format.setInteger ("vendor.qti-ext-dec-picture-order.enable",1);
                        Logging.d(TAG, "QCOM codecName: " + this.codecName + " low latency enabled");
                    }else if (codecName.startsWith(MediaCodecUtils.HISI_PREFIX)) {
                        // Kirin low latency options
                        // https://developer.huawei.com/consumer/cn/forum/topic/
                        format.setInteger("vendor.hisi-ext-low-latency-video-dec.video-scene-for-low-latency-req", 1);
                        format.setInteger("vendor.hisi-ext-low-latency-video-dec.video-scene-for-low-latency-rdy", -1);
                        Logging.d(TAG, "HISI codecName: " + this.codecName + " low latency enabled");
                    }else if (codecName.startsWith(MediaCodecUtils.EXYNOS_PREFIX)) {
                        // Exynos low latency option for H.264 decoder
                        format.setInteger ("vendor.rtc-ext-dec-low-latency.enable",1);
                        Logging.d(TAG, "EXYNOS codecName: " + this.codecName + " low latency enabled");
                    }
                }

                assert this.codec != null;
                this.codec.configure(format, this.surface, (MediaCrypto)null, 0);
                this.codec.start();
            } catch (IllegalArgumentException | IllegalStateException var5) {
                Logging.e(TAG, "initDecode failed", var5);
                this.release();
                return VideoCodecStatus.FALLBACK_SOFTWARE;
            }

            this.running = true;
            this.outputThread = this.createOutputThread();
            this.outputThread.start();
            Logging.d(TAG, "initDecodeInternal done");
            return VideoCodecStatus.OK;
        }
    }

    public VideoCodecStatus decode(EncodedImage frame, DecodeInfo info) {
        this.decoderThreadChecker.checkIsOnValidThread();
        if (this.codec != null && this.callback != null) {
            if (frame.buffer == null) {
                Logging.e(TAG, "decode() - no input data");
                return VideoCodecStatus.ERR_PARAMETER;
            } else {
                int size = frame.buffer.remaining();
                if (size == 0) {
                    Logging.e(TAG, "decode() - input buffer empty");
                    return VideoCodecStatus.ERR_PARAMETER;
                } else {
                    int width;
                    int height;
                    synchronized(this.dimensionLock) {
                        width = this.width;
                        height = this.height;
                    }

                    if (frame.encodedWidth * frame.encodedHeight > 0 && (frame.encodedWidth != width || frame.encodedHeight != height)) {
                        VideoCodecStatus status = this.reinitDecode(frame.encodedWidth, frame.encodedHeight);
                        if (status != VideoCodecStatus.OK) {
                            return status;
                        }
                    }

                    if (this.keyFrameRequired) {
                        if (frame.frameType != EncodedImage.FrameType.VideoFrameKey) {
                            Logging.e(TAG, "decode() - key frame required first");
                            return VideoCodecStatus.NO_OUTPUT;
                        }

                        if (!frame.completeFrame) {
                            Logging.e(TAG, "decode() - complete frame required first");
                            return VideoCodecStatus.NO_OUTPUT;
                        }
                    }

                    int index;
                    try {
                        index = this.codec.dequeueInputBuffer(-1);
                    } catch (IllegalStateException var11) {
                        Logging.e(TAG, "dequeueInputBuffer failed", var11);
                        return VideoCodecStatus.ERROR;
                    }

                    if (index < 0) {
                        Logging.e(TAG, "decode() - no HW buffers available; decoder falling behind");
                        return VideoCodecStatus.ERROR;
                    } else {
                        ByteBuffer buffer;
                        try {
                            buffer = this.codec.getInputBuffers()[index];
                        } catch (IllegalStateException var10) {
                            Logging.e(TAG, "getInputBuffers failed", var10);
                            return VideoCodecStatus.ERROR;
                        }

                        if (buffer.capacity() < size) {
                            Logging.e(TAG, "decode() - HW buffer too small");
                            return VideoCodecStatus.ERROR;
                        } else {
                            buffer.put(frame.buffer);
                            this.frameInfos.offer(new AndroidVideoDecoder.FrameInfo(SystemClock.elapsedRealtime(), frame.rotation));

                            try {
                                this.codec.queueInputBuffer(index, 0, size, TimeUnit.NANOSECONDS.toMicros(frame.captureTimeNs), 0);

                                if (this.keyFrameRequired) {
                                    this.keyFrameRequired = false;
                                }
                            } catch (IllegalStateException var9) {
                                Logging.e(TAG, "queueInputBuffer failed", var9);
                                this.frameInfos.pollLast();
                                return VideoCodecStatus.ERROR;
                            }

                            return VideoCodecStatus.OK;
                        }
                    }
                }
            }
        } else {
            Logging.d(TAG, "decode uninitalized, codec: " + (this.codec != null) + ", callback: " + this.callback);
            return VideoCodecStatus.UNINITIALIZED;
        }
    }

    public boolean getPrefersLateDecoding() {
        return true;
    }

    public String getImplementationName() {
        return this.codecName;
    }

    public VideoCodecStatus release() {
        Logging.d(TAG, "release");
        VideoCodecStatus status = this.releaseInternal();
        if (this.surface != null) {
            this.releaseSurface();
            this.surface = null;
            this.surfaceTextureHelper.stopListening();
            this.surfaceTextureHelper.dispose();
            this.surfaceTextureHelper = null;
        }

        synchronized(this.renderedTextureMetadataLock) {
            this.renderedTextureMetadata = null;
        }

        this.callback = null;
        this.frameInfos.clear();
        return status;
    }

    private VideoCodecStatus releaseInternal() {
        if (!this.running) {
            Logging.d(TAG, "release: Decoder is not running.");
            return VideoCodecStatus.OK;
        } else {
            try {
                this.running = false;
                VideoCodecStatus var1;
                if (!ThreadUtils.joinUninterruptibly(this.outputThread, 5000L)) {
                    Logging.e(TAG, "Media decoder release timeout", new RuntimeException());
                    var1 = VideoCodecStatus.TIMEOUT;
                    return var1;
                }

                if (this.shutdownException != null) {
                    Logging.e(TAG, "Media decoder release error", new RuntimeException(this.shutdownException));
                    this.shutdownException = null;
                    var1 = VideoCodecStatus.ERROR;
                    return var1;
                }
            } finally {
                this.codec = null;
                this.outputThread = null;
            }

            return VideoCodecStatus.OK;
        }
    }

    private VideoCodecStatus reinitDecode(int newWidth, int newHeight) {
        this.decoderThreadChecker.checkIsOnValidThread();
        VideoCodecStatus status = this.releaseInternal();
        return status != VideoCodecStatus.OK ? status : this.initDecodeInternal(newWidth, newHeight);
    }

    private Thread createOutputThread() {
        return new Thread("AndroidVideoDecoder.outputThread") {
            public void run() {
                AndroidVideoDecoder.this.outputThreadChecker = new ThreadUtils.ThreadChecker();

                while(AndroidVideoDecoder.this.running) {
                    AndroidVideoDecoder.this.deliverDecodedFrame();
                }

                AndroidVideoDecoder.this.releaseCodecOnOutputThread();
            }
        };
    }

    protected void deliverDecodedFrame() {
        this.outputThreadChecker.checkIsOnValidThread();

        try {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            assert this.codec != null;
            int result = this.codec.dequeueOutputBuffer(info, DEQUEUE_OUTPUT_BUFFER_TIMEOUT_US);
            if (result == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Logging.v(TAG, "dequeueOutputBuffer returned INFO_OUTPUT_FORMAT_CHANGED");
                this.reformat(this.codec.getOutputFormat());
                return ;
            }

            if (result == MediaCodec.INFO_TRY_AGAIN_LATER) {
                try {
                    Thread.sleep(50);
                }catch (InterruptedException ignore) { }
                Logging.v(TAG, "dequeueOutputBuffer returned INFO_TRY_AGAIN_LATER");
                return ;
            }

            if (result < 0) {
                Logging.v(TAG, "dequeueOutputBuffer returned " + result);
                return ;
            }

            AndroidVideoDecoder.FrameInfo frameInfo = (AndroidVideoDecoder.FrameInfo)this.frameInfos.poll();
            Integer decodeTimeMs = null;
            int rotation = 0;
            if (frameInfo != null) {
                decodeTimeMs = (int)(SystemClock.elapsedRealtime() - frameInfo.decodeStartTimeMs);
                rotation = frameInfo.rotation;
            }

            this.hasDecodedFirstFrame = true;
            if (this.surfaceTextureHelper != null) {
                this.deliverTextureFrame(result, info, rotation, decodeTimeMs);
            } else {
                this.deliverByteFrame(result, info, rotation, decodeTimeMs);
            }
        } catch (IllegalStateException var6) {
            Logging.e(TAG, "deliverDecodedFrame failed", var6);
        }
    }

    @SuppressLint("DefaultLocale")
    private void deliverTextureFrame(int index, MediaCodec.BufferInfo info, int rotation, Integer decodeTimeMs) {
        int width;
        int height;
        synchronized(this.dimensionLock) {
            width = this.width;
            height = this.height;
        }

        synchronized(this.renderedTextureMetadataLock) {
            assert this.codec != null;
            if (this.renderedTextureMetadata != null) {
//                Logging.e(TAG, "rendered renderedTextureMetadata");
                this.codec.releaseOutputBuffer(index, false);
            } else {
//                Logging.e(TAG, "rendered surfaceTextureHelper");
                assert this.surfaceTextureHelper != null;
                this.surfaceTextureHelper.setTextureSize(width, height);
                this.surfaceTextureHelper.setFrameRotation(rotation);
                this.renderedTextureMetadata = new AndroidVideoDecoder.DecodedTextureMetadata(info.presentationTimeUs, decodeTimeMs);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    this.codec.releaseOutputBuffer(index, 0);
                } else {
                    this.codec.releaseOutputBuffer(index, true);
                }
            }
        }
    }

    public void onFrame(VideoFrame frame) {
        Integer decodeTimeMs;
        long timestampNs;
        synchronized(this.renderedTextureMetadataLock) {
            if (this.renderedTextureMetadata == null) {
                throw new IllegalStateException("Rendered texture metadata was null in onTextureFrameAvailable.");
            }

            timestampNs = this.renderedTextureMetadata.presentationTimestampUs * 1000L;
            decodeTimeMs = this.renderedTextureMetadata.decodeTimeMs;
//            Logging.d(TAG, "renderedTextureMetadata timestampNs: " + timestampNs + ", decodeTimeMs " + decodeTimeMs);
            this.renderedTextureMetadata = null;
        }

        VideoFrame frameWithModifiedTimeStamp = new VideoFrame(frame.getBuffer(), frame.getRotation(), timestampNs);
        assert this.callback != null;
        this.callback.onDecodedFrame(frameWithModifiedTimeStamp, decodeTimeMs, (Integer)null);
    }

    @SuppressLint("DefaultLocale")
    private void deliverByteFrame(int result, MediaCodec.BufferInfo info, int rotation, Integer decodeTimeMs) {
        int width;
        int height;
        int stride;
        int sliceHeight;
        synchronized(this.dimensionLock) {
            width = this.width;
            height = this.height;
            stride = this.stride;
            sliceHeight = this.sliceHeight;
        }

        if (info.size < width * height * 3 / 2) {
            Logging.e(TAG, "Insufficient output buffer size: " + info.size);
        } else {
            if (info.size < stride * height * 3 / 2 && sliceHeight == height && stride > width) {
                stride = info.size * 2 / (height * 3);
            }

            ByteBuffer buffer = this.codec.getOutputBuffers()[result];
            buffer.position(info.offset);
            buffer.limit(info.offset + info.size);
            buffer = buffer.slice();
            VideoFrame.Buffer frameBuffer;

            Logging.d(TAG, String.format("deliverByteFrame stride: %d, sliceHeight: %d, " +
                            "width: %d, height: %d, colorFormat: %d",
                    stride, sliceHeight, width, height, this.colorFormat));
            if (this.colorFormat == 19) {
                frameBuffer = this.copyI420Buffer(buffer, stride, sliceHeight, width, height);
            } else {
                frameBuffer = this.copyNV12ToI420Buffer(buffer, stride, sliceHeight, width, height);
            }

            this.codec.releaseOutputBuffer(result, false);
            long presentationTimeNs = info.presentationTimeUs * 1000L;
            VideoFrame frame = new VideoFrame(frameBuffer, rotation, presentationTimeNs);
            this.callback.onDecodedFrame(frame, decodeTimeMs, (Integer)null);
            frame.release();
        }
    }

    private VideoFrame.Buffer copyNV12ToI420Buffer(ByteBuffer buffer, int stride, int sliceHeight, int width, int height) {
        return (new NV12Buffer(width, height, stride, sliceHeight, buffer, (Runnable)null)).toI420();
    }

    private VideoFrame.Buffer copyI420Buffer(ByteBuffer buffer, int stride, int sliceHeight, int width, int height) {
        if (stride % 2 != 0) {
            throw new AssertionError("Stride is not divisible by two: " + stride);
        } else {
            int chromaWidth = (width + 1) / 2;
            int chromaHeight = sliceHeight % 2 == 0 ? (height + 1) / 2 : height / 2;
            int uvStride = stride / 2;
            int yEnd = 0 + stride * height;
            int uPos = 0 + stride * sliceHeight;
            int uEnd = uPos + uvStride * chromaHeight;
            int vPos = uPos + uvStride * sliceHeight / 2;
            int vEnd = vPos + uvStride * chromaHeight;
            VideoFrame.I420Buffer frameBuffer = this.allocateI420Buffer(width, height);
            buffer.limit(yEnd);
            buffer.position(0);
            this.copyPlane(buffer.slice(), stride, frameBuffer.getDataY(), frameBuffer.getStrideY(), width, height);
            buffer.limit(uEnd);
            buffer.position(uPos);
            this.copyPlane(buffer.slice(), uvStride, frameBuffer.getDataU(), frameBuffer.getStrideU(), chromaWidth, chromaHeight);
            ByteBuffer dataV;
            if (sliceHeight % 2 == 1) {
                buffer.position(uPos + uvStride * (chromaHeight - 1));
                dataV = frameBuffer.getDataU();
                dataV.position(frameBuffer.getStrideU() * chromaHeight);
                dataV.put(buffer);
            }

            buffer.limit(vEnd);
            buffer.position(vPos);
            this.copyPlane(buffer.slice(), uvStride, frameBuffer.getDataV(), frameBuffer.getStrideV(), chromaWidth, chromaHeight);
            if (sliceHeight % 2 == 1) {
                buffer.position(vPos + uvStride * (chromaHeight - 1));
                dataV = frameBuffer.getDataV();
                dataV.position(frameBuffer.getStrideV() * chromaHeight);
                dataV.put(buffer);
            }

            return frameBuffer;
        }
    }

    @SuppressLint("DefaultLocale")
    private void reformat(MediaFormat format) {
        this.outputThreadChecker.checkIsOnValidThread();
        Logging.d(TAG, "Decoder format changed: " + format.toString());
        int newWidth;
        int newHeight;
        if (format.containsKey("crop-left") && format.containsKey("crop-right") && format.containsKey("crop-bottom") && format.containsKey("crop-top")) {
            newWidth = 1 + format.getInteger("crop-right") - format.getInteger("crop-left");
            newHeight = 1 + format.getInteger("crop-bottom") - format.getInteger("crop-top");
        } else {
            newWidth = format.getInteger("width");
            newHeight = format.getInteger("height");
        }

        synchronized(this.dimensionLock) {
            if (this.hasDecodedFirstFrame && (this.width != newWidth || this.height != newHeight)) {
                this.stopOnOutputThread(new RuntimeException("Unexpected size change. Configured " + this.width + "*" + this.height + ". New " + newWidth + "*" + newHeight));
                return;
            }

            this.width = newWidth;
            this.height = newHeight;
        }

        if (this.surfaceTextureHelper == null && format.containsKey("color-format")) {
            this.colorFormat = format.getInteger("color-format");
            Logging.d(TAG, "Color: 0x" + Integer.toHexString(this.colorFormat));
            if (!this.isSupportedColorFormat(this.colorFormat)) {
                this.stopOnOutputThread(new IllegalStateException("Unsupported color format: " + this.colorFormat));
                return;
            }
        }

        synchronized(this.dimensionLock) {
            if (format.containsKey("stride")) {
                this.stride = format.getInteger("stride");
            }

            if (format.containsKey("slice-height")) {
                this.sliceHeight = format.getInteger("slice-height");
            }

            this.stride = Math.max(this.width, this.stride);
            this.sliceHeight = Math.max(this.height, this.sliceHeight);
        }
        Logging.d(TAG, String.format("Output format changed stride: %d, sliceHeight: %d, " +
                        "width: %d, height: %d",
                this.stride, this.sliceHeight, this.width, this.height));
    }

    private void releaseCodecOnOutputThread() {
        this.outputThreadChecker.checkIsOnValidThread();
        Logging.d(TAG, "Releasing MediaCodec on output thread");

        try {
            this.codec.stop();
        } catch (Exception var3) {
            Logging.e(TAG, "Media decoder stop failed", var3);
        }

        try {
            this.codec.release();
        } catch (Exception var2) {
            Logging.e(TAG, "Media decoder release failed", var2);
            this.shutdownException = var2;
        }

        Logging.d(TAG, "Release on output thread done");
    }

    private void stopOnOutputThread(Exception e) {
        this.outputThreadChecker.checkIsOnValidThread();
        this.running = false;
        this.shutdownException = e;
    }

    private boolean isSupportedColorFormat(int colorFormat) {
        int[] var2 = MediaCodecUtils.DECODER_COLOR_FORMATS;
        int var3 = var2.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            int supported = var2[var4];
            if (supported == colorFormat) {
                return true;
            }
        }

        return false;
    }

    protected SurfaceTextureHelper createSurfaceTextureHelper() {
        return SurfaceTextureHelper.create("decoder-texture-thread", this.sharedContext);
    }

    protected void releaseSurface() {
        this.surface.release();
    }

    protected VideoFrame.I420Buffer allocateI420Buffer(int width, int height) {
        return JavaI420Buffer.allocate(width, height);
    }

    protected void copyPlane(ByteBuffer src, int srcStride, ByteBuffer dst, int dstStride, int width, int height) {
        YuvHelper.copyPlane(src, srcStride, dst, dstStride, width, height);
    }

    private static class DecodedTextureMetadata {
        final long presentationTimestampUs;
        final Integer decodeTimeMs;

        DecodedTextureMetadata(long presentationTimestampUs, Integer decodeTimeMs) {
            this.presentationTimestampUs = presentationTimestampUs;
            this.decodeTimeMs = decodeTimeMs;
        }
    }

    private static class FrameInfo {
        final long decodeStartTimeMs;
        final int rotation;

        FrameInfo(long decodeStartTimeMs, int rotation) {
            this.decodeStartTimeMs = decodeStartTimeMs;
            this.rotation = rotation;
        }
    }
}

