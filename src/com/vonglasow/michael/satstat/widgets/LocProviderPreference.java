package com.vonglasow.michael.satstat.widgets;

import java.util.ArrayList;
import java.util.List;

import com.vonglasow.michael.satstat.R;

import android.content.Context;
import android.location.LocationManager;
import android.preference.MultiSelectListPreference;
import android.util.AttributeSet;

public class LocProviderPreference extends MultiSelectListPreference {

	private Context mContext;

	public LocProviderPreference(Context context) {
		super(context);
		mContext = context;
		setSummary(R.string.pref_loc_prov_summary);
		updateProviders();
	}

	public LocProviderPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		mContext = context;
		setSummary(R.string.pref_loc_prov_summary);
		updateProviders();
	}
	
	/**
	 * Regenerates the list of selectable location providers.
	 */
	public void updateProviders() {
		List<String> providers = ((LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE)).getAllProviders();
		if (providers != null) {
			List<CharSequence> entries = new ArrayList<CharSequence>();
			List<CharSequence> values = new ArrayList<CharSequence>();
			
			for (String pr : providers) {
				entries.add(pr);
				values.add(pr);
			}
			
			setEntries(entries.toArray(new CharSequence[]{}));
			setEntryValues(values.toArray(new CharSequence[]{}));
		}
	}

}
