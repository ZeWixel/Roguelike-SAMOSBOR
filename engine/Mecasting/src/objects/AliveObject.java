package objects;

import classes.game.GameObject;

/**
 *
 * @author Roman Lahin
 */
public abstract class AliveObject extends GameObject{
    
    public int hp = 100;
    public long lastDamage = -1l;
    public GameObject lastDamageBy;
    public int direction = 0;

    public AliveObject() {}
    
    public void damage(int dmg, GameObject dmgObj) {
        hp-=lastDamage;
        
        if(dmg>0) {
            if(dmgObj!=null) lastDamageBy = dmgObj;
            lastDamage = System.currentTimeMillis();
        }
    }
    
    
}
