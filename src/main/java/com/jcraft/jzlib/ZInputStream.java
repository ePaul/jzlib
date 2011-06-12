/* -*-mode:java; c-basic-offset:2; indent-tabs-mode:nil -*- */
/*
Copyright (c) 2011 ymnk, JCraft,Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

  1. Redistributions of source code must retain the above copyright notice,
     this list of conditions and the following disclaimer.

  2. Redistributions in binary form must reproduce the above copyright 
     notice, this list of conditions and the following disclaimer in 
     the documentation and/or other materials provided with the distribution.

  3. The names of the authors may not be used to endorse or promote products
     derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JCRAFT,
INC. OR ANY CONTRIBUTORS TO THIS SOFTWARE BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.jcraft.jzlib;
import java.io.*;

/**
 * An input stream wrapper around a {@link ZStream}.
 * This can work either as a deflating or inflating stream,
 * depending on the constructor being used.
 *
 * @deprecated  use DeflaterOutputStream or InflaterInputStream
 */
@Deprecated
public class ZInputStream extends FilterInputStream {

  protected int flush=JZlib.Z_NO_FLUSH;
  /**
   * Wether we are inflating (false) or deflating (true).
   */
  protected boolean compress;
  /**
   * The source input stream. The data will be read from this before
   * being compressed or decompressed.
   */
  protected InputStream in=null;

  protected Deflater deflater;
  protected InflaterInputStream iis;

  /**
   * Creates a new decompressing (inflating) ZInputStream
   * reading zlib formatted data.
   * @param in the base stream, which should contain data in
   *   zlib format.
   */
  public ZInputStream(InputStream in) throws IOException {
    this(in, false);
  }
  /**
   * Creates a new decompressing (inflating) ZInputStream,
   * reading either zlib or plain deflate data.
   * @param in the base stream, which should contain data
   *   in the right format.
   * @param nowrap if true, the input is plain deflate data.
   *   If false, it is in zlib format (i.e. with a small header
   *   and a checksum).
   */
  public ZInputStream(InputStream in, boolean nowrap) throws IOException {
    super(in);
    iis = new InflaterInputStream(in);
    compress=false;
  }

  /**
   * Creates a compressing (deflating) ZInputStream,
   * producing zlib format data.
   * The stream reads uncompressed data from the base
   * stream, and produces compressed data in zlib format.
   * @param in the base stream from which to read uncompressed data.
   * @param level the compression level which will be used.
   */
  public ZInputStream(InputStream in, int level) throws IOException {
    super(in);
    this.in=in;
    deflater = new Deflater();
    deflater.init(level);
    compress=true;
  }

   /**
    * A one-byte buffer, used by {@link #read()}.
    */
  private byte[] buf1 = new byte[1];

  /**
   * Reads one byte of data.
   * @return the read byte, or -1 on end of input.
   */
  public int read() throws IOException {
    if(read(buf1, 0, 1)==-1) return -1;
    return(buf1[0]&0xFF);
  }

  /**
    * The internal buffer used for reading the
    * original stream and passing input to the
    * deflater.
    */
  private byte[] buf = new byte[512];

  /**
   * reads some data from the stream.
   * This will compress or decompress data from the
   * underlying stream.
   * @param b the buffer in which to put the data.
   * @param off the offset in b on which we should
   *     put the data.
   * @param len how much data to read maximally.
   * @return the amount of data actually read.
   */
  public int read(byte[] b, int off, int len) throws IOException {
    if(compress){
      deflater.setOutput(b, off, len);
      while(true){
        int datalen = in.read(buf, 0, buf.length);
        if(datalen == -1) return -1;
        deflater.setInput(buf, 0, datalen, true);
        int err = deflater.deflate(flush);
        if(deflater.next_out_index>0)
          return deflater.next_out_index;
        if(err == JZlib.Z_STREAM_END)
          return 0;
        if(err == JZlib.Z_STREAM_ERROR ||
           err == JZlib.Z_DATA_ERROR){
          throw new ZStreamException("deflating: "+deflater.msg);
        }
      }
    }
    else{
      return iis.read(b, off, len); 
    }
  }

  /**
   * skips some amount of (compressed or decompressed) input.
   *
   * In this implementation, we will simply read some data and
   * discard it. We will skip maximally 512 bytes on each call.
   * @return the number of bytes actually skipped.
   */
  public long skip(long n) throws IOException {
    int len=512;
    if(n<len)
      len=(int)n;
    byte[] tmp=new byte[len];
    return((long)read(tmp));
  }

  /**
   * Returns the current flush mode used for each compressing/decompressing
   * call. Normally this should be {@link JZlib#Z_NO_FLUSH Z_NO_FLUSH}.
   */
  public int getFlushMode() {
    return flush;
  }

  /**
   * Sets the current flush mode to be used for each compressing/decompressing
   * call. Normally this should be {@link JZlib#Z_NO_FLUSH Z_NO_FLUSH}.
   */
  public void setFlushMode(int flush) {
    this.flush=flush;
  }

  public long getTotalIn() {
    if(compress) return deflater.total_in;
    else return iis.getTotalIn();
  }

  public long getTotalOut() {
    if(compress) return deflater.total_out;
    else return iis.getTotalOut();
  }

  /**
   * Closes this stream.
   * This closes the underlying stream, too.
   */
  public void close() throws IOException{
    if(compress) deflater.end();
    else iis.close();
  }
}
