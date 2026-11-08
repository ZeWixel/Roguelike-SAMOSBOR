package classes.game;

import classes.Main;
import classes.MathEngine;
import classes.Texture;
import java.util.Vector;
import javax.microedition.lcdui.Graphics;
import objects.Player;

/**
 *
 * @author Roman Lahin
 */
public class World {
    private Game game;
    private Main midlet;
    
    public final int sw,sh; //Buffer resolution
    public int[] screen; //Screen buffer
    public int[] zBuffer; //Z-Buffer
    
    public int mapWidth,mapHeight; //Map size
    //Using 1D massives instead 2D because 1D is faster
    // map[x + mapWidth*y]
    public byte[] map; //Map (wall textures)
    public byte[] mapFloor; //Floor and ceiling map [x][y][floor or ceiling]
    public byte[] mapCeiling; //Floor and ceiling map [x][y][floor or ceiling]
    public byte[] mapHeightBit; //Wall height in bits
    public boolean[] mapTexRepeat; //Repeat wall textures or not
    public boolean[] mapIsWall; //Walls map; air = false; wall = true;
    public boolean[] seenMap; //Seen tiles
    public Texture[] textures;
    
    public short[] fog; //Fog values
    private int[] yToDistance; //Some lookup tables
    private int[] deltaDistDivider;
    
    final int baseDirX = -4096, baseDirY = 0; //Initial camera direction vector
    final int basePlaneX = 0;
    int basePlaneY = -1638; //Initial 2d raycaster version of camera plane
    
    int posX = 6144, posY = 6144;  //X and y camera start position
    int dirX = baseDirX, dirY = baseDirY; //Camera direction vector
    int planeX = basePlaneX, planeY = basePlaneY; //The 2d raycaster version of camera plane
    
    public int skyColor = -1; //No sky
    public int floorColor = -1; //No floor
    public boolean skipFloorcast;
    
    public Vector allObjects = new Vector();
    public Vector objects = new Vector();
    
    public Player[] players;
    public int currentPlayer = 0;
    
    public RenderObject[] renderObjects = new RenderObject[768];
    public int renderObjectsCount = 0;
    
    
    World (Main midlet, Game game, String worldPath, int sw, int sh, int playersCount) {
        this.midlet = midlet;
        this.game = game;
        
        this.sw = sw; this.sh = sh;
        if(sw>sh) {
            basePlaneY = planeY = -2703; // 4/3 aspect ratio
        } else if(sw==sh) {
            basePlaneY = planeY = -2048; // Square screen
        }

        screen = new int[sw*sh];
        zBuffer = new int[sw];
        
        players = new Player[playersCount];
        for(int i=0;i<players.length;i++) {
            players[i] = new Player();
        }
        
        WorldLoader.loadMap(this, worldPath);
        seenMap = new boolean[map.length];
        
        precacheLookupTables();
    }
    
    private void precacheLookupTables() {
        yToDistance = new int[sh];
        
        for(int y=sh/2+1;y<sh-1;y++) {
            int val = sh * 4096 / (2 * y - sh);
            yToDistance[y] = yToDistance[sh-y] = val;
        }
        yToDistance[sh/2] = yToDistance[sh/2+1];
        
        deltaDistDivider = new int[16384];
        for(int x=0;x<8192;x++) {
            deltaDistDivider[x] = MathEngine.FPSQR / (x-8192);
        }
        for(int x=8193;x<deltaDistDivider.length;x++) {
            deltaDistDivider[x] = MathEngine.FPSQR / (x-8192);
        }
        
    }
    
