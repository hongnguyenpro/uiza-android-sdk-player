package vn.loitp.uizavideov3.view.floatview;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.daimajia.androidanimations.library.Techniques;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.google.gson.Gson;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

import loitp.core.R;
import vn.loitp.core.common.Constants;
import vn.loitp.core.utilities.LAnimationUtil;
import vn.loitp.core.utilities.LConnectivityUtil;
import vn.loitp.core.utilities.LLog;
import vn.loitp.core.utilities.LPref;
import vn.loitp.core.utilities.LScreenUtil;
import vn.loitp.core.utilities.LUIUtil;
import vn.loitp.restapi.uiza.model.v3.metadata.getdetailofmetadata.Data;
import vn.loitp.uizavideo.listerner.ProgressCallback;
import vn.loitp.uizavideo.view.ComunicateMng;

/**
 * Created by loitp on 3/27/2018.
 */

public class FloatingUizaVideoServiceV3 extends Service implements FloatUizaIMAVideoV3.Callback {
    private final String TAG = getClass().getSimpleName();
    private final int CLICK_ACTION_THRESHHOLD = 200;
    private WindowManager mWindowManager;
    private View mFloatingView;
    private RelativeLayout rlControl;
    private RelativeLayout moveView;
    private ImageButton btExit;
    private ImageButton btFullScreen;
    private ImageButton btPlayPause;
    private TextView tvMsg;
    private FloatUizaIMAVideoV3 floatUizaIMAVideoV3;
    private WindowManager.LayoutParams params;

    private Data data;
    private String linkPlay;
    private Gson gson = new Gson();

    private boolean isLivestream;

