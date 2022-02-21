package com.tencent.assistd.webrtc;

import java.io.IOException;

public interface MediaCodecWrapperFactory {
    MediaCodecWrapper createByCodecName(String var1) throws IOException;
}