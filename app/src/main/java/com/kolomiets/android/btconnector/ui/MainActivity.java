package com.kolomiets.android.btconnector.ui;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.ActionBarActivity;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.kolomiets.android.btconnector.R;
import com.kolomiets.android.btconnector.service.BtConnectionService;

import java.util.Set;


public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }

    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment{ // implements BluetoothMessagesHandler.BluetoothCallback {

        // Message types sent from the BluetoothChatService Handler
        public static final int MESSAGE_STATE_CHANGE = 1;
        public static final int MESSAGE_READ = 2;
        public static final int MESSAGE_WRITE = 3;
        public static final int MESSAGE_DEVICE_NAME = 4;
        public static final int MESSAGE_TOAST = 5;

        // Key names received from the BluetoothChatService Handler
        public static final String DEVICE_NAME = "device_name";
        public static final String TOAST = "toast";

        protected static final String SMS_RECEIVED="android.provider.Telephony.SMS_RECEIVED";

        public static final String TAG = PlaceholderFragment.class.getSimpleName();
        public static String EXTRA_DEVICE_ADDRESS = "device_address";
        private static final int REQUEST_ENABLE_BT = 11;

        private String message;
        private EditText mMessageEditText;
        private Button mSendButton;

        private ListView mPairedDevicesListView;
        private ListView mNearDevicesListView;


        private BluetoothAdapter mBtAdapter = null;

        private String mConnectedDeviceName = null;
        private BtConnectionService mBtConnectionService;

        private ArrayAdapter<String> mNearDevicesAdapter;
        private Menu mMenu;

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.fragment_main, container, false);
            return v;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setHasOptionsMenu(true);

            mBtAdapter = BluetoothAdapter.getDefaultAdapter();

            mBtConnectionService =
                    new BtConnectionService(getActivity().getApplicationContext(), mHandler);

            if(mBtAdapter == null) {
                FragmentActivity activity = getActivity();
                Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
                activity.finish();
            }

//            if (!mBtAdapter.isEnabled()) {
//                // if BT is not enabled, request user to do it
//                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//                startActivityForResult(intent, REQUEST_ENABLE_BT);
//            }
//            ensureBluetoothEnable();
            //ensureDiscoverable();

        }

        @Override
        public void onStart() {
            super.onStart();

            if (!mBtAdapter.isEnabled()) {
                // if BT is not enabled, request user to do it
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intent, REQUEST_ENABLE_BT);
            }

            // Register for broadcasts when a device is discovered
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            getActivity().registerReceiver(mReceiver, filter);

            // Register for broadcasts when discovery has finished
            filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            getActivity().registerReceiver(mReceiver, filter);

            getActivity().registerReceiver(mSMSReceiver, new IntentFilter(SMS_RECEIVED));
        }



        private void ensureDiscoverable() {
            if (mBtAdapter.getScanMode() !=
                    BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 3600);
                startActivity(discoverableIntent);
            }
        }

        @Override
        public void onActivityCreated(@Nullable Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            ensureDiscoverable();
        }

        public void ensureBluetoothEnable() {
            if (!mBtAdapter.isEnabled()) {
                // if BT is not enabled, request user to do it
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intent, REQUEST_ENABLE_BT);
            }
        }

        private void doDiscovery() {
            Log.d(TAG, "doDiscovery()");

            // Turn on sub-title for new devices
            getView().findViewById(R.id.near_devices_title).setVisibility(View.VISIBLE);
            if(mBtAdapter != null) {
                // If we're already discovering, stop it
                if (mBtAdapter.isDiscovering()) {
                    mBtAdapter.cancelDiscovery();
                }

                // Request discover from BluetoothAdapter
                mBtAdapter.startDiscovery();
                getActivity().setProgressBarIndeterminateVisibility(true);

                if (mMenu != null) {
                    MenuItem scanMenuItem = mMenu.findItem(R.id.menu_find_devices);
                    if (scanMenuItem != null) {
                        scanMenuItem.setEnabled(false);
                        scanMenuItem.setTitle(R.string.searching_for_device);
                    } else {
                        Log.e(TAG, "scan for device menu item is null");
                    }
                }
            }
        }

        @Override
        public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            ArrayAdapter<String> mPairedDevicesAdapter =
                    new ArrayAdapter<String>(getActivity(), R.layout.device_list_item);
            mNearDevicesAdapter = new ArrayAdapter<String>(getActivity(), R.layout.device_list_item);

            mPairedDevicesListView = (ListView)view.findViewById(R.id.paired_devices_list);
            mPairedDevicesListView.setAdapter(mPairedDevicesAdapter);
            mPairedDevicesListView.setOnItemClickListener(mDeviceClickListener);

            mNearDevicesListView = (ListView)view.findViewById(R.id.near_devices_list);
            mNearDevicesListView.setAdapter(mNearDevicesAdapter);
            mNearDevicesListView.setOnItemClickListener(mDeviceClickListener);

            mMessageEditText = (EditText) view.findViewById(R.id.message_edit_text);
            mSendButton = (Button)view.findViewById(R.id.send_button);
            mSendButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    message = mMessageEditText.getText().toString();
                    sendMessage(message);
                }
            });

            Set<BluetoothDevice> mPairedDevices = mBtAdapter.getBondedDevices();

