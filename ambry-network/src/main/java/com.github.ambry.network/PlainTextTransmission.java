package com.github.ambry.network;

import com.github.ambry.utils.Time;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Transmission used to speak plain text to the underlying channel.
 */
public class PlainTextTransmission extends Transmission {
  private static final Logger logger = LoggerFactory.getLogger(SSLTransmission.class);

  public PlainTextTransmission(String connectionId, SocketChannel socketChannel, SelectionKey key, Time time,
      NetworkMetrics metrics) {
    super(connectionId, socketChannel, key, time, metrics);
  }

  /**
   * No handshaking or anything of those prepare phase required for plain text interactions
   * @throws IOException
   */
  @Override
  public void prepare()
      throws IOException {
  }

  /**
   * Since there is no handshake, channel is always ready
   * @return
   */
  @Override
  public boolean ready() {
    return true;
  }

  /**
   * Reads a sequence of bytes from the channel into the {@NetworkReceive}
   *
   * @return The number of bytes read, possible zero or -1 if the channel has reached end-of-stream
   * @throws IOException if some other I/O error occurs
   */
  @Override
  public long read()
      throws IOException {
    if (!hasReceive()) {
      networkReceive = new NetworkReceive(getConnectionId(), new BoundedByteBufferReceive(), time);
    }
    long bytesRead = networkReceive.getReceivedBytes().readFrom(socketChannel);
    return bytesRead;
  }

  /**
   * Writes a sequence of bytes to the channel from the payload in {@NetworkSend}
   *
   * @returns true if {@Send} in {@NetworkSend} is complete (by writing all bytes to the channel), false otherwise
   * @throws IOException If some other I/O error occurs
   */
  @Override
  public boolean write()
      throws IOException {
    Send send = networkSend.getPayload();
    if (send == null) {
      throw new IllegalStateException("Registered for write interest but no response attached to key.");
    }
    send.writeTo(socketChannel);
    logger
        .trace("Bytes written to {} using key {}", socketChannel.socket().getRemoteSocketAddress(), getConnectionId());
    return send.isSendComplete();
  }

  /**
   * Close the connection for the socket channel
   */
  @Override
  public void close()
      throws IOException {
    key.attach(null);
    key.cancel();
    try {
      socketChannel.socket().close();
      socketChannel.close();
    } catch (IOException e) {
      metrics.selectorCloseSocketErrorCount.inc();
      logger.error("Exception closing connection to node {}:", getConnectionId(), e);
    }
  }
}
