package classes.game;

import classes.MathEngine;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import objects.Player;

/**
 *
 * @author Roman Lahin
 */
public abstract class GameObject {
    
    public boolean netSync = false; //Sends data to client
    public boolean storeInRms = false; //Save or load info to/from rms
    public boolean visible = false; //Visible lol
    public boolean exist = true; //Object exists
    
    public boolean activable = false; //Player can activate object by walking thru it
    public boolean clickable = false; //Player can activate object by pressing interaction button near it
            
    public boolean collider = false; //Other objects colide with this object
    public boolean physics = false; //Can be moved by other objects
    
    public int x, y, xSpeed = 0, ySpeed = 0, radius = 300;
    
    public String[] argsOrder; //Arguments list (used to sort arguments on loading)
    
    public GameObject() {}
    
    public void init(int x, int y, Object[] args) { //Init object (object tag in level/settings)
        this.x = x; this.y = y;
    } 
    
    public void saveLoadRms(DataInputStream dis, DataOutputStream dos) {} //Save or load info to/from rms
    
    public byte[] sendData() {return null;} //Send data to cliend
    public void receiveData(byte[] data) {} //Receive data from server
    
    public void update(World world) {}
    
    public void paint(World world) {}
    
    public void activate(World world, Player player, boolean clicked) {}
    
    public boolean checkCollision(GameObject obj) {
        int mulrad = (obj.radius+radius)*(obj.radius+radius);
        return (MathEngine.distance(x, y, obj.x, obj.y)<=mulrad);
    }
    
    public void sphereCollision(GameObject obj) {
        int mulrad = (obj.radius+radius)*(obj.radius+radius);
        if(MathEngine.distance(x, y, obj.x, obj.y)>mulrad) return;
        
        
    }
    
    public void physicsUpdate(World world) {
        if(!world.collisionCheck(x+xSpeed, y, this)) x+=xSpeed;
        if(!world.collisionCheck(x, y+ySpeed, this)) y+=ySpeed;
        
        xSpeed = ySpeed = 0;
    }
    
}
