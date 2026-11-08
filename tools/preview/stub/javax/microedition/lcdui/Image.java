package javax.microedition.lcdui;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;

/** Заглушка Image: PNG из classpath через ImageIO. */
public class Image {
    private final BufferedImage img;

    private Image(BufferedImage img) { this.img = img; }

    public static Image createImage(String path) throws Exception {
        InputStream in = Image.class.getResourceAsStream(path);
        if (in == null) throw new Exception("resource not found: " + path);
        BufferedImage b = ImageIO.read(in);
        in.close();
        if (b == null) throw new Exception("bad PNG: " + path);
        return new Image(b);
    }

    public int getWidth() { return img.getWidth(); }
    public int getHeight() { return img.getHeight(); }

    public void getRGB(int[] rgb, int offset, int scanlength,
                       int x, int y, int width, int height) {
        img.getRGB(x, y, width, height, rgb, offset, scanlength);
    }
}
