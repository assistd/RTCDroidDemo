package com.tencent.assistd.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.internal.NavigationMenu;
import com.tencent.assistd.R;
import com.tencent.assistd.adapter.MobileListAdapter;
import com.tencent.assistd.fabspeeddial.FabSpeedDial;
import com.tencent.assistd.fabspeeddial.SimpleMenuListenerAdapter;
import com.tencent.assistd.signal.Mobile;
import com.tencent.assistd.signal.RTCSignalClient;
import com.tencent.assistd.utils.Settings;
import com.tencent.assistd.utils.VolumeChangeObserver;
import com.tencent.assistd.utils.WindowHelper;
import com.tencent.assistd.webrtc.DefaultVideoDecoderFactory;
import com.tencent.assistd.widgets.DragFloatActionButton;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.VideoTrack;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MobileActivity extends AppCompatActivity {
    private static final String TAG = "MobileActivity";
    private final int MAX_VOL = 25;
    private String selectedMobileId;

    private EglBase mRootEglBase;

    private PeerConnection mPeerConnection;
    private DataChannel mDataChannel;
    private PeerConnectionFactory mPeerConnectionFactory;

    private SurfaceViewRenderer mRemoteSurfaceView;
    private ProxyVideoSink mVideoSink;
    private AudioTrack mAudioTrack;
    private double mAudioTrackVol;
    private AudioManager mAudioManager;
    private VolumeChangeObserver mVolumeChangeObserver;

    private ListView mMobileListView;
    private TextView mMobileListViewTips;
    private DragFloatActionButton mMobileCtrlFloatBtn;
    private MobileListAdapter mMobileListAdapter;

    private WifiManager.WifiLock highPerfWifiLock;
    private WifiManager.WifiLock lowLatencyWifiLock;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mobile);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide(); //隐藏标题栏
        }
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mVolumeChangeObserver = new VolumeChangeObserver(this);


        mMobileCtrlFloatBtn = findViewById(R.id.mobileCtrlFloat);
        mMobileCtrlFloatBtn.setPosRightTop();
        mMobileCtrlFloatBtn.setMenuListener(ctrlMenuListener);
        mMobileListViewTips = findViewById(R.id.mobileListViewTips);
        mMobileListView = findViewById(R.id.mobileListView);
        mMobileListAdapter = new MobileListAdapter(this, R.layout.layout_mobile_list_item, mobileListListener);
        mMobileListView.setAdapter(mMobileListAdapter);

        RTCSignalClient.getInstance().setSignalEventListener(mOnSignalEventListener);

        String serverAddr = getIntent().getStringExtra(Settings.SERVER_ADDRESS);
        assert serverAddr != null;
        RTCSignalClient.getInstance().joinRoom(serverAddr);

        mRootEglBase = EglBase.create();

        mRemoteSurfaceView = findViewById(R.id.RemoteSurfaceView);
        mRemoteSurfaceView.init(mRootEglBase.getEglBaseContext(), null);
        mRemoteSurfaceView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        mRemoteSurfaceView.setMirror(false);
        mRemoteSurfaceView.setEnableHardwareScaler(false);
        mRemoteSurfaceView.setZOrderMediaOverlay(true);
        mRemoteSurfaceView.setOnTouchListener(touchListener);
        mPeerConnectionFactory = createPeerConnectionFactory(this);

        // Change volume button behavior
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        makeWifiManager();

        // NOTE: this _must_ happen while PeerConnectionFactory is alive!
        Logging.enableLogToDebugOutput(Logging.Severity.LS_VERBOSE);
    }

    public void makeWifiManager() {
        WifiManager wifiMgr = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        highPerfWifiLock = wifiMgr.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Assistd High Perf Lock");
        highPerfWifiLock.setReferenceCounted(false);
        highPerfWifiLock.acquire();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            lowLatencyWifiLock = wifiMgr.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "Assistd Low Latency Lock");
            lowLatencyWifiLock.setReferenceCounted(false);
            lowLatencyWifiLock.acquire();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Checks the orientation of the screen
