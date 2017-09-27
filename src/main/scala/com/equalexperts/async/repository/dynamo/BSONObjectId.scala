/*
 * Copyright 2017 Equal Experts
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.equalexperts.async.repository.dynamo

import scala.util.{Failure, Try}


/**
  * This is a Fork of https://github.com/ReactiveMongo/ReactiveMongo/blob/c65a35866540ef0d56351c64cafb40d9aeef22bc/bson/src/main/scala/types.scala#L349
  */

sealed trait BSONValue {
  /**
    * The code indicating the BSON type for this value
    */
  val code: Byte
}

/**
  * BSON ObjectId value.
  *
  * +------------------------+------------------------+------------------------+------------------------+
  * + timestamp (in seconds) +   machine identifier   +    thread identifier   +        increment       +
  * +        (4 bytes)       +        (3 bytes)       +        (2 bytes)       +        (3 bytes)       +
  * +------------------------+------------------------+------------------------+------------------------+
  */
@SerialVersionUID(239421902L)
class BSONObjectID private (private val raw: Array[Byte])
  extends BSONValue with Serializable with Equals {

  val code = 0x07.toByte

  import java.nio.ByteBuffer
  import java.util.Arrays

  /** ObjectId hexadecimal String representation */
  lazy val stringify = Converters.hex2Str(raw)

  override def toString = s"""BSONObjectID("${stringify}")"""

  override def canEqual(that: Any): Boolean = that.isInstanceOf[BSONObjectID]

  override def equals(that: Any): Boolean = that match {
    case BSONObjectID(other) => Arrays.equals(raw, other)
    case _                   => false
  }

  override lazy val hashCode: Int = Arrays.hashCode(raw)

  /** The time of this BSONObjectId, in milliseconds */
  def time: Long = this.timeSecond * 1000L

  /** The time of this BSONObjectId, in seconds */
  def timeSecond: Int = ByteBuffer.wrap(raw.take(4)).getInt

  def valueAsArray = Arrays.copyOf(raw, 12)
}

object BSONObjectID {
  private val maxCounterValue = 16777216
  private val increment = new java.util.concurrent.atomic.AtomicInteger(scala.util.Random.nextInt(maxCounterValue))

  private def counter = (increment.getAndIncrement + maxCounterValue) % maxCounterValue

  /**
    * The following implemtation of machineId work around openjdk limitations in
    * version 6 and 7
    *
    * Openjdk fails to parse /proc/net/if_inet6 correctly to determine macaddress
    * resulting in SocketException thrown.
    *
    * Please see:
    * * https://github.com/openjdk-mirror/jdk7u-jdk/blob/feeaec0647609a1e6266f902de426f1201f77c55/src/solaris/native/java/net/NetworkInterface.c#L1130
    * * http://lxr.free-electrons.com/source/net/ipv6/addrconf.c?v=3.11#L3442
    * * http://lxr.free-electrons.com/source/include/linux/netdevice.h?v=3.11#L1130
    * * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=7078386
    *
    * and fix in openjdk8:
    * * http://hg.openjdk.java.net/jdk8/tl/jdk/rev/b1814b3ea6d3
    */

  private val machineId = {
    import java.net._
    def p(n: String) = System.getProperty(n)
    val validPlatform = Try {
      val correctVersion = p("java.version").substring(0, 3).toFloat >= 1.8
      val noIpv6 = p("java.net.preferIPv4Stack").toBoolean == true
      val isLinux = p("os.name") == "Linux"

      !isLinux || correctVersion || noIpv6
    }.getOrElse(false)

    // Check java policies
    val permitted = {
      val sec = System.getSecurityManager();
      Try { sec.checkPermission(new NetPermission("getNetworkInformation")) }.toOption.map(_ => true).getOrElse(false);
    }

    if (validPlatform && permitted) {
      val networkInterfacesEnum = NetworkInterface.getNetworkInterfaces
      val networkInterfaces = scala.collection.JavaConverters.enumerationAsScalaIteratorConverter(networkInterfacesEnum).asScala
      val ha = networkInterfaces.find(ha => Try(ha.getHardwareAddress).isSuccess && ha.getHardwareAddress != null && ha.getHardwareAddress.length == 6)
        .map(_.getHardwareAddress)
        .getOrElse(InetAddress.getLocalHost.getHostName.getBytes("UTF-8"))
      Converters.md5(ha).take(3)
    } else {
      val threadId = Thread.currentThread.getId.toInt
      val arr = new Array[Byte](3)

      arr(0) = (threadId & 0xFF).toByte
      arr(1) = (threadId >> 8 & 0xFF).toByte
      arr(2) = (threadId >> 16 & 0xFF).toByte

      arr
    }
  }

