package com.squareup.okhttp.internal.framed;

import okio.ByteString;

public final class Header {
   public static final ByteString RESPONSE_STATUS = ByteString.encodeUtf8(":status");
   public static final ByteString TARGET_METHOD = ByteString.encodeUtf8(":method");
   public static final ByteString TARGET_PATH = ByteString.encodeUtf8(":path");
   public static final ByteString TARGET_SCHEME = ByteString.encodeUtf8(":scheme");
   public static final ByteString TARGET_AUTHORITY = ByteString.encodeUtf8(":authority");
   public static final ByteString TARGET_HOST = ByteString.encodeUtf8(":host");
   public static final ByteString VERSION = ByteString.encodeUtf8(":version");
   public final ByteString name;
   public final ByteString value;
   final int hpackSize;

   public Header(String name, String value) {
      this(ByteString.encodeUtf8(name), ByteString.encodeUtf8(value));
   }

   public Header(ByteString name, String value) {
      this(name, ByteString.encodeUtf8(value));
   }

   public Header(ByteString name, ByteString value) {
      this.name = name;
      this.value = value;
      this.hpackSize = 32 + name.size() + value.size();
   }

   public boolean equals(Object other) {
      if (!(other instanceof Header)) {
         return false;
      } else {
         Header that = (Header)other;
         return this.name.equals(that.name) && this.value.equals(that.value);
      }
   }

   public int hashCode() {
      int result = 17;
      int result = 31 * result + this.name.hashCode();
      result = 31 * result + this.value.hashCode();
      return result;
   }

   public String toString() {
      return String.format("%s: %s", this.name.utf8(), this.value.utf8());
   }
}
