package classes;

import java.io.InputStream;
import java.util.Vector;

public class StringTools {

    public static String getStringFromResource(String file) {
        InputStream is = null;

        try {
            is = (new Object()).getClass().getResourceAsStream(file);
            StringBuffer str = new StringBuffer();

            int ch;
            while ((ch = is.read()) != -1) {
                ch = ch >= 192 && ch <= 255?ch + 848:ch;
                if (ch != '\r') str.append((char) ch);
            }

            is.close();
            return str.toString();
        } catch (Exception exc) {
            System.out.println("Reading txt error: " + exc);
            if (is != null) {
                try {
                    is.close();
                } catch (Exception ex) {

                }
            }

            return null;
        }
    }
    
    private static Vector fragments(String str, char d, boolean addEmpty) {
      Vector fragments = new Vector();
      int end = 0;

      while(end < str.length()) {
            while(end < str.length()) {
                if(str.charAt(end) == d) {
                    if(addEmpty && end<str.length()-1 && str.charAt(end+1)==d) fragments.addElement(null);
                    end++;
                }
            else break;
            }

            int begin;
            for(begin = end; end < str.length(); end++) {
                if(str.charAt(end) == d) break;
            }

            if(begin < end) {
                String frg = str.substring(begin, end);
                fragments.addElement(frg);
            }
      }

      return fragments;
   }

    public static String[] cutOnStrings(String str, char d) {
        Vector fragments = fragments(str, d, false);
        String[] out = new String[fragments.size()];
        fragments.copyInto(out);
        return out;
    }
    
    public static int[] cutOnInts(String str, char d) {
        Vector fragments = fragments(str, d, false);
        int[] out = new int[fragments.size()];
        
        for(int i=0;i<out.length;i++) {
            out[i] = parseInt((String)fragments.elementAt(i));
        }
        
        return out;
    }
    
    public static String[] cutOnStringsWithNulls(String str, char d) {
        Vector fragments = fragments(str, d, true);
        String[] out = new String[fragments.size()];
        fragments.copyInto(out);
        return out;
    }
    
    public static int parseInt(String val) {
        return Integer.parseInt(deleteExtremeSpaces(val));
    }
    
    public static byte parseByte(String val) {
        return Byte.parseByte(deleteExtremeSpaces(val));
    }
    
    public static byte parseByte(String val, int def) {
        if(val==null) return (byte)def;
        return Byte.parseByte(deleteExtremeSpaces(val));
    }
    

    public static String deleteExtremeSpaces(String s) {
        while (s.length() > 0 && s.charAt(0) == ' ') {
            s = s.substring(1, s.length());
        }
        while (s.length() > 0 && s.charAt(s.length() - 1) == ' ') {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
    
    public static boolean isNumeric(String s) {
        if(s == null) return false;
        s = deleteExtremeSpaces(s);
        for(int i=0;i<s.length();i++) {
            char ch = s.charAt(i);
            if(ch!='-' && (ch<'0' || ch>'9')) return false;
        }
        return true;
    }
    
    public static String deleteNonNumeric(String s) {
        while (s.length() > 0 && s.charAt(0) != '-' && (s.charAt(0)<'0' || s.charAt(0)>'9')) {
            s = s.substring(1, s.length());
        }
        while (s.length() > 0 && s.charAt(s.length() - 1) != '-' && (s.charAt(s.length() - 1)<'0' || s.charAt(s.length() - 1)>'9')) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
    
    public static int getRGB(String rgbs) {
        int[] rgb = cutOnInts(rgbs,' ');
        
        return (rgb[0]<<16) | (rgb[1]<<8) | rgb[0];
    }

}
