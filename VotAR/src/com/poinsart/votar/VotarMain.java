package com.poinsart.votar;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.lang.System;
import org.json.JSONArray;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.media.ExifInterface;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 * 
 * @see SystemUiHider
 */
public class VotarMain extends Activity {
	public static final int MEDIA_TYPE_IMAGE = 1;
	public static final int MEDIA_TYPE_VIDEO = 2;

	private Uri cameraFileUri;
	public String lastPhotoFilePath=null;
	public String lastPointsJsonString=null;

	/** Create a file Uri for saving an image or video */

	/**
	 * The instance of the {@link SystemUiHider} for this activity.
	 */
	private static final int CAMERA_REQUEST=1;
	private static final int GALLERY_REQUEST=2;
	private ImageView imageView;
	private ProgressBar bar[]= {null, null, null, null};
	private TextView barLabel[]={null, null, null, null};
	public TextView wifiLabel=null;
	private LinearLayout mainLayout, controlLayout, imageLayout;
	
	public CountDownLatch photoLock;
	public CountDownLatch pointsLock;
	
	private static final long TIME_DIVIDE=100000;
	public long datatimestamp=Long.MIN_VALUE/TIME_DIVIDE;
	
	public BitmapFactory.Options opt=null;
	
	private VotarWebServer votarwebserver;
	public AssetManager assetMgr;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
	    System.loadLibrary("VotAR");
	    super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		assetMgr = this.getAssets();
		
		imageView = (ImageView) findViewById(R.id.imageView);
		bar[0]=(ProgressBar) findViewById(R.id.bar_a);
		bar[1]=(ProgressBar) findViewById(R.id.bar_b);
		bar[2]=(ProgressBar) findViewById(R.id.bar_c);
		bar[3]=(ProgressBar) findViewById(R.id.bar_d);
		
		barLabel[0]=(TextView) findViewById(R.id.label_a);
		barLabel[1]=(TextView) findViewById(R.id.label_b);
		barLabel[2]=(TextView) findViewById(R.id.label_c);
		barLabel[3]=(TextView) findViewById(R.id.label_d);
		
		wifiLabel=(TextView) findViewById(R.id.label_wifi);
		
		mainLayout=((LinearLayout)findViewById(R.id.mainLayout));
		controlLayout=((LinearLayout)findViewById(R.id.controlLayout));
		imageLayout=((LinearLayout)findViewById(R.id.imageLayout));
		
		adjustLayoutForOrientation(getResources().getConfiguration().orientation);