    private void renderGraphics() {
        
        int halfHeight = sh>>1;
        
        int cameraDeltaX = 8192 / sw; //delta of x-coordinate in camera space
        int cameraX = -cameraDeltaX/2*sw; //begining x-coordinate in camera space
        short fogWall;
        //Just vars initialization
        
        //#ifdef X2Scaling
        cameraDeltaX <<= 1;
        for (int x = 0; x < sw; x+=2, cameraX+=cameraDeltaX) {
        //#else
//#         for (int x = 0; x < sw; x++, cameraX+=cameraDeltaX) {
        //#endif
            //calculate ray position and direction
            int rayDirX = dirX + (planeX * cameraX >> MathEngine.fp);
            if(rayDirX==0) rayDirX=1;
            int rayDirY = dirY + (planeY * cameraX >> MathEngine.fp);
            if(rayDirY==0) rayDirY=1;

            //which box of the map we're in
            int mapX = posX & 0xfffff000;
            int mapY = posY & 0xfffff000;

            //length of ray from current position to next x or y-side
            int sideDistX;
            int sideDistY;

            //length of ray from one x or y-side to next x or y-side
            int deltaDistXNorm = deltaDistDivider[8192+rayDirX]; //Use it to avoid some divs on perpWallDist
            int deltaDistX = deltaDistXNorm<0?-deltaDistXNorm:deltaDistXNorm; //Kinda abs
            int deltaDistYNorm = deltaDistDivider[8192+rayDirY]; //Use it to avoid some divs on perpWallDist 
            int deltaDistY = deltaDistYNorm<0?-deltaDistYNorm:deltaDistYNorm; //Kinda abs
            int perpWallDist;

            //what direction to step in x or y-direction (either +1 or -1)
            int stepX;
            int stepY;

            boolean side = false; //was a NS or a EW wall hit?
            int stepDistX = 0; //Simple perpWallDist optimization
            int stepDistY = 0;

            //calculate step and initial sideDist
            if(rayDirX < 0) {
                stepX = -4096;
                stepDistX = 4096; // (2048 - stepX*2048)
                sideDistX = -((posX & 4095) << MathEngine.fp) / rayDirX; //(posX - mapX) * deltaDistX >> MathEngine.fp
            } else {
                stepX = 4096;
                sideDistX = ((mapX + 4096 - posX) << MathEngine.fp) / rayDirX;
            }
            if(rayDirY < 0) {
                stepY = -4096;
                stepDistY = 4096; // (2048 - stepY*2048)
                sideDistY = -((posY & 4095) << MathEngine.fp) / rayDirY; //(posY - mapY) * deltaDistY >> MathEngine.fp
            } else {
                stepY = 4096;
                sideDistY = ((mapY + 4096 - posY) << MathEngine.fp) / rayDirY;
            }
            
            int pos;
            //perform DDA
            while(true) {
                //jump to next map square, OR in x-direction, OR in y-direction
                if (sideDistX < sideDistY) {
                    sideDistX += deltaDistX;
                    mapX += stepX;
                    side = false;
                } else {
                    sideDistY += deltaDistY;
                    mapY += stepY;
                    side = true;
                }
                
                seenMap[(pos = (mapX>>12)+mapWidth*(mapY>>12))] = true; //Player see this tile
                //Check if ray has hit a wall
                if(mapIsWall[pos]) break;//I didnt use boolean "hit" cause it useless
            }
            
            //Calculate distance of perpendicular ray (Euclidean distance will give fisheye effect!)
            if (!side) perpWallDist = (mapX - posX + stepDistX) * deltaDistXNorm >> MathEngine.fp;
            else perpWallDist = (mapY - posY + stepDistY) * deltaDistYNorm >> MathEngine.fp;
            if(perpWallDist<=64) perpWallDist = 64;
            int perpWallDistNorm = MathEngine.FPSQR / perpWallDist;
            
            //#ifdef X2Scaling
            zBuffer[x+1] = 
            //#endif
            zBuffer[x]=perpWallDist;

            //Calculate height of line to draw on screen
            byte tileHeightBit = mapHeightBit[pos];
            int lineHeight = (sh * perpWallDistNorm >> MathEngine.fp) * tileHeightBit;

            //texturing calculations
            int texNum = map[pos] - 1; //1 subtracted from it so that texture 0 can be used!
            Texture tex = textures[texNum]; //1 subtracted from it so that texture 0 can be used!
            
            //y coordinate on the texture
            int texYDelta = (tex.h<<15)/lineHeight;
            if(mapTexRepeat[pos]) texYDelta/=tileHeightBit;
            int texY = 0;
            
            //calculate lowest and highest pixel to fill in current stripe
            int drawEnd = (lineHeight/tileHeightBit>>1) + halfHeight;
            int drawStart = drawEnd - lineHeight;
            if (drawStart < 0) { texY = -drawStart*texYDelta; drawStart = 0;} //lowest pixel fix + edit y coordinate on the texture
            drawEnd++;
            if (drawEnd >= sh) drawEnd = sh - 1;
            

            //calculate value of wallX
            int wallX; //where exactly the wall was hit
            if (!side) wallX = posY + ((perpWallDist * rayDirY) >> MathEngine.fp) & 4095;
            else wallX = posX + ((perpWallDist * rayDirX) >> MathEngine.fp) & 4095;

            //x coordinate on the texture
            int texX = ((wallX << tex.wBits) >> MathEngine.fp) & tex.wLen;
            if ((!side && rayDirX < 0) || (side && rayDirY > 0)) texX = tex.w - texX - 1;
            
            if(side && !tex.dark) fogWall = fog[sh-drawEnd];
            else fogWall = fog[drawEnd];
            
            if(tex.dark) tex = tex.darkTex;
            
            renderWall(drawStart, drawEnd, texY, texYDelta, texX, tex, x, fogWall);
            if(skipFloorcast) continue;
            
            //FLOOR CASTING
            int floorXWall, floorYWall; //x, y position of the floor texel at the bottom of the wall

            //4 different wall directions possible
            if (!side && rayDirX > 0) {
                floorXWall = mapX;
                floorYWall = mapY + wallX;
            } else if (!side && rayDirX < 0) {
                floorXWall = mapX + 4096;
                floorYWall = mapY + wallX;
            } else if (side && rayDirY > 0) {
                floorXWall = mapX + wallX;
                floorYWall = mapY;
            } else {
                floorXWall = mapX + wallX;
                floorYWall = mapY +  4096;
            }

            if (drawEnd < 0) drawEnd = sh-1; //becomes < 0 when the integer overflows
            
            renderFloorAndCeiling(drawEnd, perpWallDistNorm, floorXWall, floorYWall, x);
        }
    }
    