//            ensureDiscoverable();

            if(mPairedDevices.size() > 0) {
                view.findViewById(R.id.paired_devices_title).setVisibility(View.VISIBLE);
                for (BluetoothDevice device : mPairedDevices) {
                    mPairedDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
                }
            } else {
                mPairedDevicesAdapter.add("No paired devices");
            }
        }


        private void sendMessage(String message) {

            if(mBtConnectionService.getState() != BtConnectionService.STATE_CONNECTED) {
                Toast.makeText(getActivity(), "You are not connected to the device", Toast.LENGTH_SHORT).show();
            }

            if (message.length() > 0) {
                // Get the message bytes and tell the BluetoothChatService to write
                byte[] send = message.getBytes();
                mBtConnectionService.write(send);
            }

        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            //super.onCreateOptionsMenu(menu, inflater);
            inflater.inflate(R.menu.menu_main, menu);
            mMenu = menu;
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            // Handle action bar item clicks here. The action bar will
            // automatically handle clicks on the Home/Up button, so long
            // as you specify a parent activity in AndroidManifest.xml.
            switch (item.getItemId()) {
                case R.id.action_settings:

                    Toast.makeText(getActivity(), "Settings", Toast.LENGTH_LONG).show();

                    return true;
                case R.id.menu_find_devices:
                    doDiscovery();
                    //item.setEnabled(false);
//                    Utils.toast("Scanning ...");
                    return true;

                default:
                    return super.onOptionsItemSelected(item);
            }
        }

        @Override
        public void onResume() {
            super.onResume();

            if(mBtAdapter.isEnabled()) {
                // Performing this check in onResume() covers the case in which BT was
                // not enabled during onStart(), so we were paused to enable it...
                // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
                if (mBtConnectionService != null) {
                    // Only if the state is STATE_NONE, do we know that we haven't started already
                    if (mBtConnectionService.getState() == mBtConnectionService.STATE_NONE) {
                        // Start the Bluetooth chat services
                        mBtConnectionService.start();
                    }
                }
            }
        }

        @Override
        public void onStop() {
            super.onStop();
            if (mReceiver != null) {
                getActivity().unregisterReceiver(mReceiver);
                getActivity().unregisterReceiver(mSMSReceiver);
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if(mBtAdapter != null) {
                mBtAdapter.cancelDiscovery();
            }

//            getActqivity().unregisterReceiver(mReceiver);
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            if (mBtAdapter != null) {
                mBtAdapter.cancelDiscovery();
            }
            if(mBtConnectionService != null) {
                mBtConnectionService.stop();
            }
//            getActivity().unregisterReceiver(mReceiver);
//            getActivity().unregisterReceiver(mSMSReceiver);
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            switch (requestCode) {
                case REQUEST_ENABLE_BT:
                    if (resultCode == RESULT_OK || mBtAdapter.isEnabled()) {
                        // Performing this check in onResume() covers the case in which BT was
                        // not enabled during onStart(), so we were paused to enable it...
                        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
                        if (mBtConnectionService != null) {
                            // Only if the state is STATE_NONE, do we know that we haven't started already
                            if (mBtConnectionService.getState() == mBtConnectionService.STATE_NONE) {
                                // Start the Bluetooth chat services
                                mBtConnectionService.start();
                            }
                        }
                        Log.d(TAG, "enabled BT");

                    } else {
                        // user not confirmed enabling BT, tell him to go fuck himself
                        Log.d(TAG, "user did not enabled BT");
                        Toast.makeText(getActivity(), "user did not enabled BT", Toast.LENGTH_LONG).show();
                        getActivity().finish();
                    }
                    return;
            }
        }

        private AdapterView.OnItemClickListener mDeviceClickListener
                = new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
                // Cancel discovery because it's costly and we're about to connect
                mBtAdapter.cancelDiscovery();

                // Get the device MAC address, which is the last 17 chars in the View
                String info = ((TextView) v).getText().toString();
                String address = info.substring(info.length() - 17);

                connectDevice(address);

            }
        };

        private void connectDevice(String address) {
            // Get the BluetoothDevice object
            BluetoothDevice device = mBtAdapter.getRemoteDevice(address);
            // Attempt to connect to the device
            mBtConnectionService.connect(device);
        }

        private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                // When discovery finds a device
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    // Get the BluetoothDevice object from the Intent
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    // If it's already paired, skip it, because it's been listed already
                    // If it's already discovered, skip it, because it's existed in adapter
                    if ((device.getBondState() != BluetoothDevice.BOND_BONDED)&&
                            (mNearDevicesAdapter.getPosition(device.getName() + "\n" + device.getAddress()) == -1)) {
                        mNearDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
                    }
                    // When discovery is finished, change the Activity title
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    Activity activity = getActivity();
                    if(activity != null) {
                        activity.setProgressBarIndeterminateVisibility(false);

                    }
                    if (mMenu != null) {
                        MenuItem scanMenuItem = mMenu.findItem(R.id.menu_find_devices);
                        if (scanMenuItem != null) {
                            scanMenuItem.setEnabled(true);
                            scanMenuItem.setTitle(R.string.find_devices);
                        }
                    }
                    if (mNearDevicesAdapter.getCount() == 0) {
                        String noDevices = getResources().getText(R.string.none_found).toString();
                        mNearDevicesAdapter.add(noDevices);
                    }
                }
            }
        };

        private BroadcastReceiver mSMSReceiver = new BroadcastReceiver() {

            final SmsManager sms = SmsManager.getDefault();
            @Override
            public void onReceive(Context context, Intent intent) {
                final Bundle bundle = intent.getExtras();

                try {

                    if (bundle != null) {

                        final Object[] pdusObj = (Object[]) bundle.get("pdus");

                        for (int i = 0; i < pdusObj.length; i++) {

                            SmsMessage currentMessage = SmsMessage.createFromPdu((byte[]) pdusObj[i]);
                            String phoneNumber = currentMessage.getDisplayOriginatingAddress();

                            String senderNum = phoneNumber;
                            String message = currentMessage.getDisplayMessageBody();

                            Log.i("SmsReceiver", "senderNum: "+ senderNum + "; message: " + message);

                            Toast.makeText(context, "senderNum: "+ senderNum + ", message: " +
                                    message, Toast.LENGTH_LONG).show();

                            sendMessage(message);
                        }
                    }

                } catch (Exception e) {
                    Log.e("SmsReceiver", "Exception smsReceiver" + e);
                }
            }
        };

        /**
         * Updates the status on the action bar.
         *
         * @param resId a string resource ID
         */
        private void setStatus(int resId) {
            FragmentActivity activity = getActivity();
            if (null == activity) {
                return;
            }
            final ActionBar actionBar = activity.getActionBar();
            if (null == actionBar) {
                return;
            }
            actionBar.setSubtitle(resId);
        }

        /**
         * Updates the status on the action bar.
         *
         * @param subTitle status
         */
        private void setStatus(CharSequence subTitle) {
            FragmentActivity activity = getActivity();
            if (null == activity) {
                return;
            }
            final ActionBar actionBar = activity.getActionBar();
            if (null == actionBar) {
                return;
            }
            actionBar.setSubtitle(subTitle);
        }

        private final Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                FragmentActivity activity = getActivity();
                switch (msg.what) {
                    case MESSAGE_STATE_CHANGE:
                        switch (msg.arg1) {
                            case BtConnectionService.STATE_CONNECTED:
                                setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));

                                break;
                            case BtConnectionService.STATE_CONNECTING:
                                setStatus(R.string.title_connecting);
                                break;
                            case BtConnectionService.STATE_LISTEN:
                            case BtConnectionService.STATE_NONE:
                                setStatus(R.string.title_not_connected);
                                break;
                        }
                        break;
                    case MESSAGE_WRITE:
                        byte[] writeBuf = (byte[]) msg.obj;
                        // construct a string from the buffer
                        String writeMessage = new String(writeBuf);
