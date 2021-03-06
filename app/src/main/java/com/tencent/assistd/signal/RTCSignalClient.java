package com.tencent.assistd.signal;

import android.util.Log;

import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RTCSignalClient {
    private static final String TAG = "RTCSignalClient";

    private static RTCSignalClient mInstance;
    private OnSignalEventListener mOnSignalEventListener;

    private JWebSocketClient mSocket;
    private String mUserId = UUID.randomUUID().toString().replaceAll("-", "");
    private String mRoomName = "";

    public interface OnSignalEventListener {
        void onRemoteUserList(List<Mobile> mobileList);
        void onAnswer(JSONObject description);
        void onCandidate(JSONObject candidate);
        void onRemoteHangup(String userId);
    }

    public static RTCSignalClient getInstance() {
        synchronized (RTCSignalClient.class) {
            if (mInstance == null) {
                mInstance = new RTCSignalClient();
            }
        }
        return mInstance;
    }

    public void setSignalEventListener(final OnSignalEventListener listener) {
        mOnSignalEventListener = listener;
    }

    public void joinRoom(String url) {
        Log.i(TAG, "joinRoom: " + url);
        try {
            if (url.startsWith("https")){
                url = url.replaceFirst("https", "wss");
            }else if (url.startsWith("http")) {
                url = url.replaceFirst("http", "ws");
            }else {
                url = "ws://" + url;
            }
            if (!url.endsWith("/ws")) {
                url += "/ws";
            }
            mSocket = new JWebSocketClient(url, new IMessageListener() {
                @Override
                public void onMessage(String message) {
                    try {
                        listenSignalEvents(message);
                    }catch (JSONException ignored){}
                }

                @Override
                public void onOpen() {
                    try {
                        JSONObject args = new JSONObject();
                        args.put("type", "joinRoom");
                        JSONObject jsData = new JSONObject();
                        jsData.put("name", mUserId);
                        jsData.put("id", mUserId);
                        jsData.put("userType", 0);
                        jsData.put("roomId", mRoomName);
                        args.put("data", jsData);
                        mSocket.send(args.toString());
                    } catch (JSONException e) {
                        Log.i(TAG, "joinRoom error: " + e.getMessage());
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    closeSocket();
                }
            });
            mSocket.connect();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public void leaveRoom() {
        Log.i(TAG, "leaveRoom: " + mRoomName);
        if (mSocket == null) {
            return;
        }
        try {
            JSONObject args = new JSONObject();
            args.put("type", "leaveRoom");
            JSONObject jsData = new JSONObject();
            jsData.put("name", mUserId);
            jsData.put("id", mUserId);
            jsData.put("userType", 0);
            jsData.put("roomId", mRoomName);
            args.put("data", jsData);
            sendMsg(args.toString());
            closeSocket();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void sendOffer(String mobileId, JSONObject description) {
        Log.i(TAG, "sendOffer: ");
        if (mSocket == null) {
            return;
        }
        try {
            JSONObject args = new JSONObject();
            args.put("type", "offer");
            JSONObject jsData = new JSONObject();
            jsData.put("to", mobileId);
            jsData.put("from", mUserId);
            jsData.put("roomId", mRoomName);
            jsData.put("description", description);
            args.put("data", jsData);
            Log.d(TAG, "sendOffer: " + args.toString());
            sendMsg(args.toString());
        } catch (JSONException e) {
            Log.e(TAG, "sendOffer err: " + e.getMessage());
        }
    }

    public void sendCandidate(String mobileId, JSONObject candidate) {
        Log.i(TAG, "candidate: " + mobileId + (mSocket == null));
        try {
            JSONObject args = new JSONObject();
            args.put("type", "candidate");
            JSONObject jsData = new JSONObject();
            jsData.put("to", mobileId);
            jsData.put("from", mUserId);
            jsData.put("roomId", mRoomName);
            jsData.put("sessionId", mSessionId);
            jsData.put("candidate", candidate);
            args.put("data", jsData);
            Log.d(TAG, "sendCandidate: " + args.toString());
            sendMsg(args.toString());
        } catch (JSONException e) {
            Log.e(TAG, "sendCandidate err: " + e.getMessage());
        }
    }

    public void sendKickOut(String mobileId) {
        Log.i(TAG, "kickOut: " + mRoomName);
        try {
            JSONObject args = new JSONObject();
            args.put("type", "kickOut");
            JSONObject jsData = new JSONObject();
            jsData.put("to", mobileId);
            jsData.put("from", mUserId);
            jsData.put("roomId", mRoomName);
            jsData.put("sessionId", mSessionId);
            args.put("data", jsData);
            sendMsg(args.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void updateUserList(JSONArray jsonArray) throws JSONException {
        List<Mobile> list = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject mobileObj = jsonArray.getJSONObject(i);
            Mobile mobile = new Mobile(
                    mobileObj.getString("id"),
                    mobileObj.getString("name"),
                    mobileObj.getInt("status")
            );
            list.add(mobile);
        }
        mOnSignalEventListener.onRemoteUserList(list);
    }

    private String mSessionId = "";
    private void listenSignalEvents(String message) throws JSONException {
        JSONObject result = new JSONObject(message);
        String type = result.getString("type");
        Log.d(TAG, "listenSignalEvents, type: " + type);
        switch (type){
            case "updateUserList": updateUserList(result.getJSONArray("data"));break;
            case "answer":
                mOnSignalEventListener.onAnswer(result.getJSONObject("data").getJSONObject("description"));
                mSessionId = result.getJSONObject("data").getString("sessionId");
                break;
            case "candidate": mOnSignalEventListener.onCandidate(result.getJSONObject("data").getJSONObject("candidate"));break;
            case "hangup":
                mOnSignalEventListener.onRemoteHangup(result.getJSONObject("data").getString("from"));
                break;
        }
    }

    private void sendMsg(String msg) {
        if (mSocket == null || mSocket.isClosed()) {
            return;
        }
        try {
            mSocket.send(msg);
        }catch (WebsocketNotConnectedException ignore){}
    }

    private void closeSocket() {
        try {
            if(mSocket != null && !mSocket.isClosed()) mSocket.close();
            mSocket = null;
        }catch (Exception ignore){}
    }
}