    public void renderWall(int drawStart, int drawEnd, int texY, 
            final int texYDelta, int texX, final Texture tex, 
            final int x, final short fogWall) {
        
        drawEnd *= sw;
        int col, y = drawStart * sw;
        int x1 = x+1;
        int w2 = sw+x;
        int w21 = w2+1;
        int w3 = sw*2+x;
        int w31 = w3+1;
        int w4 = sw*3;
        
        while(drawEnd-y>w4) {
            col = tex.rgb[(tex.w * (texY>>15) + texX)&tex.len];
            //#if X2Scaling
            screen[y + x1] =
            //#endif
            screen[y + x] = ((((col & 0xFF00FF) * fogWall) & 0xFF00FF00)
                    | (((col & 0x00FF00) * fogWall) & 0x00FF0000)) >>> 8;
            
            col = tex.rgb[(tex.w * ((texY+=texYDelta)>>15) + texX)&tex.len];
            //#if X2Scaling
            screen[y + w21] =
            //#endif
            screen[y + w2] = ((((col & 0xFF00FF) * fogWall) & 0xFF00FF00)
                    | (((col & 0x00FF00) * fogWall) & 0x00FF0000)) >>> 8;
            
            
            col = tex.rgb[(tex.w * ((texY+=texYDelta)>>15) + texX)&tex.len];
            //#if X2Scaling
            screen[y + w31] =
            //#endif
            screen[y + w3] = ((((col & 0xFF00FF) * fogWall) & 0xFF00FF00)
                    | (((col & 0x00FF00) * fogWall) & 0x00FF0000)) >>> 8;
            texY+=texYDelta;
            y+=w4;
        }
        
        for (; y < drawEnd; y+=sw, texY+=texYDelta) {
            col = tex.rgb[(tex.w * (texY>>15) + texX)&tex.len];
            //#if X2Scaling
            screen[y + x1] =
            //#endif
            screen[y + x] = ((((col & 0xFF00FF) * fogWall) & 0xFF00FF00)
                    | (((col & 0x00FF00) * fogWall) & 0x00FF0000)) >>> 8;
        }
    }
    
