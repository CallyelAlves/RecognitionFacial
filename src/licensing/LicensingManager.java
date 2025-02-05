package com.app.facesample.licensing;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;


import com.neurotec.licensing.NLicense;
import com.neurotec.licensing.gui.LicensingPreferencesFragment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LicensingManager {

	// ===========================================================
	// Public nested class
	// ===========================================================

	public interface LicensingStateCallback {
		void onLicensingStateChanged(LicensingState state);
	}

	// ===========================================================
	// Private static fields
	// ===========================================================

	private static final String TAG = LicensingManager.class.getSimpleName();

	private static LicensingManager sInstance;

	// ===========================================================
	// Public static fields
	// ===========================================================

	private static final String LICENSE_FACE_EXTRACTION = "Biometrics.FaceExtraction";
	private static final String LICENSE_FACE_MATCHING = "Biometrics.FaceMatching";

	public static final int REQUEST_CODE_LICENSING_PREFERENCES = 10;

	// ===========================================================
	// Public static method
	// ===========================================================

	public static synchronized LicensingManager getInstance() {
		if (sInstance == null) {
			sInstance = new LicensingManager();
		}
		return sInstance;
	}

	public static boolean isActivated(String license) {
		if (license == null) throw new NullPointerException("license");
		try {
			return NLicense.isComponentActivated(license);
		} catch (IOException e) {
			Log.e(TAG, "IOException", e);
			return false;
		}
	}


	public static boolean isLicensesObtained() {

		try {
			return NLicense.isComponentActivated(LICENSE_FACE_EXTRACTION) &&
					NLicense.isComponentActivated(LICENSE_FACE_MATCHING);
		} catch (IOException e) {
			return false;
		}
	}

	public static void release() {
		try {
			NLicense.releaseComponents(LICENSE_FACE_EXTRACTION);
			NLicense.releaseComponents(LICENSE_FACE_MATCHING);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// ===========================================================
	// Private fields
	// ===========================================================

	private List<String> mComponents;

	// ===========================================================
	// Private constructor
	// ===========================================================

	private LicensingManager() {
		mComponents = new ArrayList<String>();
	}

	// ===========================================================
	// Private methods
	// ===========================================================

	// ===========================================================
	// Public methods
	// ===========================================================

	public void reobtainComponents(final Context context, final LicensingStateCallback callback) throws IOException {
		if (callback == null) throw new NullPointerException("callback");
		new AsyncTask<Boolean, Boolean, Boolean>() {
			@Override
			protected void onPreExecute() {
				super.onPreExecute();
				callback.onLicensingStateChanged(LicensingState.OBTAINING);
			}
			@Override
			protected Boolean doInBackground(Boolean... params) {
				try {
					return reobtainComponents(context);
				} catch (Exception e) {
					Log.e(TAG, "Exception", e);
					return false;
				}
			}
			@Override
			protected void onPostExecute(Boolean result) {
				super.onPostExecute(result);
				callback.onLicensingStateChanged(result ? LicensingState.OBTAINED : LicensingState.NOT_OBTAINED);
			}
		}.execute();
	}

	public boolean reobtainComponents(Context context) throws IOException {
		List<String> reobtainedComponents = new ArrayList<String>(mComponents);
		return obtainComponents(context, reobtainedComponents);
	}

	public void obtainComponents(Context context, LicensingStateCallback callback, List<String> components) {
		if (context == null) throw new NullPointerException("context");
		obtainComponents(callback, components, LicensingPreferencesFragment.getServerAddress(context), LicensingPreferencesFragment.getServerPort(context));
	}

	public void obtainComponents(final LicensingStateCallback callback, final List<String> components, final String address, final int port) {
		if (callback == null) throw new NullPointerException("callback");
		new AsyncTask<Boolean, Boolean, Boolean>() {
			@Override
			protected void onPreExecute() {
				super.onPreExecute();
				callback.onLicensingStateChanged(LicensingState.OBTAINING);
			}
			@Override
			protected Boolean doInBackground(Boolean... params) {
				try {
					return obtainComponents(components, address, port);
				} catch (Exception e) {
					Log.e(TAG, "Exception", e);
					return false;
				}
			}
			@Override
			protected void onPostExecute(Boolean result) {
				super.onPostExecute(result);
				callback.onLicensingStateChanged(result ? LicensingState.OBTAINED : LicensingState.NOT_OBTAINED);
			}
		}.execute();
	}

	public boolean obtainComponents(Context context) throws IOException {
		return obtainComponents(components(), LicensingPreferencesFragment.getServerAddress(context), LicensingPreferencesFragment.getServerPort(context));
	}

	public boolean obtainComponents(Context context, List<String> components) throws IOException {
		if (context == null) throw new NullPointerException("context");
		return obtainComponents(components, LicensingPreferencesFragment.getServerAddress(context), LicensingPreferencesFragment.getServerPort(context));
	}

	public boolean obtainComponents(List<String> components, String address, int port) throws IOException {
		if (components == null) throw new NullPointerException("components");
		if (components.isEmpty()) throw new IllegalArgumentException("List of components is empty");

		Log.i(TAG, String.format("Obtaining licenses from server %s:%s", address, port));

		boolean result = true;
		mComponents.addAll(components);
		for (String component : components) {
			boolean available = false;

			available = NLicense.obtainComponents(address, port, component);
			result &= available;
			Log.i(TAG, String.format("Obtaining '%s' license %s.", component, available ? "succeeded" : "failed"));
		}
		return result;
	}


	public static  List<String> components() {
        return Arrays.asList(LicensingManager.LICENSE_FACE_EXTRACTION,
                LicensingManager.LICENSE_FACE_MATCHING);
    }


}
