package com.tencent.assistd.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.tencent.assistd.R;
import com.tencent.assistd.signal.Mobile;
import com.tencent.assistd.signal.RTCSignalClient;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MobileActivity extends AppCompatActivity {
    private TextView mLogcatView;
    private Button mStartCallBtn;
    private Button mEndCallBtn;

    private static final String TAG = "MobileActivity";
    private static final String selectedMobileId = "LKN5T19520004193";

    private EglBase mRootEglBase;

    private PeerConnection mPeerConnection;
    private PeerConnectionFactory mPeerConnectionFactory;

    private SurfaceViewRenderer mRemoteSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mobile);

        mLogcatView = findViewById(R.id.LogcatView);
        mStartCallBtn = findViewById(R.id.StartCallButton);
        mEndCallBtn = findViewById(R.id.EndCallButton);

        RTCSignalClient.getInstance().setSignalEventListener(mOnSignalEventListener);

        String serverAddr = getIntent().getStringExtra("ServerAddr");
        assert serverAddr != null;
        RTCSignalClient.getInstance().joinRoom(serverAddr);

        mRootEglBase = EglBase.create();

        mRemoteSurfaceView = findViewById(R.id.RemoteSurfaceView);

        mRemoteSurfaceView.init(mRootEglBase.getEglBaseContext(), null);
        mRemoteSurfaceView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        mRemoteSurfaceView.setMirror(false);
        mRemoteSurfaceView.setEnableHardwareScaler(false);
        mRemoteSurfaceView.setZOrderMediaOverlay(true);


        mPeerConnectionFactory = createPeerConnectionFactory(this);

        // NOTE: this _must_ happen while PeerConnectionFactory is alive!
        Logging.enableLogToDebugOutput(Logging.Severity.LS_VERBOSE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doEndCall();
        mRemoteSurfaceView.release();
        PeerConnectionFactory.stopInternalTracingCapture();
        PeerConnectionFactory.shutdownInternalTracer();
        RTCSignalClient.getInstance().leaveRoom();
    }

    public static class ProxyVideoSink implements VideoSink {
        private VideoSink mTarget;
        @Override
        synchronized public void onFrame(VideoFrame frame) {
            if (mTarget == null) {
                Log.d(TAG, "Dropping frame in proxy because target is null.");
                return;
            }
            mTarget.onFrame(frame);
        }
        synchronized void setTarget(VideoSink target) {
            this.mTarget = target;
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
            Log.e(TAG, "SdpObserver onCreateFailure: " + msg);
        }

        @Override
        public void onSetFailure(String msg) {
            Log.e(TAG, "SdpObserver onSetFailure: " + msg);
        }
    }

    public void onClickStartCallButton(View v) {
        doStartCall();
    }

    public void onClickEndCallButton(View v) {
        doEndCall();
    }

    private void updateCallState(boolean idle) {
        runOnUiThread(() -> {
            if (idle) {
                mStartCallBtn.setVisibility(View.VISIBLE);
                mEndCallBtn.setVisibility(View.GONE);
                mRemoteSurfaceView.setVisibility(View.GONE);
            } else {
                mStartCallBtn.setVisibility(View.GONE);
                mEndCallBtn.setVisibility(View.VISIBLE);
                mRemoteSurfaceView.setVisibility(View.VISIBLE);
            }
        });
    }

    public void doStartCall() {
        logcatOnUI("Start Call, Wait ...");
        if (mPeerConnection == null) {
            mPeerConnection = createPeerConnection();
        }
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
                    RTCSignalClient.getInstance().sendOffer(selectedMobileId, message);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, mediaConstraints);
    }

    public void doEndCall() {
        logcatOnUI("End Call, Wait ...");
        hanup();
        RTCSignalClient.getInstance().sendKickOut(selectedMobileId);
    }

    private void hanup() {
        logcatOnUI("Hanup Call, Wait ...");
        if (mPeerConnection == null) {
            return;
        }
        mPeerConnection.close();
        mPeerConnection = null;
        logcatOnUI("Hanup Done.");
        updateCallState(true);
    }

    public PeerConnection createPeerConnection() {
        Log.i(TAG, "Create PeerConnection ...");
        PeerConnection.RTCConfiguration configuration = new PeerConnection.RTCConfiguration(new ArrayList<>());
        PeerConnection connection = mPeerConnectionFactory.createPeerConnection(configuration, mPeerConnectionObserver);
        if (connection == null) {
            Log.e(TAG, "Failed to createPeerConnection !");
            return null;
        }
        return connection;
    }

    public PeerConnectionFactory createPeerConnectionFactory(Context context) {
        final VideoEncoderFactory encoderFactory;
        final VideoDecoderFactory decoderFactory;

        encoderFactory = new DefaultVideoEncoderFactory(
                mRootEglBase.getEglBaseContext(), false /* enableIntelVp8Encoder */, true);
        decoderFactory = new DefaultVideoDecoderFactory(mRootEglBase.getEglBaseContext());

        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions());

        PeerConnectionFactory.Builder builder = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
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
                ProxyVideoSink videoSink = new ProxyVideoSink();
                videoSink.setTarget(mRemoteSurfaceView);
                remoteVideoTrack.addSink(videoSink);
            }else if(track instanceof AudioTrack) {
//                ((AudioTrack) track).setVolume(0);
            }
        }
    };

    private final RTCSignalClient.OnSignalEventListener mOnSignalEventListener = new RTCSignalClient.OnSignalEventListener() {

        @Override
        public void onRemoteUserList(List<Mobile> mobileList) {

        }

        @Override
        public void onAnswer(JSONObject description) {
            logcatOnUI("Receive Remote Answer ...");
            try {
                String sdp = description.getString("sdp");
                mPeerConnection.setRemoteDescription(new SimpleSdpObserver(), new SessionDescription(SessionDescription.Type.ANSWER, sdp));
            } catch (JSONException e) {
                logcatOnUI("Receive Remote Answer Error, " + e.getMessage());
            }
            updateCallState(false);
        }

        @Override
        public void onCandidate(JSONObject candidate) {
            logcatOnUI("Receive Remote Candidate ...");
            try {
                IceCandidate remoteIceCandidate = new IceCandidate(
                        candidate.getString("sdpMid"),
                        candidate.getInt("sdpMLineIndex"),
                        candidate.getString("candidate")
                );
                mPeerConnection.addIceCandidate(remoteIceCandidate);
            } catch (JSONException e) {
                logcatOnUI("Receive Remote Candidate Error, " + e.getMessage());
            }
        }

        private void onRemoteHangup(String userId) {
            logcatOnUI("Receive Remote Hanup Event ..." + userId);
            hanup();
        }
    };

    private void logcatOnUI(String msg) {
        Log.i(TAG, msg);
        runOnUiThread(() -> {
            String output = mLogcatView.getText() + "\n" + msg;
            mLogcatView.setText(output);
        });
    }
}