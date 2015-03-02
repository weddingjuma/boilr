package mobi.boilr.boilr.views.fragments;

import java.io.IOException;

import mobi.boilr.boilr.R;
import mobi.boilr.boilr.activities.AlarmSettingsActivity;
import mobi.boilr.boilr.domain.AndroidNotifier;
import mobi.boilr.boilr.preference.ThemableRingtonePreference;
import mobi.boilr.boilr.services.LocalBinder;
import mobi.boilr.boilr.services.StorageAndControlService;
import mobi.boilr.boilr.utils.Conversions;
import mobi.boilr.boilr.utils.IconToast;
import mobi.boilr.boilr.utils.Log;
import mobi.boilr.libdynticker.core.Pair;
import mobi.boilr.libpricealarm.Alarm;
import mobi.boilr.libpricealarm.TimeFrameSmallerOrEqualUpdateIntervalException;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;

public abstract class AlarmSettingsFragment extends AlarmPreferencesFragment {
	protected Alarm alarm;

	private class InitializePreferencesConnection implements ServiceConnection {
		private int alarmID;

		public InitializePreferencesConnection(int alarmID) {
			this.alarmID = alarmID;
		}

		@Override
		public void onServiceConnected(ComponentName className, IBinder binder) {
			mStorageAndControlService = ((LocalBinder<StorageAndControlService>) binder).getService();
			mBound = true;

			alarm = mStorageAndControlService.getAlarm(alarmID);

			String exchangeCode = alarm.getExchangeCode();
			String exchangeName = alarm.getExchange().getName();
			mExchangeIndex = mExchangeListPref.findIndexOfValue(exchangeCode);
			mExchangeListPref.setSummary(exchangeName);

			if(mRecoverSavedInstance) {
				mAlarmAlertTypePref.setSummary(mAlarmAlertTypePref.getEntry());
				mAlertSoundPref.setRingtoneType(Integer.parseInt(mAlarmAlertTypePref.getValue()));
				mAlertSoundPref.setSummary(mAlertSoundPref.getEntry());
			} else {
				mExchangeListPref.setValue(exchangeCode);

				AndroidNotifier notifier = (AndroidNotifier) alarm.getNotifier();
				Integer alertType = notifier.getAlertType();
				if(alertType == null) {
					alertType = Integer.parseInt(mSharedPrefs.getString(SettingsFragment.PREF_KEY_DEFAULT_ALERT_TYPE, ""));
				}
				mAlarmAlertTypePref.setValue(alertType.toString());
				mAlarmAlertTypePref.setSummary(mAlarmAlertTypePref.getEntries()[mAlarmAlertTypePref.findIndexOfValue(alertType.toString())]);
				mAlertSoundPref.setRingtoneType(alertType);

				String alertSound = notifier.getAlertSound();
				if(alertSound != null) {
					mAlertSoundPref.setValue(alertSound);
				} else {
					mAlertSoundPref.setDefaultValue();
				}

				Boolean isVibrate = notifier.isVibrate();
				if(isVibrate == null) {
					isVibrate = mSharedPrefs.getBoolean(SettingsFragment.PREF_KEY_VIBRATE_DEFAULT, true);
				}
				mVibratePref.setChecked(isVibrate);
			}
			initializePreferences();
			updatePairsList(exchangeCode, exchangeName, alarm.getPair().toString());
		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			mBound = false;
		}
	};

