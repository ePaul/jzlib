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
 * An inflating stream wrapper around a compressed stream.
 * This stream uses an {@link Inflater} to do the actual work -
 * such an Inflater can either be passed to the constructor or
 * will be created internally. ({@link #getInflater} can be used
 * to access this Inflater.)
 */
public class InflaterInputStream extends FilterInputStream {

  /**
   * The internal work horse which does the inflation.
   */
  protected final Inflater inflater;

  /**
   * The buffer used for raw (compressed) data from our
   * source stream, to be fed to the Inflater.
   */
  protected byte[] buf;

  private boolean closed = false;

  private boolean eof = false;

  private boolean close_in = true;

  /**
   * The default buffer size.
   */
  protected static final int DEFAULT_BUFSIZE = 512;

  /**
   * Creates a new InflaterInputStream which reads data in zlib format
   * from the given InputStream, using maximum window size and default
   * input buffer size.
   * @param in the source input stream, from which data is read to
   *  be inflated. It will be closed on closing this stream.
   */
  public InflaterInputStream(InputStream in) throws IOException {
    this(in, new Inflater());
    myinflater = true;
  }

  /**
   * Creates a new InflaterInputStream which reads data from the
   * given InputStream and decompresses it using the given Inflater
   * and the default input buffer size.
   * @param in the source input stream, from which data is read to
   *  be inflated. It will be closed on closing this stream.
   * @param inflater the Inflater which will do the work.
   */
  public InflaterInputStream(InputStream in, Inflater inflater) throws IOException {
    this(in, inflater, DEFAULT_BUFSIZE);
  }

  /**
   * Creates a new InflaterInputStream which reads data from the
   * given InputStream  and decompresses it using the given Inflater and
   * the given input buffer size.
   * @param in the source input stream, from which data is read to
   *  be inflated. It will be closed on closing this stream.
   * @param inflater the Inflater which will do the work.
   * @param size the size of the internal buffer.
   */
  public InflaterInputStream(InputStream in,
                             Inflater inflater, int size) throws IOException {
    this(in, inflater, size, true);
  }

  /**
   * Creates a new InflaterInputStream which reads data from the
   * given InputStream and decompresses it using the given Inflater,
   * using the given input buffer size.
   * @param in the source input stream, from which data is read to
   *  be inflated.
   * @param inflater the Inflater which will do the work.
   * @param size the size of the internal buffer.
   * @param close_in if {@code true}, the source input stream will closed
   *  on closing this stream. If {@code false}, it will left open, allowing
   *  more data to be read from it directly.
   */
  public InflaterInputStream(InputStream in,
                             Inflater inflater,
                             int size, boolean close_in) throws IOException {
    super(in);
    if (in == null || inflater == null) {
      throw new NullPointerException();
    }
    else if (size <= 0) {
      throw new IllegalArgumentException("buffer size must be greater than 0");
    }
    this.inflater = inflater;
    buf = new byte[size];
    this.close_in = close_in;
  }

  /**
   * Indicates whether we have an own (internal) Inflater ({@code true})
   * or one supplied to the constructor ({@code false}). In the first
   * case, it will be closed on closing this stream.
   */
  protected boolean myinflater = false;

  /**
   * A one-byte buffer used by {@link #read()}.
   */
  private byte[] byte1 = new byte[1];

  /**
   * reads one uncompressed byte from the stream.
   * @return -1, if we arrived at the end of input,
   *   the read byte (as an {@code int} in the range [0, 255]) otherwise.
   */
  public int read() throws IOException {
    if (closed) { throw new IOException("Stream closed"); }
    return read(byte1, 0, 1) == -1 ? -1 : byte1[0] & 0xff;
  }

  /**
   * Reads some bytes of inflated data into an array.
   * 
   * @param b the array into which to put the read data.
   * @param off the offset in the array from which
   *    on the inflated data should be put.
   * @param len the size of the array segment into which to put the data.
   * @return the number of bytes read, or -1 if we arrived at end of stream.
   */
  public int read(byte[] b, int off, int len) throws IOException {
    if (closed) { throw new IOException("Stream closed"); }
    if (b == null) {
      throw new NullPointerException();
    }
    else if (off < 0 || len < 0 || len > b.length - off) {
      throw new IndexOutOfBoundsException();
    }
    else if (len == 0) {
      return 0;
    }
    else if (eof) {
      return -1;
    }

    int n = 0;
    inflater.setOutput(b, off, len);
    while(!eof) {
      if(inflater.avail_in==0)
        fill();
      int err = inflater.inflate(JZlib.Z_NO_FLUSH);
      n += inflater.next_out_index - off;
      off = inflater.next_out_index;
      switch(err) {
        case JZlib.Z_DATA_ERROR:
          throw new IOException(inflater.msg);
        case JZlib.Z_STREAM_END:
        case JZlib.Z_NEED_DICT:
          eof = true;
          if(err == JZlib.Z_NEED_DICT)
            return -1;
          break;
        default:
      } 
      if(inflater.avail_out==0)
        break;
    }
    return n;
  }

  /**
   * returns an estimation for the number of bytes which can be
   * read from this stream.
   * @return
   *     either 0 (if we are already at end of file) or 1 (otherwise).
   * @throws IOException if the stream is already closed.
   */
  public int available() throws IOException {
    if (closed) { throw new IOException("Stream closed"); }
    if (eof) {
      return 0;
    }
    else {
      return 1;
    }
  }

  /**
   * A buffer used by {@link #skip}.
   */
  private byte[] b = new byte[512];

  /**
   * Skips a number of deflated bytes.
   * @param n a suggestion of how many bytes we should skip.
   * @return the number of bytes really skipped (which might be less).
   */
  public long skip(long n) throws IOException {
    if (n < 0) {
      throw new IllegalArgumentException("negative skip length");
    }

    if (closed) { throw new IOException("Stream closed"); }

    int max = (int)Math.min(n, Integer.MAX_VALUE);
    int total = 0;
    while (total < max) {
      int len = max - total;
      if (len > b.length) {
        len = b.length;
      }
      len = read(b, 0, len);
      if (len == -1) {
        eof = true;
        break;
      }
      total += len;
    }
    return total;
  }

  /**
   * closes the stream.
   */
  public void close() throws IOException {
    if (!closed) {
      if (myinflater)
        inflater.end();
      if(close_in)
        in.close();
      closed = true;
    }
  }

  /**
   * fills the buffer by input from our source stream.
   */
  protected void fill() throws IOException {
    if (closed) { throw new IOException("Stream closed"); }
    int len = in.read(buf, 0, buf.length);
    if (len == -1) {
      if(inflater.istate.was != -1){  // in reading trailer
        throw new IOException("footer is not found");
      }
      else{
        throw new EOFException("Unexpected end of ZLIB input stream");
      }
    }
    inflater.setInput(buf, 0, len, true);
  }

  /**
   * returns always {@code false} to indicate that {@link #mark} is not
   * supported by this stream.
   */
  public boolean markSupported() {
    return false;
  }

  /**
   * Does nothing.
   */
  public synchronized void mark(int readlimit) {
  }

  /**
   * Throws always an exception, because {@link #mark} and {@code reset}
   * are not supported by this stream.
   */
  public synchronized void reset() throws IOException {
    throw new IOException("mark/reset not supported");
  }

  /**
   * gets the total number of bytes read from our source stream
   * (i.e. compressed data).
   */
  public long getTotalIn() {
    return inflater.getTotalIn();
  }

  /**
   * gets the total number of bytes read from this stream
   * (i.e. decompressed data).
   */
  public long getTotalOut() {
    return inflater.getTotalOut();
  }

  /**
   * Returns remaining available input data which was not yet
   * used for deflating.
   * This is mainly useful when in an application protocol other
   * data follows after the deflated one (i.e. after "end-of-file").
   */
  public byte[] getAvailIn() {
    if(inflater.avail_in<=0)
      return null;
    byte[] tmp = new byte[inflater.avail_in];
    System.arraycopy(inflater.next_in, inflater.next_in_index,
                     tmp, 0, inflater.avail_in);
    return tmp;
  }

  /**
   * Makes sure that our Inflater has read and parsed the whole header.
   */
  public void readHeader() throws IOException {

    byte[] empty = "".getBytes();
    inflater.setInput(empty, 0, 0, false);
    inflater.setOutput(empty, 0, 0);

    int err = inflater.inflate(JZlib.Z_NO_FLUSH);
    if(!inflater.istate.inParsingHeader()){
      return;
    }

    byte[] b1 = new byte[1];
    do{
      int i = in.read(b1);
      if(i<=0)
        throw new IOException("no input");
      inflater.setInput(b1);
      err = inflater.inflate(JZlib.Z_NO_FLUSH);
      if(err!=0/*Z_OK*/)
        throw new IOException(inflater.msg);
    }
    while(inflater.istate.inParsingHeader());
  }

  /**
   * Returns the Inflater used by this stream.
   */
  public Inflater getInflater(){
    return inflater;
  }
}