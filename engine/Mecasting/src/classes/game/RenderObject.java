package classes.game;

import classes.game.World;

/**
 *
 * @author Roman Lahin
 */
public abstract class RenderObject {
    public int z;
    
    public abstract void paint(World world);
}