	protected abstract class OnAlarmSettingsPreferenceChangeListener implements
	OnPreferenceChangeListener {

		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue) {
			String key = preference.getKey();
			AndroidNotifier notifier = (AndroidNotifier) alarm.getNotifier();
			if(key.equals(PREF_KEY_EXCHANGE)) {
				ListPreference listPref = (ListPreference) preference;
				mExchangeIndex = listPref.findIndexOfValue((String) newValue);
				String exchangeName = (String) listPref.getEntries()[mExchangeIndex];
				listPref.setSummary(exchangeName);
				mPairIndex = 0;
				updatePairsList((String) newValue, exchangeName, null);
				try {
					if(!mBound) {
						throw new IOException(mEnclosingActivity.getString(R.string.not_bound, "AlarmSettingsFragment"));
					}
					alarm.setExchange(mStorageAndControlService.getExchange((String) newValue));
				} catch(Exception e) {
					Log.e("Cannot change Exchange.", e);
				}
			} else if(key.equals(PREF_KEY_PAIR)) {
				mPairIndex = Integer.parseInt((String) newValue);
				Pair pair = mPairs.get(mPairIndex);
				preference.setSummary(pair.toString());
				updateDependentOnPairAux();
				alarm.setPair(pair);
			} else if(key.equals(PREF_KEY_UPDATE_INTERVAL)) {
				try {
					alarm.setPeriod(1000 * Long.parseLong((String) newValue));
					if(mBound) {
						mStorageAndControlService.resetAlarmPeriod(alarm);
					} else {
						Log.e(mEnclosingActivity.getString(R.string.not_bound, "PriceHitAlarmSettingsFragment"));
					}
					preference.setSummary(mEnclosingActivity.getString(R.string.seconds_abbreviation, newValue));
				} catch(TimeFrameSmallerOrEqualUpdateIntervalException e) {
					String msg = mEnclosingActivity.getString(R.string.failed_save_alarm) + " "
						+ mEnclosingActivity.getString(R.string.frame_must_longer_interval);
					Log.e(msg, e);
					IconToast.warning(mEnclosingActivity, msg);
				}
			} else if(key.equals(PREF_KEY_ALARM_ALERT_TYPE)) {
				ListPreference alertTypePref = (ListPreference) preference;
				alertTypePref.setSummary(alertTypePref.getEntries()[alertTypePref.findIndexOfValue((String) newValue)]);
				// Change selectable ringtones according to the alert type
				int ringtoneType = Integer.parseInt((String) newValue);
				mAlertSoundPref.setRingtoneType(ringtoneType);
				String defaultRingtone = RingtoneManager.getDefaultUri(ringtoneType).toString();
				mAlertSoundPref.setSummary(Conversions.ringtoneUriToName(defaultRingtone, mEnclosingActivity));
				notifier.setAlertType(ringtoneType);
				notifier.setAlertSound(defaultRingtone);
			} else if(key.equals(PREF_KEY_ALARM_ALERT_SOUND)) {
				String alertSound = (String) newValue;
				if(alertSound.equals(ThemableRingtonePreference.DEFAULT))
					alertSound = null;
				notifier.setAlertSound(alertSound);
			} else if(key.equals(PREF_KEY_ALARM_VIBRATE)) {
				notifier.setVibrate((Boolean) newValue);
			} else {
				Log.d("No behavior for " + key);
				return true;
			}

			if(mBound) {
				mStorageAndControlService.replaceAlarmDB(alarm);
			} else {
				Log.e(mEnclosingActivity.getString(R.string.not_bound, "AlarmSettingsFragment"));
			}
			return true;
		}
	}

	@Override
	protected void updateDependentOnPair() {
		alarm.setPair(mPairs.get(mPairIndex));
	}

	protected abstract void initializePreferences();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		int alarmID = Integer.MIN_VALUE;
		if(savedInstanceState == null) {
			Bundle args = getArguments();
			if(args != null) {
				alarmID = args.getInt(AlarmSettingsActivity.alarmID);
			}
		} else {
			alarmID = savedInstanceState.getInt(AlarmSettingsActivity.alarmID);
		}
		mAlarmTypePref.setEnabled(false);

		mStorageAndControlServiceConnection = new InitializePreferencesConnection(alarmID);
		mEnclosingActivity.bindService(mServiceIntent, mStorageAndControlServiceConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);
		savedInstanceState.putInt(AlarmSettingsActivity.alarmID, alarm.getId());
	}
}
