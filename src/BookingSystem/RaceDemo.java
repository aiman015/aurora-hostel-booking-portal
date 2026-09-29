package BookingSystem;

import java.util.List;
import java.util.concurrent.CyclicBarrier;

/**
 * Console demo of the race condition: two students request the same room at
 * the same instant. Only one booking may succeed.
 * Run:  java BookingSystem.RaceDemo
 */
public class RaceDemo {

    public static void main(String[] args) throws Exception {

        HostelBookingSystem system = new HostelBookingSystem();
        String room = "A-101";
        String[] students = {"AU001", "AU002"};
        String[] result = new String[2];
        CyclicBarrier gate = new CyclicBarrier(2);

        System.out.println("=== Race condition demo: " + students[0] + " and "
                + students[1] + " both request " + room + " ===\n");

        Thread[] threads = new Thread[2];
        for (int i = 0; i < 2; i++) {
            final int k = i;
            threads[i] = new Thread(() -> {
                try {
                    gate.await();
                    Booking b = system.createBooking(students[k], room, "1 Semester", "REQ-DEMO-" + k);
                    result[k] = students[k] + " -> SUCCESS (" + b.id + ")";
                } catch (IllegalStateException e) {
                    result[k] = students[k] + " -> REJECTED: " + e.getMessage();
                } catch (Exception e) {
                    result[k] = students[k] + " -> ERROR";
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) t.join();

        for (String r : result) System.out.println(r);

        System.out.println("\n--- Audit trail (oldest first) ---");
        List<String> h = system.getHistory();
        for (int i = h.size() - 1; i >= 0; i--) System.out.println(h.get(i));
    }
}