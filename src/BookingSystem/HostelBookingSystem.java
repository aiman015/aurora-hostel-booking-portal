package BookingSystem;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core booking logic (no GUI code). Every state change happens inside
 * bookingLock so "check availability" + "reserve" is one atomic step.
 */
public class HostelBookingSystem {

    /** Demo value: 1 minute (the real policy is 48 hours). */
    static final long PAYMENT_WINDOW_MS = 60_000;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Map<String, Student> students = new TreeMap<>();
    private final Map<String, Room> rooms = new LinkedHashMap<>();
    private final Map<String, Integer> nextSeq = new HashMap<>();
    private final List<Booking> bookings = new ArrayList<>();
    private final Map<String, Booking> requests = new HashMap<>();   // idempotency table
    private final List<String> history = new ArrayList<>();

    private Student currentStudent;
    private final AtomicInteger bookingCounter = new AtomicInteger(1001);
    private final Object bookingLock = new Object();

    public HostelBookingSystem() {
        loadSampleStudents();
        loadRooms();
        startExpirationChecker();
    }

    // ---------------------------------------------------------
    // Sample data (all demo passwords: pass123)
    // ---------------------------------------------------------

    private void loadSampleStudents() {
        addStudent("AU001", "Ali Khan", "Computer Science", "pass123", false);
        addStudent("AU002", "Sara Ahmed", "Software Engineering", "pass123", false);
        addStudent("AU003", "Hamza Malik", "Electrical Engineering", "pass123", false);
        addStudent("AU004", "Ayesha Noor", "Business Administration", "pass123", true);
        addStudent("AU005", "Hassan Raza", "Computer Science", "pass123", false);
    }

    private void addRooms(char block, Room.Type type, int floor, int count) {
        for (int i = 0; i < count; i++) {
            int seq = nextSeq.merge(block + "" + floor, 1, Integer::sum);
            String id = block + "-" + floor + String.format("%02d", seq);
            rooms.put(id, new Room(id, block, type, floor));
        }
    }

    /** Scaled-down version of the case-study hostel (3 blocks, 3 room types). */
    private void loadRooms() {
        addRooms('A', Room.Type.SINGLE, 1, 4);
        addRooms('A', Room.Type.DOUBLE, 2, 3);
        addRooms('A', Room.Type.SHARED, 3, 2);

        addRooms('B', Room.Type.SINGLE, 1, 3);
        addRooms('B', Room.Type.DOUBLE, 2, 3);
        addRooms('B', Room.Type.SHARED, 3, 2);

        addRooms('C', Room.Type.SINGLE, 0, 4);   // ground floor, accessibility only
        addRooms('C', Room.Type.DOUBLE, 0, 2);
    }

    // ---------------------------------------------------------
    // Students and login
    // ---------------------------------------------------------

    public boolean addStudent(String id, String name, String department,
                              String password, boolean accessibilityRequired) {
        id = id.trim().toUpperCase();
        if (students.containsKey(id)) return false;
        students.put(id, new Student(id, name, department, password, accessibilityRequired));
        return true;
    }

    public List<Student> getStudents() {
        return new ArrayList<>(students.values());
    }

    public Student getStudent(String id) {
        return students.get(id);
    }

    public boolean login(String id, String password) {
        Student s = students.get(id.trim().toUpperCase());
        if (s == null || !s.passwordHash.equals(Student.hash(password))) {
            addHistory("Login failed: " + id.trim());
            return false;
        }
        currentStudent = s;
        return true;
    }

    public void logout() {
        currentStudent = null;
    }

    public Student getCurrentStudent() {
        return currentStudent;
    }

    // ---------------------------------------------------------
    // Helpers (callers must hold bookingLock)
    // ---------------------------------------------------------

    private boolean isActive(Booking b) {
        return b.status.equals(Booking.PENDING)
                || b.status.equals(Booking.CONFIRMED)
                || b.status.equals(Booking.MOVED_IN);
    }

    private boolean isRoomAvailable(String roomId) {
        for (Booking b : bookings)
            if (b.roomId.equals(roomId) && isActive(b)) return false;
        return true;
    }

