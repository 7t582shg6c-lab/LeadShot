package dev.leadshot;

/** Responsive placement in scaled GUI pixels; avoids vanishing behind hotbar and offscreen. */
public final class FlightHudLayout {
    private FlightHudLayout() {}
    public record Frame(int x,int y,int width,int height) {
        public boolean onScreen(int screenWidth,int screenHeight){
            return x>=0&&y>=0&&width>0&&height>0&&x+width<=screenWidth&&y+height<=screenHeight;
        }
    }
    public record Layout(Frame telemetry,Frame systems,Frame cue,boolean compact) {}

    /** Reduce telemetry density before large user fonts overflow a short screen.
     * Layout decisions depend on actual scaled text rows, not only GUI height.
     */
    public static int effectiveMode(int requested, int height, double fontScale, boolean audit) {
        int mode = Math.max(0, Math.min(2, requested));
        double scale = Math.max(.5, Math.min(2.0, fontScale));
        int row = Math.max(11, (int)Math.ceil(13*scale));
        int available = Math.max(0, height - 12);
        if (mode == 2 && available < 18 + (audit ? 10 : 9)*row) mode = 1;
        if (mode == 1 && available < 18 + 7*row) mode = 0;
        return mode;
    }
    /** Conservative glyph-width estimate to prevent HUD labels spilling offscreen.
     * Japanese/wide glyphs are charged more space than ASCII; gameplay data is
     * shortened with a visible ellipsis instead of drawing outside the viewport.
     */
    public static String fitText(String text, int availablePixels, double scale) {
        if (text == null || availablePixels < 1 || !Double.isFinite(scale) || scale <= 0) return "";
        int budget=(int)Math.floor(availablePixels/scale);
        if (budget < 6) return "";
        int consumed=0;
        StringBuilder clipped=new StringBuilder();
        int[] points=text.codePoints().toArray();
        for (int cp:points) {
            int width=cp>127?10:6;
            if (consumed+width>budget) {
                while(consumed+10>budget && clipped.length()>0){
                    int last=clipped.codePointBefore(clipped.length());
                    clipped.setLength(clipped.length()-Character.charCount(last));
                    consumed-=last>127?10:6;
                }
                if(consumed+10<=budget)clipped.append('…');
                break;
            }
            clipped.appendCodePoint(cp);
            consumed+=width;
        }
        return clipped.toString();
    }
    public static Layout calculate(int w,int h,int mode,double scale){
        if(w<100||h<80)throw new IllegalArgumentException("GUI too small");
        double s=Math.max(.5,Math.min(2.0,scale));
        int inset=6;
        boolean compact=w<550||h<225;
        int width=Math.min(w-2*inset,Math.max(190,(int)Math.round((compact?225:254)*s)));
        int targetHeight=mode==0?56:mode==1?123:189;
        int panelHeight=Math.min(h-2*inset,Math.max(42,(int)Math.round(targetHeight*s)));
        Frame tele=new Frame(inset,inset,width,panelHeight);
        int systemWidth=Math.min(182,(int)Math.round(166*s));
        Frame sys=null;
        if(!compact && w>=width+systemWidth+3*inset)
            sys=new Frame(w-systemWidth-inset,inset,systemWidth,Math.min(h-2*inset,(int)Math.round(83*s)));
        int cueWidth=Math.min(176,w-2*inset);
        int cueY=Math.min(h-22,Math.max(inset,h/2+32));
        Frame cue=new Frame((w-cueWidth)/2,cueY,cueWidth,17);
        return new Layout(tele,sys,cue,compact);
    }
}
