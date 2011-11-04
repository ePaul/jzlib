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
 * The core compression class: A pair of buffers with
 * some metadata for compression, and
 * the necessary compression methods.
 *<p>
 * This corresponds to the
 * <a href="http://zlib.net/manual.html#Stream">z_stream_s</a>
 * data structure (and z_streamp pointer type) in zlib, together
 * with the deflating functions defined in zlib.h.
 *</p>
 * (You should not use the inflating methods inherited
 * from our parent class.)
 * @see Inflater
 */
final public class Deflater extends ZStream{

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

  private boolean finished = false;

  /**
   * Creates a new Deflater using an Adler32 checksum, but
   * otherwise uninitialized. Use one of the {@code init} methods
   * after construction.
   * Alternatively, use one of the other constructors.
   */
  public Deflater(){
    super();
  }

  /**
   * Creates a new Deflater using the given level and the maximum
   * lookback window, creating zlib-formatted data.
   * @param level the deflation level. This should be
   *   {@link JZlib#Z_NO_COMPRESSION Z_NO_COMPRESSION},
   *   {@link JZlib#Z_DEFAULT_COMPRESSION Z_DEFAULT_COMPRESSION} or a
   *   value between {@link JZlib#Z_BEST_SPEED Z_BEST_SPEED} (1) and
   *  {@link JZlib#Z_BEST_COMPRESSION Z_BEST_COMPRESSION} (9) (both inclusive).
   */
  public Deflater(int level) throws GZIPException {
    this(level, MAX_WBITS);
  }

  /**
   * Creates a new Deflater using the given level, maximum lookback window
   * and nowrap-mode.
   * @param level the deflation level. This should be
   *   {@link JZlib#Z_NO_COMPRESSION Z_NO_COMPRESSION},
   *   {@link JZlib#Z_DEFAULT_COMPRESSION Z_DEFAULT_COMPRESSION} or a
   *   value between {@link JZlib#Z_BEST_SPEED Z_BEST_SPEED} (1) and
   *  {@link JZlib#Z_BEST_COMPRESSION Z_BEST_COMPRESSION} (9) (both inclusive).
   * @param nowrap if {@code true}, the stream uses the plain deflate
   *    format. If {@code false}, the stream uses the {@code zlib} format
   *    (which includes a header and checksum).
   */
  public Deflater(int level, boolean nowrap) throws GZIPException {
    this(level, MAX_WBITS, nowrap);
  }

  /**
   * Creates a new Deflater using the given level and 
   * lookback window size, producing zlib-formatted data.
   * @param level the deflation level. This should be
   *   {@link JZlib#Z_NO_COMPRESSION Z_NO_COMPRESSION},
   *   {@link JZlib#Z_DEFAULT_COMPRESSION Z_DEFAULT_COMPRESSION} or a
   *   value between {@link JZlib#Z_BEST_SPEED Z_BEST_SPEED} (1) and
   *  {@link JZlib#Z_BEST_COMPRESSION Z_BEST_COMPRESSION} (9) (both inclusive).
   * @param bits the base two logarithm of the window size (e.g. the size
   *   of the history buffer). This should be in the range {@code 8 .. 15},
   *   resulting in window sizes between 256 and 32768 bytes.
   *   Larger values result in better compression with more memory usage.
   */
  public Deflater(int level, int bits) throws GZIPException {
    this(level, bits, false);
  }

  /**
   * Creates a new Deflater,
   * using the given compression level and given lookback window size,
   * producing either zlib or plain deflate format.
   * @param level the deflation level. This should be
   *   {@link JZlib#Z_NO_COMPRESSION Z_NO_COMPRESSION},
   *   {@link JZlib#Z_DEFAULT_COMPRESSION Z_DEFAULT_COMPRESSION} or a
   *   value between {@link JZlib#Z_BEST_SPEED Z_BEST_SPEED} (1) and
   *   {@link JZlib#Z_BEST_COMPRESSION Z_BEST_COMPRESSION} (9) (both inclusive).
   * @param bits the base two logarithm of the window size (e.g. the size
   *   of the history buffer). This should be in the range {@code 8 .. 15},
   *   resulting in window sizes between 256 and 32768 bytes.
   *   Larger values result in better compression with more memory usage.
   * @param nowrap if {@code true}, the stream uses the plain deflate
   *    format. If {@code false}, the stream uses the {@code zlib} format
   *   (which includes a header and checksum).
   */
  public Deflater(int level, int bits, boolean nowrap) throws GZIPException {
    super();
    int ret = init(level, bits, nowrap);
    if(ret!=Z_OK)
      throw new GZIPException(ret+": "+msg);
  }