  def apply(array: Array[Byte]): BSONObjectID = {
    if (array.length != 12)
      throw new IllegalArgumentException(s"wrong byte array for an ObjectId (size ${array.length})")
    new BSONObjectID(java.util.Arrays.copyOf(array, 12))
  }

  def unapply(id: BSONObjectID): Option[Array[Byte]] = Some(id.valueAsArray)

  /** Tries to make a BSON ObjectId from a hexadecimal string representation. */
  def parse(id: String): Try[BSONObjectID] = {
    if (id.length != 24) Failure[BSONObjectID](
      new IllegalArgumentException(s"Wrong ObjectId (length != 24): '$id'")
    )
    else Try(new BSONObjectID(Converters str2Hex id))
  }

  /**
    * Generates a new BSON ObjectID using the current time.
    *
    * @see [[fromTime]]
    */
  def generate(): BSONObjectID = fromTime(System.currentTimeMillis, false)

  /**
    * Generates a new BSON ObjectID from the given timestamp in milliseconds.
    *
    * The included timestamp is the number of seconds since epoch, so a BSONObjectID time part has only
    * a precision up to the second. To get a reasonably unique ID, you _must_ set `onlyTimestamp` to false.
    *
    * Crafting a BSONObjectID from a timestamp with `fillOnlyTimestamp` set to true is helpful for range queries,
    * eg if you want of find documents an _id field which timestamp part is greater than or lesser than
    * the one of another id.
    *
    * If you do not intend to use the produced BSONObjectID for range queries, then you'd rather use
    * the `generate` method instead.
    *
    * @param fillOnlyTimestamp if true, the returned BSONObjectID will only have the timestamp bytes set; the other will be set to zero.
    */
  def fromTime(timeMillis: Long, fillOnlyTimestamp: Boolean = true): BSONObjectID = {
    // n of seconds since epoch. Big endian
    val timestamp = (timeMillis / 1000).toInt
    val id = new Array[Byte](12)

    id(0) = (timestamp >>> 24).toByte
    id(1) = (timestamp >> 16 & 0xFF).toByte
    id(2) = (timestamp >> 8 & 0xFF).toByte
    id(3) = (timestamp & 0xFF).toByte

    if (!fillOnlyTimestamp) {
      // machine id, 3 first bytes of md5(macadress or hostname)
      id(4) = machineId(0)
      id(5) = machineId(1)
      id(6) = machineId(2)

      // 2 bytes of the pid or thread id. Thread id in our case. Low endian
      val threadId = Thread.currentThread.getId.toInt
      id(7) = (threadId & 0xFF).toByte
      id(8) = (threadId >> 8 & 0xFF).toByte

      // 3 bytes of counter sequence, which start is randomized. Big endian
      val c = counter
      id(9) = (c >> 16 & 0xFF).toByte
      id(10) = (c >> 8 & 0xFF).toByte
      id(11) = (c & 0xFF).toByte
    }

    BSONObjectID(id)
  }
}


/** Common functions */
object Converters {
  private val HEX_CHARS: Array[Char] = "0123456789abcdef".toCharArray

  /** Turns an array of Byte into a String representation in hexadecimal. */
  def hex2Str(bytes: Array[Byte]): String = {
    val hex = new Array[Char](2 * bytes.length)
    var i = 0
    while (i < bytes.length) {
      hex(2 * i) = HEX_CHARS((bytes(i) & 0xF0) >>> 4)
      hex(2 * i + 1) = HEX_CHARS(bytes(i) & 0x0F)
      i = i + 1
    }
    new String(hex)
  }

  /** Turns a hexadecimal String into an array of Byte. */
  def str2Hex(str: String): Array[Byte] = {
    val bytes = new Array[Byte](str.length / 2)
    var i = 0
    while (i < bytes.length) {
      bytes(i) = Integer.parseInt(str.substring(2 * i, 2 * i + 2), 16).toByte
      i += 1
    }
    bytes
  }

  /**
    * Returns the MD5 hash for the given `string`,
    * and turns it into a hexadecimal String representation.
    *
    * @param string the string to be hashed
    * @param encoding the string encoding/charset
    */
  def md5Hex(string: String, encoding: String): String =
    hex2Str(md5(string, encoding))

  /**
    * Returns the MD5 hash of the given `string`.
    *
    * @param string the string to be hashed
    * @param encoding the string encoding/charset
    */
  def md5(string: String, encoding: String): Array[Byte] =
    md5(string.getBytes(encoding))

  /** Computes the MD5 hash of the given `bytes`. */
  def md5(bytes: Array[Byte]): Array[Byte] =
    java.security.MessageDigest.getInstance("MD5").digest(bytes)

}