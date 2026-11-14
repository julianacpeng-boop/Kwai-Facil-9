package br.com.juh.kwaifacil;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;

final class CoverLayout {
    static final float TILT = -22f;

    static PointF center(Rect window, Rect label) {
        return new PointF(window.exactCenterX(), label.exactCenterY() - window.width() * 1.198f);
    }

    static String formatName(String name) {
        if (name.contains("\n") || name.contains("  ") || name.length() < 19) return name;
        int split = name.lastIndexOf(' ', Math.round(name.length() * .70f));
        if (split < name.length() * .4f) return name;
        return name.substring(0, split).trim() + "\n" + name.substring(split + 1).trim();
    }

    static PointF editorCoverIcon(Rect window, Rect nextButton) {
        float x = window.left + window.width() * .927f;
        float y = nextButton.exactCenterY() - window.width() * 1.18f;
        return new PointF(x, y);
    }

    static Selection findSelection(Bitmap screen, Rect window, Rect label) {
        PointF center = center(window, label);
        float width = window.width();
        int radius = Math.max(9, Math.round(width * .024f));
        PointF remove = findHandle(screen, center, width, radius, false);
        PointF rotate = findHandle(screen, center, width, radius, true);
        if (remove == null || rotate == null) return null;
        float angle = findTopEdge(screen, remove, rotate, radius);
        if (Float.isNaN(angle)) return null;
        return new Selection(new PointF((remove.x + rotate.x) / 2, (remove.y + rotate.y) / 2), rotate, angle);
    }

    private static PointF findHandle(Bitmap screen, PointF center, float width, int radius, boolean lower) {
        int left = Math.max(radius, Math.round(center.x + width * (lower ? .10f : -.46f)));
        int right = Math.min(screen.getWidth() - radius - 1, Math.round(center.x + width * (lower ? .46f : -.10f)));
        int top = Math.max(radius, Math.round(center.y + width * (lower ? .035f : -.43f)));
        int bottom = Math.min(screen.getHeight() - radius - 1, Math.round(center.y + width * (lower ? .43f : -.035f)));
        int best = 0;
        PointF point = null;
        int stride = Math.max(2, radius / 6);
        for (int y = top; y <= bottom; y += stride) {
            for (int x = left; x <= right; x += stride) {
                int score = scoreHandle(screen, x, y, radius);
                if (score > best) {
                    best = score;
                    point = new PointF(x, y);
                }
            }
        }
        return point;
    }

    private static int scoreHandle(Bitmap screen, int x, int y, int radius) {
        int light = 0;
        int dark = 0;
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI / 8;
            int dx = (int) Math.round(Math.cos(angle) * radius * .68);
            int dy = (int) Math.round(Math.sin(angle) * radius * .68);
            if (white(screen, x + dx, y + dy)) light++;
            if (dark(screen, x + dx / 2, y + dy / 2)) dark++;
        }
        if (light < 15 || dark < 2 || dark > 14) return 0;
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            int dx = (int) Math.round(Math.cos(angle) * radius);
            int dy = (int) Math.round(Math.sin(angle) * radius);
            if (white(screen, x + dx, y + dy)) light++;
        }
        return light * 2 + dark;
    }

    private static float findTopEdge(Bitmap screen, PointF start, PointF opposite, int radius) {
        int best = 0;
        float angle = Float.NaN;
        int length = (int) (Math.hypot(opposite.x - start.x, opposite.y - start.y) * .53);
        for (int a = -43; a <= 20; a++) {
            double radians = Math.toRadians(a);
            double dx = Math.cos(radians);
            double dy = Math.sin(radians);
            for (int offset = -radius / 4; offset <= radius / 4; offset += 2) {
                int hits = 0;
                int samples = 0;
                for (int d = radius * 2; d <= length; d += 8) {
                    int x = Math.round((float) (start.x + dx * d));
                    int y = Math.round((float) (start.y + dy * d + offset));
                    if (white(screen, x, y) || white(screen, x, y + 1) || white(screen, x, y - 1)) hits++;
                    samples++;
                }
                if (hits >= samples * .64f && hits > best) {
                    best = hits;
                    angle = a;
                }
            }
        }
        return angle;
    }

    private static boolean white(Bitmap screen, int x, int y) {
        if (x < 0 || y < 0 || x >= screen.getWidth() || y >= screen.getHeight()) return false;
        int pixel = screen.getPixel(x, y);
        return ((pixel >>> 16) & 255) > 226 && ((pixel >>> 8) & 255) > 226 && (pixel & 255) > 226;
    }

    private static boolean dark(Bitmap screen, int x, int y) {
        int pixel = screen.getPixel(x, y);
        return ((pixel >>> 16) & 255) < 110 && ((pixel >>> 8) & 255) < 110 && (pixel & 255) < 110;
    }

    static final class Selection {
        final PointF center;
        final PointF rotate;
        final float angle;

        Selection(PointF center, PointF rotate, float angle) {
            this.center = center;
            this.rotate = rotate;
            this.angle = angle;
        }
    }
}