  // TODO
  public Deflater(int level, int bits, int memlevel) throws GZIPException {
    super();
    int ret = init(level, bits, memlevel);
    if(ret!=Z_OK)
      throw new GZIPException(ret+": "+msg);
  }

  /**
   * initializes the stream for deflation in zlib format,
   * using the given compression level and the maximum lookback window size.
   * @param level the deflation level. This should be
   *   {@link JZlib#Z_NO_COMPRESSION Z_NO_COMPRESSION},
   *   {@link JZlib#Z_DEFAULT_COMPRESSION Z_DEFAULT_COMPRESSION} or a
   *   value between {@link JZlib#Z_BEST_SPEED Z_BEST_SPEED} (1) and
   *  {@link JZlib#Z_BEST_COMPRESSION Z_BEST_COMPRESSION} (9) (both inclusive).
   */
  public int init(int level){
    return init(level, MAX_WBITS);
  }

  /**
   * initializes the stream for deflation,
   * using the given compression level and the maximum lookback window size.
   * @param level the deflation level. This should be
   *   {@link JZlib#Z_NO_COMPRESSION Z_NO_COMPRESSION},
   *   {@link JZlib#Z_DEFAULT_COMPRESSION Z_DEFAULT_COMPRESSION} or a
   *   value between {@link JZlib#Z_BEST_SPEED Z_BEST_SPEED} (1) and
   *   {@link JZlib#Z_BEST_COMPRESSION Z_BEST_COMPRESSION} (9) (both inclusive).
   * @param nowrap if {@code true}, the stream uses the plain deflate
   *    format. If {@code false}, the stream uses the {@code zlib} format
   *    (which includes a header and checksum).
   */
  public int init(int level, boolean nowrap){
    return init(level, MAX_WBITS, nowrap);
  }

  /**
   * initializes the stream for deflation in zlib format,
   * using the given compression level and lookback window size.
   * @param level the deflation level. This should be
   *   {@link JZlib#Z_NO_COMPRESSION Z_NO_COMPRESSION},
   *   {@link JZlib#Z_DEFAULT_COMPRESSION Z_DEFAULT_COMPRESSION} or a
   *   value between {@link JZlib#Z_BEST_SPEED Z_BEST_SPEED} (1) and
   *   {@link JZlib#Z_BEST_COMPRESSION Z_BEST_COMPRESSION} (9) (both inclusive).
   * @param bits the base two logarithm of the window size (e.g. the size
   *   of the history buffer). This should be in the range {@code 8 .. 15},
   *   resulting in window sizes between 256 and 32768 bytes.
   *   Larger values result in better compression with more memory usage.
   */
  public int init(int level, int bits){
    return init(level, bits, false);
  }

  // TODO
  public int init(int level, int bits, int memlevel){
    finished = false;
    dstate=new Deflate(this);
    return dstate.deflateInit(level, bits, memlevel);
  }

  /**
   * Initializes the stream for deflation,
   * using the given compression level and given lookback window size.
   * @param level the deflation level. This should be
   *   {@link JZlib#Z_NO_COMPRESSION Z_NO_COMPRESSION},
   *   {@link JZlib#Z_DEFAULT_COMPRESSION Z_DEFAULT_COMPRESSION} or a
   *   value between {@link JZlib#Z_BEST_SPEED Z_BEST_SPEED} (1) and
   *   {@link JZlib#Z_BEST_COMPRESSION Z_BEST_COMPRESSION} (9) (both inclusive).
   * @param bits the base two logarithm of the window size (e.g. the size
   *   of the history buffer). This should be in the range {@code 8 .. 15},
   *   resulting in window sizes between 256 and 32768 bytes.
   *   Larger values result in better compression with more memory usage.
   * @param nowrap if {@code true}, the stream uses the plain deflate
   *    format. If {@code false}, the stream uses the {@code zlib} format
   *   (which includes a header and checksum).
   */
  public int init(int level, int bits, boolean nowrap){
    finished = false;
    dstate=new Deflate(this);
    return dstate.deflateInit(level, nowrap?-bits:bits);
  }

