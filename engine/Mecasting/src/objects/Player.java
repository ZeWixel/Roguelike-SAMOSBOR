package objects;

import java.io.DataInputStream;
import java.io.DataOutputStream;


/**
 *
 * @author Roman Lahin
 */
public class Player extends AliveObject {

    public int[] items;
    public int[] itemsCount;
    public int maxItems = 0;
    
    public Player() {
        physics = true;
        storeInRms = true;
        items = new int[64];
        itemsCount = new int[64];
    }
    
    public void saveLoadRms(DataInputStream dis, DataOutputStream dos) {
        try {
            if(dis!=null) {
                x = dis.readInt();
                y = dis.readInt();
                maxItems = dis.readInt();
                items = new int[maxItems<64?64:maxItems];
                
                for(int i=0;i<maxItems;i++) {
                    items[i] = dis.readInt();
                    itemsCount[i] = dis.readInt();
                }
            } else {
                dos.writeInt(x);
                dos.writeInt(y);
                dos.writeInt(maxItems);
                
                for(int i=0;i<maxItems;i++) {
                    dos.writeInt(items[i]);
                    dos.writeInt(itemsCount[i]);
                }
            }
            
            
        } catch (Exception e) {
            System.out.println("Error in writing/reading player save");
        }
    }
    
    public void addItem(int id) {
        
        for(int i=0;i<maxItems;i++) {
            if(items[i]==id) {
                itemsCount[i]++; return;
            }
        }
        
        if(maxItems>=items.length) {
            int[] newItems = new int[(items.length/64)*64+64];
            int[] newItemsCount = new int[newItems.length];
            
            System.arraycopy(items, 0, newItems, 0, items.length);
            System.arraycopy(itemsCount, 0, newItemsCount, 0, itemsCount.length);
            
            items = newItems; itemsCount = newItemsCount;
        }
        items[maxItems] = id;
        itemsCount[maxItems] = 1;
        
        maxItems++;
    }
    
    public void removeItem(int id) {
        
        for(int i=0;i<maxItems;i++) {
            if(items[i]==id) {
                itemsCount[i]--;
                
                if(itemsCount[i]==0) {
                    maxItems--;
                    items[i] = items[maxItems];
                    itemsCount[i] = itemsCount[maxItems];
                }
                return;
            }
        }
        
    }
    
    public int itemsCount(int id) {
        int count = 0;
        
        for(int i=0;i<maxItems;i++) {
            if(items[i]==id) count+=itemsCount[i];
        }
        
        return count;
    }
    
}
