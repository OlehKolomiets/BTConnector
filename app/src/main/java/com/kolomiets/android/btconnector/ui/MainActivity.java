package com.kolomiets.android.btconnector.ui;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.ActionBarActivity;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ListView;
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
    public static class PlaceholderFragment extends Fragment {

        public static final String TAG = PlaceholderFragment.class.getSimpleName();
        public static String EXTRA_DEVICE_ADDRESS = "device_address";
        private static final int REQUEST_ENABLE_BT = 1;


        private ListView mPairedDevicesListView;
        private ListView mNearDevicesListView;


        private BluetoothAdapter mBtAdapter;
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

            if(mBtAdapter == null) {
                FragmentActivity activity = getActivity();
                Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
                activity.finish();
            }

            if (!mBtAdapter.isEnabled()) {
                // if BT is not enabled, request user to do it
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intent, REQUEST_ENABLE_BT);
            }
        }

        private void ensureDiscoverable() {
            if (mBtAdapter.getScanMode() !=
                    BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
                startActivity(discoverableIntent);
            }
        }

        private void doDiscovery() {
            Log.d(TAG, "doDiscovery()");

            // Turn on sub-title for new devices
            getView().findViewById(R.id.near_devices_title).setVisibility(View.VISIBLE);

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

        @Override
        public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            ArrayAdapter<String> mPairedDevicesAdapter =
                    new ArrayAdapter<String>(getActivity(), R.layout.device_list_item);
            mNearDevicesAdapter = new ArrayAdapter<String>(getActivity(), R.layout.device_list_item);

            mPairedDevicesListView = (ListView)view.findViewById(R.id.paired_devices_list);
            mPairedDevicesListView.setAdapter(mPairedDevicesAdapter);
            mNearDevicesListView = (ListView)view.findViewById(R.id.near_devices_list);
            mNearDevicesListView.setAdapter(mNearDevicesAdapter);

            // Register for broadcasts when a device is discovered
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            getActivity().registerReceiver(mReceiver, filter);

            // Register for broadcasts when discovery has finished
            filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            getActivity().registerReceiver(mReceiver, filter);

            Set<BluetoothDevice> mPairedDevices = mBtAdapter.getBondedDevices();

            if(mPairedDevices.size() > 0) {
                view.findViewById(R.id.paired_devices_title).setVisibility(View.VISIBLE);
                for (BluetoothDevice device : mPairedDevices) {
                    mPairedDevicesAdapter.add(device.getName());
                }
            } else {
                mPairedDevicesAdapter.add("No paired devices");
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
        public void onStop() {
            super.onStop();
            if (mReceiver != null) {
                getActivity().unregisterReceiver(mReceiver);
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if(mBtAdapter != null) {
                mBtAdapter.cancelDiscovery();
            }

            getActivity().unregisterReceiver(mReceiver);
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
                    if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
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
    }
}