    public FloatingUizaVideoServiceV3() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //LLog.d(TAG, "onStartCommand");
        data = LPref.getData(this, gson);
        if (data == null) {
            LLog.d(TAG, "onStartCommand data == null");
            return super.onStartCommand(intent, flags, startId);
        }
        if (intent != null && intent.getExtras() != null) {
            linkPlay = intent.getStringExtra(Constants.FLOAT_LINK_PLAY);
            isLivestream = intent.getBooleanExtra(Constants.FLOAT_IS_LIVESTREAM, false);
            setupVideo();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void findViews() {
        rlControl = (RelativeLayout) mFloatingView.findViewById(R.id.rl_control);
        moveView = (RelativeLayout) mFloatingView.findViewById(R.id.move_view);
        btExit = (ImageButton) mFloatingView.findViewById(R.id.bt_exit);
        btFullScreen = (ImageButton) mFloatingView.findViewById(R.id.bt_full_screen);
        btPlayPause = (ImageButton) mFloatingView.findViewById(R.id.bt_play_pause);
        tvMsg = (TextView) mFloatingView.findViewById(R.id.tv_msg);
        LUIUtil.setTextShadow(tvMsg);
        tvMsg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LAnimationUtil.play(v, Techniques.Pulse, new LAnimationUtil.Callback() {
                    @Override
                    public void onCancel() {
                    }

                    @Override
                    public void onEnd() {
                        setupVideo();
                    }

                    @Override
                    public void onRepeat() {
                    }

                    @Override
                    public void onStart() {
                    }
                });
            }
        });
    }

    @Override
    public void onCreate() {
        //LLog.d(TAG, "onCreate");
        super.onCreate();

        EventBus.getDefault().register(this);

        //Inflate the floating view layout we created
        mFloatingView = LayoutInflater.from(this).inflate(R.layout.v3_layout_floating_uiza_video, null);
        findViews();

        //Add the view to the window.
        int LAYOUT_FLAG;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_PHONE;
        }
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                LAYOUT_FLAG,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        setSizeMoveView();

        //Specify the view position
        //params.gravity = Gravity.TOP | Gravity.LEFT;        //Initially view will be added to top-left corner
        //params.x = 0;
        //params.y = 0;

        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = LScreenUtil.getScreenWidth() - getWidth();
        params.y = LScreenUtil.getScreenHeight() - getHeight();

        floatUizaIMAVideoV3 = (FloatUizaIMAVideoV3) mFloatingView.findViewById(R.id.uiza_video);

        //Add the view to the window
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(mFloatingView, params);

        //Drag and move floating view using user's touch action.
        dragAndMove();

        btExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopSelf();
            }
        });
        btFullScreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LPref.setClickedPip(getApplicationContext(), true);
                Intent intent = new Intent();
                intent.putExtra(Constants.FLOAT_CLICKED_PACKAGE_NAME, getPackageName());
                intent.setAction(Constants.FLOAT_CLICKED_FULLSCREEN_V3);
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                sendBroadcast(intent);
            }
        });
        btPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (floatUizaIMAVideoV3 == null) {
                    return;
                }
                boolean isToggleResume = floatUizaIMAVideoV3.togglePauseResume();
                if (isToggleResume) {
                    btPlayPause.setImageResource(R.drawable.ic_pause_black_48dp);
                } else {
                    btPlayPause.setImageResource(R.drawable.ic_play_arrow_black_48dp);
                }
            }
        });
    }

    private void slideToPosition(int goToPosX, int goToPosY) {
        int currentPosX = params.x;
        int currentPosY = params.y;
        //LLog.d(TAG, "slideToLeft current Point: " + currentPosX + " x " + currentPosY);

        final int a = (int) Math.abs(goToPosX - currentPosX);
        final int b = (int) Math.abs(goToPosY - currentPosY);
        //LLog.d(TAG, "slideToLeft " + a + " : " + b);

        rlControl.setVisibility(View.GONE);
        setSizeMoveView();

        new CountDownTimer(500, 5) {
            public void onTick(long t) {
                float step = (500 - t) / 5;
                //LLog.d(TAG, "slideToLeft onTick step: " + step);
                //LLog.d(TAG, "slideToLeft onTick: " + a * step / 100 + " - " + b * step / 100);
                updateUISlide((int) (a * step / 100), (int) (b * step / 100));
            }

            public void onFinish() {
                //LLog.d(TAG, "slideToLeft onFinish");
                updateUISlide(a, b);
            }
        }.start();
    }

    private void updateUISlide(int x, int y) {
        params.x = x;
        params.y = y;
        mWindowManager.updateViewLayout(mFloatingView, params);
    }

    private void dragAndMove() {
        moveView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private long lastTouchDown;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:

                        //remember the initial position.
                        initialX = params.x;
                        initialY = params.y;

                        //get the touch location
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();

                        lastTouchDown = System.currentTimeMillis();
                        return true;
                    case MotionEvent.ACTION_UP:
                        //on Click event
                        clickRoot(lastTouchDown);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        //Calculate the X and Y coordinates of the view.
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);

                        //Update the layout with new X & Y coordinate
                        mWindowManager.updateViewLayout(mFloatingView, params);
                        return true;
                }
                return false;
            }
        });
    }

    private void clickRoot(long lastTouchDown) {
        if (System.currentTimeMillis() - lastTouchDown < CLICK_ACTION_THRESHHOLD) {
            rlControl.setVisibility(rlControl.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            setSizeMoveView();
        }
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        if (mFloatingView != null) {
            mWindowManager.removeView(mFloatingView);
        }
        if (floatUizaIMAVideoV3 != null) {
            floatUizaIMAVideoV3.onDestroy();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private long lastCurrentPosition;

    private void setListener() {
        //LLog.d(TAG, TAG + " addListener");
        if (floatUizaIMAVideoV3 == null || floatUizaIMAVideoV3.getPlayer() == null) {
            return;
        }
        floatUizaIMAVideoV3.getPlayer().addListener(new Player.EventListener() {
            @Override
            public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {
                //LLog.d(TAG, "onTimelineChanged");
            }

            @Override
            public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
                //LLog.d(TAG, "onTracksChanged");
            }

            @Override
            public void onLoadingChanged(boolean isLoading) {
                //LLog.d(TAG, "onLoadingChanged");
            }

            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                /*if (playbackState == Player.STATE_READY) {
                    LLog.d(TAG, "onPlayerStateChanged STATE_READY");
                }*/
            }

            @Override
            public void onRepeatModeChanged(int repeatMode) {
                //LLog.d(TAG, "onRepeatModeChanged");
            }

            @Override
            public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
                //LLog.d(TAG, "onShuffleModeEnabledChanged");
            }

            @Override
            public void onPlayerError(ExoPlaybackException error) {
                LLog.e(TAG, "onPlayerError " + error.getMessage());
                lastCurrentPosition = floatUizaIMAVideoV3.getPlayer().getCurrentPosition();
                LLog.d(TAG, "onPlayerError lastCurrentPosition " + lastCurrentPosition);
                setupVideo();
            }

            @Override
            public void onPositionDiscontinuity(int reason) {
                //LLog.d(TAG, "onPositionDiscontinuity");
            }

            @Override
            public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
                //LLog.d(TAG, "onPlaybackParametersChanged");
            }

            @Override
            public void onSeekProcessed() {
                //LLog.d(TAG, "onSeekProcessed");
            }
        });
        floatUizaIMAVideoV3.getPlayer().addAudioDebugListener(new AudioRendererEventListener() {
            @Override
            public void onAudioEnabled(DecoderCounters counters) {
                //LLog.d(TAG, "onAudioEnabled");
            }

            @Override
            public void onAudioSessionId(int audioSessionId) {
                //LLog.d(TAG, "onAudioSessionId");
            }

            @Override
            public void onAudioDecoderInitialized(String decoderName, long initializedTimestampMs, long initializationDurationMs) {
                //LLog.d(TAG, "onAudioDecoderInitialized");
            }

            @Override
            public void onAudioInputFormatChanged(Format format) {
                //LLog.d(TAG, "onAudioInputFormatChanged");
            }

            @Override
            public void onAudioSinkUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
                //LLog.d(TAG, "onAudioSinkUnderrun");
            }

            @Override
            public void onAudioDisabled(DecoderCounters counters) {
                //LLog.d(TAG, "onAudioDisabled");
            }
        });
        floatUizaIMAVideoV3.setProgressCallback(new ProgressCallback() {
            @Override
            public void onAdProgress(float currentMls, int s, float duration, int percent) {
                //LLog.d(TAG, TAG + " ad progress: " + currentMls + "/" + duration + " -> " + percent + "%");
            }

            @Override
            public void onVideoProgress(float currentMls, int s, float duration, int percent) {
                //LLog.d(TAG, TAG + " video progress: " + currentMls + "/" + duration + " -> " + percent + "%");
            }
        });
        floatUizaIMAVideoV3.getPlayer().addVideoDebugListener(new VideoRendererEventListener() {
            @Override
            public void onVideoEnabled(DecoderCounters counters) {
                //LLog.d(TAG, "onVideoEnabled");
            }

            @Override
            public void onVideoDecoderInitialized(String decoderName, long initializedTimestampMs, long initializationDurationMs) {
                //LLog.d(TAG, "onVideoDecoderInitialized");
            }

            @Override
            public void onVideoInputFormatChanged(Format format) {
                //LLog.d(TAG, "onVideoInputFormatChanged");
            }

            @Override
            public void onDroppedFrames(int count, long elapsedMs) {
                //LLog.d(TAG, "onDroppedFrames");
            }

            @Override
            public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
                //LLog.d(TAG, "onVideoSizeChanged");
            }

            @Override
            public void onRenderedFirstFrame(Surface surface) {
                //LLog.d(TAG, "onRenderedFirstFrame");
            }

            @Override
            public void onVideoDisabled(DecoderCounters counters) {
                //LLog.d(TAG, "onVideoDisabled");
            }
        });
        floatUizaIMAVideoV3.getPlayer().addMetadataOutput(new MetadataOutput() {
            @Override
            public void onMetadata(Metadata metadata) {
                //LLog.d(TAG, "onMetadata");
            }
        });
        floatUizaIMAVideoV3.getPlayer().addTextOutput(new TextOutput() {
            @Override
            public void onCues(List<Cue> cues) {
                // LLog.d(TAG, "onCues");
            }
        });
    }

    @Override
    public void isInitResult(boolean isInitSuccess) {
        if (isInitSuccess && floatUizaIMAVideoV3 != null) {
            //LLog.d(TAG, "isInitResult lastCurrentPosition: " + lastCurrentPosition);
            setListener();
            if (lastCurrentPosition > 0) {
                floatUizaIMAVideoV3.getPlayer().seekTo(lastCurrentPosition);
            }
            if (!isSendMsgToActivity) {
                //LLog.d(TAG, "isPiPInitResult isSendMsgToActivity false");
                ComunicateMng.MsgFromServiceIsInitSuccess msgFromServiceIsInitSuccess = new ComunicateMng.MsgFromServiceIsInitSuccess(null);
                msgFromServiceIsInitSuccess.setInitSuccess(isInitSuccess);
                ComunicateMng.postFromService(msgFromServiceIsInitSuccess);
                isSendMsgToActivity = true;
            }
        }
    }

    private boolean isSendMsgToActivity;

    private void setupVideo() {
        if (linkPlay == null || linkPlay.isEmpty()) {
            LLog.d(TAG, "setupVideo linkPlay == null || linkPlay.isEmpty()");
            return;
        }
        LLog.d(TAG, "setupVideo linkPlay " + linkPlay + ", isLivestream: " + isLivestream);
        if (LConnectivityUtil.isConnected(this)) {
            floatUizaIMAVideoV3.init(linkPlay, isLivestream, this);
            tvMsg.setVisibility(View.GONE);
        } else {
            tvMsg.setVisibility(View.VISIBLE);
        }
    }

    private void setSizeMoveView() {
        int widthScreen = LScreenUtil.getScreenWidth();
        if (rlControl.getVisibility() == View.VISIBLE) {
            //LLog.d(TAG, "setSizeMoveView if");
            //LLog.d(TAG, "setSizeMoveView: " + widthScreen + "x" + widthScreen);
            moveView.getLayoutParams().width = widthScreen * 70 / 100;
            moveView.getLayoutParams().height = widthScreen * 70 / 100 * 9 / 16;
        } else {
            //LLog.d(TAG, "setSizeMoveView else");
            //LLog.d(TAG, "setSizeMoveView: " + widthScreen + "x" + widthScreen);
            moveView.getLayoutParams().width = widthScreen * 50 / 100;
            moveView.getLayoutParams().height = widthScreen * 50 / 100 * 9 / 16;
        }
        moveView.requestLayout();
    }

    private int getWidth() {
        return moveView.getLayoutParams().width;
    }

    private int getHeight() {
        return moveView.getLayoutParams().height;
    }

    //listen msg from activity
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ComunicateMng.MsgFromActivity msg) {
        if (msg == null) {
            return;
        }
        if (msg instanceof ComunicateMng.MsgFromActivityPosition) {
            //LLog.d(TAG, "MsgFromActivityPosition position " + ((ComunicateMng.MsgFromActivityPosition) msg).getPosition());
            if (floatUizaIMAVideoV3 != null) {
                floatUizaIMAVideoV3.seekTo(((ComunicateMng.MsgFromActivityPosition) msg).getPosition());
            }
        } else if (msg instanceof ComunicateMng.MsgFromActivityIsInitSuccess) {
            //LLog.d(TAG, "MsgFromActivityIsInitSuccess isInitSuccess: " + ((ComunicateMng.MsgFromActivityIsInitSuccess) msg).isInitSuccess());
            if (floatUizaIMAVideoV3 != null) {
                //LLog.d(TAG, "getCurrentPosition: " + floatUizaIMAVideo.getCurrentPosition());
                ComunicateMng.MsgFromServicePosition msgFromServicePosition = new ComunicateMng.MsgFromServicePosition(null);
                msgFromServicePosition.setPosition(floatUizaIMAVideoV3.getCurrentPosition());
                ComunicateMng.postFromService(msgFromServicePosition);
                stopSelf();
            }
        }
    }
}