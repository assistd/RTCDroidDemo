package com.webrtc.droid.demo.signal;

import android.util.Log;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class RTCSignalClient {
    private static final String TAG = "RTCSignalClient";

    public static final int USER_TYPE_WEB = 0x0;
    public static final int USER_TYPE_STREAMER = 0x1;

    public static final int MESSAGE_TYPE_OFFER = 0x01;
    public static final int MESSAGE_TYPE_ANSWER = 0x02;
    public static final int MESSAGE_TYPE_CANDIDATE = 0x03;
    public static final int MESSAGE_TYPE_HANGUP = 0x04;

    private static RTCSignalClient mInstance;
    private OnSignalEventListener mOnSignalEventListener;

    private Socket mSocket;
    private String mUserId;
    private String mRoomName;

    private OutputStream output;
    Socket sock;

    private static final String MSG_JOIN_ROOM = "joinRoom";
    private static final String MSG_OFFER = "offer";
    private static final String MSG_ANSWER = "answer";
    private static final String MSG_CANDIDATE = "candidate";
    private static final String MSG_UPDATE_LIST = "updateUserList";

    public interface OnSignalEventListener {
        void onConnected();
        void onConnecting();
        void onDisconnected();
        void onRemoteUserJoined(String userId);
        void onRemoteUserLeft(String userId);
        void onBroadcastReceived(JSONObject message);
    }

    public static RTCSignalClient getInstance() {
        synchronized (RTCSignalClient.class) {
            if (mInstance == null) {
                mInstance = new RTCSignalClient();
            }
        }
        return mInstance;
    }

    public void handleLoop(Socket sock) throws IOException  {
        try (InputStream input = sock.getInputStream()) {
            try (OutputStream output = sock.getOutputStream()) {
                this.output = output;
                handleReadLoop(input, output);
            }
        }
        sock.close();
    }

    private synchronized void send(JSONObject message) {
        try {
            byte[] bytes = message.toString().getBytes(StandardCharsets.UTF_8);
            byte[] lenbuf = new byte[4];
            lenbuf[0] = (byte)((bytes.length >> 24) & 0xFF);
            lenbuf[1] = (byte)((bytes.length >> 16) & 0xFF);
            lenbuf[2] = (byte)((bytes.length >> 8) & 0xFF);
            lenbuf[3] = (byte)(bytes.length & 0xFF);
            output.write(lenbuf);
            output.write(bytes);
        } catch (IOException e) {
            // ignore
        }
    }

    private void handleReadLoop(InputStream input, OutputStream output) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));

        char[] buffer = new char[4];
        char[] bodyBuffer = new char[128*1024];
        for (;;) {
            Log.i(TAG, "beginning read 4 bytes");
            reader.read(buffer, 0, 4);
            int msgLen = (buffer[3] & 0xFF) | (buffer[2] & 0xFF) << 8 | (buffer[1] & 0xFF) << 16 | (buffer[0] & 0xFF) << 24;
            reader.read(bodyBuffer, 0, msgLen);
            String message = String.valueOf(bodyBuffer);

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> msg = mapper.readValue(message, Map.class);
            Log.i(TAG, "received: " + msg);
            String cmdType = String.valueOf(msg.get("type"));
            switch (cmdType) {
                case MSG_JOIN_ROOM:{
                    break;
                }
                case MSG_ANSWER:{
                    break;
                }
                case MSG_CANDIDATE:{
                    break;
                }
                case MSG_OFFER:{
                    break;
                }
                case MSG_UPDATE_LIST:{
                    Log.i(TAG, " update list: " +  msg.get("data").toString());
                    break;
                }
            }
        }
    }

    public void setSignalEventListener(final OnSignalEventListener listener) {
        mOnSignalEventListener = listener;
    }

    public String getUserId() {
        return mUserId;
    }

    public void joinRoom(String url, String userId, String roomName) {
        Log.i(TAG, "joinRoom: " + url + ", " + userId + ", " + roomName);
        // 连接设备
        new Thread(() -> {
            try {
                sock = new Socket(url, 8090); // 连接指定服务器和端口
                output = sock.getOutputStream();
                new Thread(() ->{
                    try {
                        RTCSignalClient.this.handleLoop(sock);
                    } catch (IOException e) {
                        //
                    }
                }).start();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            mUserId = userId;
            mRoomName = roomName;
            try {
                JSONObject args = new JSONObject();
                args.put("type", MSG_JOIN_ROOM);
                JSONObject data = new JSONObject();
                data.put("name", userId);
                data.put("id", roomName);
                data.put("userType", USER_TYPE_WEB);
                data.put("roomId", "");
                args.put("data", data);

                send(args);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void leaveRoom() {
        Log.i(TAG, "leaveRoom: " + mRoomName);
        if (mSocket == null) {
            return;
        }
        try {
            JSONObject args = new JSONObject();
            args.put("userId", mUserId);
            args.put("roomName", mRoomName);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(JSONObject message) {
        Log.i(TAG, "broadcast: " + message);
        if (mSocket == null) {
            return;
        }
    }

}