    private Booking findActiveBookingForStudent(String studentId) {
        for (Booking b : bookings)
            if (b.studentId.equals(studentId) && isActive(b)) return b;
        return null;
    }

    private Booking findBooking(String bookingId) {
        for (Booking b : bookings)
            if (b.id.equals(bookingId)) return b;
        return null;
    }

    /** Releases every unpaid booking whose deadline has passed. */
    private void expireOverdueLocked() {
        long now = System.currentTimeMillis();
        for (Booking b : bookings) {
            if (!b.paid && b.status.equals(Booking.PENDING) && now > b.paymentDeadline) {
                b.status = Booking.EXPIRED;
                addHistory("Deposit deadline passed, room released: " + b.id
                        + " (" + b.roomId + ", " + b.studentId + ")");
            }
        }
    }

    /** Returns null if the student may book this room, otherwise the reason why not. */
    public String eligibilityProblem(Student s, Room r) {
        if (r.block == 'C' && !s.accessibilityRequired)
            return "Block C is reserved for students with a registered accessibility requirement.";
        return null;
    }

    // ---------------------------------------------------------
    // Read-only queries
    // ---------------------------------------------------------

    public List<Room> getAllRooms() {
        return new ArrayList<>(rooms.values());
    }

    public Room getRoom(String id) {
        return rooms.get(id);
    }

    public Booking getActiveBookingForRoom(String roomId) {
        synchronized (bookingLock) {
            expireOverdueLocked();
            for (Booking b : bookings)
                if (b.roomId.equals(roomId) && isActive(b)) return b;
            return null;
        }
    }

    public Booking getActiveBookingForStudent(String studentId) {
        synchronized (bookingLock) {
            expireOverdueLocked();
            return findActiveBookingForStudent(studentId);
        }
    }

    /** A student's bookings for display (expired and deleted ones are hidden). */
    public List<Booking> getBookingsForStudent(String studentId) {
        synchronized (bookingLock) {
            expireOverdueLocked();
            List<Booking> result = new ArrayList<>();
            for (Booking b : bookings)
                if (b.studentId.equals(studentId)
                        && !b.status.equals(Booking.EXPIRED)
                        && !b.status.equals(Booking.DELETED))
                    result.add(b);
            return result;
        }
    }

    /**
     * Recovery after a dropped connection: find what a reference actually produced.
     * Case-insensitive, ignores surrounding spaces, and also accepts a booking ID (e.g. BK1001).
     */
    public Booking findByRequest(String reference) {
        synchronized (bookingLock) {
            expireOverdueLocked();
            if (reference == null) return null;
            String key = reference.trim().toUpperCase();
            if (key.isEmpty()) return null;
            Booking b = requests.get(key);
            if (b != null) return b;
            return findBooking(key);
        }
    }

    // ---------------------------------------------------------
    // Create booking (idempotent)
    // ---------------------------------------------------------

    /**
     * Validation order:
     * 0. same requestId already processed -> return the earlier booking
     * 1. student exists   2. room exists   3. student may use this block
     * 4. student has no other active reservation   5. room is still free
     */
    public Booking createBooking(String studentId, String roomId,
                                 String duration, String requestId) {
        synchronized (bookingLock) {

            expireOverdueLocked();

            if (requestId != null) {
                requestId = requestId.trim().toUpperCase();
                Booking prev = requests.get(requestId);
                if (prev != null) {
                    addHistory("Duplicate request " + requestId
                            + " ignored, returned " + prev.id);
                    return prev;
                }
            }

            Student s = students.get(studentId);
            if (s == null) {
                addHistory("Booking rejected: unknown student " + studentId);
                throw new IllegalStateException("Student ID not found.");
            }

            Room r = rooms.get(roomId);
            if (r == null)
                throw new IllegalStateException("Room does not exist.");

            String problem = eligibilityProblem(s, r);
            if (problem != null) {
                addHistory("Booking rejected: " + studentId + " not eligible for " + roomId);
                throw new IllegalStateException(problem);
            }

            Booking existing = findActiveBookingForStudent(studentId);
            if (existing != null) {
                addHistory("Booking rejected: " + studentId
                        + " already has active reservation " + existing.id);
                throw new IllegalStateException("You already have an active reservation ("
                        + existing.id + ", " + existing.roomId
                        + "). Cancel it or switch rooms instead.");
            }

            if (!isRoomAvailable(roomId)) {
                addHistory("Booking failed: " + roomId + " already taken (" + studentId + ")");
                throw new IllegalStateException(
                        "Room is no longer available. Another student may have booked it first.");
            }

            Booking b = new Booking("BK" + bookingCounter.getAndIncrement(),
                    studentId, roomId, duration);
            b.requestId = requestId;
            b.paymentDeadline = System.currentTimeMillis() + PAYMENT_WINDOW_MS;

            bookings.add(b);
            if (requestId != null) requests.put(requestId, b);

            addHistory("Booked: " + b.id + " (" + roomId + ", " + studentId + ")");
            return b;
        }
    }

