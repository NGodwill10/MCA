package ui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.UIManager;

public final class Theme {
    public static final Color INK = new Color(33, 52, 58);
    public static final Color PAPER = new Color(245, 247, 243);
    public static final Color WHITE = Color.WHITE;
    public static final Color ACCENT = new Color(31, 113, 104);
    public static final Color ACCENT_DARK = new Color(22, 89, 80);
    public static final Color LINE = new Color(216, 225, 220);
    public static final Color MUTED = new Color(100, 118, 112);
    public static final Color ERROR = new Color(159, 54, 48);
    public static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    public static final Font BODY_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    public static final Font HEADING = new Font(Font.SANS_SERIF, Font.BOLD, 22);
    public static final int GAP = 12;
    public static final int PANEL_INSET = 20;

    private Theme() {
    }

    public static void installNimbus() {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            UIManager.put("control", PAPER);
            UIManager.put("nimbusBase", ACCENT_DARK);
            UIManager.put("nimbusFocus", ACCENT);
            UIManager.put("defaultFont", BODY);
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException exception) {
            System.getLogger(Theme.class.getName()).log(System.Logger.Level.WARNING,
                    "Nimbus look and feel could not be installed", exception);
        }
    }
}