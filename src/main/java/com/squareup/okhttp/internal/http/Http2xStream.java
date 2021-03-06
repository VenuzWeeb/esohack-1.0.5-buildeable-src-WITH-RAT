package com.squareup.okhttp.internal.http;

import com.squareup.okhttp.Headers;
import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;
import com.squareup.okhttp.internal.Util;
import com.squareup.okhttp.internal.framed.ErrorCode;
import com.squareup.okhttp.internal.framed.FramedConnection;
import com.squareup.okhttp.internal.framed.FramedStream;
import com.squareup.okhttp.internal.framed.Header;
import java.io.IOException;
import java.net.ProtocolException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okio.ByteString;
import okio.ForwardingSource;
import okio.Okio;
import okio.Sink;
import okio.Source;

public final class Http2xStream implements HttpStream {
   private static final ByteString CONNECTION = ByteString.encodeUtf8("connection");
   private static final ByteString HOST = ByteString.encodeUtf8("host");
   private static final ByteString KEEP_ALIVE = ByteString.encodeUtf8("keep-alive");
   private static final ByteString PROXY_CONNECTION = ByteString.encodeUtf8("proxy-connection");
   private static final ByteString TRANSFER_ENCODING = ByteString.encodeUtf8("transfer-encoding");
   private static final ByteString TE = ByteString.encodeUtf8("te");
   private static final ByteString ENCODING = ByteString.encodeUtf8("encoding");
   private static final ByteString UPGRADE = ByteString.encodeUtf8("upgrade");
   private static final List<ByteString> SPDY_3_SKIPPED_REQUEST_HEADERS;
   private static final List<ByteString> SPDY_3_SKIPPED_RESPONSE_HEADERS;
   private static final List<ByteString> HTTP_2_SKIPPED_REQUEST_HEADERS;
   private static final List<ByteString> HTTP_2_SKIPPED_RESPONSE_HEADERS;
   private final StreamAllocation streamAllocation;
   private final FramedConnection framedConnection;
   private HttpEngine httpEngine;
   private FramedStream stream;

   public Http2xStream(StreamAllocation streamAllocation, FramedConnection framedConnection) {
      this.streamAllocation = streamAllocation;
      this.framedConnection = framedConnection;
   }

   public void setHttpEngine(HttpEngine httpEngine) {
      this.httpEngine = httpEngine;
   }

   public Sink createRequestBody(Request request, long contentLength) throws IOException {
      return this.stream.getSink();
   }

   public void writeRequestHeaders(Request request) throws IOException {
      if (this.stream == null) {
         this.httpEngine.writingRequestHeaders();
         boolean permitsRequestBody = this.httpEngine.permitsRequestBody(request);
         List<Header> requestHeaders = this.framedConnection.getProtocol() == Protocol.HTTP_2 ? http2HeadersList(request) : spdy3HeadersList(request);
         boolean hasResponseBody = true;
         this.stream = this.framedConnection.newStream(requestHeaders, permitsRequestBody, hasResponseBody);
         this.stream.readTimeout().timeout((long)this.httpEngine.client.getReadTimeout(), TimeUnit.MILLISECONDS);
         this.stream.writeTimeout().timeout((long)this.httpEngine.client.getWriteTimeout(), TimeUnit.MILLISECONDS);
      }
   }

   public void writeRequestBody(RetryableSink requestBody) throws IOException {
      requestBody.writeToSocket(this.stream.getSink());
   }

   public void finishRequest() throws IOException {
      this.stream.getSink().close();
   }

   public Response.Builder readResponseHeaders() throws IOException {
      return this.framedConnection.getProtocol() == Protocol.HTTP_2 ? readHttp2HeadersList(this.stream.getResponseHeaders()) : readSpdy3HeadersList(this.stream.getResponseHeaders());
   }

   public static List<Header> spdy3HeadersList(Request request) {
      Headers headers = request.headers();
      List<Header> result = new ArrayList(headers.size() + 5);
      result.add(new Header(Header.TARGET_METHOD, request.method()));
      result.add(new Header(Header.TARGET_PATH, RequestLine.requestPath(request.httpUrl())));
      result.add(new Header(Header.VERSION, "HTTP/1.1"));
      result.add(new Header(Header.TARGET_HOST, Util.hostHeader(request.httpUrl())));
      result.add(new Header(Header.TARGET_SCHEME, request.httpUrl().scheme()));
      Set<ByteString> names = new LinkedHashSet();
      int i = 0;

      for(int size = headers.size(); i < size; ++i) {
         ByteString name = ByteString.encodeUtf8(headers.name(i).toLowerCase(Locale.US));
         if (!SPDY_3_SKIPPED_REQUEST_HEADERS.contains(name)) {
            String value = headers.value(i);
            if (names.add(name)) {
               result.add(new Header(name, value));
            } else {
               for(int j = 0; j < result.size(); ++j) {
                  if (((Header)result.get(j)).name.equals(name)) {
                     String concatenated = joinOnNull(((Header)result.get(j)).value.utf8(), value);
                     result.set(j, new Header(name, concatenated));
                     break;
                  }
               }
            }
         }
      }

      return result;
   }

   private static String joinOnNull(String first, String second) {
      return first + '\u0000' + second;
   }

