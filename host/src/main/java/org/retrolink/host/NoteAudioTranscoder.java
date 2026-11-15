package org.retrolink.host;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Prepares a seekable AAC-LC attachment using the platform codecs. */
final class NoteAudioTranscoder {
 private static final long WAIT_US=10000;
 private static final int MAX_PCM_BYTES=1024*1024;
 private static final String AAC=MediaFormat.MIMETYPE_AUDIO_AAC;

 static File attachment(Context context,String id){
  if(!id.matches("[a-zA-Z0-9-]{1,100}"))throw new IllegalArgumentException("Invalid recording ID");
  return new File(context.getCacheDir(),"wechat-voice-"+id+".m4a");
 }

 static synchronized void prepare(Context context,String id)throws IOException{
  File target=attachment(context,id);
  if(target.isFile()&&target.length()>0)return;
  byte[] source=new NoteStore(context).voiceAudio(id);
  if(source==null)throw new IOException("Recording not found");
  transcode(source,target);
 }

 static void transcode(byte[] source,File target)throws IOException{
  File input=File.createTempFile("note-amr-",".3gp",target.getParentFile());
  File output=null;
  try{
   output=File.createTempFile("note-aac-",".m4a",target.getParentFile());
   try(FileOutputStream stream=new FileOutputStream(input)){stream.write(source);}
   encode(decode(input),output);
   if(!output.renameTo(target))throw new IOException("Could not publish audio attachment");
  }finally{
   input.delete();
   if(output!=null)output.delete();
  }
 }

 private static final class Pcm{
  final byte[] data;
  final int rate,channels;
  Pcm(byte[] data,int rate,int channels){this.data=data;this.rate=rate;this.channels=channels;}
 }

 private static Pcm decode(File input)throws IOException{
  MediaExtractor extractor=new MediaExtractor();
  MediaCodec decoder=null;
  try{
   extractor.setDataSource(input.getPath());
   int track=-1;
   MediaFormat format=null;
   for(int i=0;i<extractor.getTrackCount();i++){
    MediaFormat candidate=extractor.getTrackFormat(i);
    String mime=candidate.getString(MediaFormat.KEY_MIME);
    if(mime!=null&&mime.startsWith("audio/")){track=i;format=candidate;break;}
   }
   if(track<0)throw new IOException("No audio track");
   extractor.selectTrack(track);
   decoder=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
   decoder.configure(format,null,null,0);
   decoder.start();
   MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
   ByteArrayOutputStream pcm=new ByteArrayOutputStream();
   boolean inputDone=false,outputDone=false;
   int rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE),channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
   long deadline=SystemClock.elapsedRealtime()+15000;
   while(!outputDone){
    if(SystemClock.elapsedRealtime()>deadline)throw new IOException("Audio decoding timed out");
    if(!inputDone){
     int index=decoder.dequeueInputBuffer(WAIT_US);
     if(index>=0){
      ByteBuffer buffer=decoder.getInputBuffer(index);
      buffer.clear();
      int size=extractor.readSampleData(buffer,0);
      inputDone=size<0;
      decoder.queueInputBuffer(index,0,Math.max(0,size),inputDone?0:extractor.getSampleTime(),
          inputDone?MediaCodec.BUFFER_FLAG_END_OF_STREAM:0);
      if(!inputDone)extractor.advance();
     }
    }
    int index=decoder.dequeueOutputBuffer(info,WAIT_US);
    if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
     MediaFormat decoded=decoder.getOutputFormat();
     rate=decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE);
     channels=decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
     if(decoded.containsKey(MediaFormat.KEY_PCM_ENCODING)
         && decoded.getInteger(MediaFormat.KEY_PCM_ENCODING)!=android.media.AudioFormat.ENCODING_PCM_16BIT){
      throw new IOException("Unsupported PCM format");
     }
    }else if(index>=0){
     if(info.size>0){
      if(info.size>MAX_PCM_BYTES-pcm.size())throw new IOException("Recording is too long");
      ByteBuffer buffer=decoder.getOutputBuffer(index);
      buffer.position(info.offset);
      buffer.limit(info.offset+info.size);
      byte[] data=new byte[info.size];
      buffer.get(data);
      pcm.write(data);
     }
     outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
     decoder.releaseOutputBuffer(index,false);
    }
   }
   if(pcm.size()==0)throw new IOException("Empty audio track");
   return new Pcm(pcm.toByteArray(),rate,channels);
  }finally{
   if(decoder!=null)decoder.release();
   extractor.release();
  }
 }

 private static void encode(Pcm pcm,File output)throws IOException{
  MediaCodec encoder=MediaCodec.createEncoderByType(AAC);
  MediaMuxer muxer=null;
  boolean muxing=false;
  try{
   MediaFormat format=MediaFormat.createAudioFormat(AAC,pcm.rate,pcm.channels);
   format.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);
   format.setInteger(MediaFormat.KEY_BIT_RATE,32000);
   encoder.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
   encoder.start();
   muxer=new MediaMuxer(output.getPath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
   MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
   int offset=0,track=-1,frameBytes=2*pcm.channels;
   boolean inputDone=false,outputDone=false,wroteAudio=false;
   long deadline=SystemClock.elapsedRealtime()+15000;
   while(!outputDone){
    if(SystemClock.elapsedRealtime()>deadline)throw new IOException("AAC encoding timed out");
    if(!inputDone){
     int index=encoder.dequeueInputBuffer(WAIT_US);
     if(index>=0){
      ByteBuffer buffer=encoder.getInputBuffer(index);
      buffer.clear();
      int size=Math.min(buffer.remaining(),pcm.data.length-offset);
      size-=size%frameBytes;
      inputDone=size==0;
      buffer.put(pcm.data,offset,size);
      encoder.queueInputBuffer(index,0,size,(offset/frameBytes)*1000000L/pcm.rate,
          inputDone?MediaCodec.BUFFER_FLAG_END_OF_STREAM:0);
      offset+=size;
     }
    }
    int index=encoder.dequeueOutputBuffer(info,WAIT_US);
    if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
     track=muxer.addTrack(encoder.getOutputFormat());
     muxer.start();
     muxing=true;
    }else if(index>=0){
     if(info.size>0&&(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){
      if(!muxing)throw new IOException("AAC output format unavailable");
      muxer.writeSampleData(track,encoder.getOutputBuffer(index),info);
      wroteAudio=true;
     }
     outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
     encoder.releaseOutputBuffer(index,false);
    }
   }
   if(!wroteAudio)throw new IOException("No AAC samples written");
   muxer.stop();
  }finally{
   if(muxer!=null)muxer.release();
   encoder.release();
  }
 }

 private NoteAudioTranscoder(){}
}
