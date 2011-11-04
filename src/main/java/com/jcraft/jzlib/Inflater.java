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
/*
 * This program is based on zlib-1.1.3, so all credit should go authors
 * Jean-loup Gailly(jloup@gzip.org) and Mark Adler(madler@alumni.caltech.edu)
 * and contributors of zlib.
 */

package com.jcraft.jzlib;


/**
 * The core decompression class: A pair of buffers with
 * some metadata for decompression, and
 * the necessary decompression methods.
 *
 * This corresponds to the
 * <a href="http://zlib.net/manual.html#Stream">z_stream_s</a>
 * data structure (and z_streamp pointer type) in zlib, together
 * with most of the inflate-related functions defined in zlib.h.
 *
 * (You should not use the inflating methods inherited
 * from our parent class.)
 * @see Deflater
 */
final public class Inflater extends ZStream{

  static final private int MAX_WBITS=15;        // 32K LZ77 window
  static final private int DEF_WBITS=MAX_WBITS;

  static final private int Z_NO_FLUSH=0;
  static final private int Z_PARTIAL_FLUSH=1;
  static final private int Z_SYNC_FLUSH=2;
  static final private int Z_FULL_FLUSH=3;
  static final private int Z_FINISH=4;

  static final private int MAX_MEM_LEVEL=9;

  static final private int Z_OK=0;
  static final private int Z_STREAM_END=1;
  static final private int Z_NEED_DICT=2;
  static final private int Z_ERRNO=-1;
  static final private int Z_STREAM_ERROR=-2;
  static final private int Z_DATA_ERROR=-3;
  static final private int Z_MEM_ERROR=-4;
  static final private int Z_BUF_ERROR=-5;
  static final private int Z_VERSION_ERROR=-6;

  /**
   * Creates a new Inflater, using
   * the default (maximum) window size and the zlib format.
   */
  public Inflater() {
    super();
    init();
  }

  /**
   * Creates a new Inflater, using
   * the given window size and the zlib format.
   * @param w the base two logarithm of the window size (e.g. the size
   *   of the history buffer). This should be in the range {@code 8 .. 15},
   *   resulting in window sizes between 256 and 32768 bytes.
   *   This must be as least as large as the window size used
   *   for compressing.
   */
  public Inflater(int w) throws GZIPException {
    this(w, false);
  }

  /**
   * Creates a new Inflater with given window size and reading either
   *  zlib or plain deflate formatted input.
   * @param w the base two logarithm of the window size (e.g. the size
   *   of the history buffer). This should be in the range {@code 8 .. 15},
   *   resulting in window sizes between 256 and 32768 bytes.
   *   This must be as least as large as the window size used
   *   for compressing.
   * @param nowrap if {@code true}, the stream uses the plain deflate
   *    format. If {@code false}, the stream uses the {@code zlib} format
   *   (which includes a header and checksum).
   */
  public Inflater(int w, boolean nowrap) throws GZIPException {
    super();
    int ret = init(w, nowrap);
    if(ret!=Z_OK)
      throw new GZIPException(ret+": "+msg);
  }

  // TODO: is this ever read? The finished() method instead
  //  evaluates another condition.
  private boolean finished = false;

  /**
   * Initializes the stream for decompression (inflating), using
   * the default (maximum) window size and the zlib format.
   */
  public int init(){
    return init(DEF_WBITS);
  }

  /**
   * Initializes the stream for decompression (inflating), using
   * the default (maximum) window size.
   * @param nowrap if {@code true}, the stream uses the plain deflate
   *    format. If {@code false}, the stream uses the {@code zlib} format
   *   (which includes a header and checksum).
   */
  public int init(boolean nowrap){
    return init(DEF_WBITS, nowrap);
  }

  /**
   * Initializes the stream for decompression (inflating), using
   * zlib format.
   * @param w the base two logarithm of the window size (e.g. the size
   *   of the history buffer). This should be in the range {@code 8 .. 15},
   *   resulting in window sizes between 256 and 32768 bytes.
   *   This must be as least as large as the window size used
   *   for compressing.
   */
  public int init(int w){
    return init(w, false);
  }

  /**
   * Initializes the stream for decompression (inflating).
   * @param w the base two logarithm of the window size (e.g. the size
   *   of the history buffer). This should be in the range {@code 8 .. 15},
   *   resulting in window sizes between 256 and 32768 bytes.
   *   This must be as least as large as the window size used
   *   for compressing.
   * @param nowrap if {@code true}, the stream uses the plain deflate
   *    format. If {@code false}, the stream uses the {@code zlib} format
   *   (which includes a header and checksum).
   */
  public int init(int w, boolean nowrap){
    finished = false;
    istate=new Inflate(this);
    return istate.inflateInit(nowrap?-w:w);
  }

