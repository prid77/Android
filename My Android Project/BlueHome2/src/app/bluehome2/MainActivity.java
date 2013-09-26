package app.bluehome2;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.StringTokenizer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class MainActivity extends Activity {

	/**
	 * Debug String
	 */
	public static final String TAG = "Bluehome2";

	public final String HOST_ADDR = "prid77.iptime.org";
	private int SERVER_PORT = 8080;

	private Socket socket;
	private msgHandler mHandler;
	private BufferedReader input;
	private DataOutputStream output;
	private InetAddress address = null;

	// Local Bluetooth adapter
	private static BluetoothAdapter mBluetoothAdapter = null;

	// Member object for the connection services
	private static BLConnection mConService = null;

	// Intent request codes
	private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;

	// Name of the connected device
	private static String mConnectedDeviceName = null;

	private static TextView mTitle, status;
	private static ToggleButton mToggleOne, mToggleTwo, mToggleDoor;

	private static ProgressDialog mProgress;

	// Communication Way : WIFI or BT
	private static int CommWay;
	private int mSelector;

	// CommMethod variable
	private static int WIFI = 0;
	private static int BT = 1;

	// delimiter string
	private String sWIFI = "WIFI";
	private String sBT = "Bluetooth";

	// ID delimeter
	private static int ID;
	private static String ThisDeviceId;

	// ID selector variables
	private final static int JKC = 0;
	private final static int KJS = 1;
	private final static int JTH = 2;
	private final static int JSA = 3;
	private final static int EXT = 4;

	// Device ID for each family members
	private final static String IDJKC = "352787040927788"; // defy id
	private final static String IDKJS = "357490048542171"; // vega racer id
	private final static String IDJTH = "354636031262069"; // motoroi id
	private final static String IDJSA = "353964051983351"; // optimus vu II id

	// Status for both read and write
	// Message types sent from the BluetoothConnectionService Handler
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;
	public static final int CONNECTION_FAILED = 6;

	// Key names received from the BluetoothConnectionService Handler
	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";

	// Return Intent extra
	private String EXTRA_DEVICE_ADDRESS = "device_address";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Set up the window layout
		requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
		setContentView(R.layout.activity_main);
		getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);

		// Set up the custom title
		mTitle = (TextView) findViewById(R.id.title_left_text);
		mTitle.setText(R.string.app_name);
		mTitle = (TextView) findViewById(R.id.title_right_text);
		// Set up the status text
		status = (TextView) findViewById(R.id.statusText);

		mHandler = new msgHandler(this, this);

		SharedPreferences pref = getSharedPreferences("Comm", 0);
		CommWay = pref.getInt("CommWay", WIFI);

		if (CommWay == WIFI) {
			// if there's no network available, shut this app off.
			if (!isNetworkAvailable()) {
				status.setText("인터넷 연결이 불안정합니다.\n인터넷 연결을 확인해주세요.");
			}
		} else if (CommWay == BT) {
			// Get local Bluetooth adapter
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

			// If the adapter is null, then Bluetooth is not supported
			if (mBluetoothAdapter == null) {
				Toast.makeText(this, R.string.notworking, Toast.LENGTH_LONG).show();
				finish();
				return;
			}
		}

		ToggleDefineAndEventListener();

		// prompt dialog to let user to select communication method.
		// false means this method is not called from android menu.
		CreateCommWayDialog(false);

	}

	@Override
	protected void onStart() {
		super.onStart();

		/*
		 * if (getDeviceId().equals(IDJKC)) { ID = JKC; // ThisDeviceId = IDJKC;
		 * } else if (getDeviceId().equals(IDKJS)) { ID = KJS; // ThisDeviceId =
		 * IDKJS; } else if (getDeviceId().equals(IDJTH)) { ID = JTH; //
		 * ThisDeviceId = IDJTH; } else if (getDeviceId().equals(IDJSA)) { ID =
		 * JSA; // ThisDeviceId = IDJSA; } else { ID = EXT; // temporary master
		 * access key for HTC Wildfire phone. // updated on 20130715. please
		 * delete following line when properly // setup the system. ThisDeviceId
		 * = "354636031262069"; }
		 */

		// temporarily acquired master access key for HTC Wildfire phone.
		// updated on 20130715. please delete following two line when properly
		// setup the system.
		// on 20130802, since dad got changed his phone from motorola defy to
		// sky vega lte,
		// one more tentative modification has been made to ID and ThisDeviceId
		// variable.
		// on20130817, since I bought secondhand phone Galaxy S3, I have made my
		// own version.
		ID = JTH;
		ThisDeviceId = "354636031262069";

		// only when commway is wifi and network is available
		if (CommWay == WIFI && isNetworkAvailable()) {
			// Initial connection
			mProgress = ProgressDialog.show(MainActivity.this, "와이파이 연결중", "연결되는 동안 잠시만 기다려 주세요.");
			// Initiate WIFIThread
			WIFIThread.start();
		} else if (CommWay == BT) {
			// If BT is not on, request that it be enabled.
			// setupConnection() will then be called during onActivityResult
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
				// Otherwise, setup the connection session
			} else {
				// sometimes it won't stopped from last execution.
				if (mConService != null) {
					mConService.stop();
				}

				setupConnection();
			}
		}

		Log.d(TAG, "onStart() called");
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.d(TAG, "+ ON RESUME +");
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (CommWay == WIFI) {
			close();
		} else if (CommWay == BT) {
			// Stop the Bluetooth services
			if (mConService != null)
				mConService.stop();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Toast.makeText(getApplication(), "앱을 종료합니다.\n편리한 기술 블루홈", Toast.LENGTH_SHORT).show();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getOrder()) {
		case 100:
			// called from android menu
			CreateCommWayDialog(true);
			break;
		}
		return false;
	}

	private void setupConnection() {
		// Initialize the BluetoothConnectionService to perform bluetooth
		// connections
		mConService = new BLConnection(this, mHandler);

		// Initial connection
		mProgress = ProgressDialog.show(MainActivity.this, "블루투스 연결중", "연결되는 동안 잠시만 기다려 주십시오.");
		// This address is manually written. do not modify.
		String address = "00:18:9A:04:02:B4";
		// Get the BLuetoothDevice object
		BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
		// Attempt to connect to the device
		mConService.connect(device);
	}

	/**
	 * Sends a message through bluetooth.
	 * 
	 * @param message
	 *            A string of text to send.
	 */
	private static void writeBT(String message) {
		// Check that we're actually connected before trying anything
		if (mConService.getState() != BLConnection.STATE_CONNECTED) {
			mTitle.setText(R.string.not_connected);
			return;
		}

		String messageWithId = null;
		switch (ID) {
		case JKC:
			messageWithId = ThisDeviceId + ":" + message;
			break;
		case KJS:
			messageWithId = ThisDeviceId + ":" + message;
			break;
		case JTH:
			messageWithId = ThisDeviceId + ":" + message;
			break;
		case JSA:
			messageWithId = ThisDeviceId + ":" + message;
			break;
		}

		// Check that there's actually something to send
		if (messageWithId.length() > 0) {
			// Get the message bytes and tell the BluetoothChatService to write
			byte[] send = messageWithId.getBytes();
			mConService.write(send);
		}
	}

	private Thread WIFIThread = new Thread() {
		String SERVER_IP;

		@Override
		public void run() {
			super.run();
			try {
				address = InetAddress.getByName(HOST_ADDR);
				SERVER_IP = address.getHostAddress();
				socket = new Socket(SERVER_IP, SERVER_PORT);
				input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				output = new DataOutputStream(socket.getOutputStream());
			} catch (UnknownHostException e) {
				Log.e(TAG, "UnknownHostException : " + e.getMessage());
				new AlertDialog.Builder(MainActivity.this).setTitle("오류메세지").setMessage("지태호한테 보여주세요\r\n" + e.getLocalizedMessage())
						.setIcon(R.drawable.ic_launcher).setCancelable(false)
						.setPositiveButton("확인", new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {
								// 확인 버튼 클릭시에 실행 할 코드
							}
						}).show();
			} catch (IOException e) {
				Log.e(TAG, "IOException : " + e.getMessage());
				new AlertDialog.Builder(MainActivity.this).setTitle("오류메세지").setMessage("지태호한테 보여주세요\r\n" + e.getLocalizedMessage())
						.setIcon(R.drawable.ic_launcher).setCancelable(false)
						.setPositiveButton("확인", new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {
								// 확인 버튼 클릭시에 실행 할 코드
							}
						}).show();
			} finally {
				try {
					// Read incoming message
					read.start();
					// send greeting message
					write("GREETINGS\n");
					Thread.sleep(100);
					write("CURRENTSTATUS\n");
					mProgress.dismiss();
				} catch (InterruptedException e) {
					new AlertDialog.Builder(MainActivity.this).setTitle("오류메세지").setMessage("지태호한테 보여주세요\r\n" + e.getLocalizedMessage())
							.setIcon(R.drawable.ic_launcher).setCancelable(false)
							.setPositiveButton("확인", new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int whichButton) {
									// 확인 버튼 클릭시에 실행 할 코드
								}
							}).show();
				}
			}
		}
	};

	private Thread read = new Thread() {
		@Override
		public void run() {
			super.run();

			try {
				try {
					Thread.sleep(200);
				} catch (Exception e) {
					Log.e(TAG, "Thread.sleep Exception");
				}
				String result;
				while ((result = input.readLine()) != null) {
					mHandler.obtainMessage(MainActivity.MESSAGE_READ, result).sendToTarget();
				}
			} catch (IOException e) {
				e.getMessage();
			}
		}
	};

	private void write(String msg) {
		if (socket.isClosed() != true) {
			try {
				switch (ID) {
				case JKC:
					output.writeBytes(ThisDeviceId + ":");
					break;
				case KJS:
					output.writeBytes(ThisDeviceId + ":");
					break;
				case JTH:
					output.writeBytes(ThisDeviceId + ":");
					break;
				case JSA:
					output.writeBytes(ThisDeviceId + ":");
					break;
				}
			} catch (IOException e) {
				Log.e(TAG, "IOException : " + e.getMessage() + " at write()");
			} finally {
				try {
					output.writeBytes(msg);
					output.flush();
				} catch (Exception e) {
					Log.e(TAG, "IOException : " + e.getMessage() + " at write() finally");
				}
			}
		} else {
			Toast.makeText(getBaseContext(), "Host is not answering or\nConnection is closed.", Toast.LENGTH_LONG).show();
		}
	}

	private void close() {
		if (socket != null) {
			try {
				// this is needed when bufferedReader is
				// used in inputstreamreader
				socket.shutdownInput();

				socket.close();
				input.close();
				output.close();
			} catch (IOException e) {
				Log.e(TAG, "cancel() failed");
				Toast.makeText(getBaseContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
			}
		}
	}

	static class msgHandler extends Handler {
		WeakReference<MainActivity> mMain;
		Context mContext;

		public msgHandler(MainActivity mActivity, Context ct) {
			mMain = new WeakReference<MainActivity>(mActivity);
			mContext = ct;
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_READ:
				if (CommWay == WIFI) {
					String read = (String) msg.obj;
					StringTokenizer st = new StringTokenizer(read, ":");

					// this contains device id which is useless
					// in BT mode.
					st.nextToken();

					String[] StringToParse = new String[st.countTokens()];
					for (int i = 0; st.hasMoreTokens(); i++) {
						StringToParse[i] = st.nextToken();
					}

					// simply process incoming strings
					processString(StringToParse);

				} else if (CommWay == BT) {
					String read = (String) msg.obj;
					StringTokenizer st = new StringTokenizer(read, ":");

					// this contains device id which is useless
					// in BT mode.
					st.nextToken();

					String[] StringToParse = new String[st.countTokens()];
					for (int i = 0; st.hasMoreTokens(); i++) {
						StringToParse[i] = st.nextToken();
					}

					// simply process incoming strings
					processString(StringToParse);
				}
			case MESSAGE_WRITE:
				break;
			case MESSAGE_DEVICE_NAME: // 연결이 성공했을 때
				// save the connected device's name
				mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
				Toast.makeText(mContext, mConnectedDeviceName + "에 연결되었습니다.", Toast.LENGTH_SHORT).show();
				// send greeting message
				writeBT("GREETINGS\n");
				// get current status
				writeBT("CURRENTSTATUS\n");
				mProgress.dismiss();
				break;
			case CONNECTION_FAILED: // 연결이 실패했을 때

				// shut off progressdialog
				mProgress.dismiss();

				// Retry dialog
				new AlertDialog.Builder(mContext).setTitle("블루홈").setMessage("연결에 실패했습니다.\r\n다시 연결하시겠습니까?").setIcon(R.drawable.ic_launcher)
						.setCancelable(false).setPositiveButton("확인", new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog, int which) {
								// sometimes it won't stopped from last
								// execution.
								if (mConService != null) {
									mConService.stop();
								}

								// Initial connection
								mProgress = ProgressDialog.show(mContext, "블루투스 연결중", "연결되는 동안 잠시만 기다려 주십시오.");
								// This address is manually written.
								// Do not modify.
								String address = "00:18:9A:04:02:B4";
								// Get the BLuetoothDevice object
								BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
								// Attempt to connect to the device
								mConService.connect(device);
							}
						}).setNegativeButton("취소", new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog, int which) {

							}
						}).show();
				break;
			}
		}
	}

	private static void processString(String[] StringToParse) {
		// cases which would match StringToParse.length of 1 are
		// 1. greetins message
		if (StringToParse.length == 1) {
			String delimiter = StringToParse[0];
			if (delimiter.equals("HELLO KICHOUNG")) {
				mTitle.setText("안녕하세요. 지기청사장님");
			} else if (delimiter.equals("HELLO JUNGSUK")) {
				mTitle.setText("안녕하세요. 김정숙여사님");
			} else if (delimiter.equals("HELLO TAEHO")) {
				mTitle.setText("안녕하세요. 지종대왕님");
			} else if (delimiter.equals("HELLO SUN")) {
				mTitle.setText("비운의 여왕 지덕여왕, 감히 블루홈을 쓰려고 하다니!");
			}

		} else if (StringToParse.length == 4) {
			// cases which would match length of 4 is
			// current status
			String delimiter = StringToParse[0];
			String l1status = StringToParse[1];
			String l2status = StringToParse[2];
			String doorstatus = StringToParse[3];

			if (delimiter.contains("CURRENTSTATUS")) {
				StringBuilder sb = new StringBuilder();
				// Light #1 Status
				if (l1status.equals("L1ON")) {
					mToggleOne.setChecked(true);
					sb.append("1번 전등 : 켜짐 , ");
				} else if (l1status.equals("L1OFF")) {
					mToggleOne.setChecked(false);
					sb.append("1번 전등 : 꺼짐 , ");
				}

				// Light #2 Status
				if (l2status.equals("L2ON")) {
					mToggleTwo.setChecked(true);
					sb.append("2번 전등 : 켜짐\n");
				} else if (l2status.equals("L2OFF")) {
					mToggleTwo.setChecked(false);
					sb.append("2번 전등 : 꺼짐\n");
				}

				// Door Status
				if (doorstatus.equals("UNKNOWN")) {
					mToggleDoor.setChecked(false);
					sb.append("현관문 상태 : 모름");
				} else if (doorstatus.equals("IsOpenAndUnlocked")) {
					mToggleDoor.setChecked(false);
					sb.append("현관문 상태 : 열려있음");
				} else if (doorstatus.equals("IsClosedAndUnlocked")) {
					mToggleDoor.setChecked(false);
					sb.append("현관문 상태 : 닫혀있지만 잠기지 않음");
				} else if (doorstatus.equals("IsClosedAndLocked")) {
					mToggleDoor.setChecked(true);
					sb.append("현관문 상태 : 안전하게 잠겨있음");
				}

				status.setText(sb.toString());

				if (mProgress.isShowing()) {
					mProgress.dismiss();
				}
			}
		}
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.d(TAG, "onActivityResult " + resultCode);
		switch (requestCode) {
		case REQUEST_CONNECT_DEVICE:
			// When DeviceListActivity returns with a device to connect
			if (resultCode == Activity.RESULT_OK) {
				// Get the device MAC address
				String address = data.getExtras().getString(this.EXTRA_DEVICE_ADDRESS);
				// Get the BLuetoothDevice object
				BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
				// Attempt to connect to the device
				mConService.connect(device);
			}
			break;
		case REQUEST_ENABLE_BT:
			// When the request to enable Bluetooth returns
			if (resultCode == Activity.RESULT_OK) {
				// Bluetooth is now enabled, so set up a chat session
				setupConnection();
			} else {
				// User did not enable Bluetooth or an error occured
				Log.d(TAG, "BT not enabled");
				Toast.makeText(this, R.string.notworking, Toast.LENGTH_SHORT).show();
				finish();
			}
		}
	}

	private void CreateCommWayDialog(boolean calledFromMenu) {
		final SharedPreferences settings = getSharedPreferences("firstAccess", MODE_PRIVATE);
		/* check if the application is launched for the first time */
		if (settings.getBoolean("firstAccess", false) == false || calledFromMenu) {
			new AlertDialog.Builder(MainActivity.this).setTitle("연결방식을 선택하세요.").setIcon(R.drawable.commway)
					.setSingleChoiceItems(R.array.commway, CommWay, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							mSelector = which;
						}
					}).setPositiveButton("저장", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							String[] mComm = getResources().getStringArray(R.array.commway);
							SharedPreferences pref = getSharedPreferences("Comm", MODE_PRIVATE);

							if (mComm[mSelector].equals(sWIFI)) {
								SharedPreferences.Editor edit = pref.edit();
								edit.putInt("CommWay", WIFI);
								edit.commit();
							} else if (mComm[mSelector].equals(sBT)) {
								SharedPreferences.Editor edit = pref.edit();
								edit.putInt("CommWay", BT);
								edit.commit();
							}

							SharedPreferences.Editor editor = settings.edit();
							editor.putBoolean("firstAccess", true);
							editor.commit();
							dialog.dismiss();

							Toast.makeText(getBaseContext(), "저장된 연결방법은\n다음 번 실행부터 적용됩니다.", Toast.LENGTH_LONG).show();
						}
					}).setNegativeButton("취소", null).show();
		}
	}

	private void ToggleDefineAndEventListener() {
		mToggleOne = (ToggleButton) findViewById(R.id.btn_toggle_one);
		mToggleTwo = (ToggleButton) findViewById(R.id.btn_toggle_two);
		mToggleDoor = (ToggleButton) findViewById(R.id.btn_toggle_door);

		mToggleOne.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mToggleOne.isChecked()) {
					if (CommWay == WIFI) {
						write("L1ON\n");
						// get current status
						write("CURRENTSTATUS\n");
					} else if (CommWay == BT) {
						writeBT("L1ON\n");
						// get current status
						writeBT("CURRENTSTATUS\n");
					}
				} else {
					if (CommWay == WIFI) {
						write("L1OFF\n");
						// get current status
						write("CURRENTSTATUS\n");
					} else if (CommWay == BT) {
						writeBT("L1OFF\n");
						// get current status
						writeBT("CURRENTSTATUS\n");
					}
				}
			}
		});

		mToggleTwo.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mToggleTwo.isChecked()) {
					if (CommWay == WIFI) {
						write("L2ON\n");
						// get current status
						write("CURRENTSTATUS\n");
					} else if (CommWay == BT) {
						writeBT("L2ON\n");
						// get current status
						writeBT("CURRENTSTATUS\n");
					}
				} else {
					if (CommWay == WIFI) {
						write("L2OFF\n");
						// get current status
						write("CURRENTSTATUS\n");
					} else if (CommWay == BT) {
						writeBT("L2OFF\n");
						// get current status
						writeBT("CURRENTSTATUS\n");
					}
				}
			}
		});

		mToggleDoor.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (CommWay == WIFI) {
					write("DOOR\n");
					// get current status
					write("CURRENTSTATUS\n");
				} else if (CommWay == BT) {
					writeBT("DOOR\n");
					// get current status
					writeBT("CURRENTSTATUS\n");
				}
			}
		});
	}

	private boolean isNetworkAvailable() {
		ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
		return activeNetworkInfo != null;
	}

	// retrieve device number
	public String getPhoneNumber() {
		TelephonyManager mgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		return mgr.getLine1Number();
	}

	// retrieve device id
	public String getDeviceId() {
		TelephonyManager mgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		return mgr.getDeviceId();
	}

}
