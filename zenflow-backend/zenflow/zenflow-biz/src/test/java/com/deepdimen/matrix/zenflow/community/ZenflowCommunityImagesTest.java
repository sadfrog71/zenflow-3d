package com.deepdimen.matrix.zenflow.community;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
class ZenflowCommunityImagesTest {
 @Test void decodedImageDeterminesMimeNotClientDeclaration() throws Exception {
  var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(10,12,BufferedImage.TYPE_INT_RGB),"png",out);
  var image=ZenflowCommunityImages.decode(Base64.getEncoder().encodeToString(out.toByteArray()));
  assertEquals("image/png",image.mime());assertEquals(10,image.width());assertEquals(12,image.height());
  assertTrue(image.thumbnail().startsWith("data:image/jpeg;base64,"));
 }
 @Test void arbitraryContentCannotBePublishedAsAnImage(){assertThrows(RuntimeException.class,()->ZenflowCommunityImages.decode(Base64.getEncoder().encodeToString("<svg onload='alert(1)'/>".getBytes())));}
 @Test void oversizedEncodedInputIsRejectedBeforeAllocation(){assertThrows(RuntimeException.class,()->ZenflowCommunityImages.decode("A".repeat(7_000_000)));}
}
