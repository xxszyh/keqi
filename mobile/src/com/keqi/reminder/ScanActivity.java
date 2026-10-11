package com.keqi.reminder;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import java.util.*;
import java.util.concurrent.*;

/** Camera permission is requested only here; the decoder accepts the app's pairing schema. */
@SuppressWarnings("deprecation")
public final class ScanActivity extends Activity implements SurfaceHolder.Callback {
    private SurfaceView preview;
    private Camera camera;
    private final ExecutorService decoder=Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicBoolean decoding=new java.util.concurrent.atomic.AtomicBoolean();
    private volatile boolean completed;
    protected void onCreate(Bundle saved){super.onCreate(saved);LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);TextView text=new TextView(this);text.setText("扫描电脑课期“连接手机”中的二维码\n也可以返回后输入完整配对信息");text.setPadding(24,30,24,30);layout.addView(text);preview=new SurfaceView(this);layout.addView(preview,new LinearLayout.LayoutParams(-1,0,1));preview.getHolder().addCallback(this);setContentView(layout);if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},1);}
    public void surfaceCreated(SurfaceHolder holder){open();}
    public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
    public void surfaceDestroyed(SurfaceHolder holder){release();}
    private void open(){if(camera!=null||checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED||!preview.getHolder().getSurface().isValid())return;try{camera=Camera.open();Camera.Parameters parameters=camera.getParameters();if(parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);camera.setParameters(parameters);Camera.CameraInfo info=new Camera.CameraInfo();Camera.getCameraInfo(0,info);int[] rotations={0,90,180,270};camera.setDisplayOrientation((info.orientation-rotations[getWindowManager().getDefaultDisplay().getRotation()]+360)%360);camera.setPreviewDisplay(preview.getHolder());camera.setPreviewCallback((data,c)->{
            if(completed||decoder.isShutdown()||!decoding.compareAndSet(false,true))return;Camera.Size size=c.getParameters().getPreviewSize();byte[] frame=data.clone();decoder.execute(()->{try{Map<DecodeHintType,Object> hints=new EnumMap<>(DecodeHintType.class);hints.put(DecodeHintType.POSSIBLE_FORMATS,Collections.singletonList(BarcodeFormat.QR_CODE));PlanarYUVLuminanceSource source=new PlanarYUVLuminanceSource(frame,size.width,size.height,0,0,size.width,size.height,false);Result result=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)),hints);LanClient.parse(result.getText());completed=true;runOnUiThread(()->{setResult(RESULT_OK,new Intent().putExtra("pairing",result.getText()));finish();});}catch(Exception ignored){}finally{decoding.set(false);}});
        });camera.startPreview();}catch(Exception e){release();Toast.makeText(this,"摄像头无法启动，可以返回使用文字配对",Toast.LENGTH_LONG).show();}}
    private void release(){if(camera!=null){camera.setPreviewCallback(null);camera.stopPreview();camera.release();camera=null;}}
    protected void onPause(){release();super.onPause();}
    protected void onResume(){super.onResume();if(preview!=null)open();}
    protected void onDestroy(){decoder.shutdownNow();super.onDestroy();}
    public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)open();else{Toast.makeText(this,"未开启相机，可以使用文字配对",Toast.LENGTH_LONG).show();finish();}}
}