   public static List<Header> http2HeadersList(Request request) {
      Headers headers = request.headers();
      List<Header> result = new ArrayList(headers.size() + 4);
      result.add(new Header(Header.TARGET_METHOD, request.method()));
      result.add(new Header(Header.TARGET_PATH, RequestLine.requestPath(request.httpUrl())));
      result.add(new Header(Header.TARGET_AUTHORITY, Util.hostHeader(request.httpUrl())));
      result.add(new Header(Header.TARGET_SCHEME, request.httpUrl().scheme()));
      int i = 0;

      for(int size = headers.size(); i < size; ++i) {
         ByteString name = ByteString.encodeUtf8(headers.name(i).toLowerCase(Locale.US));
         if (!HTTP_2_SKIPPED_REQUEST_HEADERS.contains(name)) {
            result.add(new Header(name, headers.value(i)));
         }
      }

      return result;
   }

   public static Response.Builder readSpdy3HeadersList(List<Header> headerBlock) throws IOException {
      String status = null;
      String version = "HTTP/1.1";
      Headers.Builder headersBuilder = new Headers.Builder();
      int i = 0;

      for(int size = headerBlock.size(); i < size; ++i) {
         ByteString name = ((Header)headerBlock.get(i)).name;
         String values = ((Header)headerBlock.get(i)).value.utf8();

         int end;
         for(int start = 0; start < values.length(); start = end + 1) {
            end = values.indexOf(0, start);
            if (end == -1) {
               end = values.length();
            }

            String value = values.substring(start, end);
            if (name.equals(Header.RESPONSE_STATUS)) {
               status = value;
            } else if (name.equals(Header.VERSION)) {
               version = value;
            } else if (!SPDY_3_SKIPPED_RESPONSE_HEADERS.contains(name)) {
               headersBuilder.add(name.utf8(), value);
            }
         }
      }

      if (status == null) {
         throw new ProtocolException("Expected ':status' header not present");
      } else {
         StatusLine statusLine = StatusLine.parse(version + " " + status);
         return (new Response.Builder()).protocol(Protocol.SPDY_3).code(statusLine.code).message(statusLine.message).headers(headersBuilder.build());
      }
   }

   public static Response.Builder readHttp2HeadersList(List<Header> headerBlock) throws IOException {
      String status = null;
      Headers.Builder headersBuilder = new Headers.Builder();
      int i = 0;

      for(int size = headerBlock.size(); i < size; ++i) {
         ByteString name = ((Header)headerBlock.get(i)).name;
         String value = ((Header)headerBlock.get(i)).value.utf8();
         if (name.equals(Header.RESPONSE_STATUS)) {
            status = value;
         } else if (!HTTP_2_SKIPPED_RESPONSE_HEADERS.contains(name)) {
            headersBuilder.add(name.utf8(), value);
         }
      }

      if (status == null) {
         throw new ProtocolException("Expected ':status' header not present");
      } else {
         StatusLine statusLine = StatusLine.parse("HTTP/1.1 " + status);
         return (new Response.Builder()).protocol(Protocol.HTTP_2).code(statusLine.code).message(statusLine.message).headers(headersBuilder.build());
      }
   }

   public ResponseBody openResponseBody(Response response) throws IOException {
      Source source = new Http2xStream.StreamFinishingSource(this.stream.getSource());
      return new RealResponseBody(response.headers(), Okio.buffer((Source)source));
   }

   public void cancel() {
      if (this.stream != null) {
         this.stream.closeLater(ErrorCode.CANCEL);
      }

   }

   static {
      SPDY_3_SKIPPED_REQUEST_HEADERS = Util.immutableList((Object[])(CONNECTION, HOST, KEEP_ALIVE, PROXY_CONNECTION, TRANSFER_ENCODING, Header.TARGET_METHOD, Header.TARGET_PATH, Header.TARGET_SCHEME, Header.TARGET_AUTHORITY, Header.TARGET_HOST, Header.VERSION));
      SPDY_3_SKIPPED_RESPONSE_HEADERS = Util.immutableList((Object[])(CONNECTION, HOST, KEEP_ALIVE, PROXY_CONNECTION, TRANSFER_ENCODING));
      HTTP_2_SKIPPED_REQUEST_HEADERS = Util.immutableList((Object[])(CONNECTION, HOST, KEEP_ALIVE, PROXY_CONNECTION, TE, TRANSFER_ENCODING, ENCODING, UPGRADE, Header.TARGET_METHOD, Header.TARGET_PATH, Header.TARGET_SCHEME, Header.TARGET_AUTHORITY, Header.TARGET_HOST, Header.VERSION));
      HTTP_2_SKIPPED_RESPONSE_HEADERS = Util.immutableList((Object[])(CONNECTION, HOST, KEEP_ALIVE, PROXY_CONNECTION, TE, TRANSFER_ENCODING, ENCODING, UPGRADE));
   }

   class StreamFinishingSource extends ForwardingSource {
      public StreamFinishingSource(Source delegate) {
         super(delegate);
      }

      public void close() throws IOException {
         Http2xStream.this.streamAllocation.streamFinished(Http2xStream.this);
         super.close();
      }
   }
}
