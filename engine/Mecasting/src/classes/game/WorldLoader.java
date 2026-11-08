package classes.game;

import classes.StringTools;
import classes.Texture;
import objects.Player;

/**
 *
 * @author Roman Lahin
 */
public class WorldLoader {
    
    public static void loadMap(World world, String path) {
        boolean textFile = false; //Tex file or csv
        char divider = '!';
        if(path.toLowerCase().endsWith(".txt")) textFile = true;
        else if(path.toLowerCase().endsWith(".csv")) divider = ',';
        else if(path.toLowerCase().endsWith(".ssv")) divider = ';';
        else if(path.toLowerCase().endsWith(".tsv")) divider = '|';
        
        if(divider=='!' && !textFile) (new Exception("Unsupported map format!")).printStackTrace();
            
        String mapInfo = StringTools.getStringFromResource(path);
        String[] mapLines = StringTools.cutOnStrings(mapInfo, '\n'); //Split map file into lines
        
        String[][] fileMap = null; 
        if(!textFile) { //Split map file into cells to load DSV format
            fileMap = new String[mapLines.length][];
            for(int i=0;i<mapLines.length;i++) {
                fileMap[i] = StringTools.cutOnStringsWithNulls(mapLines[i], divider);
            }
        }
        
        if(textFile) world.mapWidth = mapLines[0].length(); //Get line length
        else world.mapWidth = fileMap[0].length; 
        
        world.mapHeight=0;
        for(;world.mapHeight<mapLines.length;world.mapHeight++) { //Calculate map height
            if(mapLines[world.mapHeight].startsWith("map end")) break;
        }
        
        if(world.mapHeight==0) (new Exception("Can't find 'map end' line in map file")).printStackTrace();
        
        int texBegin = 0;
        int texEnd = 0;
        
        int floorTexLine = 0; //Sections lines
        int ceilingTexLine = 0;
        int heightsMapLine = 0;
        int repeatTexsLine = 0;
        int fogLine = 0;
        int skyColLine = 0; //Todo: load these values from settings txt
        int floorColLine = 0;
        
        byte defaultFloorTex = 0; //Default values (todo: load def values from settings txt)
        byte defaultCeilingTex = 0;
        byte defaultHeight = 0;
        boolean defaultRepeatTexs = false;
        for(int i=world.mapHeight;i<mapLines.length;i++) { //Find some sections and default values for map
            String line = mapLines[i];
            if(line.startsWith("textures begin")) texBegin = i; //textures section begin
            else if(line.startsWith("textures end")) {texEnd = i;} //textures section end
            else if(line.startsWith("floor texture")) {floorTexLine = i;} //floor texture map
            else if(line.startsWith("ceiling texture")) {ceilingTexLine = i;} //ceiling texture map
            else if(line.startsWith("wall height")) {heightsMapLine = i;} //wall height map
            else if(line.startsWith("texture repeat")) {repeatTexsLine = i;} //texture repeat map
            else if(line.startsWith("fog")) {fogLine = i;} //fog texture line
            else if(line.startsWith("skip floorcast")) {world.skipFloorcast=true;} 
            else if(line.startsWith("sky color")) {skyColLine = i;} //sky color line
            else if(line.startsWith("floor color")) {floorColLine = i;} //sky color line
            
            else if(line.startsWith("default floor texture")) { //default floor texture
                defaultFloorTex = Byte.parseByte(StringTools.deleteNonNumeric(line.substring(22)));
            } else if(line.startsWith("default ceiling texture")) { //default ceiling texture
                defaultCeilingTex = Byte.parseByte(StringTools.deleteNonNumeric(line.substring(24)));
            } else if(line.startsWith("default wall height")) { //default wall height
                defaultHeight = /*(byte)Texture.genWBits(*/Byte.parseByte(StringTools.deleteNonNumeric(line.substring(20)))/*)*/;
            } else if(line.startsWith("default texture repeat")) { //default texture repeat
                defaultRepeatTexs = mapLines[i].charAt(23)=='1';
            } 
        }
        
        world.fog = new short[world.sh]; //Load fog values
        if(fogLine==0) {
            for(int y=0;y<world.sh;y++) {
                world.fog[y] = 0xff;
            }
        } else {
            String fg;
            if(textFile) fg = mapLines[fogLine];
            else fg = fileMap[fogLine][0];
            
            Texture fogTex = Texture.loadTexture(fg.substring(4));
            
            for(int y=0;y<world.sh;y++) {
                world.fog[y] = (short) (fogTex.rgb[y*fogTex.h/world.sh]&0xff);
            }
        }
        
        if(skyColLine!=0) { //Load sky color
            String line;
            if(textFile) line = mapLines[skyColLine];
            else line = fileMap[skyColLine][0];
            
            world.skyColor = StringTools.getRGB(line.substring(10));
        }
        if(floorColLine!=0) { //Load sky color
            String line;
            if(textFile) line = mapLines[floorColLine];
            else line = fileMap[floorColLine][0];
            
            world.floorColor = StringTools.getRGB(line.substring(12));
        }
        
        if(texBegin==0 || texEnd==0) { // Load default 9 textures if map didnt contain textures info
            System.out.println("Can't find textures section! loading 10 standart textures");
            texturesLoader(world,10);
        } else { //Load user-defined textures
            world.textures = new Texture[texEnd-texBegin-1];
            Texture[] textures = world.textures;
            
            for(int i=0;i<textures.length;i++) {
                String texPath;
                boolean dark = false;
                
                if(textFile) texPath = mapLines[texBegin+i+1];
                else texPath = fileMap[texBegin+i+1][0];
                
                if(texPath.endsWith(" (dark)")) {
                    texPath = texPath.substring(0, texPath.length()-7);
                    dark = true;
                }
                    
                textures[i] = Texture.loadTexture(texPath);
                if(textures[i]==null) (new Exception("Can't load texture "+texPath)).printStackTrace();
                
                if(dark) {
                    textures[i-1].dark = true;
                    textures[i-1].darkTex = textures[i];
                }
            }
        }
        
        world.map = new byte[world.mapWidth*world.mapHeight];
        world.mapFloor = new byte[world.map.length];
        world.mapCeiling = new byte[world.map.length];
        world.mapHeightBit = new byte[world.map.length];
        world.mapTexRepeat = new boolean[world.map.length];
        world.mapIsWall = new boolean[world.map.length];
        
        for(int x=0; x<world.mapWidth;x++) {
            for(int y=0; y<world.mapHeight; y++) {
                int xx = x+world.mapWidth*y;
                
                byte wall= -1; //Set some default values
                byte floor = defaultFloorTex;
                byte ceiling = defaultCeilingTex;
                byte height = defaultHeight;
                boolean texRepeat = defaultRepeatTexs;
                
                char tile;
                if(textFile) { //TXT loader
                    tile = mapLines[y].charAt(x);
                    
                    if(tile>='0' && tile<='9') { //Load wall tile
                    wall = (byte)(tile-'0'); //Set texture
                    tile = ' '; //Reset character to skip objects testing
                    }
                    
                    if(floorTexLine>0) floor = (byte)(mapLines[floorTexLine+y+1].charAt(x)-'0'); //Load floor
                    if(ceilingTexLine>0) ceiling = (byte)(mapLines[ceilingTexLine+y+1].charAt(x)-'0'); //Load ceiling
                    if(heightsMapLine>0) height = (byte)/*Texture.genWBits((byte)*/(mapLines[heightsMapLine+y+1].charAt(x)-'0')/*)*/; //Load wall height
                    if(repeatTexsLine>0) texRepeat = mapLines[repeatTexsLine+y+1].charAt(x)=='1'; //Load tex repeat
                } else { //DSV Loader
                    String text = fileMap[y][x];
                    tile = ' '; //Skip objects testing by default
                    
                    if(text != null) { 
                        boolean isNumeric = StringTools.isNumeric(text);
                        
                        if(!isNumeric && text.charAt(text.length()-1)=='r' && text.charAt(text.length()-1)=='s'  
                                && StringTools.isNumeric(text.substring(0,text.length()-1))) { //s or r at end of tex id means scale or repeat
                            
                            texRepeat = text.charAt(text.length()-1)=='r'; //tes texture repeat
                            text = text.substring(0,text.length()-1); //crop texture id
                            isNumeric = true; //set to numeric
                        }
                            
                        if(isNumeric) wall = StringTools.parseByte(text);
                        else tile = text.charAt(0); //No wall found so game should read objects instead
                    }
                    
                    
                    if(floorTexLine>0 && fileMap[floorTexLine+y+1][x]!=null) { //Load floor
                        floor = StringTools.parseByte(fileMap[floorTexLine+y+1][x]); 
                    }
                    if(ceilingTexLine>0 && fileMap[ceilingTexLine+y+1][x]!=null) { //Load ceiling
                        ceiling = StringTools.parseByte(fileMap[ceilingTexLine+y+1][x]); 
                    }
                    if(heightsMapLine>0 && fileMap[heightsMapLine+y+1][x]!=null) {  //Load wall height
                        height = (byte)/*Texture.genWBits(*/StringTools.parseByte(fileMap[heightsMapLine+y+1][x])/*)*/;
                    }
                    if(repeatTexsLine>0 && fileMap[repeatTexsLine+y+1][x]!=null) { //Load tex repeat
                        texRepeat = fileMap[repeatTexsLine+y+1][x].charAt(0)=='1';
                    }
                }
                
                world.map[xx] = (byte)(wall+1);
                if(wall!=-1) world.mapIsWall[xx]=true;
                world.mapFloor[xx] = floor; 
                world.mapCeiling[xx] = ceiling;
                world.mapHeightBit[xx] = height;
                world.mapTexRepeat[xx] = texRepeat;
                if(tile == ' ') continue;
                        
                if(tile=='p') { //Load player
                    Player p = world.players[world.currentPlayer];
                    p.x=x*4096+2048;
                    p.y=y*4096+2048;
                    world.allObjects.addElement(p);
                    world.objects.addElement(p);
                }
            }
        }
        
    }
    
    public static void texturesLoader(World world, int count) {
        world.textures = new Texture[count];
        Texture[] textures = world.textures;
        
        for(int i=0;i<textures.length;i++) {
            textures[i] = Texture.loadTexture("/textures/"+i+".png");
            if(textures[i]==null) return;
        }
    }
}
