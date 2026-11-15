package org.retrolink.legacy;
import java.io.*;
public class FrameCodecTest {
 static byte[] frame(int size,byte[] payload)throws Exception {ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(b);o.writeInt(size);o.write(payload);return b.toByteArray();}
 static void reject(int length,byte[] payload)throws Exception {try{FrameCodec.read(new DataInputStream(new ByteArrayInputStream(frame(length,payload))));throw new AssertionError("accepted invalid frame");}catch(IOException expected){}}
 public static void main(String[] args)throws Exception {
  byte[] message="中文音乐".getBytes("UTF-8");byte[] result=FrameCodec.read(new DataInputStream(new ByteArrayInputStream(frame(message.length,message))));
  if(!java.util.Arrays.equals(message,result))throw new AssertionError("UTF-8 corrupted");
  byte[] voiceFrame=new byte[98304];
  if(FrameCodec.read(new DataInputStream(new ByteArrayInputStream(frame(voiceFrame.length,voiceFrame)))).length!=voiceFrame.length)throw new AssertionError("voice frame truncated");
  reject(-1,new byte[0]);reject(0,new byte[0]);reject(98305,new byte[0]);reject(Integer.MAX_VALUE,new byte[0]);reject(10,new byte[2]);
  System.out.println("PASS: UTF-8 frame, negative/empty/oversize bounds, truncated input");
 }
}
