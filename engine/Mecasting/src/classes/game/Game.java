package classes.game;

import classes.Keyboard;
import classes.Main;
import classes.MathEngine;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/**
 *
 * @author Roman Lahin
 */

public class Game extends Canvas {
    
    private Main midlet; //MIDlet!!!!
    private World world;
    private final int w,h; //Screen resolution
    private Keyboard keyboard; //Keyboard class
    
    private long frameTime = 15l; //FPS sync and stuff
    private long previousFrame = 0; 
    private long lastFpsTest = 0; //Last fps test(fps should update every 1000ms)
    private int frames = 0; //Frames in second counter
    private int fps = 0; //Frames per second
    
    public String mapPath = "/maps/dsv test.ssv";
    
    public Game(Main midlet) {
        this.midlet = midlet;
        setFullScreenMode(true);
        w = getWidth(); h = getHeight();
        
        keyboard = new Keyboard(this);
        world = new World(midlet,this,mapPath,w,h,1);
        
        
        lastFpsTest = System.currentTimeMillis();
        repaint();
    }

    protected void paint(Graphics g) {
        if(previousFrame!=0) frameTime = System.currentTimeMillis() - previousFrame;
        previousFrame = System.currentTimeMillis();
        
        if(previousFrame-lastFpsTest>=1000) { //Fps meter
            fps=frames; frames=0; lastFpsTest=previousFrame;
        }
        
        keyReaction();
        world.update(); //Update game world
        world.paint(g,w,h);
        
        String info = "FPS: "+fps;
        g.setColor(0);
        g.drawString(info, 4, 4, 16|4); //top = 16; left = 4
        g.setColor(0xffffff);
        g.drawString(info, 2, 2, 16|4); //top = 16; left = 4
        
        try {
            Thread.sleep(1l);
        } catch(Exception e) {}
        
        frames++;
        repaint();
    }
    
    
    public void keyPressed(int key) {
        keyboard.keyInput(key, true);
    }
    
    public void keyReleased(int key) {
        keyboard.keyInput(key, false);
    }
    
    public void keyReaction() {
        
        //speed modifiers
        int moveSpeed = (int) (frameTime * 2048 /100); //the constant value is in squares/second
        int rotSpeed = (int) (frameTime * 180 /1000); //the constant value is in radians/second

        //move forward or backwards if no wall near to you
        if(keyboard.up || keyboard.down) {
            int xSpeed = (world.dirX * moveSpeed / 4096)*(keyboard.down?-1:1);
            int ySpeed = (world.dirY * moveSpeed / 4096)*(keyboard.down?-1:1);
            
            world.movePlayer(xSpeed,ySpeed);
        }
        
        //Strafe
        if(keyboard.key1 || keyboard.key3) {
            int dir = world.getPlayer().direction + (keyboard.key1?-90:90);
            int vecX = (world.baseDirX * MathEngine.cos(dir) - world.baseDirY * MathEngine.sin(dir)) >> MathEngine.fp;
            int vecY = (world.baseDirX * MathEngine.sin(dir) + world.baseDirY * MathEngine.cos(dir)) >> MathEngine.fp;
            int xSpeed = (vecX * moveSpeed / 6144);
            int ySpeed = (vecY * moveSpeed / 6144);
            
            world.movePlayer(xSpeed,ySpeed);
        }
        
        //rotate to the right or left key
        if(keyboard.right || keyboard.left) {
            //both camera direction and camera plane must be rotated
            world.rotatePlayer(rotSpeed*(keyboard.right?1:-1));
        }

    }
    
    
    public static Image genBorder(int size, int color) {
        int[] rgb = new int[size*size];
        color&=0x00ffffff;
        int size2 = size*2;
        int sizeSqr = size*size;
        int size2Sqr = sizeSqr*4;
        
        for(int y=0;y<size;y++) {
            int yy = y*size;
            
            int yD = (size-1-y)*(size-1-y);
            int y1D = yD*4;
            int y2D = (size2-1-y*2)*(size2-y*2);
            
            for(int x=0;x<size;x++) {
                int xD = (size-1-x)*(size-1-x);
                int x1D = xD*4;
                int x2D = (size2-x*2)*(size2-1-x*2);
                
                int alpha=0;
                if(xD+yD>=sizeSqr) alpha=63;
                if(x1D+y2D>=size2Sqr) alpha+=64;
                if(x2D+y1D>=size2Sqr) alpha+=64;
                if(x2D+y2D>=size2Sqr) alpha+=64;
                
                rgb[x+yy]= (alpha<<24) | color;
            }
        }
        
        return Image.createRGBImage(rgb, size, size, true);
    }
    
    
    public static Image genBorderOutline(int size, int color) {
        int[] rgb = new int[size*size];
        color&=0x00ffffff;
        int size2 = size*2;
        int sizeSqr = size*size;
        
        for(int y=0;y<size;y++) {
            int yy = y*size;
            
            int yD = (size-1-y)*(size-1-y);
            int y1D = yD*4;
            int y2D = (size2-1-y*2)*(size2-y*2);
            
            for(int x=0;x<size;x++) {
                int xD = (size-1-x)*(size-1-x);
                int x1D = xD*4;
                int x2D = (size2-1-x*2)*(size2-x*2);
                
                int alpha=0;
                if(Math.abs(yD+xD-sizeSqr)<=size) alpha=63;
                if(Math.abs(y1D/4+x2D/4-sizeSqr)<=size) alpha+=64;
                if(Math.abs(y2D/4+x1D/4-sizeSqr)<=size) alpha+=64;
                if(Math.abs(y2D/4+x2D/4-sizeSqr)<=size) alpha+=64;
                
                rgb[x+yy]= (alpha<<24) | color;
            }
        }
        
        return Image.createRGBImage(rgb, size, size, true);
    }
    
    
}