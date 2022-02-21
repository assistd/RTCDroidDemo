package com.tencent.assistd.signal;

import android.util.Log;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;

public class JWebSocketClient extends WebSocketClient {
    private static final String TAG = "JWebSocketClient";

    private IMessageListener messageListener;
    public JWebSocketClient(String url, IMessageListener onMessage) throws URISyntaxException {
        super(new URI(url), new Draft_6455());
        messageListener = onMessage;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        Log.i(TAG, "onOpen()");
        messageListener.onOpen();
    }

    @Override
    public void onMessage(String message) {
        Log.i(TAG, "onMessage, " + message);
        messageListener.onMessage(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        Log.i(TAG, "onClose, " + code + remote + reason);
        messageListener.onClose(code, reason, remote);
    }

    @Override
    public void onError(Exception ex) {
        Log.i(TAG, "onError, " + ex.getMessage());
    }
}