//        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
//            Toast.makeText(this, "横屏模式", Toast.LENGTH_SHORT).show();
//            Log.d(TAG, "onConfigurationChanged. 横屏模式");
//        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
//            Toast.makeText(this, "竖屏模式", Toast.LENGTH_LONG).show();
//            Log.d(TAG, "onConfigurationChanged. 竖屏模式");
//        }
        mMobileCtrlFloatBtn.dispatchConfigurationChanged(newConfig);
        mRemoteSurfaceView.dispatchConfigurationChanged(newConfig);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mVolumeChangeObserver.setVolumeChangeListener(volumeChangeListener);
        mVolumeChangeObserver.registerReceiver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mVolumeChangeObserver.unregisterReceiver();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doEndCall();
        mRemoteSurfaceView.release();
        if (lowLatencyWifiLock != null) {
            lowLatencyWifiLock.release();
        }
        if (highPerfWifiLock != null) {
            highPerfWifiLock.release();
        }
        PeerConnectionFactory.stopInternalTracingCapture();
        PeerConnectionFactory.shutdownInternalTracer();
        RTCSignalClient.getInstance().leaveRoom();
    }

    private final FabSpeedDial.MenuListener ctrlMenuListener = new SimpleMenuListenerAdapter() {
        @Override
        public boolean onPrepareMenu(NavigationMenu navigationMenu) {
            @SuppressLint("RestrictedApi") MenuItem menuItem = navigationMenu.findItem(R.id.action_mute);
            if(menuItem != null) {
                String cancelString = getResources().getString(R.string.cancel);
                String muteString = getResources().getString(R.string.mobile_ctrl_mute);
                menuItem.setVisible(mAudioTrack != null);
                menuItem.setTitle((isMuted ? cancelString : "") + muteString);
            }
            if(mMobileCtrlFloatBtn.isTopRight()) mMobileCtrlFloatBtn.setX(mMobileCtrlFloatBtn.getX() - 62);
            return true ; //false : dont show menu
        }

        @SuppressLint("NonConstantResourceId")
        @Override
        public boolean onMenuItemSelected(MenuItem menuItem) {
            switch (menuItem.getItemId()) {
                case R.id.action_back: sendBackOrScreenOn(); break;
                case R.id.action_home: sendKeyPress(KeyEvent.KEYCODE_HOME); break;
                case R.id.action_recent: sendKeyPress(KeyEvent.KEYCODE_APP_SWITCH); break;
                case R.id.action_mute: switchAudioMute(); break;
                case R.id.action_exit: onExitMobileConnect(); break;
            }
            return true;
        }

        @Override
        public void onMenuClosed() {
            if(mMobileCtrlFloatBtn.isTopRight()) {
                new Handler().postDelayed(() -> {
                    mMobileCtrlFloatBtn.setX(mMobileCtrlFloatBtn.getX() + 62);
                },getResources().getInteger(android.R.integer.config_shortAnimTime)-1);
            }
        }
    };

    private final Map<Integer, int[]> mTouchPosMap = new HashMap<>();
    private final View.OnTouchListener touchListener = new View.OnTouchListener() {
        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent ev) {
            if(mVideoSink == null) return true;
            Rect r = new Rect();
            mRemoteSurfaceView.getLocalVisibleRect(r);

            final int index = ev.getActionIndex();
            float x = ev.getX(index);
            float y = ev.getY(index);
            int pointerId = ev.getPointerId(index);
            float pressure = ev.getPressure();

            int action = 0;
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN: {
                    mMobileCtrlFloatBtn.closeMenu();
                    mTouchPosMap.remove(pointerId);
                    break;
                }

                case MotionEvent.ACTION_MOVE: {
                    action = 2;
                    for (int i = 0; i < ev.getPointerCount(); i++) {
                        pointerId = ev.getPointerId(i);
                        x = ev.getX(i);
                        y = ev.getY(i);
                        int[] posCache = mTouchPosMap.get(pointerId);
                        if (posCache != null && posCache.length == 2 && (int)x == posCache[0] && (int)y == posCache[1]){
                            return true;
                        }
                        mTouchPosMap.put(pointerId, new int[]{(int)x, (int)y});
                        sendCtrl(action, x / r.width(), y / r.height(), mVideoSink.getWidth(), mVideoSink.getHeight(), pointerId, pressure);
                    }
                    return true;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP: {
                    action = 1;
                    mTouchPosMap.remove(pointerId);
                    break;
                }
                default: return true;
            }
            x = x / r.width();
            y = y / r.height();
            sendCtrl(action, x, y, mVideoSink.getWidth(), mVideoSink.getHeight(), pointerId, pressure);
            return true;
        }
    };

    private void sendChannelMsg(String msg) {
        Log.d(TAG, "sendCtrl " + msg);
        if(mDataChannel != null && mDataChannel.state() == DataChannel.State.OPEN) {
            ByteBuffer data = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
            DataChannel.Buffer buffer = new DataChannel.Buffer(data, false);
            mDataChannel.send(buffer);
        }
    }

    private void sendCtrl(int action, float x, float y, int w, int h, int pointerId, float pressure) {
        try {
            JSONObject jsonPos = new JSONObject();
            jsonPos.put("x", x);
            jsonPos.put("y", y);
            jsonPos.put("width", w);
            jsonPos.put("height", h);
            JSONObject jsonMsg = new JSONObject();
            jsonMsg.put("position", jsonPos);
            jsonMsg.put("type", 2);
            jsonMsg.put("action", action);
            jsonMsg.put("pointerId", pointerId);
            jsonMsg.put("pressure", pressure);
            sendChannelMsg(jsonMsg.toString());
        } catch (JSONException e) {
            Log.e(TAG, "sendCtrl err: " + e.getMessage());
        }

    }

    private void sendResetVideo() {
        try {
            JSONObject jsonMsg = new JSONObject();
            jsonMsg.put("type", 20);
            sendChannelMsg(jsonMsg.toString());
            Log.d(TAG, "sendResetVideo ");
        } catch (JSONException e) {
            Log.e(TAG, "sendResetVideo err: " + e.getMessage());
        }
    }

    private void sendKeyPress(int keyCode) {
        try {
            JSONObject jsonMsg = new JSONObject();
            jsonMsg.put("type", 0);
            jsonMsg.put("action", 0);
            jsonMsg.put("keyCode", keyCode);
            jsonMsg.put("metaState", 0);
            jsonMsg.put("repeat", 0);
            sendChannelMsg(jsonMsg.toString());
            jsonMsg.put("action", 1);
            sendChannelMsg(jsonMsg.toString());
        } catch (JSONException e) {
            Log.e(TAG, "sendKeyPress err: " + e.getMessage());
        }
    }

    private void sendBackOrScreenOn() {
        try {
            JSONObject jsonMsg = new JSONObject();
            jsonMsg.put("type", 4);
            jsonMsg.put("action", 0);
            sendChannelMsg(jsonMsg.toString());
            jsonMsg.put("action", 1);
            sendChannelMsg(jsonMsg.toString());
        } catch (JSONException e) {
            Log.e(TAG, "onBackOrScreenOnSend err: " + e.getMessage());
        }
    }

    public void onExitMobileConnect() {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.exit_mobile_connect)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    doEndCall();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    // 点击“返回”后的操作,这里不设置没有任何操作
                }).show();
    }

    private boolean isMuted = false;
    private void switchAudioMute() {
        if (mAudioTrack != null) {
            mAudioTrack.setVolume(isMuted ? mAudioTrackVol : 0);
            Log.i(TAG, "set vol is " + (isMuted ? mAudioTrackVol : 0));
            isMuted = !isMuted;
        }
    }

    private VolumeChangeObserver.VolumeChangeListener volumeChangeListener = new VolumeChangeObserver.VolumeChangeListener() {
        @Override
        public void onVolumeChanged(int volume) {
            Log.i(TAG, "set vol is mAudioTrack != null: " + (mAudioTrack != null));
            mAudioTrackVol = volume * 1.0 / MAX_VOL;
            Log.i(TAG, "set vol is " + mAudioTrackVol);
            if(mAudioTrack != null && !isMuted) {
                mAudioTrack.setVolume(mAudioTrackVol);
            }
        }
    };

    public static class ProxyVideoSink implements VideoSink {
        private VideoSink mTarget;
        private int width = 0;
        private int height = 0;
        @Override
        synchronized public void onFrame(VideoFrame frame) {
            if (mTarget == null) {
                Log.d(TAG, "Dropping frame in proxy because target is null.");
                return;
            }
            width = frame.getRotatedWidth();
            height = frame.getRotatedHeight();
            mTarget.onFrame(frame);
        }
        synchronized void setTarget(VideoSink target) {
            this.mTarget = target;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }

    public static class SimpleSdpObserver implements SdpObserver {
        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            Log.i(TAG, "SdpObserver: onCreateSuccess !");
        }

        @Override
        public void onSetSuccess() {
            Log.i(TAG, "SdpObserver: onSetSuccess");
        }

        @Override
        public void onCreateFailure(String msg) {
            Log.i(TAG, "SdpObserver onCreateFailure: " + msg);
        }

        @Override
        public void onSetFailure(String msg) {
            Log.e(TAG, "SdpObserver onSetFailure: " + msg);
        }
    }

    private MobileListAdapter.IMobileListListener mobileListListener = new MobileListAdapter.IMobileListListener() {
        @Override
        public void onConnect(Mobile mobile) {
            doStartCall(mobile.Id);
        }

        @Override
        public void onReset(Mobile mobile) {
            RTCSignalClient.getInstance().sendKickOut(mobile.Id);
        }
    };

    private void updateCallState(boolean idle) {
        runOnUiThread(() -> {
            if (idle) {
//                mStartCallBtn.setVisibility(View.VISIBLE);
//                mEndCallBtn.setVisibility(View.GONE);
                mRemoteSurfaceView.setVisibility(View.GONE);
                mMobileCtrlFloatBtn.setVisibility(View.GONE);
                mMobileListView.setVisibility(View.VISIBLE);
                mMobileListViewTips.setVisibility(View.VISIBLE);
                getWindow().getDecorView().setBackgroundColor(Color.WHITE);
            } else {
//                mStartCallBtn.setVisibility(View.GONE);
//                mEndCallBtn.setVisibility(View.VISIBLE);
                getWindow().getDecorView().setBackgroundColor(Color.BLACK);
                mRemoteSurfaceView.setVisibility(View.VISIBLE);
                mMobileCtrlFloatBtn.setVisibility(View.VISIBLE);
                mMobileListView.setVisibility(View.GONE);
                mMobileListViewTips.setVisibility(View.GONE);
            }
        });
    }

    public void doStartCall(String mobileId) {
        if (mPeerConnection == null) {
            mPeerConnection = createPeerConnection();
        }
        if (mPeerConnection == null) {
            Toast.makeText(getApplicationContext(), "连接失败",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        mDataChannel = mPeerConnection.createDataChannel("ClientDataChannel", new DataChannel.Init());
        selectedMobileId = mobileId;
        MediaConstraints mediaConstraints = new MediaConstraints();
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        mediaConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
        mPeerConnection.createOffer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "Create local offer success: \n" + sessionDescription.description);
                mPeerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                JSONObject message = new JSONObject();
                try {
                    message.put("type", "offer");
                    message.put("sdp", sessionDescription.description);
                    RTCSignalClient.getInstance().sendOffer(mobileId, message);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, mediaConstraints);
    }

    public void doEndCall() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        WindowHelper.makeNavigationVisible(MobileActivity.this, true);
        hanup();
    }

    private void hanup() {
        if (mPeerConnection == null) {
            return;
        }
        mVideoSink = null;
        mAudioTrack = null;
        mDataChannel.close();
        mDataChannel = null;
        mPeerConnection.close();
        mPeerConnection = null;
        updateCallState(true);
    }

    public PeerConnection createPeerConnection() {
        Log.i(TAG, "Create PeerConnection ...");
        PeerConnection.RTCConfiguration configuration = new PeerConnection.RTCConfiguration(new ArrayList<>());
        configuration.iceServers = new ArrayList<>();
        configuration.iceTransportsType = PeerConnection.IceTransportsType.ALL;
//        configuration.iceServers.add(new PeerConnection.IceServer("stun:43.134.24.200:3478", "cloudgame", "cloudgame"));
//        configuration.iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));
//        configuration.iceServers.add(new PeerConnection.IceServer("stun:stun.voipbuster.com:3478"));
        PeerConnection connection = mPeerConnectionFactory.createPeerConnection(configuration, mPeerConnectionObserver);
        if (connection == null) {
            Log.e(TAG, "Failed to createPeerConnection !");
            return null;
        }
        return connection;
    }

    public PeerConnectionFactory createPeerConnectionFactory(Context context) {
//        final VideoEncoderFactory encoderFactory;
        final VideoDecoderFactory decoderFactory;

//        encoderFactory = new DefaultVideoEncoderFactory(
//                mRootEglBase.getEglBaseContext(), false /* enableIntelVp8Encoder */, true);
        decoderFactory = new DefaultVideoDecoderFactory(mRootEglBase.getEglBaseContext());

        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions());

        PeerConnectionFactory.Builder builder = PeerConnectionFactory.builder()
