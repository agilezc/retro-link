package org.retrolink.legacy;
import java.io.*;
final class FrameCodec {
 static byte[] read(DataInputStream input)throws IOException {
  int length=input.readInt();if(length<2||length>98304)throw new IOException("Invalid frame length");
  byte[] bytes=new byte[length];input.readFully(bytes);return bytes;
 }
}
