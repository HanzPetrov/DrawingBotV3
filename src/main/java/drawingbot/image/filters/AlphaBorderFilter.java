package drawingbot.image.filters;

import com.jhlabs.image.AbstractBufferedImageOp;
import drawingbot.image.BufferedImageLoader;
import drawingbot.image.ImageTools;
import drawingbot.image.blend.EnumBlendMode;
import org.imgscalr.Scalr;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**A quick and dirty way of softening the edges of your drawing.
 * Look in the boarders directory for some examples.
 * Ideally, the boarder will have similar dimensions as the image to be drawn.
 * For far more control, just edit your input image directly.
 * Most of the examples are pretty heavy handed so you can "shrink" them a few pixels as desired.
 * It does not matter if you use a transparent background or just white.  JPEG or PNG, it's all good.
 */
public class AlphaBorderFilter extends AbstractBufferedImageOp{

    public final String prefix = "border/bc", suffix = ".png";
    public int borderNumber;

    public AlphaBorderFilter(){
        this.borderNumber = 1;
    }

    public AlphaBorderFilter(int borderNumber){
        this.borderNumber = borderNumber;
    }

    @Override
    public BufferedImage filter(BufferedImage src, BufferedImage dest) {
        if (dest == null ){
            dest = createCompatibleDestImage( src, null );
        }

        BufferedImage borderImg;
        try {
            borderImg = BufferedImageLoader.loadImage(prefix + borderNumber + suffix, true);
        } catch (IOException e) {
            return dest;
        }

        if(borderImg != null){
            borderImg = Scalr.resize(borderImg, Scalr.Method.AUTOMATIC, Scalr.Mode.FIT_EXACT, src.getWidth(), src.getHeight());
            dest = ImageTools.drawImage(src, dest);
            dest = ImageTools.lazyAlphaBlend(dest, borderImg, EnumBlendMode.NORMAL); //BlendMode.SRC_OVER
        }
        return dest;
    }
}