  /**
   * {@code inflate} decompresses as much data as possible, and stops when
   * the input buffer becomes empty or the output buffer becomes full.
   * It may introduce some output latency (reading input without producing
   * any output) except when forced to flush.
   *<p>
   *  The detailed semantics are as follows. {@code inflate} performs one or
   *  both of the following actions:
   *</p>
   *<ul>
   *  <li>Decompress more input from {@link #next_in}, starting
   *    at {@link #next_in_index}, and update {@link #next_in_index} and
   *    {@link #avail_in} accordingly. If not all input can be processed
   *    (because there is not enough room in the output buffer),
   *    {@link #next_in_index} is updated and processing will resume
   *     at this point for the next call of inflate().</li>
   *  <li>Provide more output in {@link #next_out}, starting
   *     at {@link #next_out_index} and update {@link #next_out_index}
   *     and {@link #avail_out} accordingly. {@code inflate()} provides
   *     as much output as possible, until there is no more input data
   *     or no more space in the output buffer (see below about the
   *     {@code flush} parameter).</li>
   *</ul>
   *<p>
   *  Before the call of {@code inflate()}, the application should ensure
   *  that at least one of the actions is possible, by providing more
   *  input and/or consuming more output, and updating the {code next_*_index}
   *  and {@code avail_*} values accordingly. The application can consume
   *  the uncompressed output when it wants, for example when the output
   *  buffer is full ({@link #avail_out}{@code == 0}), or after each call
   *  of {@code inflate()}. If {@code inflate} returns {@link JZlib#Z_OK Z_OK}
   *  and with zero {@link #avail_out}, it must be called again after
   *  making room in the output buffer because there might be more output
   *  pending.
   *</p>
   *<p>
   *  If a preset dictionary is needed after this call (see
   *  {@link #inflateSetDictionary}), {@code inflate} returns
   *  {@link JZlib#Z_NEED_DICT Z_NEED_DICT}, {@link #getAdler()} then
   *  will return the Adler32 checksum of the dictionary chosen by the
   *  compressor; otherwise it returns {@link JZlib#Z_OK Z_OK},
   *  {@link JZlib#Z_STREAM_END Z_STREAM_END} or an error code as
   *  described below and {@link #getAdler} will return the
   *  Adler32 checksum of all output produced so far (that is,
   *  {@link #total_out} bytes).
   *  At the end of the stream (for zlib format), {@code inflate()} checks
   *  that its computed adler32 checksum is equal to that saved by the
   *  compressor and returns {@link JZlib#Z_STREAM_END Z_STREAM_END} only if the
   *  checksum is correct.
   *</p>
   *<p>
   *  {@code inflate()} will decompress and check either zlib-wrapped
   *   or plain deflate data, depending on the inflateInit method used
   *  (and its {@code nowrap} parameter).
   *</p>
   * @param f one of
   *    {@link JZlib#Z_NO_FLUSH Z_NO_FLUSH},
   *    {@link JZlib#Z_SYNC_FLUSH Z_SYNC_FLUSH}, and
   *    {@link JZlib#Z_FINISH Z_FINISH}.
   * <p>
   *  {@link JZlib#Z_NO_FLUSH Z_NO_FLUSH} is the usual value for
   *  non-interactive usage.
   * </p>
   * <p>
   *  {@link JZlib#Z_SYNC_FLUSH Z_SYNC_FLUSH} requests that {@code inflate()}
   *  flush as much output as possible to the output buffer.
   * </p>
   * <p>
   *  {@code inflate()} should normally be called until it returns
   *  {@link JZlib#Z_STREAM_END Z_STREAM_END} or an error. However if
   *  all decompression is to be performed in a single step (a single
   *  call of {@code inflate}), the parameter {@code flush} should be set
   *  to {@link JZlib#Z_FINISH Z_FINISH}.
   *  In this case all pending input is processed and all pending output
   *  is flushed; {@link #avail_out} must be large enough to hold all
   *  the uncompressed data. (The size of the uncompressed data may have
   *  been saved by the compressor for this purpose.) The next operation
   *  on this stream must
   *  be {@link #inflateEnd} to deallocate the decompression state. The use
   *  of {@link JZlib#Z_FINISH Z_FINISH} is never required, but can be
   *  used to inform
   *  {@code inflate} that a faster approach may be used for the single
   *  {@code inflate()} call. 
   *</p>
   *<p>
   * In this implementation, {@code inflate()} always flushes as much output as
   * possible to the output buffer, and always uses the faster approach
   * on the first call. So the only effect of the flush parameter in this
   * implementation is on the return value of {@code inflate()}, as noted
   * below.</p>
   * @return <ul>
   *  <li>{@link JZlib#Z_OK Z_OK} if some progress has been made (more input
   *    processed or more output produced),</li>
   *  <li>{@link JZlib#Z_STREAM_END Z_STREAM_END} if the end of the
   *    compressed data has been reached and all uncompressed output
   *    has been produced (and for the zlib format, the checksum matches),</li>
   *  <li>{@link JZlib#Z_NEED_DICT Z_NEED_DICT} if a preset dictionary is
   *    needed at this point,</li>
   *  <li>{@link JZlib#Z_DATA_ERROR Z_DATA_ERROR} if the input data
   *    was corrupted (input stream not conforming to the zlib format
   *    or incorrect check value),</li>
   *  <li>{@link JZlib#Z_STREAM_ERROR Z_STREAM_ERROR} if the stream structure
   *    was inconsistent (for example if {@link #next_in} or
   *    {@link #next_out} was {@code null}),</li>
   *  <li>{@link JZlib#Z_BUF_ERROR Z_BUF_ERROR} if no progress is possible
   *    or if there was not enough room in the output buffer when
   *    {@link JZlib#Z_FINISH Z_FINISH} is used.</li>
   * </ul>
   * <p>
   *   Note that {@link JZlib#Z_BUF_ERROR Z_BUF_ERROR} is not fatal, and
   *   {@code inflate()} can be called again with more input and more output
   *   space to continue decompressing. If
   *   {@link JZlib#Z_DATA_ERROR Z_DATA_ERROR} is
   *   returned, the application may then call {@link #inflateSync()} to
   *   look for a good compression block if a partial recovery of the
   *   data is desired.
   * </p>
   */
  public int inflate(int f){
    if(istate==null) return Z_STREAM_ERROR;
    int ret = istate.inflate(f);
    if(ret == Z_STREAM_END) 
      finished = true;
    return ret;
  }

