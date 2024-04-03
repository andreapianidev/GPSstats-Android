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

package com.vonglasow.michael.satstat.utils;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

/**
 * A task which retrieves the contents of a remote directory in the background and notifies a listener
 * upon completion.
 */
public class RemoteDirListTask extends AsyncTask<String, Void, RemoteFile[]> {
	private static final String TAG = RemoteDirListTask.class.getSimpleName();
	private RemoteDirListListener listener = null;
	private RemoteFile parent = null;
	
	/**
	 * Creates a new {@code RemoteDirListTask} task, and registers it with a listener.
	 * 
	 * @param listener The {@link RemoteDirListListener} which will be notified when the task has completed.
	 * @param parent The directory to be listed. When this task finishes, it populates the {@code children}
	 * member of {@code parent} with the objects it retrieved. May be {@code null}.
	 */
	public RemoteDirListTask(RemoteDirListListener listener, RemoteFile parent) {
		super();
		this.listener = listener;
		this.parent = parent;
	}
	
	
	@Override
	protected RemoteFile[] doInBackground(String... params) {
		Uri uri = Uri.parse(params[0]);
		RemoteFile[] rfiles = null;
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
		df.setTimeZone(TimeZone.getDefault());
		if (uri.getScheme() == null)
			return null;
		if (uri.getScheme().equals("http") || uri.getScheme().equals("https"))
			rfiles = HttpDownloader.list(params[0]);
		
		if (rfiles == null)
			Log.w(TAG, "Error – could not retrieve content!");
		else if (rfiles.length == 0)
			Log.w(TAG, "Remote directory is empty.");
		/*
		else {
			Log.d(TAG, "Remote directory contents:");
			for (RemoteFile rf : rfiles)
				Log.d(TAG, String.format("\n\t%s \t%s \t%s \t%s",
					rf.isDirectory ? "D" : "F",
					df.format(new Date(rf.timestamp)),
					rf.getFriendlySize(),
					rf.name));
		}
		 */
		return rfiles;
	}

	protected void onPostExecute(RemoteFile[] result) {
		if (parent != null)
			parent.children = result;
		if (listener != null)
			listener.onRemoteDirListReady(this, result);
	}
}