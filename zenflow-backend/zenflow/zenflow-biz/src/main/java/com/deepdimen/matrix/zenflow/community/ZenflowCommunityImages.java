package com.deepdimen.matrix.zenflow.community;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class ZenflowCommunityImages {
 record Image(byte[] bytes,String mime,int width,int height,String thumbnail) {}
 static Image decode(String encoded) {
  try {
   if(encoded==null || encoded.length()>7_000_000)throw new IllegalArgumentException();
   byte[] bytes=Base64.getDecoder().decode(encoded);
   if(bytes.length==0 || bytes.length>5*1024*1024)throw new IllegalArgumentException();
   try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
    var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IllegalArgumentException();
    var reader=readers.next();try {
     reader.setInput(input);String format=reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
     if(!java.util.Set.of("png","jpeg","jpg").contains(format))throw new IllegalArgumentException();
     int width=reader.getWidth(0),height=reader.getHeight(0);
     if(width<1 || height<1 || width>8192 || height>8192 || (long)width*height>24_000_000)throw new IllegalArgumentException();
     var image=reader.read(0);double scale=Math.min(1,400d/Math.max(width,height));
     var thumb=new BufferedImage(Math.max(1,(int)(width*scale)),Math.max(1,(int)(height*scale)),BufferedImage.TYPE_INT_RGB);
     var g=thumb.createGraphics();try {g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,thumb.getWidth(),thumb.getHeight());g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(image,0,0,thumb.getWidth(),thumb.getHeight(),null);}finally{g.dispose();}
     var out=new ByteArrayOutputStream();ImageIO.write(thumb,"jpeg",out);
     var clean=new ByteArrayOutputStream();ImageIO.write(image,format.equals("png")?"png":"jpeg",clean);
     return new Image(clean.toByteArray(),format.equals("png")?"image/png":"image/jpeg",width,height,"data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(out.toByteArray()));
    }finally{reader.dispose();}
   }
  }catch(Exception invalid){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"COMMUNITY_IMAGE_INVALID");}
 }
}