  /**
   * All dynamically allocated data structures for this stream are freed.
   * This function discards any unprocessed input and does not flush any
   * pending output. 
   *
   * @return {@link JZlib#Z_OK Z_OK} if success,
   *    {@link JZlib#Z_STREAM_ERROR Z_STREAM_ERROR} if the stream state
   *    was inconsistent. In the error case, {@link #msg} may be set.
   */
  public int end(){
    finished = true;
    if(istate==null) return Z_STREAM_ERROR;
    int ret=istate.inflateEnd();
//    istate = null;
    return ret;
  }

  /**
   * Skips invalid compressed data until a full flush point (see above
   *  the description of {@link #deflate} with
   *  {@link JZlib#Z_FULL_FLUSH Z_FULL_FLUSH}) can be found, or until all
   *  available input is skipped. No output is provided.
   *<p>
   *  In the success case, the application may save the current current
   *  value of {@link #total_in} which indicates where valid compressed
   *  data was found. In the error case, the application may repeatedly
   *  call {@link #inflateSync}, providing more input each time, until
   *  success or end of the input data.
   *</p>
   * @return 
   *   {@link JZlib#Z_OK Z_OK} if a full flush point has been found,
   *   {@link JZlib#Z_BUF_ERROR Z_BUF_ERROR} if no more input was provided,
   *   {@link JZlib#Z_DATA_ERROR Z_DATA_ERROR} if no flush point has been
   *   found, or
   *   {@link JZlib#Z_STREAM_ERROR Z_STREAM_ERROR} if the stream structure
   *   was inconsistent.
   */
  public int sync(){
    if(istate == null)
      return Z_STREAM_ERROR;
    return istate.inflateSync();
  }

  // TODO
  public int syncPoint(){
    if(istate == null)
      return Z_STREAM_ERROR;
    return istate.inflateSyncPoint();
  }

  /**
   * Initializes the decompression dictionary from the given uncompressed
   * byte sequence.
   * <p>
   *  This function must be called immediately after a call of {@link #inflate},
   *  if that call returned {@link JZlib#Z_NEED_DICT Z_NEED_DICT}. The
   *  dictionary chosen by the compressor can be determined from the
   *  adler32 value returned by {@link #getAdler} after that call of inflate.
   *  The compressor and decompressor must use exactly the same dictionary
   *  (see {@link #deflateSetDictionary}).
   * </p>
   * <p>
   *  For raw inflate (i.e. decompressing plain deflate data without
   *  a zlib header), this
   *  function can be called immediately after {@link #inflateInit} and
   *  before any call of {@link #inflate} to set the dictionary.
   *  The application must insure that the dictionary that was used for
   *  compression is provided.
   * </p>
   * <p>
   *  {@code inflateSetDictionary} does not perform any decompression:
   *  this will be done by subsequent calls of {@link #inflate}.
   * </p>
   * @param dictionary an array containing uncompressed data to use as the
   *   dictionary for future {@link #inflate} calls.
   * @param dictLength the length of the data in the array.
   * @return  {@link JZlib#Z_OK Z_OK} if success,
   *    {@link JZlib#Z_STREAM_ERROR Z_STREAM_ERROR} if a parameter
   *    is invalid (such as {@code null} dictionary) or the stream
   *    state is inconsistent,
   *   {@link JZlib#Z_DATA_ERROR Z_DATA_ERROR} if the given dictionary
   *    doesn't match the expected one (incorrect adler32 value).
   */
  public int setDictionary(byte[] dictionary, int dictLength){
    if(istate == null)
      return Z_STREAM_ERROR;
    return istate.inflateSetDictionary(dictionary, dictLength);
  }

  // TODO
  public boolean finished(){
    return istate.mode==12 /*DONE*/;
  }
}
