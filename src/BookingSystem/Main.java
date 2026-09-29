package BookingSystem;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Arc2D;
import java.awt.geom.RoundRectangle2D;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.function.Consumer;

/**
 * Aurora University Hostel Booking Portal - Swing GUI (professional theme).
 * Screens: Sign in, Dashboard, Rooms, My Reservation, Students, Audit Trail.
 * All booking rules live in HostelBookingSystem; this class only presents them.
 */
public class Main extends JFrame {

    // =========================================================
    // THEME
    // =========================================================

    static final Color NAVY = new Color(0x0F, 0x17, 0x2A);
    static final Color NAVY_LIGHT = new Color(0x1E, 0x29, 0x3B);
    static final Color ACCENT = new Color(0x4F, 0x46, 0xE5);
    static final Color BG = new Color(0xF4, 0xF6, 0xFB);
    static final Color TEXT = new Color(0x1E, 0x29, 0x3B);
    static final Color MUTED = new Color(0x64, 0x74, 0x8B);
    static final Color SOFT = new Color(0x94, 0xA3, 0xB8);
    static final Color GREEN = new Color(0x05, 0x96, 0x69);
    static final Color AMBER = new Color(0xD9, 0x77, 0x06);
    static final Color RED = new Color(0xE1, 0x1D, 0x48);
    static final Color PURPLE = new Color(0x7C, 0x3A, 0xED);
    static final Color CYAN = new Color(0x08, 0x91, 0xB2);
    static final Color LINE = new Color(0xE2, 0xE8, 0xF0);

    static final String DEMO_PW = "pass123";
    static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("EEE d MMM  \u00B7  HH:mm:ss");
    static final Map<String, String[]> TITLES = new LinkedHashMap<>();
    static final String FAMILY = pickFamily();

    static {
        TITLES.put("dashboard", new String[]{"Dashboard", "Live overview of occupancy across all blocks"});
        TITLES.put("rooms", new String[]{"Rooms", "Choose a block and room type, then select a room to reserve it"});
        TITLES.put("mybooking", new String[]{"My Reservation", "Pay your deposit, move in, switch, cancel or recover a booking"});
        TITLES.put("students", new String[]{"Students & Occupancy", "Who has booked, who has paid, who has actually moved in"});
        TITLES.put("activity", new String[]{"Audit Trail", "Every booking event, newest first"});
    }

    // =========================================================
    // STATE
    // =========================================================

    private final HostelBookingSystem system = new HostelBookingSystem();

    private final CardLayout rootLayout = new CardLayout();
    private final JPanel root = new JPanel(rootLayout);
    private final CardLayout pageLayout = new CardLayout();
    private final JPanel pages = new JPanel(pageLayout);

    private JLabel pageTitle, pageSubtitle, clockLabel, headerName, headerId, sideName, sideId;
    private Avatar headerAvatar, sideAvatar;
    private final Map<String, NavButton> navButtons = new LinkedHashMap<>();

    private JPanel dashboardPanel, roomsPanel, bookingsPanel, studentsPanel, activityPanel, chipsPanel;
    private Field loginField;
    private PassField loginPass;
    private JLabel loginError;

    private final List<Countdown> countdowns = new ArrayList<>();
    private String lastTopHistory = "";

    // Rooms page filters: block is always one of A/B/C, type is All/Single/Double/Shared
    private String filterBlock = "A", filterType = "All";

    // dropped-connection demo
    private String pendingRequestId, pendingRoomId, pendingDuration;

    private Toast toast;
    private javax.swing.Timer toastTimer;

    // =========================================================
    // SMALL HELPERS
    // =========================================================