//                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory);
        builder.setOptions(null);

        return builder.createPeerConnectionFactory();
    }

    private final PeerConnection.Observer mPeerConnectionObserver = new PeerConnection.Observer() {
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.i(TAG, "onSignalingChange: " + signalingState);
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            Log.i(TAG, "onIceConnectionChange: " + iceConnectionState);
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {
            Log.i(TAG, "onIceConnectionChange: " + b);
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            Log.i(TAG, "onIceGatheringChange: " + iceGatheringState);
        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            Log.i(TAG, "onIceCandidate: " + iceCandidate);

            try {
                JSONObject message = new JSONObject();
                message.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
                message.put("sdpMid", iceCandidate.sdpMid);
                message.put("candidate", iceCandidate.sdp);
                message.put("sdp", iceCandidate.sdp);
                message.put("serverUrl", iceCandidate.serverUrl);
                RTCSignalClient.getInstance().sendCandidate(selectedMobileId, message);
            } catch (JSONException e) {
                Log.e(TAG, "onIceCandidate err: " + e.getMessage());
            }
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
            for (IceCandidate iceCandidate : iceCandidates) {
                Log.i(TAG, "onIceCandidatesRemoved: " + iceCandidate);
            }
            mPeerConnection.removeIceCandidates(iceCandidates);
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            Log.i(TAG, "onAddStream: " + mediaStream.videoTracks.size());
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            Log.i(TAG, "onRemoveStream");
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
            Log.i(TAG, "onDataChannel");
        }

        @Override
        public void onRenegotiationNeeded() {
            Log.i(TAG, "onRenegotiationNeeded");
        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
            MediaStreamTrack track = rtpReceiver.track();
            if (track instanceof VideoTrack) {
                Log.i(TAG, "onAddVideoTrack");
                VideoTrack remoteVideoTrack = (VideoTrack) track;
                remoteVideoTrack.setEnabled(true);
                mVideoSink = new ProxyVideoSink();
                mVideoSink.setTarget(mRemoteSurfaceView);
                remoteVideoTrack.addSink(mVideoSink);
//                resetHandler.sendMessageDelayed(new Message(), 1000);
                runOnUiThread(() -> {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                    WindowHelper.makeNavigationVisible(MobileActivity.this, false);
                });
            }else if(track instanceof AudioTrack) {
                mAudioTrack = (AudioTrack) track;
                mAudioManager.setMode(AudioManager.MODE_NORMAL);
                mAudioManager.setSpeakerphoneOn(true);
                mAudioTrackVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 1.0 / MAX_VOL;
                Log.i(TAG, "set vol is " + mAudioTrackVol);
                mAudioTrack.setVolume(mAudioTrackVol);
                Log.i(TAG, "onAddAudioTrack current vol is " + mAudioTrackVol);
            }
        }
    };

    private final RTCSignalClient.OnSignalEventListener mOnSignalEventListener = new RTCSignalClient.OnSignalEventListener() {

        @SuppressLint("DefaultLocale")
        @Override
        public void onRemoteUserList(List<Mobile> mobileList) {
            Log.d(TAG, "update mobile list, " + mobileList.size());
            runOnUiThread(() -> {
                mMobileListViewTips.setText(String.format("已连接设备（%d）", mobileList.size()));
                mMobileListAdapter.clear();
                if(mobileList.size() > 0) {
                    mMobileListAdapter.addAll(mobileList);
                }
                mMobileListAdapter.notifyDataSetChanged();
            });
        }

        @Override
        public void onAnswer(JSONObject description) {
            Log.i(TAG, "Receive Remote Answer ...");
            try {
                String sdp = description.getString("sdp");
                mPeerConnection.setRemoteDescription(new SimpleSdpObserver(), new SessionDescription(SessionDescription.Type.ANSWER, sdp));
            } catch (JSONException e) {
                Log.e(TAG, "Receive Remote Answer Error, " + e.getMessage());
            }
            updateCallState(false);
        }

        @Override
        public void onCandidate(JSONObject candidate) {
            Log.i(TAG, "Receive Remote Candidate ...");
            try {
                IceCandidate remoteIceCandidate = new IceCandidate(
                        candidate.getString("sdpMid"),
                        candidate.getInt("sdpMLineIndex"),
                        candidate.getString("candidate")
                );
                mPeerConnection.addIceCandidate(remoteIceCandidate);
            } catch (JSONException e) {
                Log.e(TAG, "Receive Remote Candidate Error, " + e.getMessage());
            }
        }

        @Override
        public void onRemoteHangup(String userId) {
            Log.e(TAG, "Receive Remote Hanup Event ..." + userId);
            hanup();
        }
    };

    @Override
    public void onBackPressed() {
        if (mPeerConnection == null) {
            super.onBackPressed();
        }else {
            prepareBack();
        }
    }

    private static boolean isExit = false;
    @SuppressLint("HandlerLeak")
    Handler exitHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            isExit = false;
        }
    };
    Handler resetHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            sendResetVideo();
        }
    };
    public void prepareBack() {
        if (!isExit) {
            isExit = true;
            Toast.makeText(getApplicationContext(), "再按一次退出",
                    Toast.LENGTH_SHORT).show();
            exitHandler.sendMessageDelayed(new Message(), 2000);
        } else {
            doEndCall();
        }
    }
}