		findViewById(R.id.buttonCamera).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);

			    cameraFileUri = getOutputMediaFileUri(MEDIA_TYPE_IMAGE); // create a file to save the image
			    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraFileUri); // set the image file name

				startActivityForResult(cameraIntent, CAMERA_REQUEST);
			}
		});
		findViewById(R.id.buttonGallery).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent();
				intent.setType("image/*");
				intent.setAction(Intent.ACTION_GET_CONTENT);
				startActivityForResult(Intent.createChooser(intent, "Select Picture"), GALLERY_REQUEST);
			}
		});
		votarwebserver=new VotarWebServer(51285, this);
		try {
			votarwebserver.start();
		} catch (IOException e) {
			Log.w("Votar MainAct", "The webserver could not be started, remote display wont be available");
		}
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (votarwebserver!=null)
			votarwebserver.stop();
	}
	
	private void adjustLayoutForOrientation(int orientation) {
		if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
			mainLayout.setOrientation(LinearLayout.HORIZONTAL);
			controlLayout.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.MATCH_PARENT,1));
			//imageLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT,1));
		} else if (orientation == Configuration.ORIENTATION_PORTRAIT){
			mainLayout.setOrientation(LinearLayout.VERTICAL);
			controlLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT,1));
			//imageLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT,1));
			//imageLayout.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT,1));
		}
	}

	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		adjustLayoutForOrientation(newConfig.orientation);
	}

	private static Uri getOutputMediaFileUri(int type){
	      return Uri.fromFile(getOutputMediaFile(type));
	}

	/** Create a File for saving an image or video */
	@SuppressLint("SimpleDateFormat")
	private static File getOutputMediaFile(int type){
	    // To be safe, you should check that the SDCard is mounted
	    // using Environment.getExternalStorageState() before doing this.

	    File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
	              Environment.DIRECTORY_PICTURES), "VotAR");
	    // This location works best if you want the created images to be shared
	    // between applications and persist after your app has been uninstalled.

	    // Create the storage directory if it does not exist
	    if (! mediaStorageDir.exists()){
	        if (! mediaStorageDir.mkdirs()){
	            Log.w("VotAR camera", "failed to create directory");
	            return null;
	        }
	    }

	    // Create a media file name
	    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
	    File mediaFile;
	    if (type == MEDIA_TYPE_IMAGE){
	        mediaFile = new File(mediaStorageDir.getPath() + File.separator + "IMG_"+ timeStamp + ".jpg");
	    } else if(type == MEDIA_TYPE_VIDEO) {
	        mediaFile = new File(mediaStorageDir.getPath() + File.separator + "VID_"+ timeStamp + ".mp4");
	    } else {
	        return null;
	    }

	    return mediaFile;
	}
	
    public String getWifiIp() {
    	int ip;
    	
    	WifiManager wm=(WifiManager) getSystemService(Context.WIFI_SERVICE);
    	WifiInfo wi=wm.getConnectionInfo();
    	if (wi==null || wi.getNetworkId()==-1)
    		return null;
    	ip=wi.getIpAddress();
    	if (ip==0x00000000)
    		return null;
    	return "" + (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >> 24 ) & 0xFF);
    }

    public boolean updateWifiStatus() {
    	String wifiIp=getWifiIp();
		if (wifiIp!=null) {
			wifiLabel.setText("http://"+wifiIp+":51285");
			return true;
		}
		wifiLabel.setText(R.string.nowificon);
		return false;
    }
    
    @Override
	protected void onResume()
	{
    	super.onResume();
    	IntentFilter intentFilter = new IntentFilter();
    	intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
    	registerReceiver(broadcastReceiver, intentFilter);
//Log.i("Votar MainAct", "Wifi update from onResume");
    	updateWifiStatus();
    }

    @Override
    protected void onPause()
    {
    	super.onPause();
    	unregisterReceiver(broadcastReceiver);
    }
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver()
    {
    	@Override
    	public void onReceive(Context context, Intent intent) {
    		final String action = intent.getAction();
    		if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
    			updateWifiStatus();
    		}
    	}
	};
	

	
	private class AnalyzeTask extends AsyncTask<Bitmap, Integer, Void> {
		private Mark mark[];
		private int prcount[];
		private Bitmap photo;
		
		private ProgressDialog mProgressDialog;
		
		// found in native code, check jni exports
		public native Mark[] nativeAnalyze(Bitmap b, int prcount[]);
		
		@Override
		protected Void doInBackground(Bitmap... photos) {
			// data is being updated and not ready for HTTP service, lock them
			photoLock=new CountDownLatch(1);
			pointsLock=new CountDownLatch(1);
			datatimestamp=System.nanoTime()/TIME_DIVIDE;
			
			opt.inJustDecodeBounds = true;
		    BitmapFactory.decodeFile(lastPhotoFilePath, opt);
		    opt.inSampleSize=computeSampleSize(opt.outWidth, opt.outHeight);
		    
		    opt.inJustDecodeBounds = false;
			photo=BitmapFactory.decodeFile(lastPhotoFilePath, opt);
			if (photo==null)
				return null;
			
			// handle orientation changes in photos
			// inspired by MKJParekh answer on stackoverflow
			// http://stackoverflow.com/questions/12299963/facing-orientation-issue-with-camera-captured-image-on-android-phones
	        ExifInterface exif;
			try {
				exif = new ExifInterface(lastPhotoFilePath);
			} catch (IOException e) {
				// invalid photo format ?
				return null;
			}
	        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
	        Log.e("orientation", "" + orientation);
	        
	        Matrix m = new Matrix();
	        if ((orientation == ExifInterface.ORIENTATION_ROTATE_180)) {
	            m.postRotate(180);
	            Log.i("VotAR Main", "Orientation change: 70");
	            photo = Bitmap.createBitmap(photo, 0, 0, photo.getWidth(), photo.getHeight(), m, true);
	        } else if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
	            m.postRotate(90);
	            Log.i("VotAR Main", "Orientation change: 90");
	            photo = Bitmap.createBitmap(photo, 0, 0, photo.getWidth(), photo.getHeight(), m, true);
	        }
	        else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
	        	m.postRotate(270);
	            Log.i("VotAR Main", "Orientation change: 270");
	            photo = Bitmap.createBitmap(photo, 0, 0, photo.getWidth(), photo.getHeight(), m, true);
	        }
			
			photoLock.countDown();
			prcount=new int[4];
			mark=nativeAnalyze(photo, prcount);
			return null;
		}
		
		private void showProgressDialog(int step) {
			switch (step) {
			case 0:
				mProgressDialog = new ProgressDialog(VotarMain.this);
				mProgressDialog.setTitle("Processing image");
				//mProgressDialog.setProgressNumberFormat("%1d%%"); // not working on API level 9
				mProgressDialog.setMax(100);
				mProgressDialog.setProgress(0);
				mProgressDialog.setIndeterminate(false);
				mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
				mProgressDialog.setMessage("Initializing...");
				mProgressDialog.show();
				break;
			case 1:
				mProgressDialog.setProgress(20);
				float mpSize=(float)photo.getWidth()*photo.getHeight()/1000000;
				mProgressDialog.setTitle("Processing image: "+new DecimalFormat("#.#").format(mpSize)+"mp");
				mProgressDialog.setMessage("Preprocessing surface...");
				break;
			case 2:
				mProgressDialog.setProgress(40);
				mProgressDialog.setMessage("Examining color patterns...");
				break;
					
			case 3:
				mProgressDialog.setProgress(90);
				mProgressDialog.setMessage("Compiling results...");
				break;
			case 4:
				mProgressDialog.dismiss();
				break;
			}
		}

		@Override
		protected void onProgressUpdate(Integer... progress) {
			showProgressDialog(progress[0]);
		}
		
		protected void onPreExecute() {
			showProgressDialog(0);
		}
		
		@Override
		protected void onPostExecute(Void unused) {
			// nativeAnalyze returns null if anything goes wrong, just silently ignore
			if (prcount!=null && mark!=null) {
				int max=0;
				for (int i=0; i<4; i++) {
					if (prcount[i]>max)
						max=prcount[i];
					barLabel[i].setText(new String(Character.toChars(97+i))+": "+prcount[i]);
				}
				for (int i=0; i<4; i++) {
					bar[i].setMax(max);
					bar[i].setProgress(prcount[i]);
				}

				writeJsonPoints(mark);
				
				if (photo.getWidth()>imageLayout.getWidth() && photo.getHeight()>imageLayout.getHeight()) {
					int maxWidth, maxHeight;
					if (photo.getWidth() / imageLayout.getWidth() > photo.getHeight() / imageLayout.getHeight()) {
						// photo is large, limit to imageView width, preserve aspect ratio
						maxWidth=imageLayout.getWidth();
						maxHeight=photo.getHeight()/(photo.getWidth()/imageLayout.getWidth());
					} else {
						// photo is high, limit to imageview height, preserve aspect ratio
						maxHeight=imageLayout.getHeight();
						maxWidth=photo.getWidth()/(photo.getHeight()/imageLayout.getHeight());
					}
					Log.i("VotAR Main","Image resized for display: "+photo.getWidth()+"x"+photo.getHeight()
							+" -> "+maxWidth+"x"+maxHeight+" [in "+imageLayout.getWidth()+"x"+imageLayout.getHeight()+"]");
					photo=Bitmap.createScaledBitmap(photo, maxWidth, maxHeight, true);
				}
				
				imageView.setImageBitmap(photo);
			}


			pointsLock.countDown();
			showProgressDialog(4);
	    }
	}

	/*
	 *  for now this just save the points into a json string,
	 *  could use a file for more permanent storage
	 */
	private void writeJsonPoints(Mark mark[]) {
		if (mark==null)
			return;
		JSONArray jsonmark=new JSONArray();
		for (int i=0; i<mark.length; i++) {
			JSONArray jsoncurrentmark=new JSONArray(Arrays.asList(new Integer[]{mark[i].x*opt.inSampleSize, mark[i].y*opt.inSampleSize, mark[i].pr}));
			jsonmark.put(jsoncurrentmark);
		}
		lastPointsJsonString=jsonmark.toString();		
	}
	
	private int computeSampleSize(int w, int h) {
		int pixelCount=w*h;
		int allowedPixelCount;
		long maxMemory = Runtime.getRuntime().maxMemory();
		
		// large memory devices are allowed bigger images
		if (maxMemory<48000000) {
			allowedPixelCount=4000000;
		} else if (maxMemory<96000000) {
			allowedPixelCount=5500000;
		} else if (maxMemory<128000000) {
			allowedPixelCount=7000000;
		} else {
			allowedPixelCount=16000000;
		}
		
		if (pixelCount<=allowedPixelCount) {
			return 1;
		}
		
		// if the image is too large for processing
		// we divide it's dimensions by 2,
		// so it's pixelCount goes down 4, should be enough
		return 2;
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		Bitmap photo=null;
		opt = new BitmapFactory.Options();
		opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
		Uri uri=null;
		
		
		if (resultCode != RESULT_OK )
			return;
		
		if (requestCode == GALLERY_REQUEST && data != null && data.getData() != null) {
	        uri = data.getData();
	        if (uri == null)
	        	return;
	        //User had pick an image.
	        Cursor cursor = getContentResolver().query(uri, new String[] { android.provider.MediaStore.Images.ImageColumns.DATA }, null, null, null);
	        cursor.moveToFirst();
	        //Link to the image
	        lastPhotoFilePath = cursor.getString(0);
	        cursor.close();
		}
		if (requestCode == CAMERA_REQUEST) {
	 		uri = cameraFileUri;
	 		lastPhotoFilePath = uri.getPath();
		}
		
		if (lastPhotoFilePath==null)
			return;
		
		lastPointsJsonString=null;
		
		new AnalyzeTask().execute(photo);
	}
}