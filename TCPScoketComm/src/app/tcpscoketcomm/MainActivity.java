package app.tcpscoketcomm;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener {

	/**
	 * Debug String
	 */
	public final String TAG = "TCPClient";

	public final String HOST_ADDR = "prid77.iptime.org";
	private int SERVER_PORT = 8080;

	private String mReceivedMsg = "";

	private Socket socket;
	private DataInputStream input;
	private DataOutputStream output;
	private InetAddress address = null;
	private Button sendButton, btn1, btn2, btn3, btn4, btn5;
	private EditText inputField;
	private static TextView textView;
	private msgHandler mHandler;

	// ID delimeter
	private static char ID;

	// ID selector variables
	private final static char JKC = 0;
	private final static char KJS = 1;
	private final static char JTH = 2;
	private final static char JSA = 3;
	private final static char EXT = 4;

	// Device ID for each family members
	private final static String IDJKC = "352787040927788"; // defy id
	private final static String IDKJS = "357490048542171"; // vega racer id
	private final static String IDJTH = "354636031262069"; // motoroi id
	private final static String IDJSA = "353964051983351"; // optimus vu II id

	// Status for both read and write
	private final static char MESSAGE_READ = 0;
	private final static char MESSAGE_WRITE = 1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		sendButton = (Button) findViewById(R.id.send);
		btn1 = (Button) findViewById(R.id.button1);
		btn2 = (Button) findViewById(R.id.button2);
		btn3 = (Button) findViewById(R.id.button3);
		btn4 = (Button) findViewById(R.id.button4);
		btn5 = (Button) findViewById(R.id.button5);

		sendButton.setOnClickListener(this);
		btn1.setOnClickListener(this);
		btn2.setOnClickListener(this);
		btn3.setOnClickListener(this);
		btn4.setOnClickListener(this);
		btn5.setOnClickListener(this);

		inputField = (EditText) findViewById(R.id.input);
		textView = (TextView) findViewById(R.id.chatlog);

		mHandler = new msgHandler(this);

	}

	@Override
	public void onClick(View v) {
		String input = inputField.getText().toString();

		switch (v.getId()) {
		case R.id.send:
			if (input == "") {
				return;
			} else {
				inputField.setText("");
				write(input);
			}
			break;
		case R.id.button1:
			write("L1STATUS\n");
			break;
		case R.id.button2:
			write("L2STATUS\n");
			break;
		case R.id.button3:
			write("DOORSTATUS\n");
			break;
		case R.id.button4:
			write("TEMPSTATUS\n");
			break;
		case R.id.button5:
			write("IDJTH\n");
			break;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (getDeviceId().equals(IDJKC)) {
			print("IDJKC Confirmed");
			ID = JKC;
		} else if (getDeviceId().equals(IDKJS)) {
			print("IDKJS Confirmed");
			ID = KJS;
		} else if (getDeviceId().equals(IDJTH)) {
			print("IDJTH Confirmed");
			ID = JTH;
		} else if (getDeviceId().equals(IDJSA)) {
			print("IDJSA Confirmed");
			ID = JSA;
		} else {
			ID = EXT;
		}

		ConnectThread.start();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		close();
	}

	private Thread ConnectThread = new Thread() {
		String SERVER_IP;

		@Override
		public void run() {
			super.run();
			try {
				address = InetAddress.getByName(HOST_ADDR);
				SERVER_IP = address.getHostAddress();
				socket = new Socket(SERVER_IP, SERVER_PORT);
				input = new DataInputStream(socket.getInputStream());
				output = new DataOutputStream(socket.getOutputStream());

				SERVER_IP = "http://prid77.iptime.org:8080\nIP : " + SERVER_IP
						+ "\nSocket Connect Success!\nNow it's ready to send!";
				mReceivedMsg = SERVER_IP;
				mHandler.obtainMessage(MainActivity.MESSAGE_READ, SERVER_IP)
						.sendToTarget();
			} catch (UnknownHostException e) {
				Log.e(TAG, "UnknownHostException : " + e.getMessage());
			} catch (IOException e) {
				Log.e(TAG, "IOException : " + e.getMessage());
			} finally {
				read.start();
				// send greeting message
				write("GREETINGS\n");
				write("DOORSTATUS\n");
			}
		}
	};

	public static void print(Object message) {
		textView.append("\n" + message);
	}

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
					mHandler.obtainMessage(MainActivity.MESSAGE_READ, result)
							.sendToTarget();
				}
			} catch (IOException e) {
				mReceivedMsg = e.getMessage();
			}
		}
	};

	private void write(String msg) {
		if (socket.isClosed() != true) {
			try {
				switch (ID) {
				case JKC:
					output.writeBytes(IDJKC + ":");
					break;
				case KJS:
					output.writeBytes(IDKJS + ":");
					break;
				case JTH:
					output.writeBytes(IDJTH + ":");
					break;
				case JSA:
					output.writeBytes(IDJSA + ":");
					break;
				}
			} catch (IOException e) {
				Log.e(TAG, "IOException : " + e.getMessage() + " at write()");
			} finally {
				try {
					output.writeBytes(msg);
					output.flush();
				} catch (Exception e) {
					Log.e(TAG, "IOException : " + e.getMessage()
							+ " at write() finally");
				}
			}
		} else {
			Toast.makeText(getBaseContext(),
					"Host is not answering or\nConnection is closed.",
					Toast.LENGTH_LONG).show();
		}
	}

	private void close() {
		if (socket != null) {
			try {
				socket.close();
				input.close();
				output.close();
				Toast.makeText(getBaseContext(),
						"소켓이 안전하게 종료되었습니다.\n프로그램을 종료합니다.", Toast.LENGTH_LONG)
						.show();
			} catch (IOException e) {
				Log.e(TAG, "cancel() failed");
			}
		}
	}

	static class msgHandler extends Handler {
		WeakReference<MainActivity> mMain;

		public msgHandler(MainActivity mActivity) {
			mMain = new WeakReference<MainActivity>(mActivity);
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_READ:
				String read = (String) msg.obj;
				print(read);
				if (read.contentEquals(IDJKC) && ID == JKC) {

				} else if (read.contentEquals(IDKJS) && ID == KJS) {

				} else if (read.contentEquals(IDJTH) && ID == JTH) {

				} else if (read.contentEquals(IDJSA) && ID == JSA) {

				} else {
					// do not show message because it is for other's
				}
				break;
			case MESSAGE_WRITE:
				break;
			}
		}
	}

	private Runnable prtMsg = new Runnable() {
		@Override
		public void run() {
			print(mReceivedMsg);
		}
	};

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
