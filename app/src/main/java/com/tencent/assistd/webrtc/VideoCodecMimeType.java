package com.tencent.assistd.webrtc;

public enum VideoCodecMimeType {
    VP8("video/x-vnd.on2.vp8"),
    VP9("video/x-vnd.on2.vp9"),
    H264("video/avc");

    private final String mimeType;

    VideoCodecMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String mimeType() {
        return this.mimeType;
    }
}