    // ---------------------------------------------------------
    // Payment, move-in, delete
    // ---------------------------------------------------------

    /** Idempotent: paying twice never charges twice. */
    public boolean payDeposit(String bookingId) {
        synchronized (bookingLock) {
            expireOverdueLocked();
            Booking b = findBooking(bookingId);
            if (b == null) return false;
            if (b.paid) return true;
            if (!b.status.equals(Booking.PENDING)) return false;

            b.paid = true;
            b.status = Booking.CONFIRMED;
            addHistory("Payment received: " + b.id + " (" + b.roomId + ")");
            return true;
        }
    }

    /** Front-desk step: the student physically arrives. Requires a paid deposit. */
    public Booking checkIn(String bookingId) {
        synchronized (bookingLock) {
            expireOverdueLocked();
            Booking b = findBooking(bookingId);
            if (b == null || !isActive(b))
                throw new IllegalStateException("No active reservation to move into.");
            if (b.status.equals(Booking.PENDING))
                throw new IllegalStateException("Pay the security deposit before moving in.");
            if (b.status.equals(Booking.MOVED_IN))
                throw new IllegalStateException("You have already moved in.");

            b.status = Booking.MOVED_IN;
            b.movedInAt = System.currentTimeMillis();
            addHistory("Moved in: " + b.id + " (" + b.roomId + ", " + b.studentId + ")");
            return b;
        }
    }

    /** Deletes a moved-in booking: the room is released and the student may book again. */
    public boolean deleteBooking(String bookingId) {
        synchronized (bookingLock) {
            Booking b = findBooking(bookingId);
            if (b == null || !b.status.equals(Booking.MOVED_IN)) return false;
            b.status = Booking.DELETED;
            addHistory("Deleted booking: " + b.id + " (" + b.roomId + ", room released)");
            return true;
        }
    }

    // ---------------------------------------------------------
    // Cancel and switch
    // ---------------------------------------------------------

    /** Only reservations that have not been moved into can be cancelled. */
    public boolean cancelBooking(String bookingId) {
        synchronized (bookingLock) {
            expireOverdueLocked();
            Booking b = findBooking(bookingId);
            if (b == null
                    || !(b.status.equals(Booking.PENDING) || b.status.equals(Booking.CONFIRMED)))
                return false;

            b.status = Booking.CANCELLED;
            addHistory("Cancelled: " + b.id + " (" + b.roomId + ", room released)");
            return true;
        }
    }

