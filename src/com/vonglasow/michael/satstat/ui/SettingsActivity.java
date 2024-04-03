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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.layer.cache.TileCache;

import com.vonglasow.michael.satstat.Const;
import com.vonglasow.michael.satstat.PasvLocListenerService;
import com.vonglasow.michael.satstat.R;

import android.Manifest;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;
import ua.com.vassiliev.androidfilebrowser.FileBrowserActivity;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;

public class SettingsActivity extends AppCompatActivity implements OnPreferenceClickListener, OnSharedPreferenceChangeListener{
	public static final String TAG = SettingsActivity.class.getSimpleName();

	public static final int REQUEST_CODE_PICK_MAP_PATH = 1;

	private SharedPreferences mSharedPreferences;
	Preference prefMapPath;
	String prefMapPathValue = Const.MAP_PATH_DEFAULT;
	Preference prefMapDownload;
	Preference prefMapPurge;

	@SuppressLint("NewApi")
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_CODE_PICK_MAP_PATH) {
			if (resultCode == RESULT_OK) {
				setMapPath(data.getStringExtra(FileBrowserActivity.returnDirectoryParameter));
			}
		}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);

		// Show the Up button in the action bar.
		ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);

		// Display the fragment as the main content.
		getFragmentManager().beginTransaction()
		.replace(android.R.id.content, new SettingsFragment())
		.commit();

		mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // some logic to use the pre-1.7 setting KEY_PREF_UPDATE_WIFI as a
		// fallback if KEY_PREF_UPDATE_NETWORKS is not set
		if (!mSharedPreferences.contains(Const.KEY_PREF_UPDATE_NETWORKS)) {
			Set<String> fallbackUpdateNetworks = new HashSet<String>();
			if (mSharedPreferences.getBoolean(Const.KEY_PREF_UPDATE_WIFI, false)) {
				fallbackUpdateNetworks.add(Const.KEY_PREF_UPDATE_NETWORKS_WIFI);
			}
			SharedPreferences.Editor spEditor = mSharedPreferences.edit();
			spEditor.putStringSet(Const.KEY_PREF_UPDATE_NETWORKS, fallbackUpdateNetworks);
			spEditor.commit();
		}
		
		// by default, show GPS and network location in map
		if (!mSharedPreferences.contains(Const.KEY_PREF_LOC_PROV)) {
			Set<String> defaultLocProvs = new HashSet<String>(Arrays.asList(new String[] {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}));
			SharedPreferences.Editor spEditor = mSharedPreferences.edit();
			spEditor.putStringSet(Const.KEY_PREF_LOC_PROV, defaultLocProvs);
			spEditor.commit();
		}
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			onBackPressed();
			return true;
		}

		return super.onOptionsItemSelected(item);
	}


	/**
	 * @author k9mail, mvglasow
	 */
	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (preference == prefMapPath) {
			Intent intent = new Intent(FileBrowserActivity.INTENT_ACTION_SELECT_DIR, null, this, FileBrowserActivity.class);
			File mapDir = new File(prefMapPathValue);
			if (!mapDir.exists())
				mapDir.mkdirs();
			if (!mapDir.exists() || !mapDir.canWrite())
				mapDir = getExternalFilesDir(null);
			else if (!mapDir.isDirectory())
				mapDir = mapDir.getParentFile();
			intent.putExtra(FileBrowserActivity.startDirectoryParameter, mapDir.getAbsolutePath());
			startActivityForResult(intent, REQUEST_CODE_PICK_MAP_PATH);
			return true;
		} else if (preference == prefMapDownload) {
			if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
				startActivity(new Intent(this, MapDownloadActivity.class));
			else
				ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, Const.PERM_REQUEST_MAP_DOWNLOAD);
			return true;
		} else if (preference == prefMapPurge) {
			TileCache mapRendererTileCache = AndroidUtil.createExternalStorageTileCache(this,
					Const.TILE_CACHE_INTERNAL_RENDER_THEME, 0, 256, true);
			TileCache mapDownloadTileCache = AndroidUtil.createExternalStorageTileCache(this,
					Const.TILE_CACHE_OSM, 0, 256, true);
			mapRendererTileCache.purge();
			mapDownloadTileCache.purge();
			mapRendererTileCache.destroy();
			mapDownloadTileCache.destroy();
			
			/*
			 * This is a hack to have the map view (if it is active) redraw the map:
			 * Setting the preference to true causes the listener in MainView to fire.
			 * The listener will, in turn, determine if a map view is active and, if so,
			 * cause it to reload all tile layers, then reset the preference to false.
			 */
			SharedPreferences.Editor spEditor = mSharedPreferences.edit();
			spEditor.putBoolean(Const.KEY_PREF_MAP_PURGE, true);
			spEditor.commit();
			
			String message = getString(R.string.status_map_purged);
			Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

			return true;
		} else
			return false;
	}


	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		if ((requestCode == Const.PERM_REQUEST_MAP_DOWNLOAD) && (grantResults.length > 0)) {
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
				startActivity(new Intent(this, MapDownloadActivity.class));
			else {
				String message = getString(R.string.status_perm_map_download);
				Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
			}
		} else if ((requestCode == Const.PERM_REQUEST_LOCATION_PREF) && (grantResults.length > 0)) {
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
				notificationManager.cancel(Const.PERM_REQUEST_LOCATION_NOTIFICATION);
			} else
				Log.i(TAG, "ACCESS_FINE_LOCATION permission not granted, a notification will appear when it is needed");
		}
	}


	@Override
	protected void onResume() {
		super.onResume();
		mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
		
		SettingsFragment sf = (SettingsFragment) getFragmentManager().findFragmentById(android.R.id.content);
		Preference prefUpdateLast = sf.findPreference(Const.KEY_PREF_UPDATE_LAST);
        final long value = mSharedPreferences.getLong(Const.KEY_PREF_UPDATE_LAST, 0);
        prefUpdateLast.setSummary(String.format(getString(R.string.pref_lastupdate_summary), value));
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		boolean needsLocationPerm = false;
		if (key.equals(Const.KEY_PREF_NOTIFY_FIX) || key.equals(Const.KEY_PREF_NOTIFY_SEARCH)) {
			boolean notifyFix = sharedPreferences.getBoolean(Const.KEY_PREF_NOTIFY_FIX, false);
			boolean notifySearch = sharedPreferences.getBoolean(Const.KEY_PREF_NOTIFY_SEARCH, false);
			if (!(notifyFix || notifySearch)) {
				Intent stopServiceIntent = new Intent(this, PasvLocListenerService.class);
				this.stopService(stopServiceIntent);
			} else if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
				needsLocationPerm = true;
			}
		} else if (key.equals(Const.KEY_PREF_UPDATE_FREQ)) {
			// this piece of code is necessary because Android has no way
			// of updating the preference summary automatically. I am
			// told the absence of such functionality is a feature...
			SettingsFragment sf = (SettingsFragment) getFragmentManager().findFragmentById(android.R.id.content);
			ListPreference prefUpdateFreq = (ListPreference) sf.findPreference(Const.KEY_PREF_UPDATE_FREQ);
            final String value = sharedPreferences.getString(key, key);
            final int index = prefUpdateFreq.findIndexOfValue(value);            
            if (index >= 0) {
                final String summary = (String)prefUpdateFreq.getEntries()[index];         
                prefUpdateFreq.setSummary(summary);
            }
		} else if (key.equals(Const.KEY_PREF_MAP_PATH)) {
			SettingsFragment sf = (SettingsFragment) getFragmentManager().findFragmentById(android.R.id.content);
			Preference prefMapPath = sf.findPreference(Const.KEY_PREF_MAP_PATH);
			prefMapPathValue = mSharedPreferences.getString(Const.KEY_PREF_MAP_PATH, prefMapPathValue);
			prefMapPath.setSummary(prefMapPathValue);
		} else if (key.equals(Const.KEY_PREF_UPDATE_NETWORKS)) {
			if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
				Set<String> updateNetworks = sharedPreferences.getStringSet(Const.KEY_PREF_UPDATE_NETWORKS, new HashSet<String>());
				if (!updateNetworks.isEmpty())
					needsLocationPerm = true;
			}
		}
		if (needsLocationPerm)
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, Const.PERM_REQUEST_LOCATION_PREF);
	}

	@Override
	protected void onStart() {
		super.onStart();

		SettingsFragment sf = (SettingsFragment) getFragmentManager().findFragmentById(android.R.id.content);
		prefMapPath = sf.findPreference(Const.KEY_PREF_MAP_PATH);
		prefMapPathValue = mSharedPreferences.getString(Const.KEY_PREF_MAP_PATH, prefMapPathValue);
		prefMapPath.setSummary(prefMapPathValue);
		prefMapPath.setOnPreferenceClickListener(this);
		prefMapDownload = sf.findPreference(Const.KEY_PREF_MAP_DOWNLOAD);
		prefMapDownload.setOnPreferenceClickListener(this);
		prefMapPurge = sf.findPreference(Const.KEY_PREF_MAP_PURGE);
		prefMapPurge.setOnPreferenceClickListener(this);
	}

	@Override
	protected void onStop() {
		mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
		super.onStop();
	}

	protected void setMapPath(String path) {
		SharedPreferences.Editor spEditor = mSharedPreferences.edit();
		spEditor.putString(Const.KEY_PREF_MAP_PATH, path);
		spEditor.commit();
	}

	public static class SettingsFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			// Load the preferences from an XML resource
			addPreferencesFromResource(R.xml.preferences);
		}

	}
}
