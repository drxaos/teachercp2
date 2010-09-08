package kello.teacher.rfb;

/*
 * 
 *  Copyright (C) 2006 Lorenzo Keller <lorenzo.keller@gmail.com>
 *  Copyright (C) 2001-2004 HorizonLive.com, Inc.  All Rights Reserved.
 *  Copyright (C) 2001,2002 Constantin Kaplinsky.  All Rights Reserved.
 *  Copyright (C) 2000 Tridia Corporation.  All Rights Reserved.
 *  Copyright (C) 1999 AT&T Laboratories Cambridge.  All Rights Reserved.
 * 
 *     This file is part of Teacher.
 *
 *   Teacher is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *
 *   Teacher is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Teacher; if not, write to the Free Software
 *   Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 */

//
//
//  This is free software; you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation; either version 2 of the License, or
//  (at your option) any later version.
//
//  This software is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with this software; if not, write to the Free Software
//  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
//  USA.
//

//
// rfbProto.java
//

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class RfbProto {

  public static final String versionMsg = "RFB 003.003\n";
  public static final int ConnFailed = 0, NoAuth = 1, VncAuth = 2, MsLogon = -6;
  public static final int VncAuthOK = 0, VncAuthFailed = 1, VncAuthTooMany = 2;

  public static final int FramebufferUpdate = 0,
      SetColourMapEntries = 1,
      Bell = 2,
      ServerCutText = 3,
      rfbFileTransfer = 7;

  public static final int SetPixelFormat = 0, FixColourMapEntries = 1,
      SetEncodings = 2, FramebufferUpdateRequest = 3, KeyEventA = 4,
      PointerEvent = 5, ClientCutText = 6;

  public static final int EncodingRaw = 0,
      EncodingCopyRect = 1,
      EncodingRRE = 2,
      EncodingCoRRE = 4,
      EncodingHextile = 5,
      EncodingZlib = 6,
      EncodingTight = 7;

  public static final int HextileRaw = (1 << 0);
  public static final int HextileBackgroundSpecified = (1 << 1);
  public static final int HextileForegroundSpecified = (1 << 2);
  public static final int HextileAnySubrects = (1 << 3);
  public static final int HextileSubrectsColoured = (1 << 4);

  public String host;
  public int port;
  public Socket sock;
  public DataInputStream is;
  public OutputStream os;
  public boolean inNormalProtocol = false;
  public RfbOptions options;

  //
  // Constructor. Just make TCP connection to RFB server.
  //

  public RfbProto(String h, int p, RfbOptions options) throws IOException {
    this.options = options;
    this.host = h;
    this.port = p;
    this.sock = new Socket(this.host, this.port);
    this.is = new DataInputStream(new BufferedInputStream(this.sock.getInputStream(),
                 16384));
    this.os = this.sock.getOutputStream();
  }

  public void close() {
    try {
      this.sock.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  //
  // Read server's protocol version message
  //

  public int serverMajor, serverMinor;

  public void readVersionMsg() throws IOException {

    byte[] b = new byte[12];

    this.is.readFully(b);

    System.out.println(new String(b));

    if ((b[0] != 'R') || (b[1] != 'F') || (b[2] != 'B') || (b[3] != ' ')
        || (b[4] < '0') || (b[4] > '9') || (b[5] < '0') || (b[5] > '9')
        || (b[6] < '0') || (b[6] > '9') || (b[7] != '.')
        || (b[8] < '0') || (b[8] > '9') || (b[9] < '0') || (b[9] > '9')
        || (b[10] < '0') || (b[10] > '9') || (b[11] != '\n')) {
      throw new IOException("Host " + this.host + " port " + this.port +
          " is not an RFB server");
    }

    this.serverMajor = (b[4] - '0') * 100 + (b[5] - '0') * 10 + (b[6] - '0');
    this.serverMinor = (b[8] - '0') * 100 + (b[9] - '0') * 10 + (b[10] - '0');
  }

  //
  // Write our protocol version message
  //

  public void writeVersionMsg() throws IOException {
    this.os.write(versionMsg.getBytes());
  }

  //
  // Find out the authentication scheme.
  //

  public int readAuthScheme() throws IOException {
    int authScheme = this.is.readInt();

    switch (authScheme) {

      case ConnFailed:
        int reasonLen = this.is.readInt();
        byte[] reason = new byte[reasonLen];
        this.is.readFully(reason);
        throw new IOException(new String(reason));

      case NoAuth:
      case VncAuth:
      case MsLogon:
        return authScheme;

      default:
        throw new IOException("Unknown authentication scheme from RFB " +
            "server " + authScheme);

    }
  }

  //
  // Write the client initialisation message
  //

  public void writeClientInit() throws IOException {
    if (this.options.isShareDesktop()) {
      this.os.write(1);
    } else {
      this.os.write(0);
    }
  }

  //
  // Read the server initialisation message
  //

  public String desktopName;
  public int framebufferWidth, framebufferHeight;
  public int bitsPerPixel, depth;
  public boolean bigEndian, trueColour;
  public int redMax, greenMax, blueMax, redShift, greenShift, blueShift;

  public void readServerInit() throws IOException {
    this.framebufferWidth = this.is.readUnsignedShort();
    this.framebufferHeight = this.is.readUnsignedShort();
    this.bitsPerPixel = this.is.readUnsignedByte();
    this.depth = this.is.readUnsignedByte();
    this.bigEndian = (this.is.readUnsignedByte() != 0);
    this.trueColour = (this.is.readUnsignedByte() != 0);
    this.redMax = this.is.readUnsignedShort();
    this.greenMax = this.is.readUnsignedShort();
    this.blueMax = this.is.readUnsignedShort();
    this.redShift = this.is.readUnsignedByte();
    this.greenShift = this.is.readUnsignedByte();
    this.blueShift = this.is.readUnsignedByte();
    byte[] pad = new byte[3];
    this.is.read(pad);
    int nameLength = this.is.readInt();
    byte[] name = new byte[nameLength];
    this.is.readFully(name);
    this.desktopName = new String(name);

    this.inNormalProtocol = true;
  }

  //
  // Read the server message type
  //

  public int readServerMessageType() throws IOException {
    return this.is.read();
  }

  //
  // Read a FramebufferUpdate message
  //

  public int updateNRects;

  public void readFramebufferUpdate() throws IOException {
    this.is.readByte();
    this.updateNRects = this.is.readUnsignedShort();
  }

  // Read a FramebufferUpdate rectangle header

  public int updateRectX, updateRectY, updateRectW, updateRectH, updateRectEncoding;

  public void readFramebufferUpdateRectHdr() throws IOException {
    this.updateRectX = this.is.readUnsignedShort();
    this.updateRectY = this.is.readUnsignedShort();
    this.updateRectW = this.is.readUnsignedShort();
    this.updateRectH = this.is.readUnsignedShort();
    this.updateRectEncoding = this.is.readInt();

    if ((this.updateRectX + this.updateRectW > this.framebufferWidth) ||
        (this.updateRectY + this.updateRectH > this.framebufferHeight)) {
      throw new IOException("Framebuffer update rectangle too large: " +
          this.updateRectW + "x" + this.updateRectH + " at (" +
          this.updateRectX + "," + this.updateRectY + ")");
    }
  }

  // Read CopyRect source X and Y.

  public int copyRectSrcX, copyRectSrcY;

  public void readCopyRect() throws IOException {
    this.copyRectSrcX = this.is.readUnsignedShort();
    this.copyRectSrcY = this.is.readUnsignedShort();
  }

  //
  // Read a ServerCutText message
  //

  public String readServerCutText() throws IOException {
    byte[] pad = new byte[3];
    this.is.read(pad);
    int len = this.is.readInt();
    byte[] text = new byte[len];
    this.is.readFully(text);
    return new String(text);
  }

  //
  // Write a FramebufferUpdateRequest message
  //

  public void writeFramebufferUpdateRequest(int x, int y, int w, int h,
             boolean incremental)
       throws IOException {
    byte[] b = new byte[10];

    b[0] = (byte) FramebufferUpdateRequest;
    b[1] = (byte) (incremental ? 1 : 0);
    b[2] = (byte) ((x >> 8) & 0xff);
    b[3] = (byte) (x & 0xff);
    b[4] = (byte) ((y >> 8) & 0xff);
    b[5] = (byte) (y & 0xff);
    b[6] = (byte) ((w >> 8) & 0xff);
    b[7] = (byte) (w & 0xff);
    b[8] = (byte) ((h >> 8) & 0xff);
    b[9] = (byte) (h & 0xff);

    this.os.write(b);
  }

  //
  // Write a SetPixelFormat message
  //

  public void writeSetPixelFormat(int bitsPerPixel, int depth, boolean bigEndian,
         boolean trueColour,
         int redMax, int greenMax, int blueMax,
         int redShift, int greenShift, int blueShift)
       throws IOException {
    byte[] b = new byte[20];

    b[0] = (byte) SetPixelFormat;
    b[4] = (byte) bitsPerPixel;
    b[5] = (byte) depth;
    b[6] = (byte) (bigEndian ? 1 : 0);
    b[7] = (byte) (trueColour ? 1 : 0);
    b[8] = (byte) ((redMax >> 8) & 0xff);
    b[9] = (byte) (redMax & 0xff);
    b[10] = (byte) ((greenMax >> 8) & 0xff);
    b[11] = (byte) (greenMax & 0xff);
    b[12] = (byte) ((blueMax >> 8) & 0xff);
    b[13] = (byte) (blueMax & 0xff);
    b[14] = (byte) redShift;
    b[15] = (byte) greenShift;
    b[16] = (byte) blueShift;

    this.os.write(b);
  }

  //
  // Write a FixColourMapEntries message. The values in the red, green and
  // blue arrays are from 0 to 65535.
  //

  public void writeFixColourMapEntries(int firstColour, int nColours,
        int[] red, int[] green, int[] blue)
       throws IOException {
    byte[] b = new byte[6 + nColours * 6];

    b[0] = (byte) FixColourMapEntries;
    b[2] = (byte) ((firstColour >> 8) & 0xff);
    b[3] = (byte) (firstColour & 0xff);
    b[4] = (byte) ((nColours >> 8) & 0xff);
    b[5] = (byte) (nColours & 0xff);

    for (int i = 0; i < nColours; i++) {
      b[6 + i * 6] = (byte) ((red[i] >> 8) & 0xff);
      b[6 + i * 6 + 1] = (byte) (red[i] & 0xff);
      b[6 + i * 6 + 2] = (byte) ((green[i] >> 8) & 0xff);
      b[6 + i * 6 + 3] = (byte) (green[i] & 0xff);
      b[6 + i * 6 + 4] = (byte) ((blue[i] >> 8) & 0xff);
      b[6 + i * 6 + 5] = (byte) (blue[i] & 0xff);
    }

    this.os.write(b);
  }

  //
  // Write a SetEncodings message
  //

  public void writeSetEncodings(int[] encs, int len) throws IOException {
    byte[] b = new byte[4 + 4 * len];

    b[0] = (byte) SetEncodings;
    b[2] = (byte) ((len >> 8) & 0xff);
    b[3] = (byte) (len & 0xff);

    for (int i = 0; i < len; i++) {
      b[4 + 4 * i] = (byte) ((encs[i] >> 24) & 0xff);
      b[5 + 4 * i] = (byte) ((encs[i] >> 16) & 0xff);
      b[6 + 4 * i] = (byte) ((encs[i] >> 8) & 0xff);
      b[7 + 4 * i] = (byte) (encs[i] & 0xff);
    }

    this.os.write(b);
  }

  //
  // Write a ClientCutText message
  //

  public void writeClientCutText(String text) throws IOException {
    byte[] b = new byte[8 + text.length()];

    b[0] = (byte) ClientCutText;
    b[4] = (byte) ((text.length() >> 24) & 0xff);
    b[5] = (byte) ((text.length() >> 16) & 0xff);
    b[6] = (byte) ((text.length() >> 8) & 0xff);
    b[7] = (byte) (text.length() & 0xff);

    System.arraycopy(text.getBytes(), 0, b, 8, text.length());

    this.os.write(b);
  }

  //
  // A buffer for putting pointer and keyboard events before being sent. This
  // is to ensure that multiple RFB events generated from a single Java Event
  // will all be sent in a single network packet. The maximum possible
  // length is 4 modifier down events, a single key event followed by 4
  // modifier up events i.e. 9 key events or 72 bytes.
  //

  byte[] eventBuf = new byte[72];
  int eventBufLen;

  // Java on UNIX does not call keyPressed() on some keys, for example
  // swedish keys To prevent our workaround to produce duplicate
  // keypresses on JVMs that actually works, keep track of if
  // keyPressed() for a "broken" key was called or not.
  boolean brokenKeyPressed = false;

  // Useful shortcuts for modifier masks.

  final static int CTRL_MASK = InputEvent.CTRL_MASK;
  final static int SHIFT_MASK = InputEvent.SHIFT_MASK;
  final static int META_MASK = InputEvent.META_MASK;
  final static int ALT_MASK = InputEvent.ALT_MASK;

  //
  // Write a pointer event message. We may need to send modifier key events
  // around it to set the correct modifier state.
  //

  int pointerMask = 0;

  public void writePointerEvent(MouseEvent evt) throws IOException {
    int modifiers = evt.getModifiers();

    int mask2 = 2;
    int mask3 = 4;
    if (this.options.isReverseMouseButtons2And3()) {
      mask2 = 4;
      mask3 = 2;
    }

    // Note: For some reason, AWT does not set BUTTON1_MASK on left
    // button presses. Here we think that it was the left button if
    // modifiers do not include BUTTON2_MASK or BUTTON3_MASK.

    if (evt.getID() == MouseEvent.MOUSE_PRESSED) {
      if ((modifiers & InputEvent.BUTTON2_MASK) != 0) {
        this.pointerMask = mask2;
        modifiers &= ~ALT_MASK;
      } else if ((modifiers & InputEvent.BUTTON3_MASK) != 0) {
        this.pointerMask = mask3;
        modifiers &= ~META_MASK;
      } else {
        this.pointerMask = 1;
      }
    } else if (evt.getID() == MouseEvent.MOUSE_RELEASED) {
      this.pointerMask = 0;
      if ((modifiers & InputEvent.BUTTON2_MASK) != 0) {
        modifiers &= ~ALT_MASK;
      } else if ((modifiers & InputEvent.BUTTON3_MASK) != 0) {
        modifiers &= ~META_MASK;
      }
    }

    this.eventBufLen = 0;
    writeModifierKeyEvents(modifiers);

    int x = evt.getX();
    int y = evt.getY();

    if (x < 0) {
      x = 0;
    }
    if (y < 0) {
      y = 0;
    }

    this.eventBuf[this.eventBufLen++] = (byte) PointerEvent;
    this.eventBuf[this.eventBufLen++] = (byte) this.pointerMask;
    this.eventBuf[this.eventBufLen++] = (byte) ((x >> 8) & 0xff);
    this.eventBuf[this.eventBufLen++] = (byte) (x & 0xff);
    this.eventBuf[this.eventBufLen++] = (byte) ((y >> 8) & 0xff);
    this.eventBuf[this.eventBufLen++] = (byte) (y & 0xff);

    //
    // Always release all modifiers after an "up" event
    //

    if (this.pointerMask == 0) {
      writeModifierKeyEvents(0);
    }

    this.os.write(this.eventBuf, 0, this.eventBufLen);
  }

  //
  // Write a key event message. We may need to send modifier key events
  // around it to set the correct modifier state. Also we need to translate
  // from the Java key values to the X keysym values used by the RFB protocol.
  //

  public void writeKeyEvent(KeyEvent evt) throws IOException {

    int keyChar = evt.getKeyChar();

    //
    // Ignore event if only modifiers were pressed.
    //

    // Some JVMs return 0 instead of CHAR_UNDEFINED in getKeyChar().
    if (keyChar == 0) {
      keyChar = KeyEvent.CHAR_UNDEFINED;
    }

    if (keyChar == KeyEvent.CHAR_UNDEFINED) {
      int code = evt.getKeyCode();
      if (code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_SHIFT ||
          code == KeyEvent.VK_META || code == KeyEvent.VK_ALT) {
        return;
      }
    }

    //
    // Key press or key release?
    //

    boolean down = (evt.getID() == KeyEvent.KEY_PRESSED);

    int key;
    if (evt.isActionKey()) {

      //
      // An action key should be one of the following.
      // If not then just ignore the event.
      //

      switch (evt.getKeyCode()) {
        case KeyEvent.VK_HOME:
          key = 0xff50;
          break;
        case KeyEvent.VK_LEFT:
          key = 0xff51;
          break;
        case KeyEvent.VK_UP:
          key = 0xff52;
          break;
        case KeyEvent.VK_RIGHT:
          key = 0xff53;
          break;
        case KeyEvent.VK_DOWN:
          key = 0xff54;
          break;
        case KeyEvent.VK_PAGE_UP:
          key = 0xff55;
          break;
        case KeyEvent.VK_PAGE_DOWN:
          key = 0xff56;
          break;
        case KeyEvent.VK_END:
          key = 0xff57;
          break;
        case KeyEvent.VK_INSERT:
          key = 0xff63;
          break;
        case KeyEvent.VK_F1:
          key = 0xffbe;
          break;
        case KeyEvent.VK_F2:
          key = 0xffbf;
          break;
        case KeyEvent.VK_F3:
          key = 0xffc0;
          break;
        case KeyEvent.VK_F4:
          key = 0xffc1;
          break;
        case KeyEvent.VK_F5:
          key = 0xffc2;
          break;
        case KeyEvent.VK_F6:
          key = 0xffc3;
          break;
        case KeyEvent.VK_F7:
          key = 0xffc4;
          break;
        case KeyEvent.VK_F8:
          key = 0xffc5;
          break;
        case KeyEvent.VK_F9:
          key = 0xffc6;
          break;
        case KeyEvent.VK_F10:
          key = 0xffc7;
          break;
        case KeyEvent.VK_F11:
          key = 0xffc8;
          break;
        case KeyEvent.VK_F12:
          key = 0xffc9;
          break;
        default:
          return;
      }

    } else {

      //
      // A "normal" key press. Ordinary ASCII characters go straight through.
      // For CTRL-<letter>, CTRL is sent separately so just send <letter>.
      // Backspace, tab, return, escape and delete have special keysyms.
      // Anything else we ignore.
      //

      key = keyChar;

      if (key < 0x20) {
        if (evt.isControlDown()) {
          key += 0x60;
        } else {
          switch (key) {
            case KeyEvent.VK_BACK_SPACE:
              key = 0xff08;
              break;
            case KeyEvent.VK_TAB:
              key = 0xff09;
              break;
            case KeyEvent.VK_ENTER:
              key = 0xff0d;
              break;
            case KeyEvent.VK_ESCAPE:
              key = 0xff1b;
              break;
          }
        }
      } else if (key == 0x7f) {
        // Delete
        key = 0xffff;
      } else if (key > 0xff) {
        // JDK1.1 on X incorrectly passes some keysyms straight through,
        // so we do too. JDK1.1.4 seems to have fixed this.
        // The keysyms passed are 0xff00 .. XK_BackSpace .. XK_Delete
        // Also, we pass through foreign currency keysyms (0x20a0..0x20af).
        if ((key < 0xff00 || key > 0xffff) &&
            !(key >= 0x20a0 && key <= 0x20af)) {
          return;
        }
      }
    }

    // Fake keyPresses for keys that only generates keyRelease events
    if ((key == 0xe5) || (key == 0xc5) || // XK_aring / XK_Aring
        (key == 0xe4) || (key == 0xc4) || // XK_adiaeresis / XK_Adiaeresis
        (key == 0xf6) || (key == 0xd6) || // XK_odiaeresis / XK_Odiaeresis
        (key == 0xa7) || (key == 0xbd) || // XK_section / XK_onehalf
        (key == 0xa3)) { // XK_sterling
      // Make sure we do not send keypress events twice on platforms
      // with correct JVMs (those that actually report KeyPress for all
      // keys)
      if (down) {
        this.brokenKeyPressed = true;
      }

      if (!down && !this.brokenKeyPressed) {
        // We've got a release event for this key, but haven't received
        // a press. Fake it.
        this.eventBufLen = 0;
        writeModifierKeyEvents(evt.getModifiers());
        writeKeyEvent(key, true);
        this.os.write(this.eventBuf, 0, this.eventBufLen);
      }

      if (!down) {
        this.brokenKeyPressed = false;
      }
    }

    this.eventBufLen = 0;
    writeModifierKeyEvents(evt.getModifiers());
    writeKeyEvent(key, down);

    // Always release all modifiers after an "up" event
    if (!down) {
      writeModifierKeyEvents(0);
    }

    this.os.write(this.eventBuf, 0, this.eventBufLen);
  }

  //
  // Add a raw key event with the given X keysym to eventBuf.
  //

  public void writeKeyEvent(int keysym, boolean down)
       throws IOException {
    this.eventBuf[this.eventBufLen++] = (byte) KeyEventA;
    this.eventBuf[this.eventBufLen++] = (byte) (down ? 1 : 0);
    this.eventBuf[this.eventBufLen++] = (byte) 0;
    this.eventBuf[this.eventBufLen++] = (byte) 0;
    this.eventBuf[this.eventBufLen++] = (byte) ((keysym >> 24) & 0xff);
    this.eventBuf[this.eventBufLen++] = (byte) ((keysym >> 16) & 0xff);
    this.eventBuf[this.eventBufLen++] = (byte) ((keysym >> 8) & 0xff);
    this.eventBuf[this.eventBufLen++] = (byte) (keysym & 0xff);
  }

  //
  // Write key events to set the correct modifier state.
  //

  int oldModifiers;

  public void writeModifierKeyEvents(int newModifiers)
       throws IOException {
    if ((newModifiers & InputEvent.CTRL_MASK) != (this.oldModifiers & InputEvent.CTRL_MASK)) {
      writeKeyEvent(0xffe3, (newModifiers & InputEvent.CTRL_MASK) != 0);
    }

    if ((newModifiers & InputEvent.SHIFT_MASK) != (this.oldModifiers & InputEvent.SHIFT_MASK)) {
      writeKeyEvent(0xffe1, (newModifiers & InputEvent.SHIFT_MASK) != 0);
    }

    if ((newModifiers & InputEvent.META_MASK) != (this.oldModifiers & InputEvent.META_MASK)) {
      writeKeyEvent(0xffe7, (newModifiers & InputEvent.META_MASK) != 0);
    }

    if ((newModifiers & InputEvent.ALT_MASK) != (this.oldModifiers & InputEvent.ALT_MASK)) {
      writeKeyEvent(0xffe9, (newModifiers & InputEvent.ALT_MASK) != 0);
    }

    this.oldModifiers = newModifiers;

  }
}