    /**
     * Atomic switch. The new room is checked BEFORE the old one is released,
     * so a failed switch never leaves the student without a room.
     * A paid deposit carries over; an unpaid one keeps its original deadline.
     * The request reference also carries over, so it can still be looked up.
     */
    public Booking switchBooking(String bookingId, String newRoomId, String newDuration) {
        synchronized (bookingLock) {

            expireOverdueLocked();

            Booking old = findBooking(bookingId);
            if (old == null
                    || !(old.status.equals(Booking.PENDING) || old.status.equals(Booking.CONFIRMED)))
                throw new IllegalStateException("Only a reservation you have not moved into can be switched.");

            if (old.roomId.equals(newRoomId))
                throw new IllegalStateException("You are already in " + newRoomId + ".");

            Room r = rooms.get(newRoomId);
            Student s = students.get(old.studentId);
            if (r == null || s == null)
                throw new IllegalStateException("Room does not exist.");

            String problem = eligibilityProblem(s, r);
            if (problem != null)
                throw new IllegalStateException(problem);

            if (!isRoomAvailable(newRoomId)) {
                addHistory("Switch failed: " + newRoomId + " not available (" + old.studentId + ")");
                throw new IllegalStateException(newRoomId + " is not available.");
            }

            old.status = Booking.SWITCHED;

            Booking fresh = new Booking("BK" + bookingCounter.getAndIncrement(),
                    old.studentId, newRoomId, newDuration);
            fresh.paid = old.paid;
            fresh.status = old.paid ? Booking.CONFIRMED : Booking.PENDING;
            fresh.paymentDeadline = old.paymentDeadline;
            fresh.requestId = old.requestId;
            bookings.add(fresh);
            if (fresh.requestId != null) requests.put(fresh.requestId, fresh);

            addHistory("Switched: " + old.id + " (" + old.roomId + ") -> "
                    + fresh.id + " (" + newRoomId + ")");
            return fresh;
        }
    }

    // ---------------------------------------------------------
    // Background expiry
    // ---------------------------------------------------------

    private void startExpirationChecker() {
        Thread checker = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                synchronized (bookingLock) {
                    expireOverdueLocked();
                }
            }
        });
        checker.setDaemon(true);
        checker.start();
    }

    // ---------------------------------------------------------
    // Audit trail
    // ---------------------------------------------------------

    public void addHistory(String message) {
        synchronized (history) {
            history.add(0, LocalTime.now().format(TIME_FORMAT) + " - " + message);
            if (history.size() > 100) history.remove(history.size() - 1);
        }
    }

    public List<String> getHistory() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    public void clearHistory() {
        synchronized (history) {
            history.clear();
        }
    }
}


// =============================================================
// STUDENT
// =============================================================

/** A registered student. Passwords are stored as SHA-256 hashes, never as plain text. */
class Student {

    final String id;
    final String name;
    final String department;
    final boolean accessibilityRequired;
    final String passwordHash;

    Student(String id, String name, String department,
            String password, boolean accessibilityRequired) {
        this.id = id;
        this.name = name;
        this.department = department;
        this.accessibilityRequired = accessibilityRequired;
        this.passwordHash = hash(password);
    }

    static String hash(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}


// =============================================================
// ROOM
// =============================================================

/** A physical hostel room: block, type, floor. Availability lives in the bookings. */
class Room {

    enum Type {
        SINGLE("Single", 1),
        DOUBLE("Double", 2),
        SHARED("Shared", 4);

        final String label;
        final int capacity;

        Type(String label, int capacity) {
            this.label = label;
            this.capacity = capacity;
        }
    }

    final String id;
    final char block;
    final Type type;
    final int floor;

    Room(String id, char block, Type type, int floor) {
        this.id = id;
        this.block = block;
        this.type = type;
        this.floor = floor;
    }

    /** Block C = ground-floor rooms near the accessible entrance. */
    boolean accessible() {
        return block == 'C';
    }

    String floorLabel() {
        return floor == 0 ? "Ground floor" : "Floor " + floor;
    }
}


// =============================================================
// BOOKING
// =============================================================

/** One reservation. Lifecycle: PENDING -> CONFIRMED -> MOVED_IN -> DELETED. */
class Booking {

    static final String PENDING = "PENDING PAYMENT";
    static final String CONFIRMED = "CONFIRMED";
    static final String MOVED_IN = "MOVED IN";
    static final String DELETED = "DELETED";
    static final String EXPIRED = "EXPIRED";
    static final String CANCELLED = "CANCELLED";
    static final String SWITCHED = "SWITCHED";

    final String id;
    final String studentId;
    final String roomId;
    final String duration;

    /** Client-generated reference; the same reference always maps to the same booking. */
    String requestId;

    String status;
    boolean paid;

    final long createdAt;
    long paymentDeadline;
    long movedInAt;

    Booking(String id, String studentId, String roomId, String duration) {
        this.id = id;
        this.studentId = studentId;
        this.roomId = roomId;
        this.duration = duration;
        this.status = PENDING;
        this.paid = false;
        this.createdAt = System.currentTimeMillis();
    }
}