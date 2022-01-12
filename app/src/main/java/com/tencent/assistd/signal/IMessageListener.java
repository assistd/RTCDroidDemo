package com.tencent.assistd.signal;

public interface IMessageListener {
    void onMessage(String message);
    void onOpen();
}
