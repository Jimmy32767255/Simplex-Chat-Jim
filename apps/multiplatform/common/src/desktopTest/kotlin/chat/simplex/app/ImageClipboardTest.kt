package chat.simplex.app

import chat.simplex.common.platform.bufferedImageToTransferable
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ImageClipboardTest {
  // The ImageBitmap -> AWT conversion needs the Skia natives, which the unit test JVM does not load,
  // so the AWT-side transferable is tested directly.
  private fun testTransferable(): Transferable = bufferedImageToTransferable(BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB))

  @Test
  fun testImageFlavorIsSupported() {
    assertTrue(testTransferable().isDataFlavorSupported(DataFlavor.imageFlavor))
  }

  @Test
  fun testOtherFlavorsAreNotSupported() {
    assertTrue(!testTransferable().isDataFlavorSupported(DataFlavor.stringFlavor))
  }

  @Test
  fun testImageFlavorReturnsTheImage() {
    val data = testTransferable().getTransferData(DataFlavor.imageFlavor) as BufferedImage
    assertEquals(2, data.width)
    assertEquals(2, data.height)
  }

  @Test
  fun testUnsupportedFlavorThrows() {
    assertFailsWith<UnsupportedFlavorException> {
      testTransferable().getTransferData(DataFlavor.stringFlavor)
    }
  }
}