    public void renderFloorAndCeiling(final int drawEnd, final int perpWallDistNorm, final int floorXWall, final int floorYWall, final int x) {
        
        int col, weight, currentFloorX, currentFloorY, currentMap, tPos;
        short fogFloor;
        byte ceilingTex;
        //draw the floor from drawEnd to the bottom of the screen
        Texture floorTex;
        
        //#ifdef X2Scaling
        int x1 = x + 1;
        //#endif
            
        for (int y = drawEnd; y < sh; y++) { //use y < sh-1 if you had some array errors here

            weight = yToDistance[y] * perpWallDistNorm >> MathEngine.fp; //lookup table
            
            currentFloorX = (weight * floorXWall + (MathEngine.FP - weight) * posX)>>MathEngine.fp;
            currentFloorY = (weight * floorYWall + (MathEngine.FP - weight) * posY)>>MathEngine.fp;
            currentMap = (currentFloorX>>12)+(currentFloorY>>12)*mapWidth;
            
            floorTex = textures[mapFloor[currentMap]];
            
            //floor
            fogFloor = fog[y];
            
            col = floorTex.rgb[(tPos=(
                    ((currentFloorY << floorTex.hBits >> MathEngine.fp) & floorTex.hLen)<<floorTex.wBits) + 
                    ((currentFloorX << floorTex.wBits >> MathEngine.fp) & floorTex.wLen)
                    )];
            //#ifdef X2Scaling
            screen[y*sw + x1] =
            //#endif
            screen[y*sw + x] = ((((col & 0xFF00FF) * fogFloor) & 0xFF00FF00)
                    | (((col & 0x00FF00) * fogFloor) & 0x00FF0000)) >>> 8; 
                
            ceilingTex = mapCeiling[currentMap];
            if(ceilingTex>=0) { //ceiling (symmetrical!) (-1 means no ceiling)
            col = textures[ceilingTex].rgb[tPos];
            //#ifdef X2Scaling
            screen[(sh - y)*sw + x1] =
            //#endif
            screen[(sh - y)*sw + x] = ((((col & 0xFF00FF) * fogFloor) & 0xFF00FF00)
                    | (((col & 0x00FF00) * fogFloor) & 0x00FF0000)) >>> 8;
            }
        }
        /*
        //Backup
            floorTexX = (currentFloorX << floorTex.wBits >> MathEngine.fp) & floorTex.wLen;
            floorTexY = (currentFloorY << floorTex.hBits >> MathEngine.fp) & floorTex.hLen;
            
            //floor
            fogFloor = fog[y];
            
            col = floorTex.rgb[(floorTexY<<floorTex.wBits) + floorTexX];
        */
    }
    
    public void clearSeenMap() {
        for(int x=0;x<seenMap.length;x++) {
            seenMap[x]=false;
        }
    }
    
    public void paint(Graphics g, int w,int h) {
        //Fill sky and floor with one color
        if(skyColor!=-1) for(int i=0;i<screen.length>>1;i++) { screen[i]=skyColor; } 
        if(floorColor!=-1) for(int i=screen.length>>1;i<screen.length;i++) { screen[i]=floorColor; }
        
        clearSeenMap(); //Reset visible tiles map
        renderGraphics(); //Render map
        renderObjects(); //Render objects
        
        g.setColor(0); //Top and bottom line
        g.drawLine(0, 0, w, 0); 
        g.drawLine(0, h-1, w, h-1);
        
        g.drawRGB(screen, sw, sw, 0, 1+(h-sh)/2, sw, sh-2, false); //Skip top and botton line cause they look like shit
    }
    
    public void renderObjects() {
        
        for(int i=0;i<objects.size();i++) { //Visible objects
            GameObject obj = (GameObject)objects.elementAt(i);
            if(!obj.visible) continue;
            
            int x = obj.x;
            int y = obj.y;
            
            boolean visible = seenMap[x+y*mapHeight] || seenMap[x-1+y*mapHeight] || seenMap[x+1+y*mapHeight] ||
                    seenMap[x+(1+y)*mapHeight] || seenMap[x-1+(1+y)*mapHeight] || seenMap[x+1+(1+y)*mapHeight] ||
                    seenMap[x+(y-1)*mapHeight] || seenMap[x-1+(y-1)*mapHeight] || seenMap[x+1+(y-1)*mapHeight];
            
            if(visible) obj.paint(this);
        }
        
        
        
    }
    
