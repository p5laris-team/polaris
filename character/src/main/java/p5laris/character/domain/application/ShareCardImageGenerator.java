package p5laris.character.domain.application;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class ShareCardImageGenerator {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1350;

    public byte[] generate(ShareCardImageCommand command) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            drawBackground(g);
            drawCard(g);
            drawBrand(g);
            drawCharacter(g, command.characterTypeCode());
            drawCenteredText(g, command.characterName(), new Font("SansSerif", Font.BOLD, 92), new Color(0x5c332a), 665);
            drawWrappedCenteredText(g, command.headline(), new Font("SansSerif", Font.BOLD, 46), new Color(0x5c332a), 540, 760, 720, 64);
            drawStats(g, command.completedCount(), command.earnedStarPiece());
            return toPngBytes(image);
        } finally {
            g.dispose();
        }
    }

    private void drawBackground(Graphics2D g) {
        g.setPaint(new java.awt.GradientPaint(0, 0, new Color(0xfff8ec), WIDTH, HEIGHT, new Color(0xdff4e6)));
        g.fillRect(0, 0, WIDTH, HEIGHT);
    }

    private void drawCard(Graphics2D g) {
        g.setColor(new Color(255, 255, 255, 180));
        g.fill(new RoundRectangle2D.Double(90, 90, 900, 1170, 60, 60));
    }

    private void drawBrand(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.BOLD, 42));
        g.setColor(new Color(0x8f4b3c));
        g.drawString("Polaris", 150, 180);
    }

    private void drawCharacter(Graphics2D g, String characterTypeCode) {
        Color fill = switch (characterTypeCode == null ? "" : characterTypeCode.toUpperCase()) {
            case "MUMU" -> new Color(0xdff4e6);
            case "JJORY" -> new Color(0xdde9ff);
            default -> new Color(0xf4d7c9);
        };
        g.setColor(fill);
        g.fillOval(390, 270, 300, 300);

        g.setStroke(new BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x7a4a22));
        g.drawArc(455, 390, 70, 50, 200, 140);
        g.drawArc(555, 390, 70, 50, 200, 140);
        g.fillOval(485, 465, 18, 18);
        g.fillOval(575, 465, 18, 18);
    }

    private void drawCenteredText(Graphics2D g, String text, Font font, Color color, int y) {
        g.setFont(font);
        g.setColor(color);
        FontMetrics metrics = g.getFontMetrics();
        int x = (WIDTH - metrics.stringWidth(text)) / 2;
        g.drawString(text, x, y);
    }

    private void drawWrappedCenteredText(Graphics2D g, String text, Font font, Color color, int x, int y, int maxWidth, int lineHeight) {
        g.setFont(font);
        g.setColor(color);
        FontMetrics metrics = g.getFontMetrics();
        int currentY = y;

        for (String line : wrapText(text, metrics, maxWidth)) {
            int drawX = x - metrics.stringWidth(line) / 2;
            g.drawString(line, drawX, currentY);
            currentY += lineHeight;
        }
    }

    private List<String> wrapText(String text, FontMetrics metrics, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        String line = "";

        for (String word : words) {
            String testLine = line.isBlank() ? word : line + " " + word;
            if (metrics.stringWidth(testLine) > maxWidth && !line.isBlank()) {
                lines.add(line);
                line = word;
            } else {
                line = testLine;
            }
        }

        if (!line.isBlank()) {
            lines.add(line);
        }
        return lines;
    }

    private void drawStats(Graphics2D g, int completedCount, int earnedStarPiece) {
        drawStatBox(g, 190, 950, "Completed", completedCount + "");
        drawStatBox(g, 590, 950, "Star pieces", "+" + earnedStarPiece);
    }

    private void drawStatBox(Graphics2D g, int x, int y, String label, String value) {
        g.setColor(new Color(0xfff4dc));
        g.fill(new RoundRectangle2D.Double(x, y, 300, 150, 36, 36));
        drawCenteredTextInBox(g, label, new Font("SansSerif", Font.BOLD, 34), new Color(0x7a4a22), x, y + 58, 300);
        drawCenteredTextInBox(g, value, new Font("SansSerif", Font.BOLD, 54), new Color(0x7a4a22), x, y + 120, 300);
    }

    private void drawCenteredTextInBox(Graphics2D g, String text, Font font, Color color, int x, int baseline, int width) {
        g.setFont(font);
        g.setColor(color);
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, x + (width - metrics.stringWidth(text)) / 2, baseline);
    }

    private byte[] toPngBytes(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render share card image.", e);
        }
    }

    public record ShareCardImageCommand(
            String characterName,
            String characterTypeCode,
            String headline,
            int completedCount,
            int earnedStarPiece
    ) {}
}
