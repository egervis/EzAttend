package edu.pace.cs389s2019team5.ez_attend;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;


import edu.pace.cs389s2019team5.ez_attend.Firebase.Attendee;
import edu.pace.cs389s2019team5.ez_attend.Firebase.Class;
import edu.pace.cs389s2019team5.ez_attend.Firebase.Controller;
import edu.pace.cs389s2019team5.ez_attend.Firebase.Student;
import edu.pace.cs389s2019team5.ez_attend.Firebase.View;

public class BluetoothAdapter {

    private static final String TAG = BluetoothAdapter.class.getName();
    public enum Role {TEACHER, STUDENT}

    public class MBroadCastReceiver extends BroadcastReceiver {

        private OnSuccessListener<Student> onSuccessListener;
        private OnFailureListener onFailureListener;

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.v(TAG, "Broadcast receiver onReceive()");
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the bluetooth device
                // object and its info from the intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceMacAddress = device.getAddress();
                if (deviceMacAddress != null) {
                    Log.v(TAG, "Device with mac address " + deviceMacAddress + " found.");

                    if (BluetoothAdapter.this.studentHashtable.containsKey(deviceMacAddress)) {
                        // Mark the student present
                        final Student student = BluetoothAdapter.this.studentHashtable.get(deviceMacAddress);
                        BluetoothAdapter.this.controller.teacherMarkPresent(m_class.getId(),
                                student.getId(), new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {
                                        onSuccessListener.onSuccess(student);
                                    }
                                }, onFailureListener);
                    }

                }
            }
        }
    }

    private Role m_role;
    private android.bluetooth.BluetoothAdapter bluetoothAdapter;
    private MBroadCastReceiver receiver = new MBroadCastReceiver();
    private Timer mTimer;
    private final Context context;
    private final Handler handler;
    private Controller controller;
    private View view;
    private Class m_class;

    private Hashtable<String, Student> studentHashtable = new Hashtable<>();

    private ListenerRegistration listener;

    /**
     * The constructor is used by both the teachers and students to mark attendance
     * @param context
     * @param role the role of the user. for example, a teacher or student
     * @param controller the controller for accessing the database.
     */
    public BluetoothAdapter (Context context, Role role, Controller controller) {
        this.m_role = role;
        this.context = context;
        this.controller = controller;
        this.view = new View();
        handler = new Handler(context.getMainLooper());
        this.bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
    }

    /**
     * Used by the teacher to begin the attendance taking process. This means it will begin scanning
     * for any devices in the area, and writing to the server that the associated student is present.
     *
     * @param m_class The class we would like to take attendance for
     * @param onSuccessListener the callback for what should happen when a student is marked present.
     *                          This callback returns the student that was marked present for being
     *                          in the area
     * @param onFailureListener any failure in marking a student present
     */
    public void beginTakingAttendance(final Class m_class,
                                      OnSuccessListener<Student> onSuccessListener,
                                      OnFailureListener onFailureListener) {

        if (this.m_role == Role.STUDENT) {
            throw new UnsupportedOperationException("Student cannot begin taking attendance");
        }

        if (onSuccessListener == null || onFailureListener == null) {
            Log.w(TAG, "On Success listener and On Failure listener cannot be null to begin" +
                    " taking attendance");
            throw new NullPointerException("Cannot have a null successlistener or failure listener");
        }

        if (mTimer != null) {
            stopTakingAttendance();
        }

        if (m_class == null) {
            Log.d(TAG, "the class id must be provided before you can take attendance");
            return;
        }

        this.m_class = m_class;

        // Convert the students list to a hashtable mapping mac address to student obj
        Iterator<String> stringIdIterator = m_class.getStudentIdsIterator();
        while (stringIdIterator.hasNext()) {
            String studentId = stringIdIterator.next();
            view.getStudent(studentId, new OnSuccessListener<Student>() {
                @Override
                public void onSuccess(Student student) {
                    studentHashtable.put(student.getMacAddress(), student);
                }
            }, new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.e(TAG, "Error getting student", e);
                }
            });
        }

        this.receiver.onSuccessListener = onSuccessListener;
        this.receiver.onFailureListener = onFailureListener;

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        context.registerReceiver(this.receiver, filter);
        bluetoothAdapter.startDiscovery();
        mTimer = new Timer();
        TimerTask mTimerTask = new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (bluetoothAdapter.isDiscovering()) {
                            // Cancel followed by a start searches for bluetooth devices again
                            // Not doing this would result in the search not being updated
                            bluetoothAdapter.cancelDiscovery();
                            bluetoothAdapter.startDiscovery();
                        } else {
                            bluetoothAdapter.startDiscovery();
                        }
                    }
                });
            }
        };

        this.mTimer.schedule(mTimerTask, 0, 10000);

    }

    /**
     * This is used by the teacher to stop taking attendance for the last class that start attendance
     * was called on
     */
    public void stopTakingAttendance() {
        context.unregisterReceiver(this.receiver);
        mTimer.cancel();
        this.removeListener();
        bluetoothAdapter.cancelDiscovery();
        mTimer = null;
        Log.d(TAG, "Timer has stopped and no longer scanning for devices");
    }

    /**
     * Used by the student to sign in. It's important that bluetooth already be enabled and set
     * to discoverable before calling this method. A listener will be created that will check
     * for an acknowledgement from the teacher on the session id that is provided. For example,
     * if sign in is called for session id 1, then when the teacher marks the student as present,
     * the success callback will be called.
     * On failure, the failure listener will be called.
     * @param view The view to watch for changes
     * @param sessionId The session id for the session you would like to check in to.
     * @param studentId The id of the student checking in. this should be the student using the app
     * @param successListener The callback for a successful ack from the teacher. This is called when
     *                        the teacher successfully marks the student with student id present to
     *                        the session with session id
     * @param failureListener The callback for a failure. The exception is passed. In most cases,
     *                        the listener will continue listening for changes even after this was
     *                        called. In other words a failure could be followed by a successful
     *                        ack.
     * @return true if the listener was successfully initialized, false otherwise such as if bluetooth
     * is disabled.
     */
    public boolean signIn(View view,
                          String classId,
                          String sessionId,
                          final String studentId,
                          final OnSuccessListener<Void> successListener,
                          final OnFailureListener failureListener) {

        if (this.m_role == Role.TEACHER) {
            throw new UnsupportedOperationException("Teacher cannot sign in to a class");
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.i(TAG, "Bluetooth isn't enabled");
            Toast.makeText(this.context, "Bluetooth isn't enabled", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (listener != null) {
            removeListener();
        }

        // Begin listening to see if I am enabled
        listener = view.listenForMarking(classId, sessionId, studentId, new OnSuccessListener<Attendee>() {
            @Override
            public void onSuccess(Attendee attendee) {
                if (attendee != null && attendee.getTeacherTimeStamp() != null) {
                    Log.i(TAG, "Student detected and marked present by teacher");
                    BluetoothAdapter.this.removeListener();
                    successListener.onSuccess(null);
                }
            }
        }, failureListener);

        return true;

    }

    /**
     * this is used specifically by a student. It removes the listener so they no longer check
     * for an ack
     */
    public void removeListener() {
        if (this.listener != null) {
            this.listener.remove();
            this.listener = null;
        }
    }

    private void runOnUiThread(Runnable r) {
        this.handler.post(r);
    }

}
