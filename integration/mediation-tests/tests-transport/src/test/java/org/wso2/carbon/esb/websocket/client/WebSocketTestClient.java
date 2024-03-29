/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.carbon.esb.websocket.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import javax.net.ssl.SSLException;

/**
 * WebSocket client class for test
 */
public class WebSocketTestClient {

    private static final Log logger = LogFactory.getLog(WebSocketTestClient.class);

    private String url = null;
    private final String subProtocol;
    private Map<String, String> customHeaders = new HashMap<>();

    private Channel channel = null;
    private WebSocketClientHandler handler;
    private EventLoopGroup group;
    private CountDownLatch latch;

    public WebSocketTestClient(String url) {
        this.url = url;
        this.subProtocol = null;
    }

    public WebSocketTestClient(String url, String subProtocol, Map<String, String> customHeaders) {
        this.url = url;
        this.subProtocol = subProtocol;
        this.customHeaders = customHeaders;

    }

    public WebSocketTestClient(String url, CountDownLatch latch) {
        this.url = url;
        this.subProtocol = null;
        this.latch = latch;
    }

    public WebSocketTestClient(String url, String subProtocol, Map<String, String> customHeaders,
            CountDownLatch latch) {
        this.url = url;
        this.subProtocol = subProtocol;
        this.customHeaders = customHeaders;
        this.latch = latch;
    }

    /**
     * @return true if the handshake is done properly.
     * @throws URISyntaxException   throws if there is an error in the URI syntax.
     * @throws InterruptedException throws if the connecting the server is interrupted.
     */
    public boolean handhshake() throws InterruptedException, URISyntaxException, SSLException, ProtocolException {
        boolean isSuccess;
        URI uri = new URI(url);
        String scheme = uri.getScheme() == null ? "ws" : uri.getScheme();
        final String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
        final int port;
        if (uri.getPort() == -1) {
            if ("ws".equalsIgnoreCase(scheme)) {
                port = 80;
            } else if ("wss".equalsIgnoreCase(scheme)) {
                port = 443;
            } else {
                port = -1;
            }
        } else {
            port = uri.getPort();
        }

        if (!"ws".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
            logger.error("Only WS(S) is supported.");
            return false;
        }

        final boolean ssl = "wss".equalsIgnoreCase(scheme);
        final SslContext sslCtx;
        if (ssl) {
            sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        } else {
            sslCtx = null;
        }

        group = new NioEventLoopGroup();

        HttpHeaders headers = new DefaultHttpHeaders();
        for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
            headers.add(entry.getKey(), entry.getValue());
        }
        // Connect with V13 (RFC 6455 aka HyBi-17). You can change it to V08 or V00.
        // If you change it to V00, ping is not supported and remember to change
        // HttpResponseDecoder to WebSocketHttpResponseDecoder in the pipeline.
        handler = new WebSocketClientHandler(
                WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, subProtocol, true, headers),
                latch);

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ChannelPipeline p = ch.pipeline();
                if (sslCtx != null) {
                    p.addLast(sslCtx.newHandler(ch.alloc(), host, port));
                }
                p.addLast(new HttpClientCodec(), new HttpObjectAggregator(8192),
                        WebSocketClientCompressionHandler.INSTANCE, handler);
            }
        });

        channel = bootstrap.connect(uri.getHost(), port).sync().channel();
        isSuccess = handler.handshakeFuture().sync().isSuccess();
        logger.info("WebSocket Handshake successful : " + isSuccess);
        return isSuccess;
    }

    /**
     * Send text to the server.
     *
     * @param text text need to be sent.
     */
    public void sendText(String text) {
        if (channel == null) {
            logger.error("Channel is null. Cannot send text.");
            throw new IllegalArgumentException("Cannot find the channel to write");
        }
        channel.writeAndFlush(new TextWebSocketFrame(text));
    }

    /**
     * Send binary data to server.
     *
     * @param buf buffer containing the data need to be sent.
     */
    public void sendBinary(ByteBuffer buf) throws IOException {
        if (channel == null) {
            logger.error("Channel is null. Cannot send text.");
            throw new IllegalArgumentException("Cannot find the channel to write");
        }
        channel.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(buf)));
    }

    /**
     * Send a ping message to the server.
     *
     * @param buf content of the ping message to be sent.
     */
    public void sendPing(ByteBuffer buf) throws IOException {
        if (channel == null) {
            logger.error("Channel is null. Cannot send text.");
            throw new IllegalArgumentException("Cannot find the channel to write");
        }
        channel.writeAndFlush(new PingWebSocketFrame(Unpooled.wrappedBuffer(buf)));
    }

    /**
     * @return the text received from the server.
     */
    public String getTextReceived() {
        return handler.getTextReceived();
    }

    /**
     * @return the binary data received from the server.
     */
    public ByteBuffer getBufferReceived() {
        return handler.getBufferReceived();
    }

    /**
     * Check whether the connection is still open or not.
     *
     * @return true if connection is still open.
     */
    public boolean isOpen() {
        return handler.isOpen();
    }

    /**
     * Shutdown the WebSocket Client.
     */
    public void shutDown() throws InterruptedException {
        logger.info("Shutting Down WebSocket Client....");
        handler.shutDown();
        group.shutdownGracefully();
    }

}