//                        mConversationArrayAdapter.add("Me:  " + writeMessage);
                        break;
                    case MESSAGE_READ:
                        byte[] readBuf = (byte[]) msg.obj;
                        // construct a string from the valid bytes in the buffer
                        String readMessage = new String(readBuf, 0, msg.arg1);
                        Toast.makeText(getActivity(), "message received :" +readMessage, Toast.LENGTH_LONG).show();
                        if (getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                            sendSMS(readMessage);
                        } else {
                            Toast.makeText(getActivity(), "Your device can not send SMS:" +readMessage, Toast.LENGTH_LONG).show();
                        }
                        break;
                    case MESSAGE_DEVICE_NAME:
                        // save the connected device's name
                        mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                        if (null != activity) {
                            Toast.makeText(activity, "Connected to "
                                    + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case MESSAGE_TOAST:
                        if (null != activity) {
                            Toast.makeText(activity, msg.getData().getString(TOAST),
                                    Toast.LENGTH_SHORT).show();
                        }
                        break;
                }
            }
        };

        public void sendSMS(String msg) {
            SmsManager sm = SmsManager.getDefault();
            String number = "+380678771029".toString();
            sm.sendTextMessage(number, null, msg, null, null);
            Toast.makeText(getActivity(), "SMS was sent", Toast.LENGTH_LONG).show();
        }
    }
}
