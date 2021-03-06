package com.squareup.okhttp.internal.http;

import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.Response;
import java.io.IOException;
import java.net.ProtocolException;

public final class StatusLine {
   public static final int HTTP_TEMP_REDIRECT = 307;
   public static final int HTTP_PERM_REDIRECT = 308;
   public static final int HTTP_CONTINUE = 100;
   public final Protocol protocol;
   public final int code;
   public final String message;

   public StatusLine(Protocol protocol, int code, String message) {
      this.protocol = protocol;
      this.code = code;
      this.message = message;
   }

   public static StatusLine get(Response response) {
      return new StatusLine(response.protocol(), response.code(), response.message());
   }

   public static StatusLine parse(String statusLine) throws IOException {
      byte codeStart;
      Protocol protocol;
      int code;
      if (statusLine.startsWith("HTTP/1.")) {
         if (statusLine.length() < 9 || statusLine.charAt(8) != ' ') {
            throw new ProtocolException("Unexpected status line: " + statusLine);
         }

         code = statusLine.charAt(7) - 48;
         codeStart = 9;
         if (code == 0) {
            protocol = Protocol.HTTP_1_0;
         } else {
            if (code != 1) {
               throw new ProtocolException("Unexpected status line: " + statusLine);
            }

            protocol = Protocol.HTTP_1_1;
         }
      } else {
         if (!statusLine.startsWith("ICY ")) {
            throw new ProtocolException("Unexpected status line: " + statusLine);
         }

         protocol = Protocol.HTTP_1_0;
         codeStart = 4;
      }

      if (statusLine.length() < codeStart + 3) {
         throw new ProtocolException("Unexpected status line: " + statusLine);
      } else {
         try {
            code = Integer.parseInt(statusLine.substring(codeStart, codeStart + 3));
         } catch (NumberFormatException var5) {
            throw new ProtocolException("Unexpected status line: " + statusLine);
         }

         String message = "";
         if (statusLine.length() > codeStart + 3) {
            if (statusLine.charAt(codeStart + 3) != ' ') {
               throw new ProtocolException("Unexpected status line: " + statusLine);
            }

            message = statusLine.substring(codeStart + 4);
         }

         return new StatusLine(protocol, code, message);
      }
   }

   public String toString() {
      StringBuilder result = new StringBuilder();
      result.append(this.protocol == Protocol.HTTP_1_0 ? "HTTP/1.0" : "HTTP/1.1");
      result.append(' ').append(this.code);
      if (this.message != null) {
         result.append(' ').append(this.message);
      }

      return result.toString();
   }
}
