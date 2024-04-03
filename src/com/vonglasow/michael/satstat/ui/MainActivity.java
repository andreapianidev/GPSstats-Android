/*
 * Copyright © 2013–2016 Michael von Glasow.
 * 
 * This file is part of LSRN Tools.
 *
 * LSRN Tools is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSRN Tools is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSRN Tools.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.vonglasow.michael.satstat.ui;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import org.mapsforge.map.android.graphics.AndroidGraphicFactory;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ContextThemeWrapper;
import android.telephony.CellInfo;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import static android.telephony.PhoneStateListener.LISTEN_CELL_INFO;
import static android.telephony.PhoneStateListener.LISTEN_CELL_LOCATION;
import static android.telephony.PhoneStateListener.LISTEN_DATA_CONNECTION_STATE;
import static android.telephony.PhoneStateListener.LISTEN_NONE;
import static android.telephony.PhoneStateListener.LISTEN_SIGNAL_STRENGTHS;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.vonglasow.michael.satstat.Const;
import com.vonglasow.michael.satstat.GpsEventReceiver;
import com.vonglasow.michael.satstat.R;
import com.vonglasow.michael.satstat.data.CellTower;
import com.vonglasow.michael.satstat.data.CellTowerList;

public class MainActivity extends AppCompatActivity implements GpsStatus.Listener, LocationListener, OnSharedPreferenceChangeListener, SensorEventListener {
	private static final String TAG = MainActivity.class.getSimpleName();

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link android.support.v4.app.FragmentPagerAdapter} derivative, which
     * will keep every loaded fragment in memory. If this becomes too memory
     * intensive, it may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    ViewPager mViewPager;
    
    /**
     * The tab view to switch between the fragments of the MainView.
     */
    TabLayout mTabLayout;
    
    /**
     * Whether the activity is stopped. 
     */
    boolean isStopped;
    
    /**
     * Whether we are running on a wide-screen device
     */
    boolean isWideScreen;

    /**
     * The rate in microseconds at which we would like to receive updates from the sensors.
     * 
     * This is chosen to meet the mission of SatStat, which is to let the human know the values reported
     * and evaluate if the sensors are working. At very high rates, the numbers cannot be read, and at
     * very slow rates, it is hard to tell if the updates would work at faster rates. We chose 100 ms as
     * a reasonable tradeoff.
     */
	private static final int iSensorRate = 100000;

	GpsSectionFragment gpsSectionFragment = null;
	SensorSectionFragment sensorSectionFragment = null;
	RadioSectionFragment radioSectionFragment = null;
	MapSectionFragment mapSectionFragment = null;
	
	TelephonyManager telephonyManager;
	ConnectivityManager connectivityManager;
	WifiManager wifiManager;
	LocationManager locationManager;
	SensorManager sensorManager;
	
	boolean[] permsRequested = new boolean[Const.PERM_REQUEST_MAX + 1];

	private Sensor mOrSensor;
	private Sensor mAccSensor;
	private Sensor mGyroSensor;
	private Sensor mMagSensor;
	private Sensor mLightSensor;
	private Sensor mProximitySensor;
	private Sensor mPressureSensor;
	private Sensor mHumiditySensor;
	private Sensor mTempSensor;
	
	private long mOrLast = 0;
	private long mAccLast = 0;
	private long mGyroLast = 0;
	private long mMagLast = 0;
	private long mLightLast = 0;
	private long mProximityLast = 0;
	private long mPressureLast = 0;
	private long mHumidityLast = 0;
	private long mTempLast = 0;

	/**
	 * Converts screen rotation to orientation for devices with a naturally tall screen.
	 */
	private final static Integer OR_FROM_ROT_TALL[] = {
		ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
		ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
		ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
		ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE};

	/**
	 * Converts screen rotation to orientation for devices with a naturally wide screen.
	 */
	private final static Integer OR_FROM_ROT_WIDE[] = {
		ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
		ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
		ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
		ActivityInfo.SCREEN_ORIENTATION_PORTRAIT};

	SharedPreferences mSharedPreferences;
	
	boolean prefUnitType = true;
	boolean prefKnots = false;
	int prefCoord = Const.KEY_PREF_COORD_DECIMAL;
	boolean prefUtc = false;
	boolean prefCid = false;
	boolean prefCid2 = false;
	int prefWifiSort = 0;
	boolean prefMapOffline = false;
	String prefMapPath = Const.MAP_PATH_DEFAULT;

	/** 
	 * The {@link PhoneStateListener} for getting radio network updates 
	 */
	private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
		@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	 	public void onCellInfoChanged(List<CellInfo> cellInfo) {
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) 
				return;
			if (radioSectionFragment != null)
				radioSectionFragment.updateCellData(null, null, cellInfo);
	 	}
	 	
		public void onCellLocationChanged (CellLocation location) {
			if (radioSectionFragment != null)
				radioSectionFragment.updateCellData(location, null, null);
		}
		
		public void onDataConnectionStateChanged (int state, int networkType) {
			if (radioSectionFragment != null)
				radioSectionFragment.onNetworkTypeChanged(networkType);
		}
		
		public void onSignalStrengthsChanged (SignalStrength signalStrength) {
			if (radioSectionFragment != null)
				radioSectionFragment.updateCellData(null, signalStrength, null);
		}
	};
	
	/** 
	 * The {@link BroadcastReceiver} for getting radio network updates 
	 */
	private final BroadcastReceiver mWifiScanReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context c, Intent intent) {
			if (intent.getAction() == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
				if (radioSectionFragment != null) {
					radioSectionFragment.scanResults = wifiManager.getScanResults();
					radioSectionFragment.refreshWifiResults();
				}
			} else {
				//something has changed about WiFi setup, rescan
				wifiManager.startScan();
			}
		}
	};
	
	private Thread.UncaughtExceptionHandler defaultUEH;
	
	
    /**
     * Converts a bearing (in degrees) into a directional name.
     */
    public static String formatOrientation(Context context, float bearing) {
		return 
			(bearing < 11.25) ? context.getString(R.string.value_N) :
				(bearing < 33.75) ? context.getString(R.string.value_NNE) :
					(bearing < 56.25) ? context.getString(R.string.value_NE) :
						(bearing < 78.75) ? context.getString(R.string.value_ENE) :
							(bearing < 101.25) ? context.getString(R.string.value_E) :
								(bearing < 123.75) ? context.getString(R.string.value_ESE) :
									(bearing < 146.25) ? context.getString(R.string.value_SE) :
										(bearing < 168.75) ? context.getString(R.string.value_SSE) :
											(bearing < 191.25) ? context.getString(R.string.value_S) :
												(bearing < 213.75) ? context.getString(R.string.value_SSW) :
													(bearing < 236.25) ? context.getString(R.string.value_SW) :
														(bearing < 258.75) ? context.getString(R.string.value_WSW) :
															(bearing < 280.25) ? context.getString(R.string.value_W) :
																(bearing < 302.75) ? context.getString(R.string.value_WNW) :
																	(bearing < 325.25) ? context.getString(R.string.value_NW) :
																		(bearing < 347.75) ? context.getString(R.string.value_NNW) :
																			context.getString(R.string.value_N);
    }
	
    
	/**
	 * Returns the serving cell.
	 * <p>
	 * This method iterates through the cell tower lists passed in
	 * {@code lists} and looks for any entries marked as the serving cell.
	 *  
	 * @param lists An array of {@link com.vonglasow.michael.satstat.data.CellTowerList}
	 * instances
	 * @return The serving cell, if one is found, or {@code null} if none is
	 * found. If multiple serving cells are found in {@code lists}, no
	 * assertion is made which cell will be returned, or even that results
	 * will be consistent between calls.
	 */
	public static CellTower getServingCell(CellTowerList[] lists) {
		for (CellTowerList<CellTower> towers : lists) {
			for (CellTower cell : towers.getAll())
				if (cell.hasSource() && cell.isServing())
					return cell;
		}
		return null;
	}
    

    /**
     * Called when a sensor's accuracy has changed. Does nothing.
     */
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
        
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
        	public void uncaughtException(Thread t, Throwable e) {
        		Context c = getApplicationContext();
        		File dumpDir = c.getExternalFilesDir(null);
        		DateFormat fmt = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.ROOT);
        		fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        		String fileName = String.format("satstat-%s.log", fmt.format(new Date(System.currentTimeMillis())));

        		File dumpFile = new File (dumpDir, fileName);
        		PrintStream s;
        		try {
        			InputStream buildInStream = getResources().openRawResource(R.raw.build);
        			s = new PrintStream(dumpFile);
        			s.append("SatStat build: ");
        			
        			int i;
        			try {
        				i = buildInStream.read();
        				while (i != -1) {
        					s.write(i);
        					i = buildInStream.read();
        				}
        				buildInStream.close();
        			} catch (IOException e1) {
        				e1.printStackTrace();
        			}
        			
        			s.append("\n\n");
        			e.printStackTrace(s);
        			s.flush();
        			s.close();
        			
        			Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        			Uri contentUri = Uri.fromFile(dumpFile);
        			mediaScanIntent.setData(contentUri);
        			c.sendBroadcast(mediaScanIntent);
        		} catch (FileNotFoundException e2) {
        			e2.printStackTrace();
        		}
        		defaultUEH.uncaughtException(t, e);
        	}
        });
        
		mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
		prefUnitType = mSharedPreferences.getBoolean(Const.KEY_PREF_UNIT_TYPE, prefUnitType);
		prefKnots = mSharedPreferences.getBoolean(Const.KEY_PREF_KNOTS, prefKnots);
		prefCoord = Integer.valueOf(mSharedPreferences.getString(Const.KEY_PREF_COORD, Integer.toString(prefCoord)));
		prefUtc = mSharedPreferences.getBoolean(Const.KEY_PREF_UTC, prefUtc);
		prefCid = mSharedPreferences.getBoolean(Const.KEY_PREF_CID, prefCid);
		prefCid2 = mSharedPreferences.getBoolean(Const.KEY_PREF_CID2, prefCid2);
		prefWifiSort = Integer.valueOf(mSharedPreferences.getString(Const.KEY_PREF_WIFI_SORT, Integer.toString(prefWifiSort)));
		prefMapOffline = mSharedPreferences.getBoolean(Const.KEY_PREF_MAP_OFFLINE, prefMapOffline);
		prefMapPath = mSharedPreferences.getString(Const.KEY_PREF_MAP_PATH, prefMapPath);

        ActionBar actionBar = getSupportActionBar();
        
        setContentView(R.layout.activity_main);
        
        // Find out default screen orientation
        Configuration config = getResources().getConfiguration();
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        int rot = wm.getDefaultDisplay().getRotation();
        isWideScreen = (config.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        	       (rot == Surface.ROTATION_0 || rot == Surface.ROTATION_180) ||
        	       config.orientation == Configuration.ORIENTATION_PORTRAIT &&
        	       (rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270));
        Log.d(TAG, "isWideScreen=" + Boolean.toString(isWideScreen));
        
        // Create the adapter that will return a fragment for each of the
        // primary sections of the app.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        
        Context ctx = new ContextThemeWrapper(getApplication(), R.style.AppTheme);
        mTabLayout = new TabLayout(ctx);
        LinearLayout.LayoutParams mTabLayoutParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        mTabLayout.setLayoutParams(mTabLayoutParams);
        
        for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
        	TabLayout.Tab newTab = mTabLayout.newTab();
        	newTab.setIcon(mSectionsPagerAdapter.getPageIcon(i));
        	mTabLayout.addTab(newTab);
        }

        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setCustomView(mTabLayout);

        mTabLayout.setOnTabSelectedListener(new TabLayout.ViewPagerOnTabSelectedListener(mViewPager));
        mViewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(mTabLayout));
        
        // This is needed by the mapsforge library.
        AndroidGraphicFactory.createInstance(this.getApplication());

        // Get system services for event delivery
    	locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        mOrSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);        
        mAccSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);     
        mGyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE); 
        mMagSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD); 
        mLightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        mProximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mPressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        mHumiditySensor = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY);
        mTempSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
        telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        connectivityManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
    }
	
	

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        
        return true;
    }
    
    @Override
    protected void onDestroy() {
		mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
		super.onDestroy();
    }
    
    /**
     * Called when the status of the GPS changes. Updates GPS display.
     */
    public void onGpsStatusChanged (int event) {
		GpsStatus status = locationManager.getGpsStatus(null);
		int satsInView = 0;
		int satsUsed = 0;
		Iterable<GpsSatellite> sats = status.getSatellites();
		for (GpsSatellite sat : sats) {
			satsInView++;
			if (sat.usedInFix()) {
				satsUsed++;
			}
		}

		if (gpsSectionFragment != null) {
    		gpsSectionFragment.onGpsStatusChanged(status, satsInView, satsUsed, sats);
    	}
    	
		if (mapSectionFragment != null) {
			mapSectionFragment.onGpsStatusChanged(status, satsInView, satsUsed, sats);
		}
    }
    
    /**
     * Called when a new location is found by a registered location provider.
     * Stores the location and updates GPS display and map view.
     */
    public void onLocationChanged(Location location) {
    	// update map view
		if (mapSectionFragment != null) {
			mapSectionFragment.onLocationChanged(location);
		}
    	
    	// update GPS view
    	if ((location.getProvider().equals(LocationManager.GPS_PROVIDER)) && (gpsSectionFragment != null)) {
    		gpsSectionFragment.onLocationChanged(location);
    	}
    }
    
    /**
     * Called when a menu item is selected, and triggers the appropriate action.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	int itemId = item.getItemId();
		if (itemId == R.id.action_agps) {
			Log.i(TAG, "User requested AGPS data update");
			if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
				GpsEventReceiver.refreshAgps(this, false, true);
			else
				ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, Const.PERM_REQUEST_REFRESH_AGPS);
			return true;
		} else if (itemId == R.id.action_settings) {
			startActivity(new Intent(this, SettingsActivity.class));
			return true;
		} else if (itemId == R.id.action_legend) {
			startActivity(new Intent(this, LegendActivity.class));
			return true;
		} else if (itemId == R.id.action_about) {
			startActivity(new Intent(this, AboutActivity.class));
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
    }
    
    /**
     * Called when a location provider is disabled. Does nothing.
     */
    public void onProviderDisabled(String provider) {}

    /**
     * Called when a location provider is enabled. Does nothing.
     */
    public void onProviderEnabled(String provider) {}
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
    	StringBuilder messageBuilder = new StringBuilder();
    	for (int i = 0; i < grantResults.length; i++)
    		if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
    			if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
    				NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    				notificationManager.cancel(Const.PERM_REQUEST_LOCATION_NOTIFICATION);

    				if (permsRequested[Const.PERM_REQUEST_PHONE_STATE_LISTENER]) {
    					registerPhoneStateListener();
    					permsRequested[Const.PERM_REQUEST_PHONE_STATE_LISTENER] = false;
    				}
    				if (permsRequested[Const.PERM_REQUEST_LOCATION_UPDATES]) {
    					requestLocationUpdates();
    					permsRequested[Const.PERM_REQUEST_LOCATION_UPDATES] = false;
    				}
    				if (permsRequested[Const.PERM_REQUEST_CELL_INFO]) {
    					if (radioSectionFragment != null)
    						radioSectionFragment.updateCellData(null, null, null);
    					permsRequested[Const.PERM_REQUEST_CELL_INFO] = false;
    				}
    				if (requestCode == Const.PERM_REQUEST_REFRESH_AGPS)
    					GpsEventReceiver.refreshAgps(this, false, true);
    			} else if (requestCode == Const.PERM_REQUEST_REFRESH_AGPS) {
    				if (messageBuilder.length() > 0)
    					messageBuilder.append("\n");
    				messageBuilder.append(getString(R.string.status_perm_refresh_agps));
    				Log.w(TAG, "Location permission not granted, cannot update AGPS data");
    			} else {
    				if (messageBuilder.length() > 0)
    					messageBuilder.append("\n");
    				messageBuilder.append(getString(R.string.status_perm_location));
    				Log.w(TAG, "ACCESS_FINE_LOCATION permission not granted. Location and cell info will not be available.");
    			} // if grantResults[i]
    		} else if (permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
    			if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
    				if (permsRequested[Const.PERM_REQUEST_OFFLINE_MAP] && (mapSectionFragment != null)) {
    					mapSectionFragment.onMapSourceChanged();
    					permsRequested[Const.PERM_REQUEST_OFFLINE_MAP] = false;
    				}
    			} else {
    				if (messageBuilder.length() > 0)
    					messageBuilder.append("\n");
    				messageBuilder.append(getString(R.string.status_perm_offline_map));
    				Log.w(TAG, "WRITE_EXTERNAL_STORAGE permission not granted. Offline map will not be available.");
    			}
    		} // if permissions[i].equals()
    	// for i
    	String message = messageBuilder.toString();
    	if (!message.isEmpty()) {
    		int length = (message.contains("\n")) ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT;
    		Toast.makeText(this, message, length).show();
    	}
    }

    /**
     * Called when a sensor's reading changes. Updates sensor display and rotates sky plot according
     * to bearing.
     */
    public void onSensorChanged(SensorEvent event) {
		//to enforce sensor rate
		boolean isRateElapsed = false;
		
		switch (event.sensor.getType()) {
			case Sensor.TYPE_ACCELEROMETER:
				isRateElapsed = (event.timestamp / 1000) - mAccLast >= iSensorRate;
				// if Z acceleration is greater than X/Y combined, lock rotation, else unlock
				if (Math.pow(event.values[2], 2) > Math.pow(event.values[0], 2) + Math.pow(event.values[1], 2)) {
					// workaround (SCREEN_ORIENTATION_LOCK is unsupported on API < 18)
					if (isWideScreen)
						setRequestedOrientation(OR_FROM_ROT_WIDE[this.getWindowManager().getDefaultDisplay().getRotation()]);
					else
						setRequestedOrientation(OR_FROM_ROT_TALL[this.getWindowManager().getDefaultDisplay().getRotation()]);
				} else {
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
				}
				break;
			case Sensor.TYPE_ORIENTATION:
				isRateElapsed = (event.timestamp / 1000) - mOrLast >= iSensorRate;
				break;
			case Sensor.TYPE_GYROSCOPE:
				isRateElapsed = (event.timestamp / 1000) - mGyroLast >= iSensorRate;
				break;
			case Sensor.TYPE_MAGNETIC_FIELD:
				isRateElapsed = (event.timestamp / 1000) - mMagLast >= iSensorRate;
				break;
			case Sensor.TYPE_LIGHT:
				isRateElapsed = (event.timestamp / 1000) - mLightLast >= iSensorRate;
				break;
			case Sensor.TYPE_PROXIMITY:
				isRateElapsed = (event.timestamp / 1000) - mProximityLast >= iSensorRate;
				break;
			case Sensor.TYPE_PRESSURE:
				isRateElapsed = (event.timestamp / 1000) - mPressureLast >= iSensorRate;
				break;
			case Sensor.TYPE_RELATIVE_HUMIDITY:
				isRateElapsed = (event.timestamp / 1000) - mHumidityLast >= iSensorRate;
				break;
			case Sensor.TYPE_AMBIENT_TEMPERATURE:
				isRateElapsed = (event.timestamp / 1000) - mTempLast >= iSensorRate;
				break;
		}
		
		if (!isRateElapsed)
			return;
		
		switch (event.sensor.getType()) {
		case Sensor.TYPE_ACCELEROMETER:
			mAccLast = event.timestamp / 1000;
			break;
		case Sensor.TYPE_ORIENTATION:
			mOrLast = event.timestamp / 1000;
			break;
		case Sensor.TYPE_GYROSCOPE:
			mGyroLast = event.timestamp / 1000;
			break;
		case Sensor.TYPE_MAGNETIC_FIELD:
			mMagLast = event.timestamp / 1000;
			break;
		case Sensor.TYPE_LIGHT:
			mLightLast = event.timestamp / 1000;
			break;
		case Sensor.TYPE_PROXIMITY:
			mProximityLast = event.timestamp / 1000;
			break;
		case Sensor.TYPE_PRESSURE:
			mPressureLast = event.timestamp / 1000;
			break;
		case Sensor.TYPE_RELATIVE_HUMIDITY:
			mHumidityLast = event.timestamp / 1000;
			break;
		case Sensor.TYPE_AMBIENT_TEMPERATURE:
			mTempLast = event.timestamp / 1000;
			break;
		}

		
		if (sensorSectionFragment != null) {
			sensorSectionFragment.onSensorChanged(event);
    	}
		if (gpsSectionFragment != null) {
			gpsSectionFragment.onSensorChanged(event);
		}
    }
    	
	/**
	 * Called when preferences are changed.
	 * 
	 * This method processes changed to KEY_PREF_LOC_PROV, the list of selected
	 * location providers. When called, it will unregister for all location 
	 * updates and re-register for updates from the selected location providers.
	 * (This includes unregistering and immediately re-registering for those
	 * providers which remain selected – this is due to the fact that Android
	 * does not support unregistering from a single location provider.) 
	 */
    @Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (key.equals(Const.KEY_PREF_LOC_PROV)) {
			// user selected or deselected location providers, refresh list
			registerLocationProviders();
		} else if (key.equals(Const.KEY_PREF_UNIT_TYPE)) {
			prefUnitType = sharedPreferences.getBoolean(Const.KEY_PREF_UNIT_TYPE, prefUnitType);
		} else if (key.equals(Const.KEY_PREF_KNOTS)) {
			prefKnots = sharedPreferences.getBoolean(Const.KEY_PREF_KNOTS, prefKnots);
		} else if (key.equals(Const.KEY_PREF_COORD)) {
			prefCoord = Integer.valueOf(mSharedPreferences.getString(Const.KEY_PREF_COORD, Integer.toString(prefCoord)));
		} else if (key.equals(Const.KEY_PREF_UTC)) {
			prefUtc = sharedPreferences.getBoolean(Const.KEY_PREF_UTC, prefUtc);
		} else if (key.equals(Const.KEY_PREF_CID)) {
			prefCid = sharedPreferences.getBoolean(Const.KEY_PREF_CID, prefCid);
		} else if (key.equals(Const.KEY_PREF_CID2)) {
			prefCid2 = sharedPreferences.getBoolean(Const.KEY_PREF_CID2, prefCid2);
		} else if (key.equals(Const.KEY_PREF_WIFI_SORT)) {
			prefWifiSort = Integer.valueOf(mSharedPreferences.getString(Const.KEY_PREF_WIFI_SORT, Integer.toString(prefWifiSort)));
		} else if (key.equals(Const.KEY_PREF_MAP_OFFLINE)) {
			prefMapOffline = sharedPreferences.getBoolean(Const.KEY_PREF_MAP_OFFLINE, prefMapOffline);
			if (mapSectionFragment != null)
				mapSectionFragment.onMapSourceChanged();
			if (prefMapOffline && (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED))
				ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, Const.PERM_REQUEST_OFFLINE_MAP);
		} else if (key.equals(Const.KEY_PREF_MAP_PATH)) {
			prefMapPath = sharedPreferences.getString(Const.KEY_PREF_MAP_PATH, prefMapPath);
			if (mapSectionFragment != null)
				mapSectionFragment.onMapSourceChanged();
		} else if (key.equals(Const.KEY_PREF_MAP_PURGE)) {
			if (sharedPreferences.getBoolean(Const.KEY_PREF_MAP_PURGE, false) && (mapSectionFragment != null))
				mapSectionFragment.onMapSourceChanged();
		}
	}

    @Override
    protected void onStart() {
        super.onStart();
        isStopped = false;
        registerLocationProviders();
        sensorManager.registerListener(this, mOrSensor, iSensorRate);
        sensorManager.registerListener(this, mAccSensor, iSensorRate);
        sensorManager.registerListener(this, mGyroSensor, iSensorRate);
        sensorManager.registerListener(this, mMagSensor, iSensorRate);
        sensorManager.registerListener(this, mLightSensor, iSensorRate);
        sensorManager.registerListener(this, mProximitySensor, iSensorRate);
        sensorManager.registerListener(this, mPressureSensor, iSensorRate);
        sensorManager.registerListener(this, mHumiditySensor, iSensorRate);
        sensorManager.registerListener(this, mTempSensor, iSensorRate);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        	registerPhoneStateListener();
        else
        	permsRequested[Const.PERM_REQUEST_PHONE_STATE_LISTENER] = true;
        
        // register for certain WiFi events indicating that new networks may be in range
        // An access point scan has completed, and results are available.
        registerReceiver(mWifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        
        // The state of Wi-Fi connectivity has changed.
        registerReceiver(mWifiScanReceiver, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
        
        // The RSSI (signal strength) has changed.
        registerReceiver(mWifiScanReceiver, new IntentFilter(WifiManager.RSSI_CHANGED_ACTION));
        
        // A connection to the supplicant has been established or the connection to the supplicant has been lost.
        registerReceiver(mWifiScanReceiver, new IntentFilter(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION));

		permsRequested[Const.PERM_REQUEST_OFFLINE_MAP] = prefMapOffline;

		/*
		 * Refresh map layers when offline map is selected and we have storage permission
		 * (it might have been granted while we were gone, in which case we wouldn't have the layer)
		 */
		if (prefMapOffline
				&& (mapSectionFragment != null)
				&& (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED))
			mapSectionFragment.onMapSourceChanged();

        requestPermissions();
    }

    /**
     * Called when a location provider's status changes. Does nothing.
     */
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    protected void onStop() {
    	isStopped = true;
    	locationManager.removeUpdates(this);
    	locationManager.removeGpsStatusListener(this);
    	sensorManager.unregisterListener(this);
        telephonyManager.listen(mPhoneStateListener, LISTEN_NONE);
        try {
        	unregisterReceiver(mWifiScanReceiver);
        } catch (IllegalArgumentException e) {
        	// sometimes the receiver isn't registered, make sure we don't crash
        	Log.d(TAG, "WifiScanReceiver was never registered, caught exception");
        }
        // we'll just skip that so locations will get invalidated in any case
        //providerInvalidationHandler.removeCallbacksAndMessages(null);
        super.onStop();
    }
    
	/**
	 * Registers for updates with selected location providers.
	 */
	protected void registerLocationProviders() {
		Set<String> providers = new HashSet<String>(mSharedPreferences.getStringSet(Const.KEY_PREF_LOC_PROV, new HashSet<String>(Arrays.asList(new String[] {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}))));
		locationManager.removeUpdates(this);
		
		if (mapSectionFragment != null)
			mapSectionFragment.onLocationProvidersChanged(providers);
		
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
			requestLocationUpdates();
		else
			permsRequested[Const.PERM_REQUEST_LOCATION_UPDATES] = true;
	}
	
	/**
	 * Registers the {@link PhoneStateListener} for all necessary events
	 */
	private void registerPhoneStateListener() {
		telephonyManager.listen(mPhoneStateListener, (LISTEN_CELL_INFO | LISTEN_CELL_LOCATION | LISTEN_DATA_CONNECTION_STATE | LISTEN_SIGNAL_STRENGTHS));
	}
	
	/**
	 * Requests location updates from the selected location providers.
	 * 
	 * This method is intended to be called by {@link #registerLocationProviders(Context)} or by
	 * {@link #onRequestPermissionsResult(int, String[], int[])}, depending on whether permissions need to be
	 * requested.
	 */
	private void requestLocationUpdates() {
		Set<String> providers = new HashSet<String>(mSharedPreferences.getStringSet(Const.KEY_PREF_LOC_PROV, new HashSet<String>(Arrays.asList(new String[] {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}))));
		List<String> allProviders = locationManager.getAllProviders();
		
		if (!isStopped) {
			for (String pr : providers) {
				if (allProviders.indexOf(pr) >= 0) {
					try {
						locationManager.requestLocationUpdates(pr, 0, 0, this);
						Log.d(TAG, "Registered with provider: " + pr);
					} catch (SecurityException e) {
						Log.w(TAG, "Permission not granted for " + pr + " location provider. Data display will not be available for this provider.");
					}
				} else {
					Log.w(TAG, "No " + pr + " location provider found. Data display will not be available for this provider.");
				}
			}
		}
		
        try {
        	// if GPS is not selected, request location updates but don't store location
        	if ((!providers.contains(LocationManager.GPS_PROVIDER)) && (!isStopped) && (allProviders.indexOf(LocationManager.GPS_PROVIDER) >= 0))
        		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);

        	locationManager.addGpsStatusListener(this);
        } catch (SecurityException e) {
        	Log.w(TAG, "Permission not granted for " + LocationManager.GPS_PROVIDER + " location provider. Data display will not be available for this provider.");
        }
	}
	
	private void requestPermissions() {
		ArrayList<String> perms = new ArrayList<String>();

		if (permsRequested[Const.PERM_REQUEST_PHONE_STATE_LISTENER]
				|| permsRequested[Const.PERM_REQUEST_LOCATION_UPDATES]
				|| permsRequested[Const.PERM_REQUEST_CELL_INFO]) {
			if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
				perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
		}

		if (permsRequested[Const.PERM_REQUEST_OFFLINE_MAP]) {
			if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
				perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
		}

		if (perms.size() > 0)
			ActivityCompat.requestPermissions(this, perms.toArray(new String[]{}), Const.PERM_REQUEST_STARTUP);
	}

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a DummySectionFragment (defined as a static inner class
            // below) with the page number as its lone argument.
        	Fragment fragment;
            switch (position) {
            case 0:
            	fragment = new GpsSectionFragment();
                return fragment;
            case 1:
            	fragment = new SensorSectionFragment();
                return fragment;
            case 2:
            	fragment = new RadioSectionFragment();
                return fragment;
            case 3:
            	fragment = new MapSectionFragment();
                return fragment;
            }
        return null;
        }

        @Override
        public int getCount() {
            // Show 4 total pages.
            return 4;
        }

        public Drawable getPageIcon(int position) {
            switch (position) {
                case 0:
                    return getResources().getDrawable(R.drawable.ic_action_gps);
                case 1:
                    return getResources().getDrawable(R.drawable.ic_action_sensor);
                case 2:
                    return getResources().getDrawable(R.drawable.ic_action_radio);
                case 3:
                    return getResources().getDrawable(R.drawable.ic_action_map);
            }
            return null;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Locale l = Locale.getDefault();
            switch (position) {
                case 0:
                    return getString(R.string.title_section1).toUpperCase(l);
                case 1:
                    return getString(R.string.title_section2).toUpperCase(l);
                case 2:
                    return getString(R.string.title_section3).toUpperCase(l);
                case 3:
                    return getString(R.string.title_section4).toUpperCase(l);
            }
            return null;
        }
    }
}
