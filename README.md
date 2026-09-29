# Aurora University — Hostel Booking Portal



A Java Swing desktop application developed as part of the **Software Construction** course. The project implements a corrected and functional **University Hostel Booking System** based on the requirements and case study provided in the assignment.



The application focuses on applying software construction principles such as **minimizing complexity, anticipating change, reuse, verification, reliability, maintainability, and concurrency control** to a realistic hostel booking workflow.



---



## Course Information



**Course:** Software Construction



**Instructor:** ENGR. Saad Mazhar



**Project:** Aurora University Hostel Booking Portal



---



## Features



### 1. Student Login & Management



* Student login using Student ID and password.



* Passwords are securely stored using **SHA-256 hashing**.



* Supports student-specific booking information and access restrictions.



### 2. Room Filtering



Students can browse rooms based on:



* **Block:** A, B, or C



* **Room Type:** Single, Double, or Shared



* **Room Status:** Available, On Hold, Reserved, or Occupied



### 3. Live Room Availability



* Displays the current status of rooms.



* Updates room availability after booking, cancellation, payment, or expiration.



* Prevents students from selecting unavailable rooms.



### 4. Booking Validation



The system enforces important booking rules:



* A student can have only **one active reservation** at a time.



* Students cannot reserve an already-held or reserved room.



* **Block C** is restricted to students with registered accessibility requirements.



* Invalid booking requests are rejected with appropriate feedback.



### 5. Deposit & Reservation Expiration



* Selected rooms are placed **On Hold** for a limited period.



* For demonstration purposes, the hold period is **1 minute**.



* The actual policy represents a **48-hour** reservation period.



* If payment is not completed within the allowed period, the room is automatically released.



### 6. Cancel & Switch Room



Students can:



* Cancel an existing reservation.



* Switch to another available room.



* Maintain their deposit status when switching rooms, where applicable.



### 7. Audit Trail



The system maintains a record of important booking events, including:



* Booking



* Payment



* Cancellation



* Room switching



* Move-in



* Reservation expiration



Each event includes a timestamp for traceability and accountability.



### 8. Idempotency & Recovery



The booking process includes mechanisms for handling interrupted or repeated requests.



* Duplicate requests do not create duplicate bookings.



* Each booking request can have a unique request reference.



* Students can use the request reference to recover or verify their booking status after a connection failure.



### 9. Race Condition Handling



The system uses a **locking mechanism** when processing room reservations.



This prevents two students from successfully booking the same last available room simultaneously.



---



## Software Construction Principles



The project demonstrates several important software construction concepts:



* **Minimizing Complexity** — keeping the booking workflow structured and manageable.



* **Anticipating Change** — organizing booking rules so they can be modified more easily.



* **Reuse** — using reusable methods and components instead of duplicating logic.



* **Constructing for Verification** — validating input and booking conditions before changing system state.



* **Reliability** — handling expired reservations, failed requests, and duplicate operations.



* **Concurrency Control** — preventing race conditions during room booking.



* **Maintainability** — organizing the application into logical components for easier modification.



---



## Technologies Used



* **Java**



* **Java Swing**



* **AWT**



* **Java Time API**



* **Java Security API**



* **Object-Oriented Programming**



* Standard Java libraries



No external libraries are required.



---



## Prerequisites



* **Java JDK 8 or higher**



* Tested with **Java 11+**



* Any Java-compatible IDE or terminal







## Validation & Error Handling



The application validates user actions and prevents invalid operations such as:



* Empty or invalid login credentials



* Invalid room selection



* Booking an unavailable room



* Creating multiple active reservations



* Accessing restricted rooms without eligibility



* Duplicate booking requests



* Invalid reservation states



* Switching to an unavailable room



Appropriate messages are displayed when an operation cannot be completed.



---



## Assignment Objective



The purpose of this project is to demonstrate how **software construction principles can be applied to identify and correct flaws in an under-specified software system**.



The implementation provides a structured and reliable hostel booking workflow while addressing important concerns such as **complexity, changeability, verification, concurrency, recovery, and maintainability**.



---



