package objects;

import classes.game.GameObject;

/**
 *
 * @author Roman Lahin
 */
public class Column extends GameObject{
    
    public int height = 4096;
    
    public Column() {
        visible = true; //Visible
        collider = true; //Collidable
    }
    
}
