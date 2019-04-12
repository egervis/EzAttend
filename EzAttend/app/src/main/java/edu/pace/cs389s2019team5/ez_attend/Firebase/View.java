package edu.pace.cs389s2019team5.ez_attend.Firebase;

import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;

public class View {

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final String TAG = View.class.getName();

    public View() { }

    /**
     * Loads the students in the class.
     * @param onSuccessListener what to do with the students info
     * @param onFailureListener if it fails, what should happen
     */
    public void getStudents(final OnSuccessListener<ArrayList<Student>> onSuccessListener,
                            OnFailureListener onFailureListener) {

        CollectionReference studentsRef = db.collection("students");

        studentsRef
            .get()
            .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                @Override
                public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                    ArrayList<Student> students = new ArrayList<>();
                    for (QueryDocumentSnapshot docSnapshot : queryDocumentSnapshots) {
                        try {
                            Student student = Student.fromSnapshot(docSnapshot);
                            students.add(student);
                        } catch (NullPointerException exc) {
                            Log.e(TAG, "Error parsing student", exc);
                        }
                    }
                    onSuccessListener.onSuccess(students);
                }
            }).addOnFailureListener(onFailureListener);
    }

    /**
     * Loads an entire student object from the student id. This includes the students first name,
     * last name, and mac address
     * @param id the id of the student
     * @param onSuccessListener the callback when the student info is loaded
     * @param onFailureListener the callback when the student info load fails
     */
    public void getStudent(final String id,
                           final OnSuccessListener<Student> onSuccessListener,
                           OnFailureListener onFailureListener) {

        DocumentReference docRef = db.collection("students").document(id);
        docRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot snapshot) {
                if (snapshot.exists()) {
                    Student student;
                    try {
                        student = Student.fromSnapshot(snapshot);
                    } catch (NullPointerException exc) {
                        student = null;
                        Log.w(TAG, "Student with id " + id + " was corrupt ", exc);
                    }
                    onSuccessListener.onSuccess(student);
                } else {
                    Log.e(TAG, "Student with id " + id + " doesn't exit");
                    onSuccessListener.onSuccess(null);
                }
            }
        }).addOnFailureListener(onFailureListener);

    }

    /**
     * Loads the id's of all the sessions that have taken place in chronological order by start time
     * @param onSuccessListener the callback for when we get this info
     * @param onFailureListener the callback for failing to get the info
     */
    public void getSessions(final OnSuccessListener<ArrayList<ClassSession>> onSuccessListener,
                            OnFailureListener onFailureListener) {

        CollectionReference sessionsRef = db.collection("sessions");

        sessionsRef
                .orderBy("startTime")
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                        ArrayList<ClassSession> sessions = new ArrayList<>();
                        for (QueryDocumentSnapshot docSnapshot : queryDocumentSnapshots) {
                            try {
                                ClassSession session = ClassSession.fromSnapshot(docSnapshot);
                                sessions.add(session);
                            } catch (NullPointerException exc) {
                                Log.e(TAG, "Error parsing session", exc);
                            }
                        }
                        onSuccessListener.onSuccess(sessions);
                    }
                }).addOnFailureListener(onFailureListener);
    }

    /**
     * Loads the session info for a provided session. The id for the session must be provided.
     * Using this info it pulls the attendance for that specific session
     * @param session the session that we are interested in getting the attendance for
     * @param onSuccessListener the callback for getting this information
     * @param onFailureListener the failure callback for this info
     */
    public void getSessionAttendance(final ClassSession session,
                                     final OnSuccessListener<ArrayList<Attendee>> onSuccessListener,
                                     OnFailureListener onFailureListener) {

        CollectionReference attendeesCollection = db.collection("sessions")
                .document(session.getId())
                .collection(ClassSession.ATTENDEES);

        attendeesCollection.get().addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
            @Override
            public void onSuccess(QuerySnapshot snapshot) {
                ArrayList<Attendee> attendees = new ArrayList<>();
                for (QueryDocumentSnapshot snap : snapshot) {
                    Log.d(TAG, "Got attendee with id: " + snap.getId());
                    attendees.add(Attendee.fromSnapshot(snap));
                }

                onSuccessListener.onSuccess(attendees);
            }
        }).addOnFailureListener(onFailureListener);

    }

    /**
     * Used by students to wait until they are marked present by their teacher.
     * @param courseId
     * @param studentId
     * @param eventListener
     * @return
     */
    public ListenerRegistration listenForMarking(String courseId,
                                                 String studentId,
                                                 EventListener<DocumentSnapshot> eventListener) {

        final DocumentReference docRef = db.collection("sessions/ " + courseId + "/attendees").document(studentId);
        return docRef.addSnapshotListener(eventListener);

    }

}
