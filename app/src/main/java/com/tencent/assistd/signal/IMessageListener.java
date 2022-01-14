package com.tencent.assistd.signal;

public interface IMessageListener {
    void onMessage(String message);
    void onOpen();
    void onClose(int code, String reason, boolean remote);
}