    static String pickFamily() {
        String[] pref = {"Inter", "Segoe UI", "SF Pro Text", "Helvetica Neue", "Roboto", "Arial"};
        Set<String> have = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String f : pref) if (have.contains(f)) return f;
        return Font.SANS_SERIF;
    }

    static Font font(int style, int size) {
        return new Font(FAMILY, style, size);
    }

    static Graphics2D aa(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    static Color lighten(Color c, int amt) {
        return new Color(Math.min(255, c.getRed() + amt), Math.min(255, c.getGreen() + amt),
                Math.min(255, c.getBlue() + amt));
    }

    static void drawShadow(Graphics2D g, int x, int y, int w, int h, int arc) {
        for (int i = 6; i >= 1; i--) {
            g.setColor(new Color(15, 23, 42, 5));
            g.fillRoundRect(x, y + i, w, h, arc, arc);
        }
    }

    static JLabel label(String text, int size, int style, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font(style, size));
        l.setForeground(color);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    static <T extends JComponent> T left(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    static String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        String r = parts[0].substring(0, 1);
        if (parts.length > 1) r += parts[parts.length - 1].substring(0, 1);
        return r.toUpperCase();
    }

    static Color statusColor(String status) {
        switch (status) {
            case Booking.CONFIRMED: return ACCENT;
            case Booking.PENDING: return AMBER;
            case Booking.MOVED_IN: return PURPLE;
            case Booking.CANCELLED: return RED;
            default: return MUTED;
        }
    }

    static String bedsText(Room r) {
        return r.type.capacity + (r.type.capacity == 1 ? " bed" : " beds");
    }

    static String newRequestId() {
        return "REQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /** Edit distance, used to suggest the reference the student probably meant to type. */
    static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    /** Clickable text link. */
    static JLabel link(String text, Runnable action) {
        JLabel l = new JLabel(text);
        l.setFont(font(Font.BOLD, 13));
        l.setForeground(ACCENT);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { action.run(); }
        });
        return l;
    }

    /** Vector line icons drawn on a 20x20 grid, scaled to size s. */
    static void icon(Graphics2D g0, String n, int x, int y, int s, Color c) {
        Graphics2D g = (Graphics2D) g0.create();
        g.translate(x, y);
        g.scale(s / 20.0, s / 20.0);
        g.setColor(c);
        g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (n) {
            case "dash":
                g.drawRoundRect(2, 2, 6, 6, 2, 2);
                g.drawRoundRect(12, 2, 6, 6, 2, 2);
                g.drawRoundRect(2, 12, 6, 6, 2, 2);
                g.drawRoundRect(12, 12, 6, 6, 2, 2);
                break;
            case "rooms":
                g.drawRoundRect(3, 2, 14, 16, 2, 2);
                g.fillRect(6, 5, 2, 2);
                g.fillRect(12, 5, 2, 2);
                g.fillRect(6, 9, 2, 2);
                g.fillRect(12, 9, 2, 2);
                g.drawRect(8, 13, 4, 5);
                break;
            case "key":
                g.drawOval(2, 6, 8, 8);
                g.drawLine(10, 10, 18, 10);
                g.drawLine(15, 10, 15, 13);
                g.drawLine(18, 10, 18, 13);
                break;
            case "users":
                g.drawOval(6, 3, 8, 8);
                g.draw(new Arc2D.Float(3, 13, 14, 12, 0, 180, Arc2D.OPEN));
                break;
            case "list":
                for (int i = 0; i < 3; i++) {
                    int yy = 5 + i * 5;
                    g.drawLine(7, yy, 18, yy);
                    g.fillOval(2, yy - 1, 2, 2);
                }
                break;
            case "out":
                g.drawLine(9, 3, 4, 3);
                g.drawLine(4, 3, 4, 17);
                g.drawLine(4, 17, 9, 17);
                g.drawLine(8, 10, 17, 10);
                g.drawLine(13, 6, 17, 10);
                g.drawLine(13, 14, 17, 10);
                break;
            case "bed":
                g.drawLine(2, 5, 2, 16);
                g.drawRoundRect(2, 10, 16, 4, 2, 2);
                g.drawRoundRect(4, 7, 4, 3, 1, 1);
                g.drawLine(18, 14, 18, 16);
                break;
            case "clock":
                g.drawOval(2, 2, 16, 16);
                g.drawLine(10, 6, 10, 10);
                g.drawLine(10, 10, 13, 12);
                break;
            case "check":
                g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(4, 10, 8, 14);
                g.drawLine(8, 14, 16, 6);
                break;
            case "lock":
                g.drawRoundRect(4, 9, 12, 9, 2, 2);
                g.draw(new Arc2D.Float(6, 2, 8, 12, 0, 180, Arc2D.OPEN));
                break;
            case "home":
                g.drawLine(2, 10, 10, 3);
                g.drawLine(10, 3, 18, 10);
                g.drawRect(5, 10, 10, 8);
                break;
            case "shield":
                g.drawPolygon(new int[]{10, 17, 17, 10, 3, 3}, new int[]{2, 5, 11, 18, 11, 5}, 6);
                break;
            default:
                break;
        }
        g.dispose();
    }

    // =========================================================
    // CUSTOM COMPONENTS
    // =========================================================

    static class RoundedPanel extends JPanel {

        private final int arc;
        private final Color fill;
        private final boolean shadow;
        private Color accent;
        private Color border = LINE;

        RoundedPanel(int arc, Color fill, boolean shadow) {
            this.arc = arc;
            this.fill = fill;
            this.shadow = shadow;
            setOpaque(false);
        }

        void setAccent(Color c) {
            accent = c;
            repaint();
        }

        void setBorderColor(Color c) {
            border = c;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int w = getWidth();
            int h = getHeight() - (shadow ? 6 : 0);
            if (shadow) drawShadow(g2, 0, 0, w, h, arc);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w, h, arc, arc);
            g2.setColor(border);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            if (accent != null) {
                g2.setClip(new RoundRectangle2D.Float(0, 0, w, h, arc, arc));
                g2.setColor(accent);
                g2.fillRect(0, 0, 5, h);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class RButton extends JButton {

        private final Color base;
        private final boolean outline;
        private boolean hover, pressed;

        RButton(String text, Color base, boolean outline) {
            super(text);
            this.base = base;
            this.outline = outline;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setFont(font(Font.BOLD, 13));
            setForeground(outline ? base : Color.WHITE);
            setBorder(new EmptyBorder(10, 20, 10, 20));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; pressed = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) { pressed = true; repaint(); }
                @Override public void mouseReleased(MouseEvent e) { pressed = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int w = getWidth(), h = getHeight();
            if (pressed) g2.translate(0, 1);
            if (!isEnabled()) {
                g2.setColor(new Color(0xE2, 0xE8, 0xF0));
                g2.fillRoundRect(0, 0, w, h, 10, 10);
            } else if (outline) {
                g2.setColor(hover ? alpha(base, 22) : Color.WHITE);
                g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
                g2.setColor(hover ? base : alpha(base, 150));
                g2.setStroke(new BasicStroke(1.3f));
                g2.drawRoundRect(0, 0, w - 2, h - 2, 10, 10);
            } else {
                if (!pressed) {
                    g2.setColor(alpha(base, 45));
                    g2.fillRoundRect(0, 2, w, h - 1, 10, 10);
                }
                g2.setPaint(new GradientPaint(0, 0, hover ? lighten(base, 22) : lighten(base, 10),
                        0, h, hover ? lighten(base, 6) : base));
                g2.fillRoundRect(0, 0, w, h - 1, 10, 10);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class NavButton extends JButton {

        private final String icon;
        private boolean active, hover;

        NavButton(String icon, String text) {
            super(text);
            this.icon = icon;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.LEFT);
            setFont(font(Font.BOLD, 14));
            setForeground(new Color(0xCB, 0xD5, 0xE1));
            setBorder(new EmptyBorder(0, 52, 0, 10));
            setPreferredSize(new Dimension(210, 44));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        void setActive(boolean a) {
            active = a;
            setForeground(a ? Color.WHITE : new Color(0xCB, 0xD5, 0xE1));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int w = getWidth(), h = getHeight();
            if (active) {
                g2.setColor(new Color(255, 255, 255, 22));
                g2.fillRoundRect(0, 0, w, h, 12, 12);
                g2.setColor(new Color(0x81, 0x8C, 0xF8));
                g2.fillRoundRect(0, 11, 4, h - 22, 4, 4);
            } else if (hover) {
                g2.setColor(new Color(255, 255, 255, 12));
                g2.fillRoundRect(0, 0, w, h, 12, 12);
            }
            icon(g2, icon, 20, (h - 20) / 2, 20,
                    active ? new Color(0xA5, 0xB4, 0xFC) : new Color(0x94, 0xA3, 0xB8));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Soft pill with a colour dot. */
    static class Badge extends JLabel {

        private final Color c;

        Badge(String text, Color c) {
            super(text, SwingConstants.LEFT);
            this.c = c;
            setFont(font(Font.BOLD, 11));
            setForeground(c.darker());
            setBorder(new EmptyBorder(5, 22, 5, 12));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            g2.setColor(alpha(c, 30));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.setColor(c);
            g2.fillOval(10, getHeight() / 2 - 3, 6, 6);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class Avatar extends JComponent {

        private String initials = "?";
        private final int size;

        Avatar(int size) {
            this.size = size;
            Dimension d = new Dimension(size, size);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
        }

        void setInitials(String s) {
            initials = s;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            g2.setPaint(new GradientPaint(0, 0, new Color(0x63, 0x66, 0xF1), size, size, PURPLE));
            g2.fillOval(0, 0, size, size);
            g2.setColor(Color.WHITE);
            g2.setFont(font(Font.BOLD, size / 3 + 1));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(initials, (size - fm.stringWidth(initials)) / 2,
                    (size + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    /** Brand mark: rounded gradient square with the letter A. */
    static class Logo extends JComponent {

        private final int size;

        Logo(int size) {
            this.size = size;
            Dimension d = new Dimension(size, size);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            g2.setPaint(new GradientPaint(0, 0, new Color(0x81, 0x8C, 0xF8), size, size, new Color(0x7C, 0x3A, 0xED)));
            g2.fillRoundRect(0, 0, size, size, size / 3, size / 3);
            g2.setColor(Color.WHITE);
            g2.setFont(font(Font.BOLD, size / 2 + 2));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString("A", (size - fm.stringWidth("A")) / 2, (size + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    /** Tinted rounded square holding a line icon. */
    static class IconBadge extends JComponent {

        private final String ic;
        private final Color c;
        private final int size;

        IconBadge(String ic, Color c, int size) {
            this.ic = ic;
            this.c = c;
            this.size = size;
            Dimension d = new Dimension(size, size);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            g2.setColor(alpha(c, 30));
            g2.fillRoundRect(0, 0, size, size, size * 3 / 10, size * 3 / 10);
            icon(g2, ic, size / 4, size / 4, size / 2, c);
            g2.dispose();
        }
    }

    static class Dot extends JComponent {

        private final Color c;

        Dot(Color c, int size) {
            this.c = c;
            Dimension d = new Dimension(size, size);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            g2.setColor(c);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
            g2.dispose();
        }
    }

    static void paintFieldBg(Graphics2D g2, JComponent c, boolean focused) {
        g2.setColor(new Color(0xF8, 0xFA, 0xFC));
        g2.fillRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 10, 10);
        if (focused) {
            g2.setColor(alpha(ACCENT, 40));
            g2.setStroke(new BasicStroke(4f));
            g2.drawRoundRect(1, 1, c.getWidth() - 3, c.getHeight() - 3, 10, 10);
        }
        g2.setColor(focused ? ACCENT : new Color(0xCB, 0xD5, 0xE1));
        g2.setStroke(new BasicStroke(focused ? 1.6f : 1.1f));
        g2.drawRoundRect(1, 1, c.getWidth() - 3, c.getHeight() - 3, 10, 10);
    }

    /** Draws the leading icon and, when the field is empty, the placeholder text. */
    static void paintHint(Graphics2D g2, JComponent c, String ic, String hint, boolean empty, boolean focused) {
        if (ic != null) icon(g2, ic, 15, (c.getHeight() - 18) / 2, 18, focused ? ACCENT : SOFT);
        if (empty && hint != null && !hint.isEmpty()) {
            g2.setFont(font(Font.PLAIN, 14));
            g2.setColor(new Color(0xA0, 0xAE, 0xC0));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(hint, 44, (c.getHeight() + fm.getAscent() - fm.getDescent()) / 2);
        }
    }

    static void styleField(JTextComponent f) {
        f.setOpaque(false);
        f.setFont(font(Font.PLAIN, 14));
        f.setForeground(TEXT);
        f.setCaretColor(ACCENT);
        f.setBorder(new EmptyBorder(11, 14, 11, 14));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        f.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    static class Field extends JTextField {

        private boolean focused;
        private String hint, ic;

        Field(int cols) {
            super(cols);
            styleField(this);
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { focused = true; repaint(); }
                @Override public void focusLost(FocusEvent e) { focused = false; repaint(); }
            });
        }

        /** Adds a leading icon and placeholder text (used on the sign-in screen). */
        Field withHint(String hint, String icon) {
            this.hint = hint;
            this.ic = icon;
            setBorder(new EmptyBorder(13, 44, 13, 14));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            paintFieldBg(g2, this, focused);
            paintHint(g2, this, ic, hint, getText().isEmpty(), focused);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class PassField extends JPasswordField {

        private boolean focused;
        private String hint, ic;

        PassField(int cols) {
            super(cols);
            styleField(this);
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { focused = true; repaint(); }
                @Override public void focusLost(FocusEvent e) { focused = false; repaint(); }
            });
        }

        PassField withHint(String hint, String icon) {
            this.hint = hint;
            this.ic = icon;
            setBorder(new EmptyBorder(13, 44, 13, 14));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            paintFieldBg(g2, this, focused);
            paintHint(g2, this, ic, hint, getPassword().length == 0, focused);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Animated donut: moved in / paid / pending on a light track. */
    static class DonutChart extends JComponent {

        private final int total;
        private final int[] parts;
        private final Color[] colors;
        private final String big, small;
        private float progress = 0f;

        DonutChart(int total, int[] parts, Color[] colors, String big, String small) {
            this.total = Math.max(1, total);
            this.parts = parts;
            this.colors = colors;
            this.big = big;
            this.small = small;
            Dimension d = new Dimension(170, 170);
            setPreferredSize(d);
            setMinimumSize(d);
            javax.swing.Timer t = new javax.swing.Timer(16, null);
            t.addActionListener(e -> {
                progress += 0.05f;
                if (progress >= 1f) {
                    progress = 1f;
                    t.stop();
                }
                repaint();
            });
            t.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int size = Math.min(getWidth(), getHeight()) - 24;
            int x = (getWidth() - size) / 2, y = (getHeight() - size) / 2;
            float thick = 16f;
            g2.setStroke(new BasicStroke(thick, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(0xEE, 0xF2, 0xF7));
            g2.drawOval(x, y, size, size);
            float ease = 1f - (float) Math.pow(1f - progress, 3);
            double start = 90;
            for (int i = 0; i < parts.length; i++) {
                double ext = -360.0 * parts[i] / total * ease;
                if (parts[i] == 0) continue;
                g2.setColor(colors[i]);
                g2.draw(new Arc2D.Double(x, y, size, size, start, ext, Arc2D.OPEN));
                start += ext;
            }
            g2.setColor(TEXT);
            g2.setFont(font(Font.BOLD, 30));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(big, (getWidth() - fm.stringWidth(big)) / 2, getHeight() / 2 + 6);
            g2.setColor(MUTED);
            g2.setFont(font(Font.PLAIN, 12));
            fm = g2.getFontMetrics();
            g2.drawString(small, (getWidth() - fm.stringWidth(small)) / 2, getHeight() / 2 + 26);
            g2.dispose();
        }
    }

    /** Animated stacked bar: moved in + paid + pending. */
    static class OccupancyBar extends JComponent {

        private final int total;
        private final int[] parts;
        private final Color[] colors;
        private float progress = 0f;

        OccupancyBar(int total, int[] parts, Color[] colors, int height) {
            this.total = Math.max(1, total);
            this.parts = parts;
            this.colors = colors;
            setPreferredSize(new Dimension(100, height));
            javax.swing.Timer t = new javax.swing.Timer(16, null);
            t.addActionListener(e -> {
                progress += 0.06f;
                if (progress >= 1f) {
                    progress = 1f;
                    t.stop();
                }
                repaint();
            });
            t.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int w = getWidth(), h = getHeight();
            g2.setColor(new Color(0xEE, 0xF2, 0xF7));
            g2.fillRoundRect(0, 0, w, h, h, h);
            g2.setClip(new RoundRectangle2D.Float(0, 0, w, h, h, h));
            float ease = 1f - (float) Math.pow(1f - progress, 3);
            int x = 0;
            for (int i = 0; i < parts.length; i++) {
                int pw = (int) (w * (float) parts[i] / total * ease);
                g2.setColor(colors[i]);
                g2.fillRect(x, 0, pw, h);
                x += pw;
            }
            g2.dispose();
        }
    }

    /** Booked -> Deposit paid -> Moved in progress tracker. */
    static class Stepper extends JComponent {

        private final String[] steps = {"Booked", "Deposit paid", "Moved in"};
        private final int cur;
        private final Color color;

        Stepper(int cur, Color color) {
            this.cur = cur;
            this.color = color;
            Dimension d = new Dimension(380, 54);
            setPreferredSize(d);
            setMaximumSize(d);
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int margin = 44, r = 10, y = 14;
            int span = getWidth() - 2 * margin;
            for (int i = 0; i < steps.length; i++) {
                int x = margin + i * span / (steps.length - 1);
                if (i < steps.length - 1) {
                    int nx = margin + (i + 1) * span / (steps.length - 1);
                    g2.setColor(i < cur ? color : LINE);
                    g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(x + r + 2, y, nx - r - 2, y);
                }
            }
            for (int i = 0; i < steps.length; i++) {
                int x = margin + i * span / (steps.length - 1);
                boolean done = i <= cur;
                if (done) {
                    g2.setColor(color);
                    g2.fillOval(x - r, y - r, r * 2, r * 2);
                    icon(g2, "check", x - 6, y - 6, 12, Color.WHITE);
                } else {
                    g2.setColor(Color.WHITE);
                    g2.fillOval(x - r, y - r, r * 2, r * 2);
                    g2.setColor(new Color(0xCB, 0xD5, 0xE1));
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawOval(x - r + 1, y - r + 1, r * 2 - 2, r * 2 - 2);
                }
                g2.setFont(font(i == cur ? Font.BOLD : Font.PLAIN, 11));
                g2.setColor(done ? TEXT : SOFT);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(steps[i], x - fm.stringWidth(steps[i]) / 2, y + r + 16);
            }
            g2.dispose();
        }
    }

    /** Thin bar that drains as the deposit deadline approaches. */
    static class TimerBar extends JComponent {

        private final Booking booking;

        TimerBar(Booking b) {
            this.booking = b;
            Dimension d = new Dimension(260, 6);
            setPreferredSize(d);
            setMaximumSize(d);
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int w = getWidth(), h = getHeight();
            double frac = (booking.paymentDeadline - System.currentTimeMillis())
                    / (double) HostelBookingSystem.PAYMENT_WINDOW_MS;
            frac = Math.max(0, Math.min(1, frac));
            g2.setColor(new Color(0xEE, 0xF2, 0xF7));
            g2.fillRoundRect(0, 0, w, h, h, h);
            g2.setColor(frac < 0.25 ? RED : AMBER);
            g2.fillRoundRect(0, 0, (int) (w * frac), h, h, h);
            g2.dispose();
        }
    }

    /** Live "pay within mm:ss" label + draining bar bound to a pending booking. */
    static class Countdown {

        final JLabel label;
        final TimerBar bar;
        final Booking booking;

        Countdown(JLabel label, TimerBar bar, Booking booking) {
            this.label = label;
            this.bar = bar;
            this.booking = booking;
            update();
        }

        void update() {
            if (!booking.status.equals(Booking.PENDING)) {
                label.setText("");
                bar.setVisible(false);
                return;
            }
            long ms = booking.paymentDeadline - System.currentTimeMillis();
            label.setText("Deposit due in " + fmt(ms));
            label.setForeground(ms < 15000 ? RED : AMBER);
            bar.repaint();
        }
    }

    /** Notification card, top-right. */
    static class Toast extends JComponent {

        String text = "";
        Color color = GREEN;

        Toast() {
            setOpaque(false);
            setVisible(false);
        }

        @Override
        public boolean contains(int x, int y) {
            return false;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (text.isEmpty()) return;
            Graphics2D g2 = aa(g);
            g2.setFont(font(Font.BOLD, 13));
            FontMetrics fm = g2.getFontMetrics();
            int w = Math.min(fm.stringWidth(text) + 84, getWidth() - 40), h = 54;
            int x = getWidth() - w - 28, y = 22;
            drawShadow(g2, x, y, w, h, 14);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(x, y, w, h, 14, 14);
            g2.setColor(LINE);
            g2.drawRoundRect(x, y, w - 1, h - 1, 14, 14);
            g2.setColor(alpha(color, 30));
            g2.fillOval(x + 14, y + h / 2 - 14, 28, 28);
            g2.setColor(color);
            g2.fillOval(x + 22, y + h / 2 - 6, 12, 12);
            g2.setColor(TEXT);
            String shown = text;
            while (fm.stringWidth(shown) > w - 84 && shown.length() > 4)
                shown = shown.substring(0, shown.length() - 2);
            if (!shown.equals(text)) shown += "\u2026";
            g2.drawString(shown, x + 54, y + (h + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    /** Timeline row for the audit trail. */
    static class ActivityRenderer extends JPanel implements ListCellRenderer<String> {

        private final JLabel time = new JLabel();
        private final JLabel msg = new JLabel();
        private Color dot = MUTED;

        ActivityRenderer() {
            super(new BorderLayout(10, 0));
            setOpaque(false);
            setBorder(new EmptyBorder(0, 4, 0, 4));
            time.setFont(font(Font.PLAIN, 12));
            time.setForeground(SOFT);
            time.setPreferredSize(new Dimension(58, 30));
            msg.setFont(font(Font.PLAIN, 13));
            msg.setForeground(TEXT);
            JComponent dotBox = new JComponent() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = aa(g);
                    g2.setColor(LINE);
                    g2.fillRect(9, 0, 2, getHeight());
                    g2.setColor(Color.WHITE);
                    g2.fillOval(4, getHeight() / 2 - 6, 12, 12);
                    g2.setColor(dot);
                    g2.fillOval(6, getHeight() / 2 - 4, 8, 8);
                    g2.dispose();
                }
            };
            dotBox.setPreferredSize(new Dimension(20, 30));
            add(dotBox, BorderLayout.WEST);
            JPanel right = new JPanel(new BorderLayout(10, 0));
            right.setOpaque(false);
            right.add(time, BorderLayout.WEST);
            right.add(msg, BorderLayout.CENTER);
            add(right, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value,
                                                      int index, boolean sel, boolean focus) {
            int i = value.indexOf(" - ");
            time.setText(i > 0 ? value.substring(0, i) : "");
            String m = i > 0 ? value.substring(i + 3) : value;
            msg.setText(m);
            String l = m.toLowerCase();
            if (l.startsWith("booked")) dot = GREEN;
            else if (l.startsWith("payment")) dot = ACCENT;
            else if (l.startsWith("moved in")) dot = PURPLE;
            else if (l.startsWith("switched")) dot = CYAN;
            else if (l.startsWith("cancelled") || l.startsWith("deleted") || l.contains("failed")
                    || l.contains("rejected") || l.contains("dropped")) dot = RED;
            else if (l.contains("released") || l.contains("deadline") || l.startsWith("duplicate")) dot = AMBER;
            else dot = SOFT;
            return this;
        }
    }

    /** Scroll content that always matches the viewport width. */
    static class ScrollBody extends JPanel implements Scrollable {
        ScrollBody() { super(new BorderLayout()); setOpaque(false); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 18; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 120; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    public Main() {

        setTitle("Aurora University - Hostel Booking Portal");
        setSize(1280, 820);
        setMinimumSize(new Dimension(900, 600)); // Allows smaller screens
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        root.add(buildLogin(), "login");
        root.add(buildShell(), "app");
        setContentPane(root);

        toast = new Toast();
        setGlassPane(toast);

        showPage("dashboard");
        refreshAll();
        rootLayout.show(root, "login");

        new javax.swing.Timer(1000, e -> {
            clockLabel.setText(LocalDateTime.now().format(CLOCK));
            for (Countdown c : countdowns) c.update();
            List<String> h = system.getHistory();
            String top = h.isEmpty() ? "" : h.get(0);
            if (!top.equals(lastTopHistory)) refreshAll();
        }).start();
    }

    // =========================================================
    // LOGIN SCREEN
    // =========================================================

    /** Benefit line on the brand panel: check badge, bold title, muted subtitle. */
    private JPanel benefitRow(String title, String sub) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        row.add(new IconBadge("check", new Color(0xA5, 0xB4, 0xFC), 32));

        JPanel t = new JPanel(new GridLayout(2, 1, 0, 1));
        t.setOpaque(false);
        JLabel a = new JLabel(title);
        a.setFont(font(Font.BOLD, 14));
        a.setForeground(Color.WHITE);
        JLabel b = new JLabel(sub);
        b.setFont(font(Font.PLAIN, 12));
        b.setForeground(alpha(Color.WHITE, 150));
        t.add(a);
        t.add(b);
        row.add(t);
        return row;
    }

    private JPanel statBlock(String num, String caption) {
        JPanel p = new JPanel(new GridLayout(2, 1, 0, 2));
        p.setOpaque(false);
        JLabel n = new JLabel(num);
        n.setFont(font(Font.BOLD, 24));
        n.setForeground(Color.WHITE);
        JLabel c = new JLabel(caption);
        c.setFont(font(Font.PLAIN, 12));
        c.setForeground(alpha(Color.WHITE, 140));
        p.add(n);
        p.add(c);
        return p;
    }

    /** Horizontal rule with centred caption. */
    private JPanel orDivider(String text) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        JPanel l1 = new JPanel();
        l1.setBackground(LINE);
        l1.setPreferredSize(new Dimension(10, 1));
        JPanel l2 = new JPanel();
        l2.setBackground(LINE);
        l2.setPreferredSize(new Dimension(10, 1));
        JLabel t = new JLabel(text);
        t.setFont(font(Font.BOLD, 11));
        t.setForeground(SOFT);
        t.setBorder(new EmptyBorder(0, 12, 0, 12));
        p.add(l1, g);
        g.weightx = 0;
        p.add(t, g);
        g.weightx = 1;
        p.add(l2, g);
        return p;
    }

    private JPanel buildLogin() {

        JPanel p = new JPanel(new GridLayout(1, 2));

        // ================= LEFT: brand panel =================
        JPanel left = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = aa(g);
                int w = getWidth(), h = getHeight();
                g2.setPaint(new GradientPaint(0, 0, NAVY, w, h, new Color(0x3B, 0x33, 0x9A)));
                g2.fillRect(0, 0, w, h);

                // soft glows
                g2.setColor(new Color(129, 140, 248, 34));
                g2.fillOval(w - 300, -170, 480, 480);
                g2.setColor(new Color(124, 58, 237, 26));
                g2.fillOval(-160, h / 2 - 60, 380, 380);
                g2.setColor(new Color(56, 189, 248, 14));
                g2.fillOval(w / 2, h - 300, 360, 360);

                // subtle dot grid (top right)
                g2.setColor(new Color(255, 255, 255, 30));
                for (int gx = 0; gx < 8; gx++)
                    for (int gy = 0; gy < 5; gy++)
                        g2.fillOval(w - 190 + gx * 18, 60 + gy * 18, 3, 3);

                // campus skyline
                Random rnd = new Random(7);
                int[] bw = {110, 80, 140, 96, 120, 90};
                int[] bh = {150, 210, 130, 180, 120, 165};
                int x = 24;
                for (int i = 0; i < bw.length; i++) {
                    g2.setColor(new Color(255, 255, 255, 9));
                    g2.fillRoundRect(x, h - bh[i], bw[i], bh[i] + 20, 12, 12);
                    for (int wy = h - bh[i] + 18; wy < h - 20; wy += 28)
                        for (int wx = x + 14; wx < x + bw[i] - 18; wx += 24) {
                            boolean lit = rnd.nextInt(4) == 0;
                            g2.setColor(lit ? new Color(253, 230, 138, 140) : new Color(255, 255, 255, 18));
                            g2.fillRoundRect(wx, wy, 10, 14, 3, 3);
                        }
                    x += bw[i] + 14;
                }
                g2.dispose();
            }
        };
        left.setBorder(new EmptyBorder(48, 60, 36, 60));

        JPanel brandRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        brandRow.setOpaque(false);
        brandRow.add(new Logo(42));
        JPanel bt = new JPanel(new GridLayout(2, 1));
        bt.setOpaque(false);
        JLabel bn = new JLabel("Aurora University");
        bn.setFont(font(Font.BOLD, 17));
        bn.setForeground(Color.WHITE);
        JLabel bs = new JLabel("Student Housing");
        bs.setFont(font(Font.PLAIN, 12));
        bs.setForeground(alpha(Color.WHITE, 150));
        bt.add(bn);
        bt.add(bs);
        brandRow.add(bt);
        left.add(brandRow, BorderLayout.NORTH);

        JPanel hero = new JPanel();
        hero.setOpaque(false);
        hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
        hero.add(Box.createVerticalGlue());
        hero.add(label("HOSTEL BOOKING PORTAL", 12, Font.BOLD, new Color(0xA5, 0xB4, 0xFC)));
        hero.add(Box.createVerticalStrut(14));
        hero.add(label("Your home on campus,", 40, Font.BOLD, Color.WHITE));
        hero.add(label("reserved in seconds.", 40, Font.BOLD, new Color(0xC7, 0xD2, 0xFE)));
        hero.add(Box.createVerticalStrut(16));
        hero.add(label("<html><div style='width:390px'>Browse live availability across every residence block, "
                + "reserve your room and manage your stay, all in one place.</div></html>",
                15, Font.PLAIN, alpha(Color.WHITE, 200)));
        hero.add(Box.createVerticalStrut(30));
        hero.add(benefitRow("Live room availability", "See what is free right now, updated in real time"));
        hero.add(Box.createVerticalStrut(6));
        hero.add(benefitRow("Simple, secure reservation", "Confirm your room and pay your deposit online"));
        hero.add(Box.createVerticalStrut(6));
        hero.add(benefitRow("Rooms for every need", "Single, double and shared rooms, including accessible options"));
        hero.add(Box.createVerticalStrut(30));

        JPanel stats = new JPanel(new GridLayout(1, 3, 20, 0));
        stats.setOpaque(false);
        stats.setAlignmentX(Component.LEFT_ALIGNMENT);
        stats.setMaximumSize(new Dimension(420, 52));
        stats.add(statBlock("3", "Residence blocks"));
        stats.add(statBlock("24/7", "Online booking"));
        stats.add(statBlock("Live", "Availability"));
        hero.add(stats);
        hero.add(Box.createVerticalGlue());
        left.add(hero, BorderLayout.CENTER);

        JLabel foot = new JLabel("\u00A9 2026 Aurora University \u00B7 Hostel Office");
        foot.setFont(font(Font.PLAIN, 12));
        foot.setForeground(alpha(Color.WHITE, 120));
        left.add(foot, BorderLayout.SOUTH);

        // ================= RIGHT: sign-in form =================
        JPanel right = new JPanel(new GridBagLayout());
        right.setBackground(Color.WHITE);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setPreferredSize(new Dimension(400, 660));

        Badge welcome = new Badge("WELCOME BACK", ACCENT);
        welcome.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(welcome);
        form.add(Box.createVerticalStrut(16));
        form.add(label("Sign in to your account", 28, Font.BOLD, TEXT));
        form.add(Box.createVerticalStrut(6));
        form.add(label("Enter your student ID and password to continue", 14, Font.PLAIN, MUTED));
        form.add(Box.createVerticalStrut(28));

        form.add(label("Student ID", 12, Font.BOLD, TEXT));
        form.add(Box.createVerticalStrut(6));
        loginField = new Field(20).withHint("e.g. AU001", "users");
        form.add(loginField);
        form.add(Box.createVerticalStrut(16));

        form.add(label("Password", 12, Font.BOLD, TEXT));
        form.add(Box.createVerticalStrut(6));
        loginPass = new PassField(20).withHint("Enter your password", "lock");
        form.add(loginPass);

        JCheckBox show = new JCheckBox("Show password");
        show.setOpaque(false);
        show.setFont(font(Font.PLAIN, 12));
        show.setForeground(MUTED);
        show.setFocusPainted(false);
        show.setAlignmentX(Component.LEFT_ALIGNMENT);
        char echo = loginPass.getEchoChar();
        show.addActionListener(e -> loginPass.setEchoChar(show.isSelected() ? (char) 0 : echo));
        form.add(Box.createVerticalStrut(10));
        form.add(show);

        loginError = label(" ", 12, Font.BOLD, RED);
        form.add(Box.createVerticalStrut(8));
        form.add(loginError);
        form.add(Box.createVerticalStrut(8));

        RButton go = new RButton("Sign in  \u2192", ACCENT, false);
        go.setFont(font(Font.BOLD, 15));
        go.setBorder(new EmptyBorder(14, 20, 14, 20));
        go.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        form.add(go);

        form.add(Box.createVerticalStrut(28));
        form.add(orDivider("QUICK DEMO ACCESS"));
        form.add(Box.createVerticalStrut(6));
        form.add(label("Password for all demo accounts: " + DEMO_PW, 11, Font.PLAIN, SOFT));
        form.add(Box.createVerticalStrut(12));

        chipsPanel = new JPanel(new GridLayout(0, 3, 8, 8));
        chipsPanel.setOpaque(false);
        chipsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(chipsPanel);

        form.add(Box.createVerticalStrut(26));
        JPanel reg = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        reg.setOpaque(false);
        reg.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel newTo = new JLabel("New to the portal?");
        newTo.setFont(font(Font.PLAIN, 13));
        newTo.setForeground(MUTED);
        reg.add(newTo);
        reg.add(link("Create an account", () -> showAddStudentDialog()));
        form.add(reg);

        right.add(form);
        p.add(left);
        p.add(right);

        ActionListener doLogin = e -> {
            String id = loginField.getText().trim();
            String pw = new String(loginPass.getPassword());
            if (id.isEmpty() || pw.isEmpty()) {
                loginError.setText("Please enter your student ID and password.");
                return;
            }
            if (!signIn(id, pw)) loginError.setText("Invalid student ID or password.");
        };

        go.addActionListener(doLogin);
        loginField.addActionListener(doLogin);
        loginPass.addActionListener(doLogin);

        rebuildChips();
        return p;
    }

    private void rebuildChips() {

        chipsPanel.removeAll();
        int n = 0;

        for (Student s : system.getStudents()) {
            if (n++ >= 6) break;
            RButton b = new RButton(s.id + "  \u00B7  " + s.name.split(" ")[0], ACCENT, true);
            b.setFont(font(Font.BOLD, 12));
            b.setBorder(new EmptyBorder(9, 4, 9, 4));
            b.addActionListener(e -> {
                if (!signIn(s.id, DEMO_PW)) {
                    loginField.setText(s.id);
                    loginPass.setText("");
                    loginPass.requestFocusInWindow();
                    loginError.setText("Enter the password for " + s.id + ".");
                }
            });
            chipsPanel.add(b);
        }

        chipsPanel.revalidate();
        chipsPanel.repaint();
    }

    private boolean signIn(String id, String password) {

        if (!system.login(id, password))
            return false;

        Student s = system.getCurrentStudent();
        system.addHistory("Signed in: " + s.id);

        loginField.setText("");
        loginPass.setText("");
        loginError.setText(" ");
        filterBlock = "A";
        filterType = "All";

        updateUser();
        refreshAll();
        showPage("dashboard");
        rootLayout.show(root, "app");
        showToast("Welcome back, " + s.name.split(" ")[0], GREEN);
        return true;
    }

    private void signOut() {

        Student s = system.getCurrentStudent();
        if (s != null) system.addHistory("Signed out: " + s.id);

        system.logout();
        pendingRequestId = null;
        updateUser();
        refreshAll();
        rootLayout.show(root, "login");
    }

    // =========================================================
    // APP SHELL
    // =========================================================

    private JPanel buildShell() {

        JPanel shell = new JPanel(new BorderLayout());
        shell.add(buildSidebar(), BorderLayout.WEST);

        JPanel right = new JPanel(new BorderLayout());
        right.setBackground(BG);
        right.add(buildHeader(), BorderLayout.NORTH);

        pages.setOpaque(false);
        pages.setBorder(new EmptyBorder(4, 32, 24, 32));

        dashboardPanel = pagePanel();
        roomsPanel = pagePanel();
        bookingsPanel = pagePanel();
        studentsPanel = pagePanel();
        activityPanel = pagePanel();

        pages.add(dashboardPanel, "dashboard");
        pages.add(roomsPanel, "rooms");
        pages.add(bookingsPanel, "mybooking");
        pages.add(studentsPanel, "students");
        pages.add(activityPanel, "activity");

        right.add(pages, BorderLayout.CENTER);
        shell.add(right, BorderLayout.CENTER);
        return shell;
    }

    private JPanel pagePanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        return p;
    }

    private JPanel buildSidebar() {

        JPanel side = new JPanel();
        side.setBackground(NAVY);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(new EmptyBorder(26, 20, 22, 20));
        side.setPreferredSize(new Dimension(210, 0)); // Narrower for small screens

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        brand.setOpaque(false);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        brand.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        brand.add(new Logo(38));
        JPanel bt = new JPanel(new GridLayout(2, 1));
        bt.setOpaque(false);
        JLabel b1 = new JLabel("Aurora");
        b1.setFont(font(Font.BOLD, 17));
        b1.setForeground(Color.WHITE);
        JLabel b2 = new JLabel("Hostel Portal");
        b2.setFont(font(Font.PLAIN, 12));
        b2.setForeground(SOFT);
        bt.add(b1);
        bt.add(b2);
        brand.add(bt);
        side.add(brand);
        side.add(Box.createVerticalStrut(32));
        side.add(label("MENU", 11, Font.BOLD, new Color(0x64, 0x74, 0x8B)));
        side.add(Box.createVerticalStrut(10));

        addNav(side, "dash", "Dashboard", "dashboard");
        addNav(side, "rooms", "Rooms", "rooms");
        addNav(side, "key", "My Reservation", "mybooking");
        addNav(side, "users", "Students", "students");
        addNav(side, "list", "Audit Trail", "activity");

        side.add(Box.createVerticalGlue());

        RoundedPanel user = new RoundedPanel(14, NAVY_LIGHT, false);
        user.setBorderColor(new Color(255, 255, 255, 18));
        user.setLayout(new BorderLayout(12, 0));
        user.setBorder(new EmptyBorder(12, 12, 12, 12));
        user.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
        user.setAlignmentX(Component.LEFT_ALIGNMENT);

        sideAvatar = new Avatar(40);
        sideName = new JLabel("Guest");
        sideName.setFont(font(Font.BOLD, 13));
        sideName.setForeground(Color.WHITE);
        sideId = new JLabel("-");
        sideId.setFont(font(Font.PLAIN, 12));
        sideId.setForeground(SOFT);

        JPanel names = new JPanel(new GridLayout(2, 1));
        names.setOpaque(false);
        names.add(sideName);
        names.add(sideId);

        user.add(sideAvatar, BorderLayout.WEST);
        user.add(names, BorderLayout.CENTER);

        side.add(user);
        side.add(Box.createVerticalStrut(10));

        NavButton out = new NavButton("out", "Sign out");
        out.addActionListener(e -> signOut());
        side.add(out);
        return side;
    }

    private void addNav(JPanel side, String icon, String text, String key) {
        NavButton b = new NavButton(icon, text);
        b.addActionListener(e -> showPage(key));
        navButtons.put(key, b);
        side.add(b);
        side.add(Box.createVerticalStrut(4));
    }

    private JPanel buildHeader() {

        JPanel h = new JPanel(new BorderLayout());
        h.setOpaque(false);
        h.setBorder(new EmptyBorder(26, 32, 16, 32));

        pageTitle = label("Dashboard", 28, Font.BOLD, TEXT);
        pageSubtitle = label(" ", 13, Font.PLAIN, MUTED);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(pageTitle);
        titles.add(Box.createVerticalStrut(3));
        titles.add(pageSubtitle);

        RoundedPanel chip = new RoundedPanel(30, Color.WHITE, false);
        chip.setLayout(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        chip.setBorder(new EmptyBorder(6, 16, 6, 8));

        clockLabel = new JLabel(LocalDateTime.now().format(CLOCK));
        clockLabel.setFont(font(Font.PLAIN, 12));
        clockLabel.setForeground(MUTED);

        headerName = new JLabel("Guest");
        headerName.setFont(font(Font.BOLD, 13));
        headerName.setForeground(TEXT);
        headerId = new JLabel("-");
        headerId.setFont(font(Font.PLAIN, 11));
        headerId.setForeground(SOFT);
        JPanel hn = new JPanel(new GridLayout(2, 1));
        hn.setOpaque(false);
        hn.add(headerName);
        hn.add(headerId);

        headerAvatar = new Avatar(34);

        chip.add(clockLabel);
        chip.add(hn);
        chip.add(headerAvatar);

        JPanel chipWrap = new JPanel(new GridBagLayout());
        chipWrap.setOpaque(false);
        chipWrap.add(chip);

        h.add(titles, BorderLayout.WEST);
        h.add(chipWrap, BorderLayout.EAST);
        return h;
    }

    private void showPage(String key) {
        pageLayout.show(pages, key);
        for (Map.Entry<String, NavButton> e : navButtons.entrySet())
            e.getValue().setActive(e.getKey().equals(key));
        String[] t = TITLES.get(key);
        pageTitle.setText(t[0]);
        pageSubtitle.setText(t[1]);
    }

    private void updateUser() {

        Student s = system.getCurrentStudent();
        String name = s == null ? "Guest" : s.name;
        String init = s == null ? "?" : initials(s.name);
        String sub = s == null ? "-" : s.id + "  \u00B7  " + (s.accessibilityRequired ? "Accessibility" : s.department);

        headerName.setText(name);
        headerId.setText(s == null ? "-" : s.id);
        headerAvatar.setInitials(init);
        sideName.setText(name);
        sideId.setText(sub);
        sideAvatar.setInitials(init);
    }

    private void refreshAll() {

        List<String> h = system.getHistory();
        lastTopHistory = h.isEmpty() ? "" : h.get(0);
        countdowns.clear();

        rebuildDashboard();
        rebuildRooms();
        rebuildBookings();
        rebuildStudents();
        rebuildActivity();
        rebuildChips();
    }

    private void showToast(String text, Color color) {

        toast.text = text;
        toast.color = color;
        toast.setVisible(true);
        toast.repaint();

        if (toastTimer != null) toastTimer.stop();
        toastTimer = new javax.swing.Timer(3400, e -> toast.setVisible(false));
        toastTimer.setRepeats(false);
        toastTimer.start();
    }

    // =========================================================
    // CARD HELPERS
    // =========================================================

    private RoundedPanel card() {
        RoundedPanel c = new RoundedPanel(16, Color.WHITE, true);
        c.setBorder(new EmptyBorder(22, 24, 28, 24));
        return c;
    }

    private RoundedPanel statCard(String ic, String title, String value, String sub, Color color) {
        RoundedPanel c = card();
        c.setLayout(new BorderLayout(16, 0));
        c.setBorder(new EmptyBorder(18, 20, 24, 16));
        JPanel wrap = new JPanel(new GridBagLayout());
        wrap.setOpaque(false);
        wrap.add(new IconBadge(ic, color, 46));
        c.add(wrap, BorderLayout.WEST);

        JPanel t = new JPanel();
        t.setOpaque(false);
        t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        t.add(label(title, 12, Font.BOLD, MUTED));
        t.add(label(value, 30, Font.BOLD, TEXT));
        t.add(label(sub, 12, Font.PLAIN, SOFT));
        c.add(t, BorderLayout.CENTER);
        return c;
    }

    private JPanel legendItem(String text, Color c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setOpaque(false);
        p.add(new Dot(c, 10));
        JLabel l = new JLabel(text);
        l.setFont(font(Font.PLAIN, 12));
        l.setForeground(MUTED);
        p.add(l);
        return p;
    }

    /** Countdown label + draining bar, kept up to date by the 1-second ticker. */
    private JPanel countdownBlock(Booking b) {
        JPanel p = left(new JPanel());
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel l = label("", 13, Font.BOLD, AMBER);
        TimerBar bar = new TimerBar(b);
        p.add(l);
        p.add(Box.createVerticalStrut(6));
        p.add(bar);
        countdowns.add(new Countdown(l, bar, b));
        return p;
    }

    private int stepFor(String status) {
        if (status.equals(Booking.MOVED_IN)) return 2;
        if (status.equals(Booking.CONFIRMED)) return 1;
        return 0;
    }

    private JComponent activityView(int limit, boolean scroll) {

        List<String> h = system.getHistory();

        if (h.isEmpty()) {
            JLabel empty = label("No activity yet. Booking events will appear here.", 13, Font.PLAIN, MUTED);
            empty.setBorder(new EmptyBorder(10, 4, 10, 4));
            return empty;
        }

        DefaultListModel<String> m = new DefaultListModel<>();
        for (int i = 0; i < h.size() && i < limit; i++) m.addElement(h.get(i));

        JList<String> list = new JList<>(m);
        list.setCellRenderer(new ActivityRenderer());
        list.setOpaque(false);
        list.setFocusable(false);

        if (!scroll) return list;

        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    private JScrollPane scroll(JComponent inner) {
        ScrollBody wrap = new ScrollBody();
        wrap.add(inner, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(wrap);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    /** {total, movedIn, paid, pending} for one block ("A","B","C") or "*" for all. */
    private int[] counts(String block) {
        int[] c = new int[4];
        for (Room r : system.getAllRooms()) {
            if (!block.equals("*") && r.block != block.charAt(0)) continue;
            c[0]++;
            Booking b = system.getActiveBookingForRoom(r.id);
            if (b == null) continue;
            if (b.status.equals(Booking.MOVED_IN)) c[1]++;
            else if (b.status.equals(Booking.CONFIRMED)) c[2]++;
            else c[3]++;
        }
        return c;
    }

    // =========================================================
    // DASHBOARD
    // =========================================================

    private void rebuildDashboard() {

        JPanel p = dashboardPanel;
        p.removeAll();
        p.setLayout(new BorderLayout());

        Student cur = system.getCurrentStudent();

        int[] all = counts("*");
        int booked = all[1] + all[2] + all[3];
        int avail = all[0] - booked;

        // Responsive stat cards
        JPanel stats = new JPanel(new GridBagLayout());
        stats.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.insets = new Insets(0, 6, 0, 6);

        stats.add(statCard("bed", "AVAILABLE ROOMS", String.valueOf(avail), "of " + all[0] + " total", GREEN), gbc);
        stats.add(statCard("clock", "PENDING PAYMENT", String.valueOf(all[3]), "awaiting deposit", AMBER), gbc);
        stats.add(statCard("lock", "RESERVED (PAID)", String.valueOf(all[2]), "deposit received", RED), gbc);
        stats.add(statCard("home", "MOVED IN", String.valueOf(all[1]), "physically occupied", PURPLE), gbc);

        // ---------- occupancy ----------
        Color[] cols = {PURPLE, RED, AMBER};

        RoundedPanel occ = card();
        occ.setLayout(new BorderLayout(0, 14));
        JPanel occHead = new JPanel(new BorderLayout());
        occHead.setOpaque(false);
        occHead.add(label("Occupancy", 16, Font.BOLD, TEXT), BorderLayout.WEST);
        int pct = all[0] == 0 ? 0 : booked * 100 / all[0];
        occHead.add(label(all[1] + " of " + booked + " booked rooms occupied", 12, Font.PLAIN, MUTED), BorderLayout.EAST);
        occ.add(occHead, BorderLayout.NORTH);

        JPanel occBody = new JPanel(new BorderLayout(24, 0));
        occBody.setOpaque(false);
        occBody.add(new DonutChart(all[0], new int[]{all[1], all[2], all[3]}, cols, pct + "%", "reserved"),
                BorderLayout.WEST);

        JPanel east = new JPanel();
        east.setOpaque(false);
        east.setLayout(new BoxLayout(east, BoxLayout.Y_AXIS));
        JPanel legend = new JPanel(new GridLayout(2, 2, 0, 2));
        legend.setOpaque(false);
        legend.setAlignmentX(Component.LEFT_ALIGNMENT);
        legend.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        legend.add(legendItem("Moved in", PURPLE));
        legend.add(legendItem("Reserved (paid)", RED));
        legend.add(legendItem("Pending payment", AMBER));
        legend.add(legendItem("Available", new Color(0xEE, 0xF2, 0xF7)));
        east.add(legend);
        east.add(Box.createVerticalStrut(12));

        for (String bl : new String[]{"A", "B", "C"}) {
            int[] c = counts(bl);
            int free = c[0] - c[1] - c[2] - c[3];
            JPanel row = new JPanel(new BorderLayout(0, 5));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            top.add(label("Block " + bl + (bl.equals("C") ? "  \u00B7  accessible" : ""), 12, Font.BOLD, TEXT),
                    BorderLayout.WEST);
            top.add(label(free + " / " + c[0] + " free", 12, Font.PLAIN, MUTED), BorderLayout.EAST);
            row.add(top, BorderLayout.NORTH);
            row.add(new OccupancyBar(c[0], new int[]{c[1], c[2], c[3]}, cols, 8), BorderLayout.CENTER);
            east.add(row);
            east.add(Box.createVerticalStrut(8));
        }
        occBody.add(east, BorderLayout.CENTER);
        occ.add(occBody, BorderLayout.CENTER);

        // ---------- recent activity ----------
        RoundedPanel act = card();
        act.setLayout(new BorderLayout(0, 8));
        JPanel actHead = new JPanel(new BorderLayout());
        actHead.setOpaque(false);
        actHead.add(label("Recent activity", 16, Font.BOLD, TEXT), BorderLayout.WEST);
        actHead.add(link("View all", () -> showPage("activity")), BorderLayout.EAST);
        act.add(actHead, BorderLayout.NORTH);
        act.add(activityView(5, false), BorderLayout.CENTER);

        // ---------- your reservation ----------
        RoundedPanel res = card();
        res.setLayout(new BorderLayout(0, 14));
        res.add(label("Your reservation", 16, Font.BOLD, TEXT), BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        Booking mine = cur == null ? null : system.getActiveBookingForStudent(cur.id);

        if (mine == null) {

            body.add(Box.createVerticalStrut(10));
            body.add(left(new IconBadge("key", ACCENT, 52)));
            body.add(Box.createVerticalStrut(16));
            body.add(label("No active reservation", 22, Font.BOLD, TEXT));
            body.add(Box.createVerticalStrut(6));
            body.add(label("<html><div style='width:250px'>Pick a room and reserve it in a few clicks. "
                    + "You can hold one reservation at a time.</div></html>", 13, Font.PLAIN, MUTED));
            if (cur != null && cur.accessibilityRequired) {
                body.add(Box.createVerticalStrut(10));
                body.add(label("<html><div style='width:250px'>Block C ground-floor rooms are reserved for you.</div></html>",
                        13, Font.BOLD, GREEN));
            }
            body.add(Box.createVerticalStrut(22));

            RButton browse = new RButton("Browse rooms", ACCENT, false);
            browse.addActionListener(e -> showPage("rooms"));
            body.add(browse);

        } else {

            Room r = system.getRoom(mine.roomId);
            body.add(label(mine.roomId, 30, Font.BOLD, TEXT));
            body.add(Box.createVerticalStrut(2));
            body.add(label(r.type.label + "  \u00B7  " + r.floorLabel() + "  \u00B7  " + mine.duration,
                    13, Font.PLAIN, MUTED));
            body.add(label(mine.id, 12, Font.PLAIN, SOFT));
            body.add(Box.createVerticalStrut(14));

            JPanel badges = left(new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)));
            badges.setOpaque(false);
            badges.add(new Badge(mine.status, statusColor(mine.status)));
            badges.add(Box.createHorizontalStrut(8));
            badges.add(new Badge(mine.paid ? "Deposit paid" : "Deposit unpaid", mine.paid ? GREEN : AMBER));
            body.add(badges);
            body.add(Box.createVerticalStrut(16));
            body.add(new Stepper(stepFor(mine.status), statusColor(mine.status)));

            if (mine.status.equals(Booking.PENDING)) {
                body.add(Box.createVerticalStrut(8));
                body.add(countdownBlock(mine));
            }

            body.add(Box.createVerticalStrut(18));

            JPanel btns = left(new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)));
            btns.setOpaque(false);

            if (mine.status.equals(Booking.PENDING)) {
                RButton pay = new RButton("Pay deposit", GREEN, false);
                pay.addActionListener(e -> doPay(mine));
                btns.add(pay);
                btns.add(Box.createHorizontalStrut(10));
            } else if (mine.status.equals(Booking.CONFIRMED)) {
                RButton in = new RButton("Confirm move-in", PURPLE, false);
                in.addActionListener(e -> doCheckIn(mine));
                btns.add(in);
                btns.add(Box.createHorizontalStrut(10));
            }

            RButton manage = new RButton("Manage", ACCENT, true);
            manage.addActionListener(e -> showPage("mybooking"));
            btns.add(manage);
            body.add(btns);
        }

        res.add(body, BorderLayout.CENTER);

        // ---------- layout ----------
        JPanel leftCol = new JPanel(new GridBagLayout());
        leftCol.setOpaque(false);
        GridBagConstraints lc = new GridBagConstraints();
        lc.fill = GridBagConstraints.BOTH;
        lc.weightx = 1;
        lc.gridx = 0;
        lc.gridy = 0;
        lc.insets = new Insets(0, 0, 10, 0);
        leftCol.add(occ, lc);
        lc.gridy = 1;
        lc.weighty = 1;
        lc.insets = new Insets(0, 0, 0, 0);
        leftCol.add(act, lc);

        // Responsive layout logic for middle section
        JPanel mid = new JPanel(new BorderLayout());
        mid.setOpaque(false);

        if (getWidth() > 0 && getWidth() < 1100) {
            // Small screen: stack vertically
            JPanel stacked = new JPanel();
            stacked.setOpaque(false);
            stacked.setLayout(new BoxLayout(stacked, BoxLayout.Y_AXIS));
            
            occ.setMaximumSize(new Dimension(Integer.MAX_VALUE, 320));
            act.setMaximumSize(new Dimension(Integer.MAX_VALUE, 250));
            res.setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));
            
            stacked.add(occ);
            stacked.add(Box.createVerticalStrut(10));
            stacked.add(act);
            stacked.add(Box.createVerticalStrut(10));
            stacked.add(res);
            
            mid.add(scroll(stacked), BorderLayout.CENTER);
        } else {
            // Large screen: side by side
            JPanel sideBySide = new JPanel(new GridBagLayout());
            sideBySide.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.fill = GridBagConstraints.BOTH;
            gc.weighty = 1;
            gc.weightx = 0.58;
            gc.gridx = 0;
            gc.insets = new Insets(0, 0, 0, 8);
            sideBySide.add(leftCol, gc);
            gc.weightx = 0.42;
            gc.gridx = 1;
            gc.insets = new Insets(0, 8, 6, 0);
            sideBySide.add(res, gc);
            
            mid.add(sideBySide, BorderLayout.CENTER);
        }

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setOpaque(false);
        stats.setBorder(new EmptyBorder(0, 0, 4, 0));
        content.add(stats, BorderLayout.NORTH);
        content.add(mid, BorderLayout.CENTER);

        p.add(scroll(content), BorderLayout.CENTER);
        p.revalidate();
        p.repaint();
    }

    // =========================================================
    // ROOMS PAGE
    // =========================================================

    private class RoomTile extends JPanel {

        private final Room room;
        private final Booking b;
        private final boolean mine, eligible;
        private boolean hover;

        RoomTile(Room room) {

            this.room = room;
            this.b = system.getActiveBookingForRoom(room.id);

            Student cur = system.getCurrentStudent();
            this.mine = b != null && cur != null && b.studentId.equals(cur.id);
            this.eligible = cur != null && system.eligibilityProblem(cur, room) == null;

            setOpaque(false);
            setPreferredSize(new Dimension(150, 110)); // Slightly smaller
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(room.id + " - " + room.type.label + ", " + room.floorLabel());

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { onRoomClicked(RoomTile.this.room, b, mine); }
            });
        }

        private Color stateColor() {
            if (b == null) return eligible ? GREEN : SOFT;
            if (mine) return ACCENT;
            if (b.status.equals(Booking.MOVED_IN)) return PURPLE;
            return b.status.equals(Booking.CONFIRMED) ? RED : AMBER;
        }

        private String stateText() {
            if (b == null) return eligible ? "Available" : "Restricted";
            if (mine) return "Your room";
            if (b.status.equals(Booking.MOVED_IN)) return "Occupied";
            return b.status.equals(Booking.CONFIRMED) ? "Reserved" : "On hold";
        }

        @Override
        protected void paintComponent(Graphics g) {

            Graphics2D g2 = aa(g);
            int w = getWidth(), h = getHeight() - 6;
            Color sc = stateColor();
            boolean dim = b == null && !eligible;

            drawShadow(g2, 0, hover ? -1 : 0, w, h, 14);
            g2.setColor(mine ? new Color(0xEE, 0xF2, 0xFF) : Color.WHITE);
            g2.fillRoundRect(0, 0, w, h, 14, 14);

            g2.setStroke(new BasicStroke(hover || mine ? 1.8f : 1f));
            g2.setColor(hover || mine ? sc : LINE);
            g2.drawRoundRect(1, 1, w - 3, h - 3, 14, 14);

            g2.setClip(new RoundRectangle2D.Float(0, 0, w, h, 14, 14));
            g2.setColor(sc);
            g2.fillRect(0, 0, w, 4);
            g2.setClip(null);

            g2.setColor(dim ? SOFT : TEXT);
            g2.setFont(font(Font.BOLD, 21));
            g2.drawString(room.id, 16, 38);

            g2.setColor(MUTED);
            g2.setFont(font(Font.PLAIN, 12));
            g2.drawString(room.type.label, 16, 56);
            icon(g2, "bed", w - 34, 22, 18, dim ? SOFT : MUTED);
            String beds = String.valueOf(room.type.capacity);
            g2.setFont(font(Font.BOLD, 11));
            g2.drawString(beds, w - 34 + 9 - g2.getFontMetrics().stringWidth(beds) / 2, 52);

            String st = stateText();
            g2.setFont(font(Font.BOLD, 11));
            FontMetrics fm = g2.getFontMetrics();
            int pw = fm.stringWidth(st) + 30;
            g2.setColor(alpha(sc, 30));
            g2.fillRoundRect(16, h - 34, pw, 22, 22, 22);
            g2.setColor(sc);
            g2.fillOval(25, h - 26, 6, 6);
            g2.setColor(sc.darker());
            g2.drawString(st, 37, h - 19);

            g2.setColor(SOFT);
            g2.setFont(font(Font.PLAIN, 11));
            String fl = room.floorLabel();
            g2.drawString(fl, w - 16 - g2.getFontMetrics().stringWidth(fl), h - 19);

            g2.dispose();
        }
    }

    private void onRoomClicked(Room room, Booking b, boolean mine) {

        Student cur = system.getCurrentStudent();

        if (cur == null) {
            showToast("Please sign in first", AMBER);
            return;
        }

        if (b == null) {

            String problem = system.eligibilityProblem(cur, room);
            if (problem != null) {
                showToast(problem, RED);
                return;
            }

            Booking existing = system.getActiveBookingForStudent(cur.id);

            if (existing != null && !(existing.status.equals(Booking.PENDING)
                    || existing.status.equals(Booking.CONFIRMED))) {
                showToast("You have already moved in - delete your booking before booking again", AMBER);
                return;
            }

            Choice c = roomDialog(room, existing);
            if (c == null) return;

            if (existing == null) doBook(cur.id, room, c.duration, c.drop);
            else doSwitch(existing, room, c.duration);

        } else if (mine) {
            showPage("mybooking");
        } else {
            showToast(room.id + " is not available", RED);
        }
    }

    /** Labelled dropdown used by the Rooms page filters. */
    private JPanel dropdown(String title, String[] labels, String[] values, String selected,
                            Consumer<String> onPick) {

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.add(label(title, 11, Font.BOLD, SOFT));

        JComboBox<String> combo = new JComboBox<>(labels);
        combo.setFont(font(Font.BOLD, 13));
        combo.setBackground(Color.WHITE);
        combo.setForeground(TEXT);
        combo.setPreferredSize(new Dimension(150, 38));
        combo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        for (int i = 0; i < values.length; i++)
            if (values[i].equals(selected)) combo.setSelectedIndex(i);

        // listener added after the initial selection so it doesn't fire during build
        combo.addActionListener(e -> {
            int i = combo.getSelectedIndex();
            if (i >= 0) onPick.accept(values[i]);
        });
        row.add(combo);
        return row;
    }

    /** One horizontal row of room tiles for a single room type, with a label on the left. */
    private JPanel typeRow(Room.Type t, List<Room> rooms) {

        JPanel row = new JPanel(new BorderLayout(18, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel lab = new JPanel(new GridBagLayout());
        lab.setOpaque(false);
        lab.setPreferredSize(new Dimension(118, 110));

        JPanel in = new JPanel();
        in.setOpaque(false);
        in.setLayout(new BoxLayout(in, BoxLayout.Y_AXIS));
        in.add(left(new IconBadge("bed", ACCENT, 34)));
        in.add(Box.createVerticalStrut(8));
        in.add(label(t.label, 15, Font.BOLD, TEXT));
        in.add(Box.createVerticalStrut(2));
        in.add(label(t.capacity + (t.capacity == 1 ? " bed" : " beds") + " per room", 12, Font.PLAIN, MUTED));
        in.add(label(rooms.size() + (rooms.size() == 1 ? " room" : " rooms"), 12, Font.PLAIN, SOFT));
        lab.add(in);

        JPanel tiles = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10)); 
        tiles.setOpaque(false);
        for (Room r : rooms) tiles.add(new RoomTile(r));

        row.add(lab, BorderLayout.WEST);
        row.add(tiles, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 118));
        return row;
    }

    private void rebuildRooms() {

        JPanel p = roomsPanel;
        p.removeAll();
        p.setLayout(new BorderLayout(0, 12));

        JPanel north = new JPanel();
        north.setOpaque(false);
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));

        JPanel f1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        f1.setOpaque(false);
        f1.setAlignmentX(Component.LEFT_ALIGNMENT);
        f1.add(dropdown("BLOCK",
                new String[]{"Block A", "Block B", "Block C"},
                new String[]{"A", "B", "C"},
                filterBlock, v -> { filterBlock = v; rebuildRooms(); }));
        f1.add(Box.createHorizontalStrut(24));
        f1.add(dropdown("TYPE",
                new String[]{"All types", "Single", "Double", "Shared"},
                new String[]{"All", "Single", "Double", "Shared"},
                filterType, v -> { filterType = v; rebuildRooms(); }));

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        legend.setOpaque(false);
        legend.setAlignmentX(Component.LEFT_ALIGNMENT);
        legend.add(new Badge("Available", GREEN));
        legend.add(new Badge("On hold", AMBER));
        legend.add(new Badge("Reserved", RED));
        legend.add(new Badge("Occupied", PURPLE));
        legend.add(new Badge("Your room", ACCENT));
        legend.add(new Badge("Restricted", SOFT));

        north.add(f1);
        north.add(Box.createVerticalStrut(10));
        north.add(legend);
        p.add(north, BorderLayout.NORTH);

        JPanel sections = new JPanel();
        sections.setOpaque(false);
        sections.setLayout(new BoxLayout(sections, BoxLayout.Y_AXIS));

        Color[] occCols = {PURPLE, RED, AMBER};
        int shown = 0;

        // only the selected block is displayed
        String bl = filterBlock;

        List<Room> list = new ArrayList<>();
        int free = 0, total = 0;
        for (Room r : system.getAllRooms()) {
            if (r.block != bl.charAt(0)) continue;
            total++;
            if (system.getActiveBookingForRoom(r.id) == null) free++;
            if (!filterType.equals("All") && !r.type.label.equals(filterType)) continue;
            list.add(r);
        }

        if (!list.isEmpty()) {

            // ---------- block card ----------
            RoundedPanel blockCard = card();
            // FIX: Removed setAccent to stop the blue line from appearing at the bottom
            // blockCard.setAccent(bl.equals("A") ? ACCENT : bl.equals("B") ? CYAN : GREEN);
            blockCard.setLayout(new BorderLayout(0, 14));
            blockCard.setBorder(new EmptyBorder(18, 26, 28, 22));
            blockCard.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel head = new JPanel(new BorderLayout(16, 0));
            head.setOpaque(false);

            JPanel titles = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            titles.setOpaque(false);
            titles.add(label("Block " + bl, 18, Font.BOLD, TEXT));
            titles.add(label(free + " of " + total + " free"
                    + (bl.equals("C") ? "  \u00B7  accessibility block, ground floor" : ""), 12, Font.PLAIN, MUTED));
            head.add(titles, BorderLayout.WEST);

            int[] c = counts(bl);
            OccupancyBar bar = new OccupancyBar(c[0], new int[]{c[1], c[2], c[3]}, occCols, 8);
            bar.setPreferredSize(new Dimension(180, 8));
            JPanel barWrap = new JPanel(new GridBagLayout());
            barWrap.setOpaque(false);
            barWrap.add(bar);
            head.add(barWrap, BorderLayout.EAST);

            blockCard.add(head, BorderLayout.NORTH);

            // ---------- one row per room type ----------
            JPanel rows = new JPanel();
            rows.setOpaque(false);
            rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));

            boolean first = true;
            for (Room.Type t : Room.Type.values()) {
                List<Room> ofType = new ArrayList<>();
                for (Room r : list) if (r.type == t) ofType.add(r);
                if (ofType.isEmpty()) continue;

                if (!first) {
                    rows.add(Box.createVerticalStrut(8));
                    JPanel div = new JPanel();
                    div.setBackground(LINE);
                    div.setPreferredSize(new Dimension(1, 1));
                    div.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                    div.setAlignmentX(Component.LEFT_ALIGNMENT);
                    rows.add(div);
                    rows.add(Box.createVerticalStrut(10));
                }
                rows.add(typeRow(t, ofType));
                shown += ofType.size();
                first = false;
            }
            blockCard.add(rows, BorderLayout.CENTER);

            blockCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, blockCard.getPreferredSize().height));
            sections.add(blockCard);
            sections.add(Box.createVerticalStrut(14));
        }

        if (shown == 0) {
            JPanel empty = new JPanel(new BorderLayout());
            empty.setOpaque(false);
            JLabel l = label("No " + (filterType.equals("All") ? "" : filterType.toLowerCase() + " ")
                    + "rooms in Block " + bl + ".", 15, Font.BOLD, MUTED);
            l.setBorder(new EmptyBorder(40, 10, 0, 0));
            empty.add(l, BorderLayout.NORTH);
            p.add(empty, BorderLayout.CENTER);
        } else {
            p.add(scroll(sections), BorderLayout.CENTER);
        }

        p.revalidate();
        p.repaint();
    }

    // =========================================================
    // MY RESERVATION PAGE
    // =========================================================

    private void rebuildBookings() {

        JPanel p = bookingsPanel;
        p.removeAll();
        p.setLayout(new BorderLayout());

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));

        Student cur = system.getCurrentStudent();
        List<Booking> all = cur == null ? new ArrayList<>() : system.getBookingsForStudent(cur.id);

        if (all.isEmpty()) {

            RoundedPanel empty = card();
            empty.setLayout(new BorderLayout(0, 14));
            empty.add(label("No reservations yet", 20, Font.BOLD, TEXT), BorderLayout.NORTH);
            empty.add(label("Head to the Rooms page and pick a room.", 14, Font.PLAIN, MUTED), BorderLayout.CENTER);

            RButton go = new RButton("Browse rooms", ACCENT, false);
            JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            south.setOpaque(false);
            south.add(go);
            go.addActionListener(e -> showPage("rooms"));
            empty.add(south, BorderLayout.SOUTH);

            list.add(empty);
            list.add(Box.createVerticalStrut(14));

        } else {
            for (int i = all.size() - 1; i >= 0; i--) {
                list.add(bookingCard(all.get(i)));
                list.add(Box.createVerticalStrut(14));
            }
        }

        list.add(recoveryCard(cur));

        p.add(scroll(list), BorderLayout.CENTER);
        p.revalidate();
        p.repaint();
    }

    private JPanel bookingCard(Booking b) {

        boolean pending = b.status.equals(Booking.PENDING);
        boolean confirmed = b.status.equals(Booking.CONFIRMED);
        boolean moved = b.status.equals(Booking.MOVED_IN);
        boolean active = pending || confirmed || moved;
        Room r = system.getRoom(b.roomId);

        RoundedPanel c = card();
        c.setAccent(statusColor(b.status));
        c.setLayout(new BorderLayout(20, 0));
        c.setBorder(new EmptyBorder(22, 30, 28, 24));

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        info.add(label(b.roomId, 26, Font.BOLD, active ? TEXT : MUTED));
        info.add(Box.createVerticalStrut(2));
        info.add(label(r.type.label + "  \u00B7  Block " + r.block + "  \u00B7  " + r.floorLabel()
                + "  \u00B7  " + b.duration, 13, Font.PLAIN, MUTED));
        info.add(Box.createVerticalStrut(2));
        info.add(label("Booking " + b.id + (b.requestId == null ? "" : "   \u00B7   Reference " + b.requestId),
                12, Font.PLAIN, SOFT));
        info.add(Box.createVerticalStrut(12));

        JPanel badges = left(new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)));
        badges.setOpaque(false);
        badges.add(new Badge(b.status, statusColor(b.status)));
        badges.add(Box.createHorizontalStrut(8));
        badges.add(new Badge(b.paid ? "Deposit paid" : "Deposit unpaid", b.paid ? GREEN : AMBER));
        if (moved) {
            badges.add(Box.createHorizontalStrut(8));
            badges.add(new Badge("Moved in at " + LocalTime.ofInstant(
                    Instant.ofEpochMilli(b.movedInAt), ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm")), PURPLE));
        }
        info.add(badges);

        if (active) {
            info.add(Box.createVerticalStrut(14));
            info.add(new Stepper(stepFor(b.status), statusColor(b.status)));
        }

        if (pending) {
            info.add(Box.createVerticalStrut(8));
            info.add(countdownBlock(b));
        }

        c.add(info, BorderLayout.CENTER);

        if (active) {

            JPanel btns = new JPanel(new GridLayout(0, 1, 0, 8));
            btns.setOpaque(false);

            if (pending) {
                RButton pay = new RButton("Pay deposit", GREEN, false);
                pay.addActionListener(e -> doPay(b));
                btns.add(pay);
            }
            if (confirmed) {
                RButton in = new RButton("Confirm move-in", PURPLE, false);
                in.addActionListener(e -> doCheckIn(b));
                btns.add(in);
            }
            if (pending || confirmed) {
                RButton sw = new RButton("Switch room", ACCENT, true);
                sw.addActionListener(e -> {
                    showPage("rooms");
                    showToast("Select an available room to switch", ACCENT);
                });
                btns.add(sw);

                RButton cancel = new RButton("Cancel", RED, true);
                cancel.addActionListener(e -> doCancel(b));
                btns.add(cancel);
            }
            if (moved) {
                RButton del = new RButton("Delete booking", RED, true);
                del.addActionListener(e -> doDeleteBooking(b));
                btns.add(del);
            }

            JPanel east = new JPanel(new GridBagLayout());
            east.setOpaque(false);
            east.add(btns);
            c.add(east, BorderLayout.EAST);
        }

        return c;
    }

    /** Latest request reference of this student (used to pre-fill the recovery box). */
    private String latestReference(Student cur) {
        if (cur == null) return "";
        List<Booking> all = system.getBookingsForStudent(cur.id);
        for (int i = all.size() - 1; i >= 0; i--)
            if (all.get(i).requestId != null) return all.get(i).requestId;
        return "";
    }

    /** Idempotency in action: look up (or safely retry) a request after a dropped connection. */
    private JPanel recoveryCard(Student cur) {

        RoundedPanel c = card();
        c.setLayout(new BorderLayout(0, 12));
        if (pendingRequestId != null) c.setAccent(AMBER);

        c.add(label("Lost connection? Recover your booking", 16, Font.BOLD, TEXT), BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        String msg = pendingRequestId != null
                ? "Your last request (" + pendingRequestId + ") got no response. Check its status - retrying with the same reference can never create a second booking."
                : "Every booking request has a reference (shown on your booking card). Enter it, or your Booking ID, to see what the server actually did with it.";
        body.add(label("<html><div style='width:520px'>" + msg + "</div></html>", 13, Font.PLAIN,
                pendingRequestId != null ? AMBER : MUTED));
        body.add(Box.createVerticalStrut(14));

        Field ref = new Field(18);
        ref.setText(pendingRequestId != null ? pendingRequestId : latestReference(cur));
        ref.setMaximumSize(new Dimension(320, 46));
        body.add(ref);
        body.add(Box.createVerticalStrut(12));

        JPanel btns = left(new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)));
        btns.setOpaque(false);
        RButton look = new RButton("Check status", ACCENT, false);
        look.addActionListener(e -> doLookup(ref));
        ref.addActionListener(e -> doLookup(ref));
        btns.add(look);
        if (pendingRequestId != null) {
            btns.add(Box.createHorizontalStrut(10));
            RButton retry = new RButton("Retry request", GREEN, true);
            retry.addActionListener(e -> doRetry());
            btns.add(retry);
        }
        body.add(btns);

        c.add(body, BorderLayout.CENTER);
        return c;
    }

    // =========================================================
    // STUDENTS & OCCUPANCY PAGE
    // =========================================================

    private void rebuildStudents() {

        JPanel p = studentsPanel;
        p.removeAll();
        p.setLayout(new BorderLayout());

        RoundedPanel c = card();
        c.setLayout(new BorderLayout(0, 16));

        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.add(label(system.getStudents().size() + " registered students", 16, Font.BOLD, TEXT), BorderLayout.WEST);
        RButton race = new RButton("Simulate race condition", PURPLE, true);
        race.setBorder(new EmptyBorder(8, 18, 8, 18));
        race.addActionListener(e -> doRaceDemo());
        JPanel barBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        barBtns.setOpaque(false);
        barBtns.add(race);
        bar.add(barBtns, BorderLayout.EAST);

        DefaultTableModel model = new DefaultTableModel(
                new String[]{"STUDENT ID", "NAME", "DEPARTMENT", "ACCESS", "ROOM", "STATUS"}, 0) {
            @Override public boolean isCellEditable(int r, int col) { return false; }
        };

        for (Student s : system.getStudents()) {
            Booking b = system.getActiveBookingForStudent(s.id);
            String status = "\u2014";
            if (b != null) {
                if (b.status.equals(Booking.MOVED_IN)) status = "Moved in";
                else if (b.status.equals(Booking.CONFIRMED)) status = "Paid, not moved in";
                else status = "Unpaid (on hold)";
            }
            model.addRow(new Object[]{
                    s.id, s.name, s.department,
                    s.accessibilityRequired ? "Accessible" : "Standard",
                    b == null ? "\u2014" : b.roomId, status});
        }

        JTable table = new JTable(model);
        table.setRowHeight(46);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFont(font(Font.PLAIN, 14));
        table.setSelectionBackground(new Color(0xEE, 0xF2, 0xFF));
        table.setSelectionForeground(TEXT);
        table.setFillsViewportHeight(true);
        table.setBackground(Color.WHITE);

        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setPreferredSize(new Dimension(100, 40));
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, false, false, r, col);
                l.setFont(font(Font.BOLD, 11));
                l.setForeground(SOFT);
                l.setBackground(new Color(0xF8, 0xFA, 0xFC));
                l.setOpaque(true);
                l.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, LINE), new EmptyBorder(0, 14, 0, 14)));
                return l;
            }
        });

        Student cur = system.getCurrentStudent();

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean f, int r, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, sel, false, r, col);
                l.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xF1, 0xF5, 0xF9)),
                        new EmptyBorder(0, 14, 0, 14)));
                if (!sel) l.setBackground(Color.WHITE);

                String text = String.valueOf(v);
                boolean isMe = cur != null && cur.id.equals(t.getValueAt(r, 0));
                l.setFont(font(isMe ? Font.BOLD : Font.PLAIN, 14));
                l.setForeground(TEXT);

                if (col == 5) {
                    l.setFont(font(Font.BOLD, 13));
                    if (text.startsWith("Unpaid")) l.setForeground(AMBER);
                    else if (text.startsWith("Paid")) l.setForeground(RED);
                    else if (text.startsWith("Moved")) l.setForeground(PURPLE);
                    else l.setForeground(SOFT);
                    if (!text.equals("\u2014")) l.setText("\u25CF  " + text);
                }
                if (col == 3 && text.equals("Accessible")) l.setForeground(GREEN);
                if (col == 2 || col == 4) l.setForeground(MUTED);
                return l;
            }
        });

        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(LINE));
        sp.getViewport().setBackground(Color.WHITE);

        c.add(bar, BorderLayout.NORTH);
        c.add(sp, BorderLayout.CENTER);

        p.add(c, BorderLayout.CENTER);
        p.revalidate();
        p.repaint();
    }

    // =========================================================
    // AUDIT TRAIL PAGE
    // =========================================================

    private void rebuildActivity() {

        JPanel p = activityPanel;
        p.removeAll();
        p.setLayout(new BorderLayout());

        RoundedPanel c = card();
        c.setLayout(new BorderLayout(0, 10));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(label("All events (last 100)", 16, Font.BOLD, TEXT), BorderLayout.WEST);

        RButton clear = new RButton("Clear log", RED, true);
        clear.setBorder(new EmptyBorder(6, 16, 6, 16));
        clear.addActionListener(e -> {
            system.clearHistory();
            refreshAll();
        });
        head.add(clear, BorderLayout.EAST);

        c.add(head, BorderLayout.NORTH);
        c.add(activityView(100, true), BorderLayout.CENTER);

        p.add(c, BorderLayout.CENTER);
        p.revalidate();
        p.repaint();
    }

    // =========================================================
    // ACTIONS
    // =========================================================

    private void doBook(String studentId, Room room, String duration, boolean simulateDrop) {

        String req = newRequestId();

        try {
            Booking b = system.createBooking(studentId, room.id, duration, req);

            if (simulateDrop) {
                // The server processed it, but the client never heard back.
                pendingRequestId = req;
                pendingRoomId = room.id;
                pendingDuration = duration;
                system.addHistory("Connection dropped after request " + req);
                refreshAll();
                showPage("mybooking");
                showToast("Connection lost - status unknown. Recover it below.", RED);
                return;
            }

            refreshAll();
            showPage("mybooking");
            showToast("Reserved " + b.roomId + " - pay your deposit in time", GREEN);

        } catch (IllegalStateException ex) {
            refreshAll();
            showToast(ex.getMessage(), RED);
        }
    }

    /**
     * Race-condition demo: two students request the SAME room at the same instant
     * (two threads released together by a barrier). The bookingLock in
     * HostelBookingSystem lets exactly one through; the other is rejected.
     * If fewer than two students are free, throw-away demo students are created.
     */
    private void doRaceDemo() {

        List<Student> free = new ArrayList<>();
        for (Student s : system.getStudents())
            if (system.getActiveBookingForStudent(s.id) == null) free.add(s);

        // create demo students if fewer than two are free
        while (free.size() < 2) {
            String id = "DEMO" + (system.getStudents().size() + 1);
            system.addStudent(id, "Demo Student", "Demo", DEMO_PW, false);
            free.add(system.getStudent(id));
        }

        Room target = null;
        for (Room r : system.getAllRooms())
            if (r.block != 'C' && system.getActiveBookingForRoom(r.id) == null) {
                target = r;
                break;
            }

        if (target == null) {
            JOptionPane.showMessageDialog(this,
                    "No free room in Block A or B. Cancel a reservation first.",
                    "Race condition demo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final Room room = target;
        final Student[] who = {free.get(0), free.get(1)};
        final String[] result = new String[2];
        final CyclicBarrier gate = new CyclicBarrier(2);

        system.addHistory("RACE DEMO: " + who[0].id + " and " + who[1].id
                + " both request " + room.id + " at the same instant");

        Thread[] threads = new Thread[2];
        for (int i = 0; i < 2; i++) {
            final int k = i;
            threads[i] = new Thread(() -> {
                try {
                    gate.await();   // both threads fire together
                    Booking b = system.createBooking(who[k].id, room.id, "1 Semester", newRequestId());
                    result[k] = who[k].name + " (" + who[k].id + ")  \u2192  GOT " + b.roomId + " (" + b.id + ")";
                } catch (IllegalStateException ex) {
                    result[k] = who[k].name + " (" + who[k].id + ")  \u2192  REJECTED: " + ex.getMessage();
                } catch (Exception ex) {
                    result[k] = who[k].name + " (" + who[k].id + ")  \u2192  ERROR";
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        int winners = 0;
        for (String r : result) if (r != null && r.contains("GOT")) winners++;
        system.addHistory("RACE DEMO result for " + room.id + ": " + winners + " winner, "
                + (2 - winners) + " rejected");
        refreshAll();

        JOptionPane.showMessageDialog(this,
                "Room " + room.id + " requested by two students simultaneously:\n\n"
                        + result[0] + "\n" + result[1] + "\n\n"
                        + "Exactly " + winners + " booking was created. No double-booking.",
                "Race condition demo", JOptionPane.INFORMATION_MESSAGE);
        showToast(winners == 1 ? "Race handled: one winner, one rejected" : "Unexpected result", winners == 1 ? GREEN : RED);
    }

    /** The stored reference closest to what was typed (max 2 typos), or null. */
    private String closestReference(String typed) {

        Student cur = system.getCurrentStudent();
        if (cur == null) return null;

        String best = null;
        int bestD = 3;
        for (Booking b : system.getBookingsForStudent(cur.id)) {
            if (b.requestId == null) continue;
            int d = distance(typed, b.requestId);
            if (d < bestD) {
                bestD = d;
                best = b.requestId;
            }
        }
        return best;
    }

    private void doLookup(Field refField) {

        String r = refField.getText().trim().toUpperCase().replaceAll("\\s+", "");
        if (r.isEmpty()) {
            showToast("Enter a request reference or booking ID first", AMBER);
            return;
        }

        Student cur = system.getCurrentStudent();
        Booking b = system.findByRequest(r);

        // a student may only look up their own bookings
        if (b != null && cur != null && !b.studentId.equals(cur.id)) b = null;

        if (b == null) {
            String guess = closestReference(r);
            if (guess != null) {
                refField.setText(guess);
                showToast("Not found - did you mean " + guess + "? Press Check status.", AMBER);
            } else {
                showToast("No booking found for " + r + " - safe to try again", AMBER);
            }
            return;
        }

        if (r.equals(pendingRequestId)) pendingRequestId = null;
        system.addHistory("Recovered request " + r + " -> " + b.id + " (" + b.roomId + ")");
        refreshAll();
        showToast("Found " + b.id + " for " + b.roomId + " - " + b.status, GREEN);

        JOptionPane.showMessageDialog(this,
                "Reference:  " + (b.requestId == null ? "-" : b.requestId)
                        + "\nBooking:  " + b.id
                        + "\nRoom:  " + b.roomId
                        + "\nStatus:  " + b.status
                        + "\nDeposit:  " + (b.paid ? "Paid" : "Unpaid"),
                "Booking found", JOptionPane.INFORMATION_MESSAGE);
    }

    private void doRetry() {

        Student cur = system.getCurrentStudent();
        if (cur == null || pendingRequestId == null) return;

        try {
            Booking b = system.createBooking(cur.id, pendingRoomId, pendingDuration, pendingRequestId);
            pendingRequestId = null;
            refreshAll();
            showToast("Recovered " + b.id + " (" + b.roomId + ") - no duplicate created", GREEN);
        } catch (IllegalStateException ex) {
            refreshAll();
            showToast(ex.getMessage(), RED);
        }
    }

    private void doSwitch(Booking old, Room newRoom, String duration) {
        try {
            Booking b = system.switchBooking(old.id, newRoom.id, duration);
            refreshAll();
            showPage("mybooking");
            showToast("Switched to " + b.roomId, ACCENT);
        } catch (IllegalStateException ex) {
            refreshAll();
            showToast(ex.getMessage(), RED);
        }
    }

    private void doPay(Booking b) {
        if (system.payDeposit(b.id))
            showToast("Payment received - booking confirmed", GREEN);
        else
            showToast("Payment failed - the booking may have expired", RED);
        refreshAll();
    }

    private void doCheckIn(Booking b) {
        try {
            system.checkIn(b.id);
            showToast("Welcome to " + b.roomId + " - move-in recorded", PURPLE);
        } catch (IllegalStateException ex) {
            showToast(ex.getMessage(), RED);
        }
        refreshAll();
    }

    private void doDeleteBooking(Booking b) {

        int r = JOptionPane.showConfirmDialog(this,
                "Delete your booking for " + b.roomId + "?\nThe room will be released for other students.",
                "Delete booking", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;

        if (system.deleteBooking(b.id)) showToast("Booking deleted - room released", MUTED);
        else showToast("Could not delete this booking", AMBER);
        refreshAll();
    }

    private void doCancel(Booking b) {

        int r = JOptionPane.showConfirmDialog(this,
                "Cancel reservation " + b.id + " for " + b.roomId + "?\nThe room will be released immediately.",
                "Cancel reservation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;

        if (system.cancelBooking(b.id)) showToast("Reservation cancelled", RED);
        else showToast("Could not cancel this reservation", AMBER);
        refreshAll();
    }

    // =========================================================
    // DIALOGS
    // =========================================================

    private static class Choice {
        String duration;
        boolean drop;
    }

    private void infoRow(JPanel grid, String key, String value) {
        JLabel k = new JLabel(key);
        k.setFont(font(Font.BOLD, 11));
        k.setForeground(SOFT);
        JLabel v = new JLabel(value);
        v.setFont(font(Font.BOLD, 13));
        v.setForeground(TEXT);
        grid.add(k);
        grid.add(v);
    }

    /** Room detail + confirm dialog. existing != null means a switch. */
    private Choice roomDialog(Room room, Booking existing) {

        boolean switching = existing != null;
        JDialog d = new JDialog(this, switching ? "Switch room" : "Reserve room", true);
        final Choice[] result = {null};

        JPanel body = new JPanel();
        body.setBackground(Color.WHITE);
        body.setBorder(new EmptyBorder(26, 30, 24, 30));
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        body.add(label((switching ? "Switch to " : "Reserve ") + room.id, 22, Font.BOLD, TEXT));
        body.add(Box.createVerticalStrut(14));

        RoundedPanel info = new RoundedPanel(12, new Color(0xF8, 0xFA, 0xFC), false);
        info.setLayout(new GridLayout(0, 2, 10, 10));
        info.setBorder(new EmptyBorder(16, 18, 16, 18));
        info.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoRow(info, "BLOCK", "Block " + room.block + (room.accessible() ? "  (accessibility)" : ""));
        infoRow(info, "TYPE", room.type.label + "  \u00B7  " + bedsText(room));
        infoRow(info, "FLOOR", room.floorLabel());
        infoRow(info, "ACCESS", room.accessible() ? "Ground floor, accessible entrance" : "Standard");
        body.add(info);
        body.add(Box.createVerticalStrut(14));

        String msg = switching
                ? "Move from " + existing.roomId + " to " + room.id + ". Your deposit status and deadline carry over."
                : "Pay the security deposit within 1 minute (demo mode; 48 hours in the real policy) or the room is released automatically.";
        body.add(label("<html><div style='width:340px'>" + msg + "</div></html>", 13, Font.PLAIN, MUTED));
        body.add(Box.createVerticalStrut(16));
        body.add(label("Duration", 12, Font.BOLD, TEXT));
        body.add(Box.createVerticalStrut(6));

        JComboBox<String> combo = new JComboBox<>(new String[]{"1 Semester", "2 Semesters"});
        combo.setSelectedItem(switching ? existing.duration : "1 Semester");
        combo.setFont(font(Font.PLAIN, 14));
        combo.setBackground(Color.WHITE);
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        combo.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(combo);

        JCheckBox drop = new JCheckBox("Testing: simulate a dropped connection after I confirm");
        drop.setOpaque(false);
        drop.setFont(font(Font.PLAIN, 12));
        drop.setForeground(SOFT);
        drop.setFocusPainted(false);
        drop.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (!switching) {
            body.add(Box.createVerticalStrut(12));
            body.add(drop);
        }
        body.add(Box.createVerticalStrut(22));

        JPanel btns = left(new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0)));
        btns.setOpaque(false);
        RButton cancel = new RButton("Cancel", MUTED, true);
        RButton ok = new RButton(switching ? "Switch" : "Reserve room", ACCENT, false);
        btns.add(cancel);
        btns.add(ok);
        body.add(btns);

        cancel.addActionListener(e -> d.dispose());
        ok.addActionListener(e -> {
            Choice c = new Choice();
            c.duration = (String) combo.getSelectedItem();
            c.drop = drop.isSelected();
            result[0] = c;
            d.dispose();
        });

        d.setContentPane(body);
        d.pack();
        d.setResizable(false);
        d.setLocationRelativeTo(this);
        d.setVisible(true);

        return result[0];
    }

    private void showAddStudentDialog() {

        JDialog d = new JDialog(this, "Add student", true);

        JPanel body = new JPanel();
        body.setBackground(Color.WHITE);
        body.setBorder(new EmptyBorder(26, 30, 24, 30));
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        body.add(label("Register a student", 22, Font.BOLD, TEXT));
        body.add(Box.createVerticalStrut(18));

        Field id = new Field(24), name = new Field(24), dept = new Field(24);
        PassField pw = new PassField(24);

        body.add(label("Student ID", 12, Font.BOLD, TEXT));
        body.add(Box.createVerticalStrut(6));
        body.add(id);
        body.add(Box.createVerticalStrut(12));
        body.add(label("Full name", 12, Font.BOLD, TEXT));
        body.add(Box.createVerticalStrut(6));
        body.add(name);
        body.add(Box.createVerticalStrut(12));
        body.add(label("Department", 12, Font.BOLD, TEXT));
        body.add(Box.createVerticalStrut(6));
        body.add(dept);
        body.add(Box.createVerticalStrut(12));
        body.add(label("Password (min 4 characters)", 12, Font.BOLD, TEXT));
        body.add(Box.createVerticalStrut(6));
        body.add(pw);
        body.add(Box.createVerticalStrut(12));

        JCheckBox access = new JCheckBox("I have a registered accessibility requirement (unlocks Block C)");
        access.setOpaque(false);
        access.setFont(font(Font.PLAIN, 12));
        access.setForeground(TEXT);
        access.setFocusPainted(false);
        access.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(access);

        JLabel err = label(" ", 12, Font.PLAIN, RED);
        body.add(Box.createVerticalStrut(8));
        body.add(err);
        body.add(Box.createVerticalStrut(10));

        JPanel btns = left(new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0)));
        btns.setOpaque(false);
        RButton cancel = new RButton("Cancel", MUTED, true);
        RButton add = new RButton("Add student", ACCENT, false);
        btns.add(cancel);
        btns.add(add);
        body.add(btns);

        cancel.addActionListener(e -> d.dispose());

        add.addActionListener(e -> {

            String i = id.getText().trim();
            String n = name.getText().trim();
            String dp = dept.getText().trim();
            String p = new String(pw.getPassword());

            if (i.isEmpty() || n.isEmpty() || dp.isEmpty() || p.isEmpty()) {
                err.setText("Please fill in all fields.");
                return;
            }
            if (p.length() < 4) {
                err.setText("Password must be at least 4 characters.");
                return;
            }
            if (!system.addStudent(i, n, dp, p, access.isSelected())) {
                err.setText("That student ID already exists.");
                return;
            }

            system.addHistory("Student registered: " + i.toUpperCase());
            d.dispose();
            refreshAll();
            showToast("Student " + n + " added - you can now sign in", GREEN);
        });

        d.setContentPane(body);
        d.pack();
        d.setResizable(false);
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    // =========================================================
    // MAIN
    // =========================================================

    public static void main(String[] args) {

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        UIManager.put("OptionPane.messageFont", font(Font.PLAIN, 14));
        UIManager.put("OptionPane.buttonFont", font(Font.BOLD, 13));

        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }
}