  /**
   * {@code deflate} compresses as much data as possible, and stops when the
   * input buffer becomes empty or the output buffer becomes full. It may
   * introduce some output latency (reading input without producing any
   * output) except when forced to flush.
   *<p>
   * The detailed semantics are as follows. {@code deflate} performs one or
   * both of the following actions:
   *</p>
   *<ul>
   * <li>Compress more input from {@link #next_in}, starting at
   *   {@link #next_in_index}, and update {@link #next_in_index} and
   *   {@link #avail_in} accordingly.
   *   If not all input can be processed (because there is not enough room in
   *   the output buffer), {@link #next_in_index} and {@link #avail_in} are
   *   updated and processing will resume at this point for the next call
   *   of {@code deflate()}.</li>
   * <li>Provide more output in {@link #next_out}, starting at
   *   {@link #next_out_index} and update {@link #next_out_index} and
   *   {@link #avail_out} accordingly.
   *   This action is forced if the parameter {@code flush} is non zero (i.e.
   *   not {@link JZlib#Z_NO_FLUSH Z_NO_FLUSH}). Forcing flush frequently
   *   degrades the  compression ratio, so this parameter should be set
   *   only when necessary (in interactive applications). Some output may
   *   be provided even if {@code flush} is not set.</li>
   *</ul>
   *<p>
   * Before the call of {@code deflate()}, the application should ensure that
   * at least one of the actions is possible, by providing more input and/or
   * consuming more output, and updating {@link #avail_in} or {@link #avail_out}
   * accordingly; {@link #avail_out} should never be zero before the call.
   * The application can consume the compressed output when it wants, for
   * example when the output buffer is full ({@link #avail_out}{@code == 0}), or
   * after each call of {@code deflate()}. If deflate returns
   *  {@link JZlib#Z_OK Z_OK}
   * and with zero {@link #avail_out}, it must be called again after making room
   * in the output buffer because there might be more output pending.
   *</p>
   * @param flush whether and how to flush output.
   *  <p>
   *   Normally the parameter {@code flush} is set to
   *   {@link JZlib#Z_NO_FLUSH Z_NO_FLUSH},
   *   which allows {@code deflate} to decide how much data to accumulate
   *   before producing output, in order to maximize compression.
   *  </p>
   *  <p>
   *   If the parameter {@code flush} is set to
   *   {@link JZlib#Z_SYNC_FLUSH Z_SYNC_FLUSH}, all
   *   pending output is flushed to the output buffer and the output is
   *   aligned on a byte boundary, so that the decompressor can get all
   *   input data available so far. (In particular {@link #avail_in} is
   *   zero after the call if enough output space has been provided
   *   before the call.) <em>Flushing may degrade compression for some
   *   compression algorithms and so it should be used only when
   *   necessary</em>. This completes the current deflate block and follows
   *   it with an empty stored block that is three bits plus filler bits
   *   to the next byte, followed by four bytes (00 00 ff ff).
   *  </p>
   *  <p>
   *   If {@code flush} is set to
   *   {@link JZlib#Z_PARTIAL_FLUSH Z_PARTIAL_FLUSH}, all pending
   *   output is flushed to the output buffer, but the output is not aligned
   *   to a byte boundary. All of the input data so far will be available to
   *   the decompressor, as for {@link JZlib#Z_SYNC_FLUSH Z_SYNC_FLUSH}.
   *   This completes
   *   the current deflate block and follows it with an empty fixed codes
   *   block that is 10 bits long. This assures that enough bytes are output
   *   in order for the decompressor to finish the block before the empty
   *   fixed code block.
   *  </p>
   *  <p>
   *   If {@code flush} is set to {@link JZlib#Z_FULL_FLUSH Z_FULL_FLUSH},
   *   all output is flushed as with {@link JZlib#Z_SYNC_FLUSH Z_SYNC_FLUSH},
   *   and the compression
   *   state is reset so that decompression can restart from this point
   *   if previous compressed data has been damaged or if random access
   *   is desired. Using {@link JZlib#Z_FULL_FLUSH Z_FULL_FLUSH} too
   *   often can seriously degrade compression.
   *   On the decompression side, such reset points can be found with
   *   {@link #inflateSync}.
   *  </p>
   *  <p>
   *   If {@code deflate} returns with {@link #avail_out}{@code == 0}, this
   *   function must be called again with the same value of the {@code flush}
   *   parameter and more output space (updated {@link #avail_out}), until
   *   the flush is complete (deflate returns with non-zero {@link #avail_out}).
   *   In the case of a {@link JZlib#Z_FULL_FLUSH Z_FULL_FLUSH} or
   *   {@link JZlib#Z_SYNC_FLUSH Z_SYNC_FLUSH}, make sure that
   *   {@link #avail_out} is
   *   greater than six to avoid repeated flush markers due to
   *   {@link #avail_out}{@code == 0} on return.
   *  </p>
   *  <p>
   *   If the parameter {@code flush} is set to {@link JZlib#Z_FINISH Z_FINISH},
   *   pending input is processed, pending output is flushed and deflate
   *   returns with {@link JZlib#Z_STREAM_END Z_STREAM_END} if there was
   *   enough output
   *   space; if deflate returns with {@link JZlib#Z_OK Z_OK}, this function
   *   must be called again with {@link JZlib#Z_FINISH Z_FINISH} and more output
   *   space (updated {@link #avail_out}) but no more input data, until
   *   it returns with {@link JZlib#Z_STREAM_END Z_STREAM_END} or an error.
   *   After {@code deflate} has returned
   *   {@link JZlib#Z_STREAM_END Z_STREAM_END}, the only
   *   possible operation on the stream is {@link #deflateEnd}.
   *  </p>
   *  <p>
   *   Z_FINISH can be used immediately after {@link #deflateInit} if all
   *   the compression is to be done in a single step. In this case,
   *   {@link #avail_out} must be large enough to compress everything.
   *   If {@code deflate} does not return
   *    {@link JZlib#Z_STREAM_END Z_STREAM_END},
   *   then it must be called again as described above.
   *  </p>
   * @return <ul>
   *   <li>{@link JZlib#Z_OK Z_OK} if some progress
   *     has been made (more input processed or more output produced),</li>
   *   <li>{@link JZlib#Z_STREAM_END Z_STREAM_END} if all input has been
   *     consumed and all output has been produced (only when {@code flush}
   *     is set to {@link JZlib#Z_FINISH Z_FINISH}),</li>
   *   <li>{@link JZlib#Z_STREAM_ERROR Z_STREAM_ERROR} if the stream state
   *     was inconsistent (for example if {@link #next_in} or
   *     {@link #next_out} was {@code null}),</li>
   *   <li>{@link JZlib#Z_BUF_ERROR Z_BUF_ERROR} if no progress is possible
   *     (for example {@link #avail_in} or {@link #avail_out} was zero).</li>
   *</ul>
   * <p>
   *     Note that {@link JZlib#Z_BUF_ERROR Z_BUF_ERROR} is not fatal, and
   *    {@code deflate()} can be called again with more input and more
   *    output space to continue compressing.
   * </p>
   */
  public int deflate(int flush){
    if(dstate==null){
      return Z_STREAM_ERROR;
    }
    int ret = dstate.deflate(flush);
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
   *    {@link JZlib#Z_STREAM_ERROR Z_STREAM_ERROR}
   *    if the stream state was inconsistent,
   *   {@link JZlib#Z_DATA_ERROR Z_DATA_ERROR} if
   *    the stream was freed prematurely (some input or output was discarded).
   *    In the error case, {@link #msg} may be set.
   */
  public int end(){
    finished = true;
    if(dstate==null) return Z_STREAM_ERROR;
    int ret=dstate.deflateEnd();
    dstate=null;
    free();
    return ret;
  }
  /**
   * Dynamically update the compression level and compression strategy.
   *<p>
   *  This can be used to switch between compression and straight copy of
   *  the input data, or to switch to a different kind of input data
   *  requiring a different strategy. If the compression level is
   *  changed, the input available so far is compressed with the
   *  old level (and may be flushed); the new level will take effect
   *  only at the next call of deflate().
   *</p>
   *<p>
   * Before the call of {@code deflateParams}, the stream state must be
   * set as for a call of {@link #deflate}, since the currently available
   * input may have to be compressed and flushed. In particular,
   * {@link #avail_out} must be non-zero.
   *</p>
   * @param level the deflation level. This should be
   *   {@link JZlib#Z_NO_COMPRESSION Z_NO_COMPRESSION},
   *   {@link JZlib#Z_DEFAULT_COMPRESSION Z_DEFAULT_COMPRESSION} or a
   *   value between {@link JZlib#Z_BEST_SPEED Z_BEST_SPEED} (1) and
   *   {@link JZlib#Z_BEST_COMPRESSION Z_BEST_COMPRESSION} (9) (both inclusive).
   * @param strategy one of {@link JZlib#Z_DEFAULT_STRATEGY Z_DEFAULT_STRATEGY},
   *   {@link JZlib#Z_FILTERED Z_FILTERED} and
   *   {@link JZlib#Z_HUFFMAN_ONLY Z_HUFFMAN_ONLY}. (See the description
   *    of these constants for details on each.)
   * @return
   *   {@link JZlib#Z_OK Z_OK} if success,
   *   {@link JZlib#Z_STREAM_ERROR Z_STREAM_ERROR} if the source stream
   *    state was inconsistent or if a parameter was invalid,
   *   {@link JZlib#Z_BUF_ERROR Z_BUF_ERROR} if {@link #avail_out} was zero.
   */
  public int params(int level, int strategy){
    if(dstate==null) return Z_STREAM_ERROR;
    return dstate.deflateParams(level, strategy);
  }

  /**
   * Initializes the compression dictionary from the given byte sequence,
   * without producing any compressed output.
   *<p>
   * This function must be
   *  called immediately after {@link #deflateInit}, before any call
   *  of {@link #deflate}. The compressor and decompressor must use
   *  exactly the same dictionary (see {@link #inflateSetDictionary}).
   *</p>
   *<p>
   * The dictionary should consist of strings (byte sequences) that are
   * likely to be encountered later in the data to be compressed, with
   * the most commonly used strings preferably put towards the end of
   * the dictionary. Using a dictionary is most useful when the data
   * to be compressed is short and can be predicted with good accuracy;
   * the data can then be compressed better than with the default empty
   * dictionary.
   *</p>
   *<p>
   * Depending on the size of the compression data structures selected
   * by {@link #deflateInit}, a part of the dictionary may in effect be
   * discarded, for example if the dictionary is larger than the window
   * size used in {@link #deflateInit}. Thus the strings most likely
   * to be useful should be put at the end of the dictionary, not at
   * the front. In addition, the current implementation of deflate will
   * use at most the window size minus 262 bytes of the provided dictionary.
   *</p>
   *<p>
   * After return of this function, {@link #getAdler} will return the Adler32
   * value of the dictionary; the decompressor may later use this value
   * to determine which dictionary has been used by the compressor.
   * (The Adler32 value applies to the whole dictionary even if only a
   * subset of the dictionary is actually used by the compressor.) If a
   * raw deflate was requested (i.e. {@link #deflateInit(int,boolean)}
   * was invoked with {@code nowrap == true}, then the adler32 value is
   * not computed and cannot be retrieved from {@link #getAdler}.
   *</p>
   * <p>
   *  {@code deflateSetDictionary} does not perform any compression:
   *  this will be done by deflate().
   * </p>
   * @param dictionary an array containing the dictionary (from the start).
   * @param dictLength the length of the dictionary. (This should be at most
   *    {@code dictionary.length}.
   * @return
   *   {@link JZlib#Z_OK Z_OK} if success, or
   *   {@link JZlib#Z_STREAM_ERROR Z_STREAM_ERROR} if a parameter is
   *    invalid (such as a {@code null} dictionary) or the stream state
   *    is inconsistent (for example if {@link #deflate} has already
   *    been called for this stream).
   */
  public int setDictionary (byte[] dictionary, int dictLength){
    if(dstate == null)
      return Z_STREAM_ERROR;
    return dstate.deflateSetDictionary(dictionary, dictLength);
  }

  public boolean finished(){
    return finished;
  }

  public int copy(Deflater src){
    this.finished = src.finished;
    return Deflate.deflateCopy(this, src);
  }
}