    public void addRenderObject(RenderObject obj) {
        
    }
    
    
    public void update() {
        Player player = players[currentPlayer];
        
        for(int i=0;i<objects.size();i++) { //Update existings objects
            GameObject obj = (GameObject)objects.elementAt(i);
            obj.update(this);
            if(obj.physics) obj.physicsUpdate(this);
        }
        
        for(int i=0;i<objects.size();i++) { //Actitave objects
            GameObject obj = (GameObject)objects.elementAt(i);
            if(!obj.activable) continue;
            
            if(MathEngine.distance(player.x, player.x, obj.x, obj.y)<2048*2048) obj.activate(this, player, false);
        }
        
        
        
        for(int i=0;i<objects.size();i++) { //Sphere-like physics
            GameObject obj = (GameObject)objects.elementAt(i);
            if(!obj.physics) continue;
            
            for(int i2=i+1;i2<objects.size();i2++) {
                GameObject obj2 = (GameObject)objects.elementAt(i2);
                if(!obj2.physics) continue;
                
                obj.sphereCollision(obj2);
            }
        }
        
        posX = player.x; posY = player.y;
    }
    
    public Player getPlayer() {
        return players[currentPlayer];
    }
    
    public void movePlayer(int xSpeed, int ySpeed) {
        players[currentPlayer].xSpeed += xSpeed;
        players[currentPlayer].ySpeed += ySpeed;
    }
    
    public void rotatePlayer(int direction) {
        int pDir = MathEngine.fixDegree(players[currentPlayer].direction+direction);
        players[currentPlayer].direction = pDir;
        
        dirX = MathEngine.rotateXVector(baseDirX, baseDirY, pDir);
        dirY = MathEngine.rotateYVector(baseDirX, baseDirY, pDir);
            
        planeX = MathEngine.rotateXVector(basePlaneX, basePlaneY, pDir);
        planeY = MathEngine.rotateYVector(basePlaneX, basePlaneY, pDir);
    }
    
    public boolean fastCollisionCheck(int x, int y, int radius) {
        final int xx = x>>12;
        final int yy = y>>12;
        final int xmr = (x-radius)>>12;
        final int xpr = (x+radius)>>12;
        final int ymr = (y-radius)>>12;
        final int ypr = (y+radius)>>12;
        
        return mapIsWall[xmr+mapWidth*ymr] || mapIsWall[xx+mapWidth*ymr] || mapIsWall[xpr+mapWidth*ymr] ||
                mapIsWall[xmr+mapWidth*yy] || mapIsWall[xx+mapWidth*yy] || mapIsWall[xpr+mapWidth*yy] ||
                mapIsWall[xmr+mapWidth*ypr] || mapIsWall[xx+mapWidth*ypr] || mapIsWall[xpr+mapWidth*ypr];
    }
    
    
    public boolean collisionCheck(int x, int y, GameObject curObj) {
        final int xx = x>>12;
        final int yy = y>>12;
        final int xmr = (x-curObj.radius)>>12;
        final int xpr = (x+curObj.radius)>>12;
        final int ymr = (y-curObj.radius)>>12;
        final int ypr = (y+curObj.radius)>>12;
        
        if(mapIsWall[xmr+mapWidth*ymr] || mapIsWall[xx+mapWidth*ymr] || mapIsWall[xpr+mapWidth*ymr] ||
                mapIsWall[xmr+mapWidth*yy] || mapIsWall[xx+mapWidth*yy] || mapIsWall[xpr+mapWidth*yy] ||
                mapIsWall[xmr+mapWidth*ypr] || mapIsWall[xx+mapWidth*ypr] || mapIsWall[xpr+mapWidth*ypr]) return true;
        
        for(int i=0;i<objects.size();i++) {
            GameObject obj = (GameObject)objects.elementAt(i);
            if(obj == curObj || !obj.collider) continue;
            
            if(obj.checkCollision(curObj)) return true;
        }
        
        return false;
    }
    
}
