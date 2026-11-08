package classes;

import javax.microedition.lcdui.Image;

/**
 *
 * @author Roman Lahin
 */
public class Texture {
    public int w,h,wBits,hBits,wLen,hLen,len;
    public int[] rgb;
    
    public boolean dark = false; //Has darker version
    public Texture darkTex; //Darker version
    
    public static Texture loadTexture(String path) {
        try {
            Image img = Image.createImage(path);
            
            int w = img.getWidth(); int h = img.getHeight();
            int[] rgb = new int[w*h];
            
            img.getRGB(rgb, 0, w, 0, 0, w, h);
            img = null;
            
            return new Texture(rgb,w,h);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public static int genWBits(int w) {
        int x = 0;
        while(1<<x<w) {
            x++;
        }
        return x;
    }
    
    public Texture(int[] rgb, int w, int h) {
        this.rgb = rgb;
        this.w = w; this.h = h;
        wLen = w-1; hLen= h-1; len = rgb.length-1;
        wBits = genWBits(w); hBits = genWBits(h);
    }
    
    public Texture(int w, int h) {
        this.rgb = new int[w*h];
        this.w = w; this.h = h;
        wLen = w-1; hLen= h-1; len = rgb.length-1;
        wBits = genWBits(w); hBits = genWBits(h);
    }
    
}
