package com.example.galaxyimageautoclick;

import android.graphics.Bitmap;
import java.util.Arrays;

/** Fast sampled pixel matcher. Exact-size screenshots; no AI, no scale invariance. */
public final class ImageMatcher {
    public static final class Result {
        public int x, y, width, height; public double score;
        Result(int x, int y, int w, int h, double s) {
            this.x=x;this.y=y;this.width=w;this.height=h;this.score=s;
        }
    }
    public static Result find(Bitmap screen, Bitmap template, double threshold) {
        if(screen == null || template == null) return null;
        int sw = screen.getWidth(), sh = screen.getHeight();
        int tw = template.getWidth(), th = template.getHeight();
        if(tw < 16 || th < 16 || tw > sw || th > sh) return null;
        int[] sc = new int[sw*sh], tp = new int[tw*th];
        screen.getPixels(sc, 0, sw, 0,0,sw,sh);
        template.getPixels(tp, 0, tw, 0,0,tw,th);
        // Anchor rejection plus dense sampled color verification.
        int[] ax = {1,3,5,7,2,6,4,1,7,4};
        int[] ay = {1,1,1,1,4,4,7,7,7,3};
        int step = Math.max(2, Math.min(5, Math.min(tw,th)/16));
        Result best = null;
        for(int y=0;y<=sh-th;y+=step) {
            for(int x=0;x<=sw-tw;x+=step) {
                int mismatch = 0;
                for(int k=0;k<ax.length;k++) {
                    int tx = Math.min(tw-1, (ax[k]*tw)/8), ty = Math.min(th-1, (ay[k]*th)/8);
                    int p=tp[ty*tw+tx], q=sc[(y+ty)*sw+x+tx];
                    int d = (Math.abs(((p>>16)&255)-((q>>16)&255)) +
                        Math.abs(((p>>8)&255)-((q>>8)&255)) + Math.abs((p&255)-(q&255)))/3;
                    if(d>48 && ++mismatch >= 4) break;
                }
                if(mismatch>=4) continue;
                long differences=0; int count=0;
                for(int yy=1;yy<=8;yy++) for(int xx=1;xx<=8;xx++) {
                    int tx=Math.min(tw-1,xx*(tw-1)/9), ty=Math.min(th-1,yy*(th-1)/9);
                    int p=tp[ty*tw+tx], q=sc[(y+ty)*sw+x+tx];
                    differences += Math.abs(((p>>16)&255)-((q>>16)&255));
                    differences += Math.abs(((p>>8)&255)-((q>>8)&255));
                    differences += Math.abs((p&255)-(q&255)); count++;
                }
                double score = 1.0 - differences/(count*3.0*255);
                if(score >= threshold && (best==null || score>best.score)) best=new Result(x,y,tw,th,score);
            }
        }
        // The faster coarse pass can offset clicks a few pixels; refine around best.
        if(best!=null) {
            int lowX=Math.max(0,best.x-step), hiX=Math.min(sw-tw,best.x+step);
            int lowY=Math.max(0,best.y-step), hiY=Math.min(sh-th,best.y+step);
            for(int y=lowY;y<=hiY;y++) for(int x=lowX;x<=hiX;x++) {
                long diff=0; int count=0;
                for(int yy=1;yy<=8;yy++) for(int xx=1;xx<=8;xx++) {
                    int tx=xx*(tw-1)/9,ty=yy*(th-1)/9;
                    int p=tp[ty*tw+tx],q=sc[(y+ty)*sw+x+tx];
                    diff+=Math.abs(((p>>16)&255)-((q>>16)&255));
                    diff+=Math.abs(((p>>8)&255)-((q>>8)&255));
                    diff+=Math.abs((p&255)-(q&255)); count++;
                }
                double score=1.0-diff/(count*765.0);
                if(score>best.score) best=new Result(x,y,tw,th,score);
            }
        }
        return best;
    }
}
