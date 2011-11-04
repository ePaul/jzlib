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
 * An inflating stream wrapper around a compressed stream in
 * gzip format.
 * In addition to its superclass' methods, it has some methods
 * to access the attributed which might have been encoded in the
 * gzip header.
 * @see <a href="http://tools.ietf.org/html/rfc1952">RFC 1952: GZIP file
 *    format specification version 4.3</a>
 */
public class GZIPInputStream extends InflaterInputStream {

  /**
   * Creates a GZIPInputStream using the default buffer size.
   * @param in the source stream from which to read the gzip data.
   *   It will be closed when this stream is closed.
   */
  public GZIPInputStream(InputStream in) throws IOException {
    this(in, DEFAULT_BUFSIZE, true);
  }

  /**
   * Creates a GZIPInputStream.
   * @param in the source stream from which to read the gzip data.
   * @param size the size of the input buffer used for reading from the
   *   source stream.
   * @param close_in if {@code true}, the source input stream will closed
   *  on closing this stream. If {@code false}, it will left open, allowing
   *  more data to be read from it directly.
   */
  public GZIPInputStream(InputStream in,
                         int size,
                         boolean close_in) throws IOException {
    this(in, new Inflater(15+16), size, close_in);
    myinflater = true;
  }

  /**
   * Creates a GZIPInputStream.
   * @param in the source stream from which to read the gzip data.
   * @param inflater the inflater used to decompress the data. This deflater
   *   should know how to read the gzip header data.
   * @param size the size of the input buffer used for reading from the
   *   source stream.
   * @param close_in if {@code true}, the source input stream will closed
   *  on closing this stream. If {@code false}, it will left open, allowing
   *  more data to be read from it directly.
   */
  public GZIPInputStream(InputStream in, 
                         Inflater inflater,
                         int size,
                         boolean close_in) throws IOException {
    super(in, inflater, size, close_in);
  }

  /**
   * Gets the modification time from the gzip file header.
   * @return the recorded modification time as seconds since the
   *    Unix Epoch (1970-01-01 00:00 GMT). If 0, no time stamp is
   *   available.
   * @see <a href="http://tools.ietf.org/html/rfc1952#page-7">RFC 1952,
   *      section 2.3.1</a>
   */
  public long getModifiedtime() {
    return inflater.istate.getGZIPHeader().getModifiedTime();
  }

  /**
   * Gets the recorded operation system flag.
   * @return one of these values:<pre>
   *      0 - FAT filesystem (MS-DOS, OS/2, NT/Win32)
   *      1 - Amiga
   *      2 - VMS (or OpenVMS)
   *      3 - Unix
   *      4 - VM/CMS
   *      5 - Atari TOS
   *      6 - HPFS filesystem (OS/2, NT)
   *      7 - Macintosh
   *      8 - Z-System
   *      9 - CP/M
   *     10 - TOPS-20
   *     11 - NTFS filesystem (NT)
   *     12 - QDOS
   *     13 - Acorn RISCOS
   *    255 - unknown</pre>
   * @see <a href="http://tools.ietf.org/html/rfc1952#page-7">RFC 1952,
   *      section 2.3.1</a>
   */
  public int getOS() {
    return inflater.istate.getGZIPHeader().getOS();
  }

  /**
   * Gets the recorded file name.
   * @return {@code ""} (the empty string) if there is no recorded file name,
   *   otherwise the file name as recorded in the gzip header.
   * @see <a href="http://tools.ietf.org/html/rfc1952#page-7">RFC 1952,
   *      section 2.3.1</a>
   */
  public String getName() {
    return inflater.istate.getGZIPHeader().getName();
  }

  
  /**
   * Gets the recorded file comment.
   * @return {@code ""} (the empty string) if there is no recorded file comment,
   *   otherwise the file comment as recorded in the gzip header.
   * @see <a href="http://tools.ietf.org/html/rfc1952#page-7">RFC 1952,
   *      section 2.3.1</a>
   */
  public String getComment() {
    return inflater.istate.getGZIPHeader().getComment();
  }

  /**
   * Gets the checksum value.
   * This method can only be called after the end of a gzip-compressed
   * data block (as the checksum is stored at the end).
   * @return the CRC-32 check sum of the uncompressed data.
   * @see <a href="http://tools.ietf.org/html/rfc1952#page-8">RFC 1952,
   *      section 2.3.1</a>
   */
  public long getCRC() throws GZIPException {
    if(inflater.istate.mode != 12 /*DONE*/)
      throw new GZIPException("checksum is not calculated yet.");
    return inflater.istate.getGZIPHeader().getCRC();
  }


  /**
   * Reads a gzip header from the source stream.
   */
  public void readHeader() throws IOException {

    byte[] empty = "".getBytes();
    inflater.setOutput(empty, 0, 0);
    inflater.setInput(empty, 0, 0, false);

    byte[] b = new byte[10];

    int n = fill(b);
    if(n!=10){
      if(n>0){
        inflater.setInput(b, 0, n, false);
        //inflater.next_in_index = n;
        inflater.next_in_index = 0;
        inflater.avail_in = n;
      }
      throw new IOException("no input");
    }

    inflater.setInput(b, 0, n, false);

    byte[] b1 = new byte[1];
    do{
      if(inflater.avail_in<=0){
        int i = in.read(b1);
        if(i<=0)
          throw new IOException("no input");
        inflater.setInput(b1, 0, 1, true);
      }

      int err = inflater.inflate(JZlib.Z_NO_FLUSH);

      if(err!=0/*Z_OK*/){
        int len = 2048-inflater.next_in.length;
        if(len>0){
          byte[] tmp = new byte[len];
          n = fill(tmp);
          if(n>0){
            inflater.avail_in += inflater.next_in_index;
            inflater.next_in_index = 0;
            inflater.setInput(tmp, 0, n, true);
          }
        }
        //inflater.next_in_index = inflater.next_in.length;
        inflater.avail_in += inflater.next_in_index;
        inflater.next_in_index = 0;
        throw new IOException(inflater.msg);
      }
    }
    while(inflater.istate.inParsingHeader());
  }

  /**
   * fills a buffer with data from the source stream.
   */
  private int fill(byte[] buf) {
    int len = buf.length;
    int n = 0;
    do{
      int i = -1;
      try {
        i = in.read(buf, n, buf.length - n);
      }
      catch(IOException e){
      }
      if(i == -1){
        break;
      }
      n+=i;
    }
    while(n<len);
    return n;
  }